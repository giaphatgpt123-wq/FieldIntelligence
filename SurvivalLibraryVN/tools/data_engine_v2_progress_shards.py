#!/usr/bin/env python3
"""Build category-sharded Data Engine progress payloads.

The legacy progress-snapshot.json remains available during migration. Android clients that
understand progress shards can fetch only categories whose SHA-256 changed, avoiding one
large JSON download/import when a single category is updated.
"""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1
MAX_PROGRESS_SHARD_BYTES = 2 * 1024 * 1024
MAX_SOURCE_HEALTH_BYTES = 512 * 1024


def encode_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n").encode("utf-8")


def safe_name(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9._-]+", "-", value.strip().lower()).strip("-._")
    return normalized or "uncategorized"


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def build_progress_shards(
    snapshot: dict[str, Any],
    output_dir: Path,
    raw_root: str,
) -> dict[str, Any]:
    schema = int(snapshot.get("schemaVersion") or 0)
    version = int(snapshot.get("version") or 0)
    generated_at = int(snapshot.get("generatedAt") or 0)
    if schema != SCHEMA_VERSION or version <= 0:
        raise RuntimeError("Progress snapshot schema/version không hợp lệ")

    entities = snapshot.get("entities", [])
    aliases = snapshot.get("aliases", [])
    fields = snapshot.get("fields", [])
    tasks = snapshot.get("tasks", [])
    source_health = snapshot.get("sourceHealth", [])
    if not all(isinstance(rows, list) for rows in (entities, aliases, fields, tasks, source_health)):
        raise RuntimeError("Progress snapshot arrays không hợp lệ")

    entity_category: dict[str, str] = {}
    grouped_entities: dict[str, list[dict[str, Any]]] = {}
    for row in entities:
        if not isinstance(row, dict):
            continue
        canonical_id = str(row.get("canonicalId") or "").strip()
        category_id = str(row.get("categoryId") or "").strip()
        if not canonical_id or not category_id:
            raise RuntimeError("Progress entity thiếu canonicalId/categoryId")
        previous = entity_category.get(canonical_id)
        if previous and previous != category_id:
            raise RuntimeError(f"Entity {canonical_id} thuộc nhiều category")
        entity_category[canonical_id] = category_id
        grouped_entities.setdefault(category_id, []).append(row)

    def group_by_entity(rows: list[Any], label: str) -> dict[str, list[dict[str, Any]]]:
        grouped: dict[str, list[dict[str, Any]]] = {}
        for row in rows:
            if not isinstance(row, dict):
                continue
            canonical_id = str(row.get("canonicalId") or "").strip()
            category_id = entity_category.get(canonical_id)
            if not category_id:
                raise RuntimeError(f"{label} tham chiếu entity không tồn tại: {canonical_id}")
            grouped.setdefault(category_id, []).append(row)
        return grouped

    grouped_aliases = group_by_entity(aliases, "Alias")
    grouped_fields = group_by_entity(fields, "Field")
    grouped_tasks = group_by_entity(tasks, "Task")

    output_dir.mkdir(parents=True, exist_ok=True)
    expected_files: set[str] = set()
    manifest_rows: list[dict[str, Any]] = []
    for category_id in sorted(grouped_entities):
        file_name = safe_name(category_id) + ".json"
        expected_files.add(file_name)
        payload = {
            "schemaVersion": SCHEMA_VERSION,
            "version": version,
            "generatedAt": generated_at,
            "categoryId": category_id,
            "entities": grouped_entities.get(category_id, []),
            "aliases": grouped_aliases.get(category_id, []),
            "fields": grouped_fields.get(category_id, []),
            "tasks": grouped_tasks.get(category_id, []),
        }
        raw = encode_json(payload)
        if len(raw) > MAX_PROGRESS_SHARD_BYTES:
            raise RuntimeError(
                f"Progress shard {category_id} vượt {MAX_PROGRESS_SHARD_BYTES} bytes; cần bucket nhỏ hơn"
            )
        (output_dir / file_name).write_bytes(raw)
        manifest_rows.append(
            {
                "categoryId": category_id,
                "file": file_name,
                "url": raw_root.rstrip("/") + "/progress/" + file_name,
                "sha256": sha256(raw),
                "bytes": len(raw),
                "entityCount": len(payload["entities"]),
                "aliasCount": len(payload["aliases"]),
                "fieldCount": len(payload["fields"]),
                "taskCount": len(payload["tasks"]),
            }
        )

    for old in output_dir.glob("*.json"):
        if old.name == "source-health.json":
            continue
        if old.name not in expected_files:
            old.unlink()

    source_payload = {
        "schemaVersion": SCHEMA_VERSION,
        "version": version,
        "generatedAt": generated_at,
        "sourceHealth": source_health,
    }
    source_raw = encode_json(source_payload)
    if len(source_raw) > MAX_SOURCE_HEALTH_BYTES:
        raise RuntimeError("Source health progress payload vượt giới hạn")
    source_file = "source-health.json"
    (output_dir / source_file).write_bytes(source_raw)

    return {
        "schemaVersion": SCHEMA_VERSION,
        "version": version,
        "generatedAt": generated_at,
        "shardCount": len(manifest_rows),
        "maxShardBytes": MAX_PROGRESS_SHARD_BYTES,
        "shards": manifest_rows,
        "sourceHealth": {
            "file": source_file,
            "url": raw_root.rstrip("/") + "/progress/" + source_file,
            "sha256": sha256(source_raw),
            "bytes": len(source_raw),
            "rowCount": len(source_health),
        },
    }
