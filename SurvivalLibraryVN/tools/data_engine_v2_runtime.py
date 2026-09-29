#!/usr/bin/env python3
"""Bounded production runtime for Data Engine V2.

Keeps the V2 evidence/publication contract unchanged while reducing avoidable latency:
- bounded in-memory GET cache for repeated taxonomy lookups;
- one cached adapter per source and one parsed local-authority index per adapter;
- bounded retry for malformed/transient JSON responses;
- token-bucket source throttling using registry maxRequestsPerMinute;
- buffered source-health persistence (one write at batch end, not one write per success);
- explicit terminal evidence for narrowly curated fields, avoiding redundant fallbacks.

The collector deliberately remains sequential. Publication/state code is not made concurrent
until its health/evidence stores have explicit transaction/thread-safety guarantees.
"""

from __future__ import annotations

import argparse
import json
import os
import threading
import time
from collections import OrderedDict
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

import data_engine_v2 as core

DEFAULT_TIMEOUT_SECONDS = 20
CACHE_MAX_ENTRIES = 256
CACHE_MAX_RESPONSE_BYTES = 256 * 1024
TRANSIENT_HTTP_CODES = {429, 500, 502, 503, 504}

# Terminal evidence is intentionally source+field specific. It does not mean an entire tier
# is authoritative for every field, and it does not relax app-side LibraryRules. These two
# local packs were curated exactly for the listed fields and already preserve provenance.
TERMINAL_EVIDENCE_FIELDS: dict[str, frozenset[str]] = {
    "vn-authority-local": frozenset({"VIETNAMESE_PRIMARY_NAME", "VIETNAMESE_ALIASES"}),
    "curated-canary-media": frozenset({"MEDIA_PRIMARY", "MEDIA_DIAGNOSTIC_SET"}),
}


class TransientPayloadError(RuntimeError):
    pass


class ResilientCachingJsonTransport:
    """Small bounded JSON cache + bounded retry for network and malformed payloads."""

    def __init__(self, timeout: int = DEFAULT_TIMEOUT_SECONDS, max_entries: int = CACHE_MAX_ENTRIES):
        self.timeout = max(5, min(int(timeout), core.TIMEOUT_SECONDS))
        self.max_entries = max(0, min(int(max_entries), 2048))
        self._cache: OrderedDict[str, Any] = OrderedDict()
        self._lock = threading.Lock()
        self.network_requests = 0
        self.cache_hits = 0
        self.retry_count = 0

    @staticmethod
    def final_url(url: str, params: dict[str, Any] | None = None) -> str:
        if not params:
            return url
        return url + ("&" if "?" in url else "?") + urlencode(params, doseq=True)

    def is_cached(self, url: str, params: dict[str, Any] | None = None) -> bool:
        key = self.final_url(url, params)
        with self._lock:
            return key in self._cache

    def _cache_get(self, key: str) -> Any | None:
        with self._lock:
            if key not in self._cache:
                return None
            value = self._cache.pop(key)
            self._cache[key] = value
            self.cache_hits += 1
            return value

    def _cache_put(self, key: str, value: Any, raw_size: int) -> None:
        if self.max_entries <= 0 or raw_size > CACHE_MAX_RESPONSE_BYTES:
            return
        with self._lock:
            self._cache.pop(key, None)
            self._cache[key] = value
            while len(self._cache) > self.max_entries:
                self._cache.popitem(last=False)

    def get_json(self, url: str, params: dict[str, Any] | None = None) -> Any:
        final_url = self.final_url(url, params)
        cached = self._cache_get(final_url)
        if cached is not None:
            return cached

        last: Exception | None = None
        for attempt in range(3):
            try:
                req = Request(final_url, headers={"User-Agent": core.USER_AGENT, "Accept": "application/json"})
                self.network_requests += 1
                with urlopen(req, timeout=self.timeout) as response:
                    declared = response.headers.get("Content-Length")
                    if declared and declared.isdigit() and int(declared) > core.MAX_JSON_BYTES:
                        raise RuntimeError(f"JSON quá lớn: {declared} bytes")
                    raw = response.read(core.MAX_JSON_BYTES + 1)
                    if len(raw) > core.MAX_JSON_BYTES:
                        raise RuntimeError("JSON vượt giới hạn")
                    try:
                        decoded = raw.decode("utf-8")
                        value = json.loads(decoded)
                    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                        prefix = raw[:120].decode("utf-8", errors="replace").replace("\n", " ")
                        raise TransientPayloadError(f"response không phải JSON hợp lệ: {prefix!r}") from exc
                    self._cache_put(final_url, value, len(raw))
                    return value
            except HTTPError as exc:
                last = exc
                if exc.code not in TRANSIENT_HTTP_CODES:
                    raise
                retry_after = exc.headers.get("Retry-After") if exc.headers else None
                requested = int(retry_after) if retry_after and retry_after.isdigit() else (2 ** attempt)
                self.retry_count += 1
                time.sleep(min(requested, 5))
            except (URLError, TimeoutError, TransientPayloadError) as exc:
                last = exc
                self.retry_count += 1
                time.sleep(min(2 ** attempt, 5))
        raise RuntimeError(f"HTTP JSON thất bại sau bounded retry: {last}")


class TokenBucketLimiter:
    """Average-rate limiter with a small startup burst to avoid needless pilot latency."""

    def __init__(self, requests_per_minute: int):
        rpm = max(1, int(requests_per_minute))
        self.rate_per_second = rpm / 60.0
        self.capacity = float(max(1, min(5, rpm)))
        self.tokens = self.capacity
        self.updated = time.monotonic()
        self._lock = threading.Lock()

    def wait(self) -> None:
        while True:
            with self._lock:
                now = time.monotonic()
                elapsed = max(0.0, now - self.updated)
                self.tokens = min(self.capacity, self.tokens + elapsed * self.rate_per_second)
                self.updated = now
                if self.tokens >= 1.0:
                    self.tokens -= 1.0
                    return
                delay = (1.0 - self.tokens) / self.rate_per_second
            time.sleep(min(max(delay, 0.01), 2.0))


class SourceBoundTransport:
    """Applies one limiter per source only on real network cache misses."""

    def __init__(self, base: ResilientCachingJsonTransport, max_requests_per_minute: int):
        self.base = base
        self.limiter = TokenBucketLimiter(max_requests_per_minute)

    def get_json(self, url: str, params: dict[str, Any] | None = None) -> Any:
        if not self.base.is_cached(url, params):
            self.limiter.wait()
        return self.base.get_json(url, params)


class BufferedSourceHealthStore(core.SourceHealthStore):
    """Keep batch state in memory and persist it once at the end."""

    def __init__(self, path: Path | None):
        super().__init__(path)
        self.dirty = False

    def success(self, source_id: str) -> None:
        self.rows[source_id] = core.SourceHealth()
        self.dirty = True

    def failure(self, source_id: str, error: str, now: float | None = None) -> None:
        now = time.time() if now is None else now
        old = self.rows.get(source_id, core.SourceHealth())
        failures = old.failures + 1
        cooldown = min(30 * (2 ** max(0, failures - 1)), 1800)
        self.rows[source_id] = core.SourceHealth(
            failures=failures,
            cooldown_until=now + cooldown,
            last_error=error[:500],
        )
        self.dirty = True

    def flush(self) -> None:
        if self.dirty:
            super().save()
            self.dirty = False


class CachedLocalAuthorityAdapter(core.SourceAdapter):
    """Parse and index a local evidence pack once per source adapter instance."""

    def __init__(self, source: core.SourceDefinition, transport: Any):
        super().__init__(source, transport)
        self.path = core.ROOT / source.data_path
        self._loaded = False
        self._index: dict[tuple[str, str], dict[str, Any]] = {}

    def _load(self) -> None:
        if self._loaded:
            return
        self._loaded = True
        if not self.path.exists():
            return
        data = json.loads(self.path.read_text(encoding="utf-8"))
        records = data.get("records", []) if isinstance(data, dict) else []
        for row in records:
            if not isinstance(row, dict):
                continue
            scientific_name = str(row.get("scientificName") or "").strip().casefold()
            category_id = str(row.get("categoryId") or "").strip()
            if scientific_name:
                self._index[(scientific_name, category_id)] = row

    def collect(self, task: core.LoadTask) -> list[dict[str, Any]]:
        self._load()
        target = self._index.get((task.scientific_name.casefold(), task.category_id))
        if not target:
            return []

        if task.field_key == "VIETNAMESE_ALIASES":
            values = target.get("aliases", [])
        else:
            fields = target.get("fields", {})
            value = fields.get(task.field_key) if isinstance(fields, dict) else None
            values = value if isinstance(value, list) else ([] if value is None else [value])

        rows: list[dict[str, Any]] = []
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
            row["curatedLocalAuthority"] = True
            rows.append(row)
        return rows


RUNTIME_ADAPTERS: dict[str, type[core.SourceAdapter]] = dict(core.ADAPTERS)
RUNTIME_ADAPTERS["local_authority"] = CachedLocalAuthorityAdapter


class OptimizedBatchCollector(core.BatchCollector):
    """Reuse adapters/transports, short-circuit curated evidence, flush health once."""

    def __init__(
        self,
        registry: core.SourceRegistry,
        health: BufferedSourceHealthStore,
        transport: ResilientCachingJsonTransport,
        adapter_overrides: dict[str, core.SourceAdapter] | None = None,
    ):
        super().__init__(registry, health, transport, adapter_overrides)
        self._adapter_cache: dict[str, core.SourceAdapter] = {}

    def _adapter(self, source: core.SourceDefinition) -> core.SourceAdapter:
        override = self.adapter_overrides.get(source.source_id)
        if override:
            return override
        existing = self._adapter_cache.get(source.source_id)
        if existing is not None:
            return existing
        adapter_cls = RUNTIME_ADAPTERS.get(source.adapter or "")
        if not adapter_cls:
            raise RuntimeError(f"Chưa có adapter cho {source.source_id}")
        bound_transport = SourceBoundTransport(self.transport, source.max_requests_per_minute)
        adapter = adapter_cls(source, bound_transport)
        self._adapter_cache[source.source_id] = adapter
        return adapter

    @staticmethod
    def _is_terminal_evidence(source: core.SourceDefinition, task: core.LoadTask, rows: list[dict[str, Any]]) -> bool:
        return bool(rows) and task.field_key in TERMINAL_EVIDENCE_FIELDS.get(source.source_id, frozenset())

    def collect_task(self, task: core.LoadTask) -> dict[str, Any]:
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
                    if self._is_terminal_evidence(source, task, rows):
                        break
            except Exception as exc:
                message = f"{source.source_id}: {exc}"
                errors.append(message)
                self.health.failure(source.source_id, str(exc))
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

    def collect_many(self, tasks: Iterable[core.LoadTask], limit: int = 500) -> list[dict[str, Any]]:
        try:
            return super().collect_many(tasks, limit=limit)
        finally:
            if isinstance(self.health, BufferedSourceHealthStore):
                self.health.flush()


def main() -> int:
    parser = argparse.ArgumentParser(description="SurvivalLibraryVN Data Engine V2 bounded runtime")
    parser.add_argument("--tasks", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--registry", type=Path, default=core.DEFAULT_REGISTRY)
    parser.add_argument("--health-file", type=Path, default=core.DEFAULT_HEALTH)
    parser.add_argument("--limit", type=int, default=500)
    args = parser.parse_args()

    if args.limit < 1 or args.limit > 5000:
        parser.error("--limit phải trong 1..5000")

    timeout = int(os.environ.get("DATA_ENGINE_HTTP_TIMEOUT", DEFAULT_TIMEOUT_SECONDS))
    transport = ResilientCachingJsonTransport(timeout=timeout)
    health = BufferedSourceHealthStore(args.health_file)
    registry = core.SourceRegistry.load(args.registry)
    collector = OptimizedBatchCollector(registry, health, transport)
    tasks = core.read_tasks(args.tasks)

    started = time.monotonic()
    results = collector.collect_many(tasks, limit=args.limit)
    elapsed = time.monotonic() - started
    core.write_results(args.output, results)

    completed = sum(1 for row in results if row["status"] == "COMPLETED")
    retry = sum(1 for row in results if row["status"] == "RETRY")
    blocked = sum(1 for row in results if row["status"] == "BLOCKED")
    print(
        "Data Engine V2 runtime: "
        f"tasks={len(results)} completed={completed} retry={retry} blocked={blocked} "
        f"elapsed={elapsed:.1f}s networkRequests={transport.network_requests} "
        f"cacheHits={transport.cache_hits} payloadRetries={transport.retry_count}"
    )
    return 2 if blocked else 0


if __name__ == "__main__":
    raise SystemExit(main())
