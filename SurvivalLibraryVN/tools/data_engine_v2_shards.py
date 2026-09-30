#!/usr/bin/env python3
"""Sharded persistent evidence store for Data Engine V2.

Evidence is partitioned by library category so an update to one category does not rewrite
all historical evidence. The legacy monolithic evidence-store.json is supported as a
one-time migration source. Shards remain JSON for auditability and easy rollback in Git.
"""

from __future__ import annotations

import hashlib
import json
import re
import time
from pathlib import Path
from typing import Any, Iterable

SCHEMA_VERSION = 1
MAX_SHARD_BYTES = 5 * 1024 * 1024


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


def safe_shard_name(category_id: str) -> str:
    value = re.sub(r"[^a-z0-9._-]+", "-", category_id.strip().lower()).strip("-._")
    return value or "uncategorized"


def read_json(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def encode_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n").encode("utf-8")


def write_json(path: Path, value: Any) -> bytes:
    raw = encode_json(value)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)
    return raw


def category_lookup(tasks: Iterable[dict[str, Any]]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    for task in tasks:
        if not isinstance(task, dict):
            continue
        canonical_id = str(task.get("canonicalId") or "").strip()
        category_id = str(task.get("categoryId") or "").strip()
        if not canonical_id:
            continue
        previous = mapping.get(canonical_id)
        if previous and category_id and previous != category_id:
            raise RuntimeError(f"Canonical ID {canonical_id} xuất hiện ở nhiều category: {previous}, {category_id}")
        if category_id:
            mapping[canonical_id] = category_id
    return mapping


def category_for_row(row: dict[str, Any], mapping: dict[str, str]) -> str:
    canonical_id = str(row.get("canonicalId") or "").strip()
    direct = str(row.get("categoryId") or "").strip()
    mapped = mapping.get(canonical_id, "")
    if direct and mapped and direct != mapped:
        raise RuntimeError(f"Evidence category xung đột cho {canonical_id}: {direct} != {mapped}")
    return direct or mapped or "uncategorized"


def rows_from_results(results: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for result in results:
        if not isinstance(result, dict):
            continue
        for row in result.get("evidence", []):
            if isinstance(row, dict):
                rows.append(row)
    return rows


def load_legacy_rows(legacy_path: Path) -> list[dict[str, Any]]:
    payload = read_json(legacy_path, {"evidence": []})
    if not isinstance(payload, dict):
        return []
    rows = payload.get("evidence", [])
    return [row for row in rows if isinstance(row, dict)] if isinstance(rows, list) else []


def load_shard_rows(shard_path: Path) -> list[dict[str, Any]]:
    payload = read_json(shard_path, {"evidence": []})
    if not isinstance(payload, dict):
        raise RuntimeError(f"Evidence shard không hợp lệ: {shard_path}")
    rows = payload.get("evidence", [])
    if not isinstance(rows, list):
        raise RuntimeError(f"Evidence shard thiếu mảng evidence: {shard_path}")
    return [row for row in rows if isinstance(row, dict)]


def merge_rows(existing: Iterable[dict[str, Any]], incoming: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    merged: dict[str, dict[str, Any]] = {}
    for row in existing:
        if isinstance(row, dict):
            merged[evidence_key(row)] = row
    for row in incoming:
        if isinstance(row, dict):
            merged[evidence_key(row)] = row
    return sorted(
        merged.values(),
        key=lambda row: (
            str(row.get("canonicalId") or ""),
            str(row.get("field") or ""),
            str(row.get("sourceId") or ""),
            str(row.get("sourceUri") or ""),
            evidence_key(row),
        ),
    )


def _existing_index(index_path: Path) -> dict[str, Any]:
    payload = read_json(index_path, {"schemaVersion": SCHEMA_VERSION, "shards": []})
    if not isinstance(payload, dict):
        return {"schemaVersion": SCHEMA_VERSION, "shards": []}
    return payload


def shard_paths_from_index(index_path: Path, shard_dir: Path) -> dict[str, Path]:
    payload = _existing_index(index_path)
    result: dict[str, Path] = {}
    for item in payload.get("shards", []):
        if not isinstance(item, dict):
            continue
        category_id = str(item.get("categoryId") or "").strip()
        file_name = str(item.get("file") or "").strip()
        if category_id and file_name:
            result[category_id] = shard_dir / Path(file_name).name
    return result


def migrate_legacy_if_needed(
    legacy_path: Path,
    shard_dir: Path,
    index_path: Path,
    mapping: dict[str, str],
) -> bool:
    if index_path.exists() or not legacy_path.exists():
        return False
    legacy_rows = load_legacy_rows(legacy_path)
    if not legacy_rows:
        return False
    grouped: dict[str, list[dict[str, Any]]] = {}
    for row in legacy_rows:
        grouped.setdefault(category_for_row(row, mapping), []).append(row)
    now = int(time.time() * 1000)
    for category_id, rows in grouped.items():
        file_name = safe_shard_name(category_id) + ".json"
        write_json(
            shard_dir / file_name,
            {
                "schemaVersion": SCHEMA_VERSION,
                "categoryId": category_id,
                "updatedAt": now,
                "evidenceCount": len(merge_rows([], rows)),
                "evidence": merge_rows([], rows),
            },
        )
    rebuild_index(shard_dir, index_path)
    return True


def update_touched_shards(
    shard_dir: Path,
    index_path: Path,
    incoming_rows: Iterable[dict[str, Any]],
    mapping: dict[str, str],
) -> set[str]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    for row in incoming_rows:
        grouped.setdefault(category_for_row(row, mapping), []).append(row)
    touched: set[str] = set()
    now = int(time.time() * 1000)
    known = shard_paths_from_index(index_path, shard_dir)
    for category_id, rows in grouped.items():
        shard_path = known.get(category_id, shard_dir / (safe_shard_name(category_id) + ".json"))
        existing = load_shard_rows(shard_path) if shard_path.exists() else []
        merged = merge_rows(existing, rows)
        raw = encode_json(
            {
                "schemaVersion": SCHEMA_VERSION,
                "categoryId": category_id,
                "updatedAt": now,
                "evidenceCount": len(merged),
                "evidence": merged,
            }
        )
        if len(raw) > MAX_SHARD_BYTES:
            raise RuntimeError(
                f"Evidence shard {category_id} vượt {MAX_SHARD_BYTES} bytes; cần bucket nhỏ hơn trước khi tiếp tục"
            )
        shard_path.parent.mkdir(parents=True, exist_ok=True)
        shard_path.write_bytes(raw)
        touched.add(category_id)
    rebuild_index(shard_dir, index_path)
    return touched


def rebuild_index(shard_dir: Path, index_path: Path) -> dict[str, Any]:
    shard_dir.mkdir(parents=True, exist_ok=True)
    shards: list[dict[str, Any]] = []
    total = 0
    for path in sorted(shard_dir.glob("*.json")):
        raw = path.read_bytes()
        if len(raw) > MAX_SHARD_BYTES:
            raise RuntimeError(f"Evidence shard vượt giới hạn: {path} ({len(raw)} bytes)")
        payload = json.loads(raw)
        if not isinstance(payload, dict) or payload.get("schemaVersion") != SCHEMA_VERSION:
            raise RuntimeError(f"Evidence shard schema không hợp lệ: {path}")
        rows = payload.get("evidence", [])
        if not isinstance(rows, list):
            raise RuntimeError(f"Evidence shard thiếu evidence array: {path}")
        category_id = str(payload.get("categoryId") or path.stem)
        count = len(rows)
        total += count
        shards.append(
            {
                "categoryId": category_id,
                "file": path.name,
                "evidenceCount": count,
                "bytes": len(raw),
                "sha256": hashlib.sha256(raw).hexdigest(),
            }
        )
    index = {
        "schemaVersion": SCHEMA_VERSION,
        "updatedAt": int(time.time() * 1000),
        "evidenceCount": total,
        "shardCount": len(shards),
        "maxShardBytes": MAX_SHARD_BYTES,
        "shards": shards,
    }
    write_json(index_path, index)
    return index


def iter_all_rows(shard_dir: Path, index_path: Path):
    paths = shard_paths_from_index(index_path, shard_dir)
    for category_id in sorted(paths):
        for row in load_shard_rows(paths[category_id]):
            yield row


def load_all_rows(shard_dir: Path, index_path: Path) -> list[dict[str, Any]]:
    return list(iter_all_rows(shard_dir, index_path))
