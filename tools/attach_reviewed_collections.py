#!/usr/bin/env python3
"""Attach reviewed Vietnamese collection membership to an existing taxonomy pack.

CSV columns: collection_id,scientific_name,vietnamese_name,source_url,reviewed_by.
Only exact scientific names already present in the pack are accepted. A blank review field
or an ambiguous scientific name blocks the entire batch; no taxonomy row is invented.
"""
import argparse
import csv
import sqlite3
from pathlib import Path

COLLECTIONS = {"flowers", "timber-trees", "fruit-crops"}
FIELDS = {"collection_id", "scientific_name", "vietnamese_name", "source_url", "reviewed_by"}


def attach(db_path: Path, csv_path: Path) -> dict[str, int]:
    with csv_path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not FIELDS.issubset(reader.fieldnames or []):
            raise ValueError("Thiếu cột duyệt danh mục")
        rows = list(reader)
    db = sqlite3.connect(db_path)
    try:
        if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise ValueError("SQLite không hợp lệ")
        approved = []
        seen = set()
        for index, row in enumerate(rows, 2):
            category = (row["collection_id"] or "").strip()
            scientific = (row["scientific_name"] or "").strip()
            vietnamese = (row["vietnamese_name"] or "").strip()
            source = (row["source_url"] or "").strip()
            reviewer = (row["reviewed_by"] or "").strip()
            if category not in COLLECTIONS or not scientific or not vietnamese or not source.startswith("https://") or not reviewer:
                raise ValueError(f"Dòng {index}: thiếu danh mục, tên, nguồn HTTPS hoặc người duyệt")
            key = category, scientific.casefold()
            if key in seen:
                raise ValueError(f"Dòng {index}: trùng phân loại")
            seen.add(key)
            matches = db.execute(
                "SELECT source_id,source_record_id FROM taxon WHERE scientific_name_search=?",
                (scientific.casefold(),),
            ).fetchall()
            if len(matches) != 1:
                raise ValueError(f"Dòng {index}: tên khoa học không có đúng một bản ghi: {scientific}")
            approved.append((category, *matches[0], vietnamese, source, reviewer))
        with db:
            db.execute("""CREATE TABLE IF NOT EXISTS reviewed_collection (
                collection_id TEXT NOT NULL, source_id TEXT NOT NULL, source_record_id TEXT NOT NULL,
                vietnamese_name TEXT NOT NULL, source_url TEXT NOT NULL, reviewed_by TEXT NOT NULL,
                PRIMARY KEY(collection_id,source_id,source_record_id))""")
            db.execute("DELETE FROM reviewed_collection WHERE collection_id IN (?,?,?)", tuple(sorted(COLLECTIONS)))
            db.executemany("INSERT INTO reviewed_collection VALUES (?,?,?,?,?,?)", approved)
        return {category: sum(row[0] == category for row in approved) for category in sorted(COLLECTIONS)}
    finally:
        db.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--reviewed-csv", type=Path, required=True)
    args = parser.parse_args()
    print(attach(args.database, args.reviewed_csv))
