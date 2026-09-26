#!/usr/bin/env python3
"""Attach an offline reference-media manifest to a schema-v2 scientific SQLite database.

This is an additive extension: older schema-v2 databases without species_media_local remain valid.
Local media rows must correspond to an existing licensed species_media source row. The image bytes
remain external files under scientific-library/media/ and are verified again by the Android importer.
"""
from __future__ import annotations

import argparse
import json
import re
import sqlite3
from pathlib import Path

ALLOWED_LICENSES = {
    "CC0-1.0", "CC-BY-4.0", "CC-BY-NC-4.0",
    "https://creativecommons.org/publicdomain/zero/1.0/",
    "https://creativecommons.org/licenses/by/4.0/",
    "https://creativecommons.org/licenses/by-nc/4.0/",
}
LOCAL_PATH_RE = re.compile(r"^media/[0-9a-f]{2}/([0-9a-f]{64})\.(jpg|png|webp)$")
SHA_RE = re.compile(r"^[0-9a-f]{64}$")


def read_meta(db: sqlite3.Connection) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in db.execute("SELECT key,value FROM meta"):
        try:
            result[key] = json.loads(value)
        except json.JSONDecodeError:
            result[key] = value
    return result


def validate_entry(entry: dict) -> None:
    source_id = str(entry.get("sourceId") or "").strip()
    source_record_id = str(entry.get("sourceRecordId") or "").strip()
    source_identifier = str(entry.get("sourceIdentifier") or "").strip()
    local_path = str(entry.get("localPath") or "").strip()
    sha256 = str(entry.get("sha256") or "").strip().lower()
    license_id = str(entry.get("license") or "").strip()
    size_bytes = int(entry.get("sizeBytes") or 0)
    if not source_id or not source_record_id or not source_identifier:
        raise ValueError("offline media entry is missing source identity")
    match = LOCAL_PATH_RE.fullmatch(local_path)
    if not match:
        raise ValueError(f"unsafe offline media path: {local_path}")
    if not SHA_RE.fullmatch(sha256) or match.group(1) != sha256:
        raise ValueError("offline media SHA-256 does not match local path")
    if size_bytes <= 0 or size_bytes > 8 * 1024 * 1024:
        raise ValueError("offline media size is outside allowed range")
    if license_id not in ALLOWED_LICENSES:
        raise ValueError("offline media license is not accepted")


def attach(database: Path, manifest_path: Path) -> dict:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("manifestVersion") != 1 or manifest.get("scope") != "scientific-reference-media-offline-cache":
        raise ValueError("unsupported scientific media manifest")
    entries = manifest.get("records") or []
    if not isinstance(entries, list):
        raise ValueError("scientific media manifest records must be a list")

    db = sqlite3.connect(database)
    try:
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        if str(integrity).lower() != "ok":
            raise ValueError(f"SQLite integrity_check failed before media attach: {integrity}")
        tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        required = {"meta", "taxon", "species_media"}
        if not required.issubset(tables):
            raise ValueError("scientific SQLite is missing required media/taxon tables")
        meta = read_meta(db)
        if int(meta.get("schemaVersion") or 0) != 2:
            raise ValueError("offline media attachment currently requires scientific schema v2")

        db.execute("""CREATE TABLE IF NOT EXISTS species_media_local (
            source_id TEXT NOT NULL,
            source_record_id TEXT NOT NULL,
            source_identifier TEXT NOT NULL,
            local_path TEXT NOT NULL,
            sha256 TEXT NOT NULL,
            size_bytes INTEGER NOT NULL,
            media_license TEXT NOT NULL,
            PRIMARY KEY (source_id, source_record_id, source_identifier)
        ) WITHOUT ROWID""")
        db.execute("CREATE INDEX IF NOT EXISTS idx_media_local_record ON species_media_local(source_id,source_record_id)")
        db.execute("DELETE FROM species_media_local")

        inserted = 0
        unmatched = 0
        for entry in entries:
            validate_entry(entry)
            source_id = str(entry["sourceId"]).strip()
            source_record_id = str(entry["sourceRecordId"]).strip()
            source_identifier = str(entry["sourceIdentifier"]).strip()
            base = db.execute(
                """SELECT media_license FROM species_media
                   WHERE source_id=? AND source_record_id=? AND media_identifier=? LIMIT 1""",
                (source_id, source_record_id, source_identifier),
            ).fetchone()
            if base is None:
                unmatched += 1
                continue
            base_license = str(base[0] or "").strip()
            if base_license != str(entry["license"]).strip():
                raise ValueError("offline media license does not match scientific media source row")
            db.execute(
                """INSERT INTO species_media_local
                   (source_id,source_record_id,source_identifier,local_path,sha256,size_bytes,media_license)
                   VALUES (?,?,?,?,?,?,?)""",
                (source_id, source_record_id, source_identifier, str(entry["localPath"]).strip(),
                 str(entry["sha256"]).strip().lower(), int(entry["sizeBytes"]), base_license),
            )
            inserted += 1

        metrics = {
            "localMediaRecordCount": inserted,
            "localMediaUnmatched": unmatched,
            "taxaWithLocalMedia": db.execute(
                "SELECT COUNT(DISTINCT source_id || char(31) || source_record_id) FROM species_media_local"
            ).fetchone()[0],
            "fishWithLocalMedia": db.execute(
                """SELECT COUNT(*) FROM taxon t
                   WHERE t.library_group='Cá nước ngọt' AND EXISTS (
                     SELECT 1 FROM species_media_local l
                     WHERE l.source_id=t.source_id AND l.source_record_id=t.source_record_id
                   )"""
            ).fetchone()[0],
        }
        for key, value in metrics.items():
            db.execute("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", (key, json.dumps(value)))
        db.commit()
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        if str(integrity).lower() != "ok":
            raise ValueError(f"SQLite integrity_check failed after media attach: {integrity}")
        return metrics
    finally:
        db.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(attach(args.database, args.manifest), ensure_ascii=False))


if __name__ == "__main__":
    main()
