#!/usr/bin/env python3
"""Embed a verified offline reference-media cache into a schema-v2 scientific SQLite database.

This is additive: older schema-v2 databases without the local-media tables remain valid. Media
bytes are content-addressed once by SHA-256, while species_media_local links licensed source rows
to those blobs. Keeping the bytes inside SQLite preserves the existing atomic bundle importer.
"""
from __future__ import annotations

import argparse
import hashlib
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
MAX_FILE_BYTES = 5 * 1024 * 1024
MIME_BY_EXTENSION = {"jpg": "image/jpeg", "png": "image/png", "webp": "image/webp"}


def read_meta(db: sqlite3.Connection) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in db.execute("SELECT key,value FROM meta"):
        try:
            result[key] = json.loads(value)
        except json.JSONDecodeError:
            result[key] = value
    return result


def validate_entry(entry: dict) -> re.Match[str]:
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
    if size_bytes <= 0 or size_bytes > MAX_FILE_BYTES:
        raise ValueError("offline media size is outside allowed range")
    if license_id not in ALLOWED_LICENSES:
        raise ValueError("offline media license is not accepted")
    return match


def verified_bytes(manifest_path: Path, entry: dict, match: re.Match[str]) -> tuple[bytes, str]:
    local_path = str(entry["localPath"]).strip()
    media_file = manifest_path.parent / local_path
    if not media_file.is_file():
        raise ValueError(f"offline media file is missing: {local_path}")
    expected_size = int(entry["sizeBytes"])
    if media_file.stat().st_size != expected_size:
        raise ValueError(f"offline media size mismatch: {local_path}")
    data = media_file.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    expected_sha = str(entry["sha256"]).strip().lower()
    if digest != expected_sha:
        raise ValueError(f"offline media SHA-256 mismatch: {local_path}")
    mime_type = MIME_BY_EXTENSION[match.group(2)]
    return data, mime_type


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
        if not {"meta", "taxon", "species_media"}.issubset(tables):
            raise ValueError("scientific SQLite is missing required media/taxon tables")
        meta = read_meta(db)
        if int(meta.get("schemaVersion") or 0) != 2:
            raise ValueError("offline media attachment currently requires scientific schema v2")

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
        db.execute("DELETE FROM species_media_local")
        db.execute("DELETE FROM scientific_media_blob")

        inserted = unmatched = 0
        embedded_bytes = 0
        embedded_hashes: set[str] = set()
        for entry in entries:
            match = validate_entry(entry)
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
            data, mime_type = verified_bytes(manifest_path, entry, match)
            sha256 = str(entry["sha256"]).strip().lower()
            if sha256 not in embedded_hashes:
                db.execute(
                    "INSERT INTO scientific_media_blob(sha256,mime_type,size_bytes,media_blob) VALUES(?,?,?,?)",
                    (sha256, mime_type, len(data), sqlite3.Binary(data)),
                )
                embedded_hashes.add(sha256)
                embedded_bytes += len(data)
            db.execute(
                """INSERT INTO species_media_local
                   (source_id,source_record_id,source_identifier,sha256,media_license)
                   VALUES (?,?,?,?,?)""",
                (source_id, source_record_id, source_identifier, sha256, base_license),
            )
            inserted += 1

        metrics = {
            "localMediaRecordCount": inserted,
            "localMediaUnmatched": unmatched,
            "localMediaBlobCount": len(embedded_hashes),
            "localMediaBytes": embedded_bytes,
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
