#!/usr/bin/env python3
"""Merge a verified schema-v2 scientific SQLite into another schema-v2 library.

The target database is modified in place. Taxon keys must be disjoint; source provenance remains
on each taxon row. Optional embedded offline media tables are copied content-addressably. Aggregate
meta is recomputed from the merged database instead of trusting stale source counters.
"""
from __future__ import annotations

import argparse
import json
import sqlite3
from pathlib import Path

CORE_TABLES = {"meta", "taxon", "species_media", "vernacular_name", "occurrence_summary"}
TAXON_COLUMNS = [
    "source_id", "source_record_id", "scientific_name", "scientific_name_search",
    "accepted_name_usage_id", "taxonomic_status", "kingdom", "phylum", "class_name",
    "order_name", "family", "genus", "specific_epithet", "library_group",
    "authority", "license", "source_scope", "source_version", "source_doi",
]


def read_meta(db: sqlite3.Connection, schema: str = "main") -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in db.execute(f"SELECT key,value FROM {schema}.meta"):
        try:
            result[key] = json.loads(value)
        except json.JSONDecodeError:
            result[key] = value
    return result


def tables(db: sqlite3.Connection, schema: str = "main") -> set[str]:
    return {row[0] for row in db.execute(f"SELECT name FROM {schema}.sqlite_master WHERE type='table'")}


def validate_schema(db: sqlite3.Connection, schema: str) -> None:
    integrity = db.execute(f"PRAGMA {schema}.integrity_check").fetchone()[0]
    if str(integrity).lower() != "ok":
        raise ValueError(f"{schema} SQLite integrity_check failed: {integrity}")
    actual = tables(db, schema)
    if not CORE_TABLES.issubset(actual):
        raise ValueError(f"{schema} scientific SQLite missing core tables")
    meta = read_meta(db, schema)
    if int(meta.get("schemaVersion") or 0) != 2 or meta.get("scope") != "taxonomy-media-occurrence":
        raise ValueError(f"{schema} scientific SQLite is not supported schema v2")


def ensure_local_media_tables(db: sqlite3.Connection) -> None:
    db.executescript("""
    CREATE TABLE IF NOT EXISTS scientific_media_blob (
        sha256 TEXT PRIMARY KEY,
        mime_type TEXT NOT NULL,
        size_bytes INTEGER NOT NULL,
        media_blob BLOB NOT NULL
    ) WITHOUT ROWID;
    CREATE TABLE IF NOT EXISTS species_media_local (
        source_id TEXT NOT NULL,
        source_record_id TEXT NOT NULL,
        source_identifier TEXT NOT NULL,
        sha256 TEXT NOT NULL,
        media_license TEXT NOT NULL,
        PRIMARY KEY (source_id, source_record_id, source_identifier)
    ) WITHOUT ROWID;
    CREATE INDEX IF NOT EXISTS idx_media_local_record
      ON species_media_local(source_id,source_record_id);
    """)


def scalar(db: sqlite3.Connection, sql: str, args=()) -> int:
    return int(db.execute(sql, args).fetchone()[0])


def recompute_meta(db: sqlite3.Connection, source_label: str) -> dict[str, object]:
    fish_group = "Cá nước ngọt"
    result: dict[str, object] = {
        "schemaVersion": 2,
        "recordCount": scalar(db, "SELECT COUNT(*) FROM taxon"),
        "acceptedRecordCount": scalar(db, "SELECT COUNT(*) FROM taxon WHERE lower(taxonomic_status) IN ('accepted','accepted name','acceptedname')"),
        "sourceId": "fieldintelligence-merged-scientific-library",
        "sourceVersion": source_label,
        "sourceDoi": "",
        "sourceLicense": "mixed-see-record-provenance",
        "scope": "taxonomy-media-occurrence",
        "mediaRecordCount": scalar(db, "SELECT COUNT(*) FROM species_media"),
        "recordsWithMedia": scalar(db, "SELECT COUNT(DISTINCT source_id || char(31) || source_record_id) FROM species_media"),
        "fishTaxa": scalar(db, "SELECT COUNT(*) FROM taxon WHERE library_group=?", (fish_group,)),
        "fishWithMedia": scalar(db, """SELECT COUNT(*) FROM taxon t WHERE t.library_group=? AND EXISTS (
            SELECT 1 FROM species_media m WHERE m.source_id=t.source_id AND m.source_record_id=t.source_record_id)""", (fish_group,)),
        "fishWith2PlusMedia": scalar(db, """SELECT COUNT(*) FROM (
            SELECT t.source_id,t.source_record_id FROM taxon t JOIN species_media m
            ON m.source_id=t.source_id AND m.source_record_id=t.source_record_id
            WHERE t.library_group=? GROUP BY t.source_id,t.source_record_id HAVING COUNT(*)>=2)""", (fish_group,)),
        "vernacularNameCount": scalar(db, "SELECT COUNT(*) FROM vernacular_name"),
        "occurrenceSummaryCount": scalar(db, "SELECT COUNT(*) FROM occurrence_summary"),
        "fishPublishRule": "requires-verified-offline-media-for-main-library",
        "medicalClaimsIncluded": False,
        "edibilityClaimsIncluded": False,
        "toxicityClaimsIncluded": False,
        "imageIdentificationIncluded": False,
    }
    local_tables = tables(db)
    if {"scientific_media_blob", "species_media_local"}.issubset(local_tables):
        result.update({
            "localMediaRecordCount": scalar(db, "SELECT COUNT(*) FROM species_media_local"),
            "localMediaBlobCount": scalar(db, "SELECT COUNT(*) FROM scientific_media_blob"),
            "localMediaBytes": scalar(db, "SELECT COALESCE(SUM(size_bytes),0) FROM scientific_media_blob"),
            "taxaWithLocalMedia": scalar(db, "SELECT COUNT(DISTINCT source_id || char(31) || source_record_id) FROM species_media_local"),
            "fishWithLocalMedia": scalar(db, """SELECT COUNT(*) FROM taxon t WHERE t.library_group=? AND EXISTS (
                SELECT 1 FROM species_media_local l WHERE l.source_id=t.source_id AND l.source_record_id=t.source_record_id)""", (fish_group,)),
        })
    result["fishPendingMedia"] = int(result["fishTaxa"]) - int(result.get("fishWithLocalMedia", 0))
    return result


def merge(target: Path, source: Path, source_label: str = "WFO + verified fish") -> dict[str, object]:
    if not target.is_file() or not source.is_file():
        raise ValueError("target and source SQLite files are required")
    db = sqlite3.connect(target)
    try:
        db.execute("ATTACH DATABASE ? AS incoming", (str(source),))
        validate_schema(db, "main")
        validate_schema(db, "incoming")
        ensure_local_media_tables(db)

        duplicate = db.execute("""SELECT t.source_id,t.source_record_id FROM taxon t
            JOIN incoming.taxon i USING(source_id,source_record_id) LIMIT 1""").fetchone()
        if duplicate:
            raise ValueError(f"duplicate taxon key during scientific merge: {duplicate[0]}/{duplicate[1]}")

        incoming_tables = tables(db, "incoming")
        db.execute("BEGIN")
        cols = ",".join(TAXON_COLUMNS)
        db.execute(f"INSERT INTO taxon({cols}) SELECT {cols} FROM incoming.taxon")
        db.execute("""INSERT INTO species_media
            (source_id,source_record_id,media_identifier,media_type,references_url,title,description,creator,rights_holder,media_license)
            SELECT source_id,source_record_id,media_identifier,media_type,references_url,title,description,creator,rights_holder,media_license
            FROM incoming.species_media""")
        db.execute("""INSERT INTO vernacular_name(source_id,source_record_id,vernacular_name)
            SELECT source_id,source_record_id,vernacular_name FROM incoming.vernacular_name""")
        db.execute("""INSERT INTO occurrence_summary
            (source_id,source_record_id,country_code,state_province,locality,event_date,basis_of_record,dataset_key)
            SELECT source_id,source_record_id,country_code,state_province,locality,event_date,basis_of_record,dataset_key
            FROM incoming.occurrence_summary""")
        if "source_reference" in incoming_tables:
            db.execute("""CREATE TABLE IF NOT EXISTS source_reference (
                source_id TEXT NOT NULL, source_record_id TEXT NOT NULL,
                source_url TEXT NOT NULL DEFAULT '', retrieved_at TEXT NOT NULL DEFAULT '',
                content_sha256 TEXT NOT NULL DEFAULT '', offline_state TEXT NOT NULL DEFAULT 'metadata-only',
                PRIMARY KEY (source_id, source_record_id, source_url)) WITHOUT ROWID""")
            db.execute("""INSERT OR IGNORE INTO source_reference
                (source_id,source_record_id,source_url,retrieved_at,content_sha256,offline_state)
                SELECT source_id,source_record_id,source_url,retrieved_at,content_sha256,offline_state
                FROM incoming.source_reference""")

        if {"scientific_media_blob", "species_media_local"}.issubset(incoming_tables):
            db.execute("""INSERT OR IGNORE INTO scientific_media_blob(sha256,mime_type,size_bytes,media_blob)
                SELECT sha256,mime_type,size_bytes,media_blob FROM incoming.scientific_media_blob""")
            db.execute("""INSERT INTO species_media_local(source_id,source_record_id,source_identifier,sha256,media_license)
                SELECT source_id,source_record_id,source_identifier,sha256,media_license FROM incoming.species_media_local""")

        meta = recompute_meta(db, source_label)
        db.execute("DELETE FROM meta")
        db.executemany("INSERT INTO meta(key,value) VALUES(?,?)", [(k, json.dumps(v, ensure_ascii=False)) for k, v in meta.items()])
        db.commit()
        db.execute("ANALYZE")
        db.execute("VACUUM")
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        if str(integrity).lower() != "ok":
            raise ValueError(f"merged SQLite integrity_check failed: {integrity}")
        return {**meta, "integrity": integrity, "sizeBytes": target.stat().st_size}
    except Exception:
        if db.in_transaction:
            db.rollback()
        raise
    finally:
        run_detach = False
        try:
            run_detach = any(row[1] == "incoming" for row in db.execute("PRAGMA database_list"))
        except sqlite3.Error:
            pass
        if run_detach:
            try: db.execute("DETACH DATABASE incoming")
            except sqlite3.Error: pass
        db.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--source-label", default="WFO + verified fish")
    args = parser.parse_args()
    print(json.dumps(merge(args.target, args.source, args.source_label), ensure_ascii=False))


if __name__ == "__main__":
    main()
