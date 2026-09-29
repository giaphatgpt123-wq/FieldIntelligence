#!/usr/bin/env python3
"""SurvivalLibraryVN Data Engine V2-B multi-source collector.

The collector consumes small independent tasks produced by the AI Library Manager.
It never publishes records. It only emits field-level evidence for staging; the
Android Rule Engine remains the final publication authority.

Design goals:
- Vietnam-first source routing.
- One failed/rate-limited source never aborts unrelated tasks.
- Bounded retries and persisted source cooldown.
- Batch input/output so thousands of tasks do not require one process per species.
- Preserve source URI, publisher, rights/license and raw identifiers on evidence.
- No HTML scraping in this core; adapters use documented APIs or local authoritative packs.
"""

from __future__ import annotations

import argparse
import json
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = ROOT / "tools" / "data_engine_v2_sources.json"
DEFAULT_HEALTH = ROOT / "data" / "staging" / "source_health.json"
USER_AGENT = "SurvivalLibraryVN-DataEngineV2/2.0 (source-backed biodiversity ingestion)"
MAX_JSON_BYTES = 8 * 1024 * 1024
TIMEOUT_SECONDS = 35

TIER_ORDER_BY_FIELD: dict[str, list[str]] = {
    "CANONICAL_IDENTITY": [
        "GLOBAL_AUTHORITY",
        "OFFICIAL_VIETNAM",
        "SPECIALIST_VIETNAM",
        "OPEN_SCIENCE",
        "COMMUNITY_REFERENCE",
    ],
    "VIETNAMESE_PRIMARY_NAME": [
        "OFFICIAL_VIETNAM",
        "SPECIALIST_VIETNAM",
        "GLOBAL_AUTHORITY",
        "OPEN_SCIENCE",
        "COMMUNITY_REFERENCE",
    ],
    "VIETNAMESE_ALIASES": [
        "OFFICIAL_VIETNAM",
        "SPECIALIST_VIETNAM",
        "GLOBAL_AUTHORITY",
        "OPEN_SCIENCE",
        "COMMUNITY_REFERENCE",
    ],
    "VIETNAM_DISTRIBUTION": [
        "OFFICIAL_VIETNAM",
        "SPECIALIST_VIETNAM",
        "GLOBAL_AUTHORITY",
        "OPEN_SCIENCE",
        "COMMUNITY_REFERENCE",
    ],
    "MEDIA_PRIMARY": [
        "OFFICIAL_VIETNAM",
        "SPECIALIST_VIETNAM",
        "OPEN_SCIENCE",
        "GLOBAL_AUTHORITY",
        "COMMUNITY_REFERENCE",
    ],
    "MEDIA_DIAGNOSTIC_SET": [
        "OFFICIAL_VIETNAM",
        "SPECIALIST_VIETNAM",
        "OPEN_SCIENCE",
        "GLOBAL_AUTHORITY",
        "COMMUNITY_REFERENCE",
    ],
    "IDENTIFICATION_TRAITS": [
        "SPECIALIST_VIETNAM",
        "OFFICIAL_VIETNAM",
        "GLOBAL_AUTHORITY",
        "OPEN_SCIENCE",
        "COMMUNITY_REFERENCE",
    ],
    "CONFUSABLE_SPECIES": [
        "SPECIALIST_VIETNAM",
        "OFFICIAL_VIETNAM",
        "GLOBAL_AUTHORITY",
        "OPEN_SCIENCE",
        "COMMUNITY_REFERENCE",
    ],
    "USAGE_LEVEL": [
        "OFFICIAL_VIETNAM",
        "SPECIALIST_VIETNAM",
        "GLOBAL_AUTHORITY",
    ],
    "USAGE_CONTENT": [
        "OFFICIAL_VIETNAM",
        "SPECIALIST_VIETNAM",
        "GLOBAL_AUTHORITY",
    ],
    "SAFETY": [
        "OFFICIAL_VIETNAM",
        "GLOBAL_AUTHORITY",
        "SPECIALIST_VIETNAM",
    ],
}

VIETNAMESE_LANGUAGE_KEYS = {"vi", "vie", "vietnamese", "tiếng việt", "tieng viet"}
AUTOMATIC_MEDIA_LICENSES = {"cc0", "cc-by", "cc-by-sa", "cc by", "cc by-sa"}


@dataclass(frozen=True)
class SourceDefinition:
    source_id: str
    label: str
    tier: str
    automated: bool
    adapter: str | None
    capabilities: tuple[str, ...]
    categories: tuple[str, ...]
    base_url: str = ""
    data_path: str = ""
    reference_url: str = ""
    max_requests_per_minute: int = 60
    rights_policy: str = ""
    notes: str = ""

    @classmethod
    def from_json(cls, value: dict[str, Any]) -> "SourceDefinition":
        return cls(
            source_id=str(value["id"]),
            label=str(value.get("label") or value["id"]),
            tier=str(value["tier"]),
            automated=bool(value.get("automated", False)),
            adapter=value.get("adapter"),
            capabilities=tuple(str(x) for x in value.get("capabilities", [])),
            categories=tuple(str(x) for x in value.get("categories", ["*"])),
            base_url=str(value.get("baseUrl") or ""),
            data_path=str(value.get("dataPath") or ""),
            reference_url=str(value.get("referenceUrl") or ""),
            max_requests_per_minute=int(value.get("maxRequestsPerMinute") or 60),
            rights_policy=str(value.get("rightsPolicy") or ""),
            notes=str(value.get("notes") or ""),
        )


@dataclass(frozen=True)
class LoadTask:
    task_id: str
    canonical_id: str
    task_type: str
    field_key: str
    scientific_name: str
    category_id: str
    vietnamese_name: str = ""
    max_sources: int = 3

    @classmethod
    def from_json(cls, value: dict[str, Any]) -> "LoadTask":
        field_key = str(value.get("field") or value.get("fieldKey") or "")
        return cls(
            task_id=str(value["taskId"]),
            canonical_id=str(value["canonicalId"]),
            task_type=str(value.get("taskType") or ""),
            field_key=field_key,
            scientific_name=str(value.get("scientificName") or ""),
            category_id=str(value.get("categoryId") or ""),
            vietnamese_name=str(value.get("vietnameseName") or ""),
            max_sources=max(1, min(int(value.get("maxSources") or 3), 8)),
        )


@dataclass
class SourceHealth:
    failures: int = 0
    cooldown_until: float = 0.0
    last_error: str = ""


class SourceHealthStore:
    def __init__(self, path: Path | None):
        self.path = path
        self.rows: dict[str, SourceHealth] = {}
        if path and path.exists():
            try:
                raw = json.loads(path.read_text(encoding="utf-8"))
                for key, row in raw.get("sources", {}).items():
                    self.rows[str(key)] = SourceHealth(
                        failures=int(row.get("failures") or 0),
                        cooldown_until=float(row.get("cooldownUntil") or 0),
                        last_error=str(row.get("lastError") or ""),
                    )
            except Exception:
                # Health state is an optimization only; corrupted state must never block ingestion.
                self.rows = {}

    def available(self, source_id: str, now: float | None = None) -> bool:
        now = time.time() if now is None else now
        return self.rows.get(source_id, SourceHealth()).cooldown_until <= now

    def success(self, source_id: str) -> None:
        self.rows[source_id] = SourceHealth()
        self.save()

    def failure(self, source_id: str, error: str, now: float | None = None) -> None:
        now = time.time() if now is None else now
        old = self.rows.get(source_id, SourceHealth())
        failures = old.failures + 1
        # 30s, 60s, 120s... capped at 30 minutes.
        cooldown = min(30 * (2 ** max(0, failures - 1)), 1800)
        self.rows[source_id] = SourceHealth(
            failures=failures,
            cooldown_until=now + cooldown,
            last_error=error[:500],
        )
        self.save()

    def save(self) -> None:
        if not self.path:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "schemaVersion": 1,
            "updatedAt": int(time.time() * 1000),
            "sources": {
                key: {
                    "failures": row.failures,
                    "cooldownUntil": row.cooldown_until,
                    "lastError": row.last_error,
                }
                for key, row in sorted(self.rows.items())
            },
        }
        self.path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class JsonTransport:
    def __init__(self, timeout: int = TIMEOUT_SECONDS):
        self.timeout = timeout

    def get_json(self, url: str, params: dict[str, Any] | None = None) -> Any:
        if params:
            url = url + ("&" if "?" in url else "?") + urlencode(params, doseq=True)
        last: Exception | None = None
        for attempt in range(3):
            try:
                req = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
                with urlopen(req, timeout=self.timeout) as response:
                    declared = response.headers.get("Content-Length")
                    if declared and int(declared) > MAX_JSON_BYTES:
                        raise RuntimeError(f"JSON quá lớn: {declared} bytes")
                    raw = response.read(MAX_JSON_BYTES + 1)
                    if len(raw) > MAX_JSON_BYTES:
                        raise RuntimeError("JSON vượt giới hạn")
                    return json.loads(raw.decode("utf-8"))
            except HTTPError as exc:
                last = exc
                if exc.code not in {429, 500, 502, 503, 504}:
                    raise
                retry_after = exc.headers.get("Retry-After") if exc.headers else None
                requested = int(retry_after) if retry_after and retry_after.isdigit() else (2 ** attempt)
                time.sleep(min(requested, 5))
            except URLError as exc:
                last = exc
                time.sleep(min(2 ** attempt, 5))
        raise RuntimeError(f"HTTP JSON thất bại sau bounded retry: {last}")


class SourceAdapter:
    def __init__(self, source: SourceDefinition, transport: JsonTransport):
        self.source = source
        self.transport = transport

    def collect(self, task: LoadTask) -> list[dict[str, Any]]:
        raise NotImplementedError

    def evidence(self, task: LoadTask, value: Any, source_uri: str, **extra: Any) -> dict[str, Any]:
        row = {
            "canonicalId": task.canonical_id,
            "field": task.field_key,
            "value": value,
            "sourceId": self.source.source_id,
            "publisher": self.source.label,
            "sourceTier": self.source.tier,
            "sourceUri": source_uri,
            "collectedAt": int(time.time() * 1000),
            "verified": False,
        }
        row.update(extra)
        return row


class LocalAuthorityAdapter(SourceAdapter):
    def __init__(self, source: SourceDefinition, transport: JsonTransport):
        super().__init__(source, transport)
        self.path = ROOT / source.data_path

    def collect(self, task: LoadTask) -> list[dict[str, Any]]:
        if not self.path.exists():
            return []
        data = json.loads(self.path.read_text(encoding="utf-8"))
        records = data.get("records", []) if isinstance(data, dict) else []
        target = next(
            (
                row
                for row in records
                if str(row.get("scientificName") or "").casefold() == task.scientific_name.casefold()
                and str(row.get("categoryId") or task.category_id) == task.category_id
            ),
            None,
        )
        if not target:
            return []

        rows: list[dict[str, Any]] = []
        if task.field_key == "VIETNAMESE_ALIASES":
            values = target.get("aliases", [])
        else:
            fields = target.get("fields", {})
            value = fields.get(task.field_key)
            values = value if isinstance(value, list) else ([] if value is None else [value])

        for value in values:
            if isinstance(value, dict):
                source_uri = str(value.get("sourceUri") or target.get("sourceUri") or "")
                publisher = str(value.get("publisher") or target.get("publisher") or self.source.label)
                payload = value.get("value", value)
                rights = str(value.get("license") or value.get("rights") or "")
            else:
                source_uri = str(target.get("sourceUri") or "")
                publisher = str(target.get("publisher") or self.source.label)
                payload = value
                rights = ""
            if not source_uri.startswith("https://"):
                continue
            row = self.evidence(task, payload, source_uri, publisher=publisher, rights=rights)
            # Local authority packs are curated but still need field-level Rule Engine verification.
            row["curatedLocalAuthority"] = True
            rows.append(row)
        return rows


class GbifAdapter(SourceAdapter):
    def _match(self, scientific_name: str) -> tuple[int, dict[str, Any]]:
        url = self.source.base_url.rstrip("/") + "/v1/species/match"
        data = self.transport.get_json(url, {"name": scientific_name})
        if not isinstance(data, dict):
            raise RuntimeError("GBIF match response không phải object")
        key = data.get("usageKey") or data.get("key")
        if not key:
            return 0, data
        return int(key), data

    @staticmethod
    def _results(value: Any) -> list[dict[str, Any]]:
        if isinstance(value, list):
            return [x for x in value if isinstance(x, dict)]
        if isinstance(value, dict):
            rows = value.get("results", [])
            return [x for x in rows if isinstance(x, dict)] if isinstance(rows, list) else []
        return []

    def collect(self, task: LoadTask) -> list[dict[str, Any]]:
        if not task.scientific_name:
            return []
        key, match = self._match(task.scientific_name)
        match_uri = f"https://www.gbif.org/species/{key}" if key else "https://www.gbif.org/species/search"

        if task.field_key == "CANONICAL_IDENTITY":
            if not key:
                return []
            value = {
                "usageKey": key,
                "scientificName": match.get("scientificName") or match.get("canonicalName") or task.scientific_name,
                "canonicalName": match.get("canonicalName") or "",
                "rank": match.get("rank") or "",
                "status": match.get("status") or "",
                "matchType": match.get("matchType") or "",
                "confidence": match.get("confidence"),
            }
            return [self.evidence(task, value, match_uri, externalId=str(key))]

        if not key:
            return []

        if task.field_key == "VIETNAMESE_ALIASES":
            data = self.transport.get_json(self.source.base_url.rstrip("/") + f"/v1/species/{key}/vernacularNames")
            rows = []
            for item in self._results(data):
                language = str(item.get("language") or item.get("languageCode") or "").strip().casefold()
                name = str(item.get("vernacularName") or "").strip()
                if name and language in VIETNAMESE_LANGUAGE_KEYS:
                    rows.append(
                        self.evidence(
                            task,
                            {"displayName": name, "language": language, "country": item.get("country") or ""},
                            match_uri,
                        )
                    )
            return rows

        if task.field_key == "VIETNAM_DISTRIBUTION":
            data = self.transport.get_json(
                self.source.base_url.rstrip("/") + "/v1/occurrence/search",
                {"taxon_key": key, "country": "VN", "limit": 0},
            )
            count = int(data.get("count") or 0) if isinstance(data, dict) else 0
            if count <= 0:
                return []
            source_uri = f"https://www.gbif.org/occurrence/search?country=VN&taxon_key={key}"
            return [self.evidence(task, {"countryCode": "VN", "occurrenceCount": count}, source_uri)]

        if task.field_key in {"MEDIA_PRIMARY", "MEDIA_DIAGNOSTIC_SET"}:
            data = self.transport.get_json(self.source.base_url.rstrip("/") + f"/v1/species/{key}/media")
            rows = []
            for item in self._results(data):
                identifier = str(item.get("identifier") or item.get("references") or "")
                if not identifier.startswith("https://"):
                    continue
                rows.append(
                    self.evidence(
                        task,
                        {
                            "url": identifier,
                            "type": item.get("type") or "",
                            "title": item.get("title") or "",
                            "creator": item.get("creator") or "",
                            "license": item.get("license") or "",
                            "rightsHolder": item.get("rightsHolder") or "",
                        },
                        str(item.get("references") or match_uri),
                    )
                )
            return rows[:20]

        return []


class WormsAdapter(SourceAdapter):
    def _match(self, scientific_name: str) -> dict[str, Any] | None:
        url = self.source.base_url.rstrip("/") + "/AphiaRecordsByName/" + quote(scientific_name, safe="")
        data = self.transport.get_json(url, {"like": "false", "marine_only": "false", "offset": 1})
        rows = data if isinstance(data, list) else []
        exact = [x for x in rows if isinstance(x, dict) and str(x.get("scientificname") or "").casefold() == scientific_name.casefold()]
        candidates = exact or [x for x in rows if isinstance(x, dict)]
        return candidates[0] if candidates else None

    def collect(self, task: LoadTask) -> list[dict[str, Any]]:
        if task.category_id != "marine-life" or not task.scientific_name:
            return []
        record = self._match(task.scientific_name)
        if not record:
            return []
        aphia_id = int(record.get("AphiaID") or 0)
        valid_id = int(record.get("valid_AphiaID") or aphia_id)
        if not valid_id:
            return []
        source_uri = f"https://www.marinespecies.org/aphia.php?p=taxdetails&id={valid_id}"

        if task.field_key == "CANONICAL_IDENTITY":
            value = {
                "aphiaId": aphia_id,
                "validAphiaId": valid_id,
                "scientificName": record.get("scientificname") or task.scientific_name,
                "acceptedName": record.get("valid_name") or record.get("scientificname") or "",
                "authority": record.get("authority") or "",
                "rank": record.get("rank") or "",
                "status": record.get("status") or "",
            }
            return [self.evidence(task, value, source_uri, externalId=str(valid_id))]

        if task.field_key == "VIETNAMESE_ALIASES":
            data = self.transport.get_json(self.source.base_url.rstrip("/") + f"/AphiaVernacularsByAphiaID/{valid_id}")
            rows = []
            for item in data if isinstance(data, list) else []:
                if not isinstance(item, dict):
                    continue
                language = str(item.get("language_code") or item.get("language") or "").strip().casefold()
                name = str(item.get("vernacular") or "").strip()
                if name and language in VIETNAMESE_LANGUAGE_KEYS:
                    rows.append(self.evidence(task, {"displayName": name, "language": language}, source_uri))
            return rows

        if task.field_key == "VIETNAM_DISTRIBUTION":
            data = self.transport.get_json(self.source.base_url.rstrip("/") + f"/AphiaDistributionsByAphiaID/{valid_id}")
            rows = []
            for item in data if isinstance(data, list) else []:
                if not isinstance(item, dict):
                    continue
                haystack = " ".join(str(item.get(k) or "") for k in ("locality", "locationID", "higherGeography")).casefold()
                if "viet nam" not in haystack and "vietnam" not in haystack and " vn" not in haystack:
                    continue
                rows.append(self.evidence(task, item, source_uri))
            return rows

        return []


class INaturalistAdapter(SourceAdapter):
    def _taxon(self, scientific_name: str) -> dict[str, Any] | None:
        data = self.transport.get_json(
            self.source.base_url.rstrip("/") + "/v1/taxa",
            {"q": scientific_name, "locale": "vi", "per_page": 10},
        )
        rows = data.get("results", []) if isinstance(data, dict) else []
        exact = [x for x in rows if isinstance(x, dict) and str(x.get("name") or "").casefold() == scientific_name.casefold()]
        candidates = exact or [x for x in rows if isinstance(x, dict)]
        return candidates[0] if candidates else None

    def collect(self, task: LoadTask) -> list[dict[str, Any]]:
        if not task.scientific_name:
            return []
        taxon = self._taxon(task.scientific_name)
        if not taxon:
            return []
        taxon_id = int(taxon.get("id") or 0)
        source_uri = f"https://www.inaturalist.org/taxa/{taxon_id}" if taxon_id else "https://www.inaturalist.org/taxa"

        if task.field_key == "VIETNAMESE_ALIASES":
            name = str(taxon.get("preferred_common_name") or "").strip()
            if not name:
                return []
            # locale=vi requests Vietnamese localization, but this remains candidate evidence,
            # not sufficient by itself to mark a Vietnamese name verified.
            return [self.evidence(task, {"displayName": name, "localeRequested": "vi"}, source_uri, candidateOnly=True)]

        if task.field_key in {"MEDIA_PRIMARY", "MEDIA_DIAGNOSTIC_SET"}:
            photo = taxon.get("default_photo") if isinstance(taxon.get("default_photo"), dict) else None
            if not photo:
                return []
            license_code = str(photo.get("license_code") or "").strip().casefold()
            if license_code not in AUTOMATIC_MEDIA_LICENSES:
                return []
            image_url = str(photo.get("medium_url") or photo.get("url") or "")
            if not image_url.startswith("https://"):
                return []
            value = {
                "url": image_url,
                "license": license_code,
                "attribution": photo.get("attribution") or "",
                "photoId": photo.get("id"),
            }
            return [self.evidence(task, value, source_uri)]

        return []


ADAPTERS: dict[str, type[SourceAdapter]] = {
    "local_authority": LocalAuthorityAdapter,
    "gbif": GbifAdapter,
    "worms": WormsAdapter,
    "inaturalist": INaturalistAdapter,
}


class SourceRegistry:
    def __init__(self, sources: Iterable[SourceDefinition]):
        self.sources = list(sources)
        self.position = {source.source_id: index for index, source in enumerate(self.sources)}

    @classmethod
    def load(cls, path: Path = DEFAULT_REGISTRY) -> "SourceRegistry":
        data = json.loads(path.read_text(encoding="utf-8"))
        if int(data.get("schemaVersion") or 0) != 1:
            raise RuntimeError("Source registry schemaVersion không được hỗ trợ")
        return cls(SourceDefinition.from_json(row) for row in data.get("sources", []))

    def candidates(self, task: LoadTask) -> list[SourceDefinition]:
        tier_order = TIER_ORDER_BY_FIELD.get(task.field_key, [])
        rank = {tier: index for index, tier in enumerate(tier_order)}
        values = [
            source
            for source in self.sources
            if source.automated
            and source.adapter in ADAPTERS
            and task.field_key in source.capabilities
            and ("*" in source.categories or task.category_id in source.categories)
        ]
        return sorted(
            values,
            key=lambda source: (rank.get(source.tier, 999), self.position.get(source.source_id, 9999)),
        )


class BatchCollector:
    def __init__(
        self,
        registry: SourceRegistry,
        health: SourceHealthStore,
        transport: JsonTransport | None = None,
        adapter_overrides: dict[str, SourceAdapter] | None = None,
    ):
        self.registry = registry
        self.health = health
        self.transport = transport or JsonTransport()
        self.adapter_overrides = adapter_overrides or {}

    def _adapter(self, source: SourceDefinition) -> SourceAdapter:
        override = self.adapter_overrides.get(source.source_id)
        if override:
            return override
        adapter_cls = ADAPTERS.get(source.adapter or "")
        if not adapter_cls:
            raise RuntimeError(f"Chưa có adapter cho {source.source_id}")
        return adapter_cls(source, self.transport)

    def collect_task(self, task: LoadTask) -> dict[str, Any]:
        started = int(time.time() * 1000)
        if task.task_type == "PUBLISH_RECORD":
            return {
                "taskId": task.task_id,
                "canonicalId": task.canonical_id,
                "field": task.field_key,
                "status": "BLOCKED",
                "evidence": [],
                "attemptedSources": [],
                "errors": ["PUBLISH_RECORD chỉ được thực hiện sau LibraryRules trên app"],
                "startedAt": started,
                "finishedAt": int(time.time() * 1000),
            }

        candidates = self.registry.candidates(task)
        evidence: list[dict[str, Any]] = []
        attempted: list[str] = []
        errors: list[str] = []
        successful_sources = 0

        for source in candidates:
            if successful_sources >= task.max_sources:
                break
            if not self.health.available(source.source_id):
                continue
            attempted.append(source.source_id)
            try:
                rows = self._adapter(source).collect(task)
                self.health.success(source.source_id)
                if rows:
                    successful_sources += 1
                    evidence.extend(rows)
            except Exception as exc:
                message = f"{source.source_id}: {exc}"
                errors.append(message)
                self.health.failure(source.source_id, str(exc))
                # Continue to the next source: one source may never abort this task batch.
                continue

        if evidence:
            status = "COMPLETED"
        elif not candidates:
            status = "BLOCKED"
            errors.append("Không có automated adapter phù hợp cho field/category")
        else:
            status = "RETRY"
            if not errors:
                errors.append("Các nguồn hiện chưa trả evidence phù hợp; giữ task để bổ sung sau")

        return {
            "taskId": task.task_id,
            "canonicalId": task.canonical_id,
            "field": task.field_key,
            "status": status,
            "evidence": evidence,
            "attemptedSources": attempted,
            "errors": errors,
            "startedAt": started,
            "finishedAt": int(time.time() * 1000),
        }

    def collect_many(self, tasks: Iterable[LoadTask], limit: int = 500) -> list[dict[str, Any]]:
        rows = []
        for index, task in enumerate(tasks):
            if index >= limit:
                break
            # Per-task exception containment is deliberate; malformed/upstream failure on one
            # species must not prevent later species from being processed.
            try:
                rows.append(self.collect_task(task))
            except Exception as exc:
                rows.append(
                    {
                        "taskId": task.task_id,
                        "canonicalId": task.canonical_id,
                        "field": task.field_key,
                        "status": "RETRY",
                        "evidence": [],
                        "attemptedSources": [],
                        "errors": [f"collector task isolation: {exc}"],
                        "startedAt": int(time.time() * 1000),
                        "finishedAt": int(time.time() * 1000),
                    }
                )
        return rows


def read_tasks(path: Path) -> list[LoadTask]:
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    if text.startswith("["):
        raw_rows = json.loads(text)
    else:
        raw_rows = [json.loads(line) for line in text.splitlines() if line.strip()]
    return [LoadTask.from_json(row) for row in raw_rows]


def write_results(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.casefold() == ".jsonl":
        path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")
    else:
        path.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="SurvivalLibraryVN Data Engine V2-B batch collector")
    parser.add_argument("--tasks", type=Path, required=True, help="JSON array hoặc JSONL task file")
    parser.add_argument("--output", type=Path, required=True, help="Output .json hoặc .jsonl")
    parser.add_argument("--registry", type=Path, default=DEFAULT_REGISTRY)
    parser.add_argument("--health-file", type=Path, default=DEFAULT_HEALTH)
    parser.add_argument("--limit", type=int, default=500)
    args = parser.parse_args()

    if args.limit < 1 or args.limit > 5000:
        parser.error("--limit phải trong 1..5000")
    tasks = read_tasks(args.tasks)
    registry = SourceRegistry.load(args.registry)
    collector = BatchCollector(registry, SourceHealthStore(args.health_file))
    results = collector.collect_many(tasks, limit=args.limit)
    write_results(args.output, results)

    completed = sum(1 for row in results if row["status"] == "COMPLETED")
    retry = sum(1 for row in results if row["status"] == "RETRY")
    blocked = sum(1 for row in results if row["status"] == "BLOCKED")
    print(f"Data Engine V2: tasks={len(results)} completed={completed} retry={retry} blocked={blocked}")
    # RETRY is not a pipeline failure: it represents partial progress. BLOCKED indicates
    # unsupported work and should fail CI/manual runs that expect all task types supported.
    return 2 if blocked else 0


if __name__ == "__main__":
    raise SystemExit(main())
