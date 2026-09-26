#!/usr/bin/env python3
"""Build an offline-searchable SQLite taxonomy database from normalized scientific NDJSON.

The database stores taxonomy/provenance only. It deliberately has no edibility, toxicity,
medical-treatment, or image-identification fields. It is intended to be distributed as a
separate scientific data pack rather than baked into the APK.
"""

from __future__ import annotations

import argparse
import gzip
import json
import sqlite3
from pathlib import Path

SCHEMA_VERSION = 1


def open_text(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


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
        CREATE TABLE taxon (
            source_id TEXT NOT NULL,
            source_record_id TEXT NOT NULL,
            scientific_name TEXT NOT NULL,
            scientific_name_search TEXT NOT NULL,
            accepted_name_usage_id TEXT NOT NULL DEFAULT '',
            taxonomic_status TEXT NOT NULL DEFAULT '',
            kingdom TEXT NOT NULL DEFAULT '',
            phylum TEXT NOT NULL DEFAULT '',
            class_name TEXT NOT NULL DEFAULT '',
            order_name TEXT NOT NULL DEFAULT '',
            family TEXT NOT NULL DEFAULT '',
            genus TEXT NOT NULL DEFAULT '',
            specific_epithet TEXT NOT NULL DEFAULT '',
            library_group TEXT NOT NULL DEFAULT '',
            authority TEXT NOT NULL DEFAULT '',
            license TEXT NOT NULL DEFAULT '',
            source_scope TEXT NOT NULL DEFAULT '',
            source_version TEXT NOT NULL DEFAULT '',
            source_doi TEXT NOT NULL DEFAULT '',
            PRIMARY KEY (source_id, source_record_id)
        ) WITHOUT ROWID;
        CREATE INDEX idx_taxon_scientific_name_search ON taxon(scientific_name_search);
        CREATE INDEX idx_taxon_genus ON taxon(genus);
        CREATE INDEX idx_taxon_family ON taxon(family);
        CREATE INDEX idx_taxon_status ON taxon(taxonomic_status);
        CREATE INDEX idx_taxon_accepted_id ON taxon(accepted_name_usage_id);
        """
    )


def row_from_record(record: dict) -> tuple:
    provenance = record.get("provenance") or {}
    scientific = str(record.get("scientificName") or "").strip()
    return (
        str(record.get("sourceId") or "").strip(),
        str(record.get("sourceRecordId") or "").strip(),
        scientific,
        scientific.casefold(),
        str(record.get("acceptedNameUsageId") or "").strip(),
        str(record.get("taxonomicStatus") or "").strip(),
        str(record.get("kingdom") or "").strip(),
        str(record.get("phylum") or "").strip(),
        str(record.get("class") or "").strip(),
        str(record.get("order") or "").strip(),
        str(record.get("family") or "").strip(),
        str(record.get("genus") or "").strip(),
        str(record.get("specificEpithet") or "").strip(),
        str(record.get("libraryGroup") or "").strip(),
        str(provenance.get("authority") or "").strip(),
        str(provenance.get("license") or "").strip(),
        str(provenance.get("scope") or "").strip(),
        str(provenance.get("version") or "").strip(),
        str(provenance.get("versionDoi") or "").strip(),
    )


def build(input_path: Path, output_path: Path, source_meta_path: Path | None = None, batch_size: int = 5000) -> dict:
    if output_path.exists():
        output_path.unlink()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(output_path)
    try:
        create_schema(db)
        insert_sql = """
            INSERT OR REPLACE INTO taxon (
                source_id, source_record_id, scientific_name, scientific_name_search,
                accepted_name_usage_id, taxonomic_status, kingdom, phylum, class_name,
                order_name, family, genus, specific_epithet, library_group,
                authority, license, source_scope, source_version, source_doi
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """
        count = 0
        accepted = 0
        batch = []
        with open_text(input_path) as handle:
            for line in handle:
                if not line.strip():
                    continue
                record = json.loads(line)
                row = row_from_record(record)
                if not row[0] or not row[1] or not row[2]:
                    continue
                batch.append(row)
                count += 1
                if row[5].casefold() in {"accepted", "accepted name", "acceptedname"}:
                    accepted += 1
                if len(batch) >= batch_size:
                    db.executemany(insert_sql, batch)
                    batch.clear()
            if batch:
                db.executemany(insert_sql, batch)

        source_meta = {}
        if source_meta_path and source_meta_path.is_file():
            source_meta = json.loads(source_meta_path.read_text(encoding="utf-8"))
        meta = {
            "schemaVersion": SCHEMA_VERSION,
            "recordCount": count,
            "acceptedRecordCount": accepted,
            "sourceId": source_meta.get("sourceId", ""),
            "sourceVersion": source_meta.get("version", ""),
            "sourceDoi": source_meta.get("versionDoi", ""),
            "sourceLicense": source_meta.get("license", ""),
            "scope": "taxonomy-only",
            "medicalClaimsIncluded": False,
            "edibilityClaimsIncluded": False,
            "toxicityClaimsIncluded": False,
            "imageIdentificationIncluded": False,
        }
        db.executemany("INSERT INTO meta(key,value) VALUES(?,?)", [(k, json.dumps(v, ensure_ascii=False)) for k, v in meta.items()])
        db.commit()
        db.execute("ANALYZE")
        db.execute("VACUUM")
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise SystemExit(f"SQLite integrity check failed: {integrity}")
        stored = db.execute("SELECT COUNT(*) FROM taxon").fetchone()[0]
        if stored != count:
            raise SystemExit(f"SQLite row-count mismatch: parsed={count} stored={stored}")
        return {**meta, "sqliteRowCount": stored, "sizeBytes": output_path.stat().st_size}
    finally:
        db.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--source-meta", type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.input, args.output, args.source_meta), ensure_ascii=False))


if __name__ == "__main__":
    main()
