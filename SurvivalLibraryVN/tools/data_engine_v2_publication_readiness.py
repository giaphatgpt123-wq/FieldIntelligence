#!/usr/bin/env python3
"""Deterministic STAGING -> PUBLISHED readiness gate for SurvivalLibraryVN.

This gate is deliberately stricter than collector completion. A COMPLETED task only
means evidence was collected. Publication readiness requires verified field state and
schema-v3 identification media quality. The tool never mutates `published` and never
promotes records; it only emits a machine-readable report and can optionally fail CI
when a caller explicitly requires all selected records to be ready.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ACTIVE_TASKS = ROOT / "data" / "staging" / "active-tasks.json"
DEFAULT_BASELINE_TASKS = ROOT / "data" / "staging" / "pilot-12-tasks.json"
DEFAULT_SNAPSHOT = ROOT / "data" / "staging" / "progress-snapshot.json"
DEFAULT_EVIDENCE_DIR = ROOT / "data" / "staging" / "evidence"

# Keep this aligned with schema-v3 CATEGORY_POLICY / Android IdentificationMediaPolicy.
CATEGORY_POLICY: dict[str, dict[str, Any]] = {
    "vegetables": {"required": ["WHOLE", "LEAF", "STEM"], "primary": "WHOLE", "min": 5},
    "roots": {"required": ["WHOLE", "UNDERGROUND_PART", "LEAF"], "primary": "UNDERGROUND_PART", "min": 5},
    "fruit-crops": {"required": ["WHOLE", "FRUIT", "LEAF"], "primary": "FRUIT", "min": 5},
    "flowers": {"required": ["WHOLE", "FLOWER", "LEAF"], "primary": "FLOWER", "min": 5},
    "timber-trees": {"required": ["WHOLE", "BARK", "LEAF"], "primary": "WHOLE", "min": 5},
    "mushrooms": {"required": ["WHOLE", "UNDERSIDE"], "primary": "WHOLE", "min": 6},
    "freshwater-fish": {"required": ["WHOLE", "SIDE", "HEAD"], "primary": "WHOLE", "min": 6},
    "marine-life": {"required": ["WHOLE", "SIDE", "HEAD"], "primary": "WHOLE", "min": 6},
    "insects": {"required": ["WHOLE", "DORSAL", "HEAD"], "primary": "WHOLE", "min": 6},
    "animals": {"required": ["ADULT_WHOLE", "JUVENILE", "HEAD"], "primary": "ADULT_WHOLE", "min": 6},
    "medicinal-plants": {"required": ["WHOLE", "UNDERGROUND_PART", "LEAF"], "primary": "UNDERGROUND_PART", "min": 5},
    "danger": {"required": ["WHOLE", "DORSAL", "SIDE"], "primary": "WHOLE", "min": 6},
}

BASE_REQUIRED_VERIFIED_FIELDS = (
    "CANONICAL_IDENTITY",
    "VIETNAMESE_ALIASES",
    "VIETNAM_DISTRIBUTION",
    "MEDIA_PRIMARY",
    "MEDIA_DIAGNOSTIC_SET",
    "IDENTIFICATION_TRAITS",
)

TRACKED_NON_BLOCKING_FIELDS = (
    "CONFUSABLE_SPECIES",
    "USAGE_LEVEL",
    "USAGE_CONTENT",
)


@dataclass(frozen=True)
class EntityRef:
    canonical_id: str
    category_id: str
    scientific_name: str
    vietnamese_name: str
    high_risk: bool
    published: bool


def _load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _field_map(snapshot: dict[str, Any], canonical_id: str) -> dict[str, dict[str, Any]]:
    return {
        str(row.get("field") or ""): row
        for row in snapshot.get("fields", [])
        if row.get("canonicalId") == canonical_id
    }


def _task_status_map(snapshot: dict[str, Any], canonical_id: str) -> dict[str, str]:
    return {
        str(row.get("field") or ""): str(row.get("status") or "")
        for row in snapshot.get("tasks", [])
        if row.get("canonicalId") == canonical_id and row.get("field")
    }


def _load_evidence(evidence_dir: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    if not evidence_dir.exists():
        return rows
    for path in sorted(evidence_dir.glob("*.json")):
        payload = _load_json(path)
        for row in payload.get("evidence", []):
            if isinstance(row, dict):
                rows.append(row)
    return rows


def _entity_media_rows(evidence: Iterable[dict[str, Any]], canonical_id: str) -> list[dict[str, Any]]:
    return [
        row
        for row in evidence
        if row.get("canonicalId") == canonical_id
        and row.get("field") in {"MEDIA_PRIMARY", "MEDIA_DIAGNOSTIC_SET"}
    ]


def _media_value_rows(row: dict[str, Any]) -> list[dict[str, Any]]:
    value = row.get("value")
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    if isinstance(value, dict):
        items = value.get("media")
        if isinstance(items, list):
            return [item for item in items if isinstance(item, dict)]
        return [value]
    return []


def _verified_diagnostic_media(evidence: Iterable[dict[str, Any]], canonical_id: str) -> list[dict[str, Any]]:
    media: list[dict[str, Any]] = []
    for row in _entity_media_rows(evidence, canonical_id):
        if row.get("field") != "MEDIA_DIAGNOSTIC_SET" or row.get("verified") is not True:
            continue
        for value in _media_value_rows(row):
            merged = dict(value)
            merged.setdefault("sourceUri", row.get("sourceUri") or "")
            media.append(merged)
    return media


def _valid_diagnostic_media(rows: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    valid: list[dict[str, Any]] = []
    for media in rows:
        checksum = str(media.get("checksum") or "").strip().lower()
        license_name = str(media.get("license") or media.get("rights") or "").strip()
        creator = str(media.get("creator") or media.get("rightsHolder") or "").strip()
        source_uri = str(media.get("sourceUri") or "").strip()
        view_role = str(media.get("viewRole") or "").strip()
        diagnostic = media.get("diagnostic", True)
        if (
            diagnostic is True
            and len(checksum) == 64
            and license_name
            and creator
            and source_uri.startswith("https://")
            and view_role
        ):
            valid.append(media)
    return valid


def _dedupe_media(rows: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[str] = set()
    result: list[dict[str, Any]] = []
    for row in rows:
        checksum = str(row.get("checksum") or "").lower()
        if not checksum or checksum in seen:
            continue
        seen.add(checksum)
        result.append(row)
    return result


def evaluate_entity(
    entity: EntityRef,
    snapshot: dict[str, Any],
    evidence: list[dict[str, Any]],
) -> dict[str, Any]:
    fields = _field_map(snapshot, entity.canonical_id)
    task_status = _task_status_map(snapshot, entity.canonical_id)
    required_fields = list(BASE_REQUIRED_VERIFIED_FIELDS)
    if entity.high_risk:
        required_fields.append("SAFETY")

    blockers: list[str] = []
    field_state: dict[str, dict[str, Any]] = {}
    for field_name in required_fields:
        row = fields.get(field_name)
        verified = bool(row and row.get("verified") is True)
        value_present = bool(row and (row.get("valuePresent") is True or int(row.get("evidenceCount") or 0) > 0))
        status = task_status.get(field_name, "MISSING")
        field_state[field_name] = {
            "verified": verified,
            "valuePresent": value_present,
            "taskStatus": status,
            "evidenceCount": int(row.get("evidenceCount") or 0) if row else 0,
        }
        if not verified:
            if not value_present:
                blockers.append(f"{field_name}: chưa có evidence đã chuẩn hóa")
            else:
                blockers.append(f"{field_name}: đã có evidence nhưng chưa verified")

    policy = CATEGORY_POLICY.get(entity.category_id, {"required": [], "primary": "", "min": 5})
    raw_media = _verified_diagnostic_media(evidence, entity.canonical_id)
    valid_media = _dedupe_media(_valid_diagnostic_media(raw_media))
    roles = {str(row.get("viewRole") or "").strip() for row in valid_media if row.get("viewRole")}
    required_roles = set(policy["required"])
    missing_roles = sorted(required_roles - roles)
    primary_role = str(policy["primary"])
    primary_rows = [row for row in valid_media if row.get("isPrimary") is True]

    if len(valid_media) < int(policy["min"]):
        blockers.append(f"MEDIA_DIAGNOSTIC_SET: {len(valid_media)}/{policy['min']} ảnh chẩn đoán đủ provenance")
    if missing_roles:
        blockers.append("MEDIA_DIAGNOSTIC_SET: thiếu vai trò " + ", ".join(missing_roles))
    if len(primary_rows) != 1:
        blockers.append(f"MEDIA_DIAGNOSTIC_SET: cần đúng 1 ảnh đại diện, hiện có {len(primary_rows)}")
    elif primary_role and str(primary_rows[0].get("viewRole") or "") != primary_role:
        blockers.append(f"MEDIA_DIAGNOSTIC_SET: ảnh đại diện phải là {primary_role}")

    tracked_gaps: list[str] = []
    for field_name in TRACKED_NON_BLOCKING_FIELDS:
        row = fields.get(field_name)
        if not row or row.get("verified") is not True:
            tracked_gaps.append(field_name)

    media_candidates = _entity_media_rows(evidence, entity.canonical_id)
    return {
        "canonicalId": entity.canonical_id,
        "categoryId": entity.category_id,
        "scientificName": entity.scientific_name,
        "vietnameseName": entity.vietnamese_name,
        "highRisk": entity.high_risk,
        "currentlyPublished": entity.published,
        "publicationReady": not blockers,
        "blockers": blockers,
        "fieldState": field_state,
        "trackedNonBlockingGaps": tracked_gaps,
        "mediaPolicy": {
            "minimum": int(policy["min"]),
            "requiredRoles": list(policy["required"]),
            "primaryRole": primary_role,
            "collectorMediaEvidenceRows": len(media_candidates),
            "verifiedDiagnosticMedia": len(valid_media),
            "availableRoles": sorted(roles),
            "missingRoles": missing_roles,
        },
    }


def build_report(
    snapshot: dict[str, Any],
    evidence: list[dict[str, Any]],
    active_tasks: list[dict[str, Any]],
    baseline_tasks: list[dict[str, Any]],
    scope: str,
) -> dict[str, Any]:
    active_ids = {str(row["canonicalId"]) for row in active_tasks}
    baseline_ids = {str(row["canonicalId"]) for row in baseline_tasks}
    selected_ids = active_ids - baseline_ids if scope == "expansion" else active_ids

    entities: list[EntityRef] = []
    for row in snapshot.get("entities", []):
        canonical_id = str(row.get("canonicalId") or "")
        if canonical_id not in selected_ids:
            continue
        entities.append(
            EntityRef(
                canonical_id=canonical_id,
                category_id=str(row.get("categoryId") or ""),
                scientific_name=str(row.get("scientificName") or ""),
                vietnamese_name=str(row.get("vietnameseName") or ""),
                high_risk=bool(row.get("highRisk", False)),
                published=bool(row.get("published", False)),
            )
        )

    rows = [evaluate_entity(entity, snapshot, evidence) for entity in sorted(entities, key=lambda x: x.canonical_id)]
    ready = sum(1 for row in rows if row["publicationReady"])
    return {
        "schemaVersion": 1,
        "snapshotVersion": int(snapshot.get("version") or 0),
        "scope": scope,
        "selectedCount": len(rows),
        "readyCount": ready,
        "blockedCount": len(rows) - ready,
        "records": rows,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshot", type=Path, default=DEFAULT_SNAPSHOT)
    parser.add_argument("--evidence-dir", type=Path, default=DEFAULT_EVIDENCE_DIR)
    parser.add_argument("--active-tasks", type=Path, default=DEFAULT_ACTIVE_TASKS)
    parser.add_argument("--baseline-tasks", type=Path, default=DEFAULT_BASELINE_TASKS)
    parser.add_argument("--scope", choices=("expansion", "all"), default="expansion")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--require-ready", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    snapshot = _load_json(args.snapshot)
    evidence = _load_evidence(args.evidence_dir)
    active_tasks = _load_json(args.active_tasks)
    baseline_tasks = _load_json(args.baseline_tasks)
    report = build_report(snapshot, evidence, active_tasks, baseline_tasks, args.scope)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(
        "Publication readiness: "
        f"scope={report['scope']} selected={report['selectedCount']} "
        f"ready={report['readyCount']} blocked={report['blockedCount']}"
    )
    for record in report["records"]:
        state = "READY" if record["publicationReady"] else "BLOCKED"
        print(f"- {record['vietnameseName']} ({record['scientificName']}): {state}")
        for blocker in record["blockers"]:
            print(f"  · {blocker}")

    if args.require_ready and report["blockedCount"]:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
