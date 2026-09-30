#!/usr/bin/env python3
"""Merge Data Engine V2 collector results into persistent, device-syncable progress state.

This script does not publish library records. It keeps field-level evidence, task status,
source health, aliases and a compact staging snapshot. Only evidence that satisfies a
field-specific conservative verification rule is marked verified. Media, usage and safety
remain behind their dedicated quality/evidence gates.

V2-F persists evidence as category shards. The legacy evidence-store.json is read only as a
one-time migration source; it is no longer rewritten on every collector run.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import time
from pathlib import Path
from typing import Any

from data_engine_v2_shards import (
    category_lookup,
    load_all_rows,
    migrate_legacy_if_needed,
    rows_from_results,
    update_touched_shards,
)

ROOT = Path(__file__).resolve().parents[1]
STAGING = ROOT / "data" / "staging"
DEFAULT_TASKS = STAGING / "active-tasks.json"
DEFAULT_RESULTS = STAGING / "latest-collector-results.json"
DEFAULT_EVIDENCE = STAGING / "evidence-store.json"  # legacy migration source only
DEFAULT_EVIDENCE_DIR = STAGING / "evidence"
DEFAULT_EVIDENCE_INDEX = STAGING / "evidence-index.json"
DEFAULT_HEALTH = STAGING / "source-health-pipeline.json"
DEFAULT_SNAPSHOT = STAGING / "progress-snapshot.json"
DEFAULT_INDEX = STAGING / "progress-index.json"
RAW_ROOT = "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/SurvivalLibraryVN/data/staging/"

FIELD_TO_TASK = {
    "CANONICAL_IDENTITY": "RESOLVE_CANONICAL",
    "VIETNAMESE_PRIMARY_NAME": "COLLECT_VIETNAMESE_NAMES",
    "VIETNAMESE_ALIASES": "COLLECT_VIETNAMESE_NAMES",
    "VIETNAM_DISTRIBUTION": "COLLECT_DISTRIBUTION",
    "MEDIA_PRIMARY": "COLLECT_MEDIA",
    "MEDIA_DIAGNOSTIC_SET": "COLLECT_MEDIA",
    "IDENTIFICATION_TRAITS": "COLLECT_IDENTIFICATION",
    "CONFUSABLE_SPECIES": "COLLECT_CONFUSABLE",
    "USAGE_LEVEL": "COLLECT_USAGE",
    "USAGE_CONTENT": "COLLECT_USAGE",
    "SAFETY": "COLLECT_SAFETY",
}

FIELD_PRIORITY = {
    "CANONICAL_IDENTITY": 100,
    "SAFETY": 98,
    "VIETNAMESE_PRIMARY_NAME": 96,
    "VIETNAM_DISTRIBUTION": 93,
    "MEDIA_PRIMARY": 92,
    "VIETNAMESE_ALIASES": 88,
    "MEDIA_DIAGNOSTIC_SET": 84,
    "IDENTIFICATION_TRAITS": 80,
    "CONFUSABLE_SPECIES": 76,
    "USAGE_LEVEL": 66,
    "USAGE_CONTENT": 60,
}

PREFERRED_TIERS = {
    "CANONICAL_IDENTITY": ["GLOBAL_AUTHORITY", "OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM", "OPEN_SCIENCE"],
    "VIETNAMESE_PRIMARY_NAME": ["OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM", "GLOBAL_AUTHORITY", "OPEN_SCIENCE", "COMMUNITY_REFERENCE"],
    "VIETNAMESE_ALIASES": ["OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM", "GLOBAL_AUTHORITY", "OPEN_SCIENCE", "COMMUNITY_REFERENCE"],
    "VIETNAM_DISTRIBUTION": ["OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM", "GLOBAL_AUTHORITY", "OPEN_SCIENCE", "COMMUNITY_REFERENCE"],
    "MEDIA_PRIMARY": ["OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM", "OPEN_SCIENCE", "GLOBAL_AUTHORITY", "COMMUNITY_REFERENCE"],
    "MEDIA_DIAGNOSTIC_SET": ["OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM", "OPEN_SCIENCE", "GLOBAL_AUTHORITY", "COMMUNITY_REFERENCE"],
    "IDENTIFICATION_TRAITS": ["SPECIALIST_VIETNAM", "OFFICIAL_VIETNAM", "GLOBAL_AUTHORITY", "OPEN_SCIENCE"],
    "CONFUSABLE_SPECIES": ["SPECIALIST_VIETNAM", "OFFICIAL_VIETNAM", "GLOBAL_AUTHORITY", "OPEN_SCIENCE"],
    "USAGE_LEVEL": ["OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM", "GLOBAL_AUTHORITY"],
    "USAGE_CONTENT": ["OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM", "GLOBAL_AUTHORITY"],
    "SAFETY": ["OFFICIAL_VIETNAM", "GLOBAL_AUTHORITY", "SPECIALIST_VIETNAM"],
}

TRUSTED_VIETNAM_TIERS = {"OFFICIAL_VIETNAM", "SPECIALIST_VIETNAM"}
VIETNAM_REGION_KEYS = {"việt nam", "viet nam", "vietnam", "vn"}
VIETNAMESE_LANGUAGE_KEYS = {"vi", "vie", "vietnamese", "tiếng việt", "tieng viet"}


def load_json(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def evidence_key(row: dict[str, Any]) -> str:
    material = {
        "canonicalId": row.get("canonicalId"),
        "field": row.get("field"),
        "sourceId": row.get("sourceId"),
        "sourceUri": row.get("sourceUri"),
        "value": row.get("value"),
    }
    return hashlib.sha256(canonical_json(material).encode("utf-8")).hexdigest()


def trusted_curated_vietnam_row(row: dict[str, Any]) -> bool:
    """Return True only for locally curated evidence from an approved Vietnam source tier."""
    tier = str(row.get("sourceTier") or "").strip().upper()
    source_uri = str(row.get("sourceUri") or "").strip()
    return (
        tier in TRUSTED_VIETNAM_TIERS
        and row.get("curatedLocalAuthority") is True
        and source_uri.startswith("https://")
    )


def identity_auto_verified(evidence: list[dict[str, Any]]) -> bool:
    for row in evidence:
        tier = str(row.get("sourceTier") or "")
        value = row.get("value") if isinstance(row.get("value"), dict) else {}
        if trusted_curated_vietnam_row(row):
            return True
        if tier != "GLOBAL_AUTHORITY":
            continue
        match_type = str(value.get("matchType") or "").upper()
        confidence = value.get("confidence")
        if match_type == "EXACT" and isinstance(confidence, (int, float)) and confidence >= 95:
            return True
        if value.get("validAphiaId") and str(value.get("status") or "").lower() in {"accepted", "valid"}:
            return True
    return False


def vietnamese_name_evidence_verified(row: dict[str, Any]) -> bool:
    if not trusted_curated_vietnam_row(row):
        return False
    value = row.get("value") if isinstance(row.get("value"), dict) else {}
    display_name = str(value.get("displayName") or "").strip()
    language = str(value.get("language") or "").strip().casefold()
    region = str(value.get("region") or value.get("country") or "").strip().casefold()
    country_code = str(value.get("countryCode") or "").strip().upper()
    language_ok = language in VIETNAMESE_LANGUAGE_KEYS
    region_ok = region in VIETNAM_REGION_KEYS or country_code == "VN"
    return bool(display_name and language_ok and region_ok)


def vietnamese_name_auto_verified(evidence: list[dict[str, Any]]) -> bool:
    return any(vietnamese_name_evidence_verified(row) for row in evidence)


def vietnam_distribution_evidence_verified(row: dict[str, Any]) -> bool:
    if not trusted_curated_vietnam_row(row):
        return False
    value = row.get("value") if isinstance(row.get("value"), dict) else {}
    country_code = str(value.get("countryCode") or "").strip().upper()
    country = str(value.get("country") or value.get("region") or "").strip().casefold()
    return country_code == "VN" or country in VIETNAM_REGION_KEYS


def vietnam_distribution_auto_verified(evidence: list[dict[str, Any]]) -> bool:
    return any(vietnam_distribution_evidence_verified(row) for row in evidence)


def field_verified(field: str, evidence: list[dict[str, Any]]) -> bool:
    if field == "CANONICAL_IDENTITY":
        return identity_auto_verified(evidence)
    if field in {"VIETNAMESE_PRIMARY_NAME", "VIETNAMESE_ALIASES"}:
        return vietnamese_name_auto_verified(evidence)
    if field == "VIETNAM_DISTRIBUTION":
        return vietnam_distribution_auto_verified(evidence)
    return False


def merge_evidence(existing: list[dict[str, Any]], results: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Compatibility helper kept for unit tests and callers outside the sharded persistence path."""
    rows: dict[str, dict[str, Any]] = {}
    for row in existing:
        if isinstance(row, dict):
            rows[evidence_key(row)] = row
    for result in results:
        if not isinstance(result, dict):
            continue
        for row in result.get("evidence", []):
            if not isinstance(row, dict):
                continue
            rows[evidence_key(row)] = row
    return sorted(
        rows.values(),
        key=lambda row: (
            str(row.get("canonicalId") or ""),
            str(row.get("field") or ""),
            str(row.get("sourceId") or ""),
            str(row.get("sourceUri") or ""),
        ),
    )


def load_version(index_path: Path) -> int:
    if not index_path.exists():
        return 0
    try:
        return int(load_json(index_path, {}).get("snapshot", {}).get("version") or 0)
    except Exception:
        return 0


def normalize_status(raw: str) -> str:
    value = raw.upper().strip()
    return value if value in {"PENDING", "RUNNING", "RETRY", "COMPLETED", "BLOCKED"} else "RETRY"


def build_snapshot(
    tasks: list[dict[str, Any]],
    results: list[dict[str, Any]],
    evidence: list[dict[str, Any]],
    source_health: dict[str, Any],
    version: int,
) -> dict[str, Any]:
    now = int(time.time() * 1000)
    result_by_task = {str(row.get("taskId")): row for row in results if isinstance(row, dict) and row.get("taskId")}

    entity_seed: dict[str, dict[str, Any]] = {}
    for task in tasks:
        canonical_id = str(task["canonicalId"])
        entity_seed.setdefault(
            canonical_id,
            {
                "canonicalId": canonical_id,
                "categoryId": str(task.get("categoryId") or ""),
                "scientificName": str(task.get("scientificName") or ""),
                "vietnameseName": str(task.get("vietnameseName") or ""),
                "highRisk": str(task.get("categoryId") or "") == "danger",
                "published": False,
                "updatedAt": now,
            },
        )

    evidence_by_field: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for row in evidence:
        key = (str(row.get("canonicalId") or ""), str(row.get("field") or ""))
        if key[0] and key[1]:
            evidence_by_field.setdefault(key, []).append(row)

    fields: list[dict[str, Any]] = []
    aliases: dict[tuple[str, str, str], dict[str, Any]] = {}
    for (canonical_id, field), rows in sorted(evidence_by_field.items()):
        source_keys = sorted({str(row.get("sourceId") or "") for row in rows if row.get("sourceId")})
        fields.append(
            {
                "canonicalId": canonical_id,
                "field": field,
                "valuePresent": bool(rows),
                "evidenceCount": len(rows),
                "verified": field_verified(field, rows),
                "sourceKey": source_keys[0] if len(source_keys) == 1 else ("multi-source" if source_keys else ""),
                "lastError": "",
                "updatedAt": max(int(row.get("collectedAt") or now) for row in rows),
                "valueJson": "",
            }
        )
        if field == "VIETNAMESE_ALIASES":
            for row in rows:
                value = row.get("value") if isinstance(row.get("value"), dict) else {}
                display = str(value.get("displayName") or "").strip()
                if not display:
                    continue
                region = str(value.get("region") or value.get("country") or "").strip()
                key = (canonical_id, display.casefold(), region.casefold())
                aliases[key] = {
                    "canonicalId": canonical_id,
                    "displayName": display,
                    "aliasType": "OTHER_NAME",
                    "region": region,
                    "sourceKey": str(row.get("sourceId") or ""),
                    "verified": vietnamese_name_evidence_verified(row),
                    "updatedAt": int(row.get("collectedAt") or now),
                }

    for entity in entity_seed.values():
        display = str(entity.get("vietnameseName") or "").strip()
        if display:
            key = (entity["canonicalId"], display.casefold(), "")
            aliases.setdefault(
                key,
                {
                    "canonicalId": entity["canonicalId"],
                    "displayName": display,
                    "aliasType": "PRIMARY",
                    "region": "",
                    "sourceKey": "pilot-seed",
                    "verified": False,
                    "updatedAt": now,
                },
            )

    task_rows: list[dict[str, Any]] = []
    for task in tasks:
        task_id = str(task["taskId"])
        result = result_by_task.get(task_id)
        field = str(task.get("field") or task.get("fieldKey") or "")
        status = normalize_status(str(result.get("status") if result else "PENDING"))
        errors = result.get("errors", []) if result else []
        last_error = " | ".join(str(error) for error in errors if error)[:1000]
        task_rows.append(
            {
                "taskId": task_id,
                "canonicalId": str(task["canonicalId"]),
                "taskType": str(task.get("taskType") or FIELD_TO_TASK.get(field, "VERIFY_FIELD")),
                "field": field,
                "priority": int(task.get("priority") or FIELD_PRIORITY.get(field, 0)),
                "preferredSourceTiers": PREFERRED_TIERS.get(field, []),
                "status": status,
                "attempts": 1 if result else 0,
                "retryAfter": 0,
                "lastError": last_error,
                "updatedAt": int(result.get("finishedAt") or now) if result else now,
            }
        )

    health_rows: list[dict[str, Any]] = []
    for source_key, row in sorted((source_health.get("sources") or {}).items()):
        if not isinstance(row, dict):
            continue
        health_rows.append(
            {
                "sourceKey": source_key,
                "tier": "",
                "consecutiveFailures": int(row.get("failures") or 0),
                "cooldownUntil": int(float(row.get("cooldownUntil") or 0) * 1000),
                "lastError": str(row.get("lastError") or ""),
                "updatedAt": int(source_health.get("updatedAt") or now),
            }
        )

    return {
        "schemaVersion": 1,
        "version": version,
        "generatedAt": now,
        "entities": sorted(entity_seed.values(), key=lambda row: (row["categoryId"], row["canonicalId"])),
        "aliases": sorted(aliases.values(), key=lambda row: (row["canonicalId"], row["aliasType"], row["displayName"])),
        "fields": fields,
        "tasks": sorted(task_rows, key=lambda row: (-row["priority"], row["taskId"])),
        "sourceHealth": health_rows,
    }


def write_json(path: Path, value: Any) -> bytes:
    raw = (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n").encode("utf-8")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)
    return raw


def main() -> int:
    parser = argparse.ArgumentParser(description="Merge V2 collector evidence and build Android progress snapshot")
    parser.add_argument("--tasks", type=Path, default=DEFAULT_TASKS)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--evidence", type=Path, default=DEFAULT_EVIDENCE, help="Legacy monolithic migration source")
    parser.add_argument("--evidence-dir", type=Path, default=DEFAULT_EVIDENCE_DIR)
    parser.add_argument("--evidence-index", type=Path, default=DEFAULT_EVIDENCE_INDEX)
    parser.add_argument("--health", type=Path, default=DEFAULT_HEALTH)
    parser.add_argument("--snapshot", type=Path, default=DEFAULT_SNAPSHOT)
    parser.add_argument("--index", type=Path, default=DEFAULT_INDEX)
    args = parser.parse_args()

    tasks = load_json(args.tasks, [])
    results = load_json(args.results, [])
    if not isinstance(tasks, list) or not isinstance(results, list):
        raise RuntimeError("tasks/results phải là JSON arrays")
    task_ids = [str(row.get("taskId") or "") for row in tasks if isinstance(row, dict)]
    if not task_ids or len(task_ids) != len(set(task_ids)):
        raise RuntimeError("Task seed trống hoặc có taskId trùng")

    mapping = category_lookup(tasks)
    migrated = migrate_legacy_if_needed(args.evidence, args.evidence_dir, args.evidence_index, mapping)
    incoming_rows = rows_from_results(results)
    touched = update_touched_shards(args.evidence_dir, args.evidence_index, incoming_rows, mapping)
    merged_evidence = load_all_rows(args.evidence_dir, args.evidence_index)

    version = load_version(args.index) + 1
    health = load_json(args.health, {"schemaVersion": 1, "sources": {}})
    snapshot = build_snapshot(tasks, results, merged_evidence, health, version)
    snapshot_raw = write_json(args.snapshot, snapshot)
    digest = hashlib.sha256(snapshot_raw).hexdigest()

    evidence_index = load_json(args.evidence_index, {})
    index = {
        "schemaVersion": 1,
        "channel": "data-engine-v2-pilot",
        "snapshot": {
            "version": version,
            "schemaVersion": 1,
            "generatedAt": snapshot["generatedAt"],
            "sha256": digest,
            "snapshotUrl": RAW_ROOT + args.snapshot.name,
            "entityCount": len(snapshot["entities"]),
            "taskCount": len(snapshot["tasks"]),
        },
        "evidence": {
            "schemaVersion": int(evidence_index.get("schemaVersion") or 1),
            "evidenceCount": int(evidence_index.get("evidenceCount") or len(merged_evidence)),
            "shardCount": int(evidence_index.get("shardCount") or 0),
            "indexUrl": RAW_ROOT + args.evidence_index.name,
        },
    }
    write_json(args.index, index)

    completed = sum(1 for row in snapshot["tasks"] if row["status"] == "COMPLETED")
    retry = sum(1 for row in snapshot["tasks"] if row["status"] == "RETRY")
    blocked = sum(1 for row in snapshot["tasks"] if row["status"] == "BLOCKED")
    verified = sum(1 for row in snapshot["fields"] if row["verified"])
    print(
        f"V2 state: entities={len(snapshot['entities'])} tasks={len(snapshot['tasks'])} "
        f"completed={completed} retry={retry} blocked={blocked} evidence={len(merged_evidence)} "
        f"verifiedFields={verified} migratedLegacy={migrated} touchedShards={len(touched)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
