#!/usr/bin/env python3
"""Build a separate offline SQLite pack for specialist safety/medicinal evidence.

This database is intentionally separate from the taxonomy database. Every claim is bounded by
its source record and scope note; the builder does not infer diagnosis, dose, treatment,
edibility, toxicity severity, or specimen identification.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
from pathlib import Path

SCHEMA_VERSION = 1
ALLOWED_DOMAINS = {
    "VIETNAM_TRADITIONAL_MEDICINE",
    "HERBAL_MEDICINE_MONOGRAPH",
    "TOXICOLOGY",
}
ALLOWED_CLASSES = {
    "OFFICIAL_LISTING",
    "REGULATORY_MONOGRAPH",
    "PUBLIC_HEALTH_TOXICOLOGY",
    "BOTANICAL_HAZARD_PROFILE",
}


def create_schema(db: sqlite3.Connection) -> None:
    db.executescript(
        """
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        PRAGMA temp_store=MEMORY;
        CREATE TABLE meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
        CREATE TABLE evidence (
            evidence_id TEXT PRIMARY KEY,
            species_id TEXT NOT NULL,
            domain TEXT NOT NULL,
            evidence_class TEXT NOT NULL,
            title TEXT NOT NULL,
            statement TEXT NOT NULL,
            plant_part TEXT NOT NULL DEFAULT '',
            source_name TEXT NOT NULL,
            source_url TEXT NOT NULL,
            source_record TEXT NOT NULL DEFAULT '',
            scope_note TEXT NOT NULL,
            source_id TEXT NOT NULL DEFAULT '',
            source_record_id TEXT NOT NULL DEFAULT '',
            jurisdiction TEXT NOT NULL DEFAULT '',
            source_date TEXT NOT NULL DEFAULT '',
            last_verified TEXT NOT NULL DEFAULT ''
        ) WITHOUT ROWID;
        CREATE INDEX idx_evidence_species ON evidence(species_id);
        CREATE INDEX idx_evidence_domain ON evidence(domain);
        CREATE INDEX idx_evidence_class ON evidence(evidence_class);
        CREATE INDEX idx_evidence_source_id ON evidence(source_id);
        """
    )


def require_text(record: dict, key: str) -> str:
    value = str(record.get(key) or "").strip()
    if not value:
        raise SystemExit(f"Missing required field {key} for evidence record")
    return value


def row_from_record(record: dict) -> tuple:
    evidence_id = require_text(record, "evidenceId")
    species_id = require_text(record, "speciesId")
    domain = require_text(record, "domain")
    evidence_class = require_text(record, "evidenceClass")
    title = require_text(record, "title")
    statement = require_text(record, "statement")
    source_name = require_text(record, "sourceName")
    source_url = require_text(record, "sourceUrl")
    scope_note = require_text(record, "scopeNote")

    if domain not in ALLOWED_DOMAINS:
        raise SystemExit(f"Unsupported evidence domain: {domain}")
    if evidence_class not in ALLOWED_CLASSES:
        raise SystemExit(f"Unsupported evidence class: {evidence_class}")
    if not source_url.startswith("https://"):
        raise SystemExit(f"Evidence source must use HTTPS: {evidence_id}")

    return (
        evidence_id,
        species_id,
        domain,
        evidence_class,
        title,
        statement,
        str(record.get("plantPart") or "").strip(),
        source_name,
        source_url,
        str(record.get("sourceRecord") or "").strip(),
        scope_note,
        str(record.get("sourceId") or "").strip(),
        str(record.get("sourceRecordId") or "").strip(),
        str(record.get("jurisdiction") or "").strip(),
        str(record.get("sourceDate") or "").strip(),
        str(record.get("lastVerified") or "").strip(),
    )


def load_records(path: Path) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict) and isinstance(payload.get("records"), list):
        return payload["records"]
    raise SystemExit("Evidence input must be a JSON array or an object with records[]")


def build(input_path: Path, output_path: Path) -> dict:
    records = load_records(input_path)
    if output_path.exists():
        output_path.unlink()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(output_path)
    try:
        create_schema(db)
        rows = [row_from_record(record) for record in records]
        ids = [row[0] for row in rows]
        if len(ids) != len(set(ids)):
            raise SystemExit("Duplicate evidenceId detected")

        db.executemany(
            """
            INSERT INTO evidence (
                evidence_id, species_id, domain, evidence_class, title, statement, plant_part,
                source_name, source_url, source_record, scope_note, source_id, source_record_id,
                jurisdiction, source_date, last_verified
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            rows,
        )
        meta = {
            "schemaVersion": SCHEMA_VERSION,
            "recordCount": len(rows),
            "scope": "specialist-evidence-only",
            "taxonomyIncluded": False,
            "diagnosisIncluded": False,
            "doseRecommendationsIncluded": False,
            "treatmentRecommendationsIncluded": False,
            "imageIdentificationIncluded": False,
        }
        db.executemany(
            "INSERT INTO meta(key,value) VALUES(?,?)",
            [(key, json.dumps(value, ensure_ascii=False)) for key, value in meta.items()],
        )
        db.commit()
        db.execute("ANALYZE")
        db.execute("VACUUM")
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise SystemExit(f"SQLite integrity check failed: {integrity}")
        stored = db.execute("SELECT COUNT(*) FROM evidence").fetchone()[0]
        if stored != len(rows):
            raise SystemExit(f"SQLite row-count mismatch: parsed={len(rows)} stored={stored}")
        return {**meta, "sqliteRowCount": stored, "sizeBytes": output_path.stat().st_size}
    finally:
        db.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.input, args.output), ensure_ascii=False))


if __name__ == "__main__":
    main()
