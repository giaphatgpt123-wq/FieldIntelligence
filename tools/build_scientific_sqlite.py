#!/usr/bin/env python3
"""Build an offline-searchable SQLite taxonomy database from normalized scientific NDJSON.

The database stores taxonomy plus source-linked media, vernacular names and occurrence summaries.
It deliberately does not infer edibility, toxicity, medical treatment or specimen identity from media.
"""

from __future__ import annotations

import argparse
import gzip
import json
import sqlite3
from pathlib import Path

SCHEMA_VERSION = 2


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
        CREATE TABLE species_media (
            source_id TEXT NOT NULL, source_record_id TEXT NOT NULL,
            media_identifier TEXT NOT NULL, media_type TEXT NOT NULL DEFAULT '',
            references_url TEXT NOT NULL DEFAULT '', title TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '', creator TEXT NOT NULL DEFAULT '',
            rights_holder TEXT NOT NULL DEFAULT '', media_license TEXT NOT NULL DEFAULT '',
            PRIMARY KEY (source_id, source_record_id, media_identifier)
        ) WITHOUT ROWID;
        CREATE INDEX idx_media_record ON species_media(source_id, source_record_id);
        CREATE TABLE vernacular_name (
            source_id TEXT NOT NULL, source_record_id TEXT NOT NULL, vernacular_name TEXT NOT NULL,
            PRIMARY KEY (source_id, source_record_id, vernacular_name)
        ) WITHOUT ROWID;
        CREATE TABLE occurrence_summary (
            source_id TEXT NOT NULL, source_record_id TEXT NOT NULL,
            country_code TEXT NOT NULL DEFAULT '', state_province TEXT NOT NULL DEFAULT '',
            locality TEXT NOT NULL DEFAULT '', event_date TEXT NOT NULL DEFAULT '',
            basis_of_record TEXT NOT NULL DEFAULT '', dataset_key TEXT NOT NULL DEFAULT '',
            PRIMARY KEY (source_id, source_record_id)
        ) WITHOUT ROWID;
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
        media_count = 0
        vernacular_count = 0
        occurrence_count = 0
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
                source_id, source_record_id = row[0], row[1]
                media_items = record.get("mediaItems") or []
                if not media_items and record.get("media"):
                    media_items = [record.get("media") or {}]
                for media in media_items:
                    media_identifier = str(media.get("identifier") or "").strip()
                    if not media_identifier:
                        continue
                    media_license = str(media.get("license") or "").strip()
                    # A media URL without an explicit reusable licence is not eligible
                    # for the offline scientific library.
                    if media_license not in {"CC0-1.0", "CC-BY-4.0", "CC-BY-NC-4.0",
                                             "https://creativecommons.org/publicdomain/zero/1.0/",
                                             "https://creativecommons.org/licenses/by/4.0/",
                                             "https://creativecommons.org/licenses/by-nc/4.0/"}:
                        continue
                    db.execute("""INSERT OR IGNORE INTO species_media
                        (source_id,source_record_id,media_identifier,media_type,references_url,title,description,creator,rights_holder,media_license)
                        VALUES (?,?,?,?,?,?,?,?,?,?)""", (
                        source_id, source_record_id, media_identifier,
                        str(media.get("mediaType") or "").strip(), str(media.get("references") or "").strip(),
                        str(media.get("title") or "").strip(), str(media.get("description") or "").strip(),
                        str(media.get("creator") or "").strip(), str(media.get("rightsHolder") or "").strip(),
                        media_license))
                    media_count += db.execute("SELECT changes()").fetchone()[0]
                vernacular = str(record.get("vernacularName") or "").strip()
                if vernacular:
                    db.execute("INSERT OR IGNORE INTO vernacular_name VALUES (?,?,?)", (source_id, source_record_id, vernacular))
                    vernacular_count += db.execute("SELECT changes()").fetchone()[0]
                occurrence = record.get("occurrence") or {}
                if any(str(occurrence.get(k) or "").strip() for k in ("countryCode","stateProvince","locality","eventDate","basisOfRecord","datasetKey")):
                    db.execute("""INSERT OR REPLACE INTO occurrence_summary
                        (source_id,source_record_id,country_code,state_province,locality,event_date,basis_of_record,dataset_key)
                        VALUES (?,?,?,?,?,?,?,?)""", (
                        source_id, source_record_id, str(occurrence.get("countryCode") or "").strip(),
                        str(occurrence.get("stateProvince") or "").strip(), str(occurrence.get("locality") or "").strip(),
                        str(occurrence.get("eventDate") or "").strip(), str(occurrence.get("basisOfRecord") or "").strip(),
                        str(occurrence.get("datasetKey") or "").strip()))
                    occurrence_count += 1
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
        fish_taxa = db.execute("SELECT COUNT(*) FROM taxon WHERE library_group = ?", ("Cá nước ngọt",)).fetchone()[0]
        fish_with_media = db.execute("""SELECT COUNT(*) FROM taxon t
            WHERE t.library_group = ? AND EXISTS (
                SELECT 1 FROM species_media m
                WHERE m.source_id=t.source_id AND m.source_record_id=t.source_record_id
            )""", ("Cá nước ngọt",)).fetchone()[0]
        fish_pending_media = fish_taxa - fish_with_media
        meta = {
            "schemaVersion": SCHEMA_VERSION,
            "recordCount": count,
            "acceptedRecordCount": accepted,
            "sourceId": source_meta.get("sourceId", ""),
            "sourceVersion": source_meta.get("version", ""),
            "sourceDoi": source_meta.get("versionDoi", ""),
            "sourceLicense": source_meta.get("license", ""),
            "scope": "taxonomy-media-occurrence",
            "mediaRecordCount": media_count,
            "recordsWithMedia": db.execute("SELECT COUNT(DISTINCT source_id || char(31) || source_record_id) FROM species_media").fetchone()[0],
            "fishTaxa": fish_taxa,
            "fishWithMedia": fish_with_media,
            "fishPendingMedia": fish_pending_media,
            "fishPublishRule": "requires-at-least-one-licensed-media",
            "vernacularNameCount": vernacular_count,
            "occurrenceSummaryCount": occurrence_count,
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
