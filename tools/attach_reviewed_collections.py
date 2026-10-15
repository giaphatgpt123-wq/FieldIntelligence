#!/usr/bin/env python3
"""Attach reviewed Vietnamese collection membership to an existing taxonomy pack.

CSV columns: collection_id,scientific_name,vietnamese_name,source_url,reviewed_by.
Only one accepted species-level binomial already present in the pack is accepted. Author
abbreviations can differ between sources; ambiguous names still block the whole batch.
"""
import argparse
import csv
import re
import sqlite3
from pathlib import Path

COLLECTIONS = {"vegetables", "flowers", "timber-trees", "fruit-crops"}
FIELDS = {"collection_id", "scientific_name", "vietnamese_name", "source_url", "reviewed_by"}


def attach(db_path: Path, csv_path: Path, goals_path: Path | None = None) -> dict[str, int]:
    with csv_path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not FIELDS.issubset(reader.fieldnames or []):
            raise ValueError("Thiếu cột duyệt danh mục")
        rows = list(reader)
    goals = []
    if goals_path is not None:
        with goals_path.open(encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            if not {"collection_id", "target_count"}.issubset(reader.fieldnames or []):
                raise ValueError("Thiếu cột mục tiêu danh mục")
            for row in reader:
                category = (row["collection_id"] or "").strip()
                raw = (row["target_count"] or "").strip()
                if category not in COLLECTIONS or any(g[0] == category for g in goals):
                    raise ValueError("Danh mục mục tiêu không hợp lệ hoặc trùng")
                if raw:
                    count = int(raw)
                    if count <= 0:
                        raise ValueError("Mục tiêu phải lớn hơn 0")
                    goals.append((category, count))
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
            words = scientific.split()
            if len(words) < 2 or not all(re.fullmatch(r"[A-Za-z-]+", word) for word in words[:2]):
                raise ValueError(f"Dòng {index}: tên khoa học không có chi và loài: {scientific}")
            binomial = " ".join(words[:2]).casefold()
            candidates = db.execute(
                """SELECT source_id,source_record_id,scientific_name_search FROM taxon
                   WHERE library_group='Thực vật' AND lower(taxonomic_status) IN
                     ('accepted','accepted name','acceptedname')
                     AND (scientific_name_search=? OR scientific_name_search LIKE ?)""",
                (binomial, binomial + " %"),
            ).fetchall()
            matches = [row[:2] for row in candidates if
                       len(row[2].split()) >= 2 and
                       " ".join(row[2].split()[:2]) == binomial and
                       (len(row[2].split()) == 2 or row[2].split()[2] not in
                        {"subsp.", "subsp", "var.", "var", "f.", "f"})]
            if len(matches) != 1:
                raise ValueError(f"Dòng {index}: {scientific} có {len(matches)} bản ghi loài được chấp nhận; cần đối chiếu thủ công")
            approved.append((category, *matches[0], vietnamese, source, reviewer))
        with db:
            db.execute("""CREATE TABLE IF NOT EXISTS reviewed_collection (
                collection_id TEXT NOT NULL, source_id TEXT NOT NULL, source_record_id TEXT NOT NULL,
                vietnamese_name TEXT NOT NULL, source_url TEXT NOT NULL, reviewed_by TEXT NOT NULL,
                PRIMARY KEY(collection_id,source_id,source_record_id))""")
            placeholders = ",".join("?" for _ in COLLECTIONS)
            db.execute(
                f"DELETE FROM reviewed_collection WHERE collection_id IN ({placeholders})",
                tuple(sorted(COLLECTIONS)),
            )
            db.executemany("INSERT INTO reviewed_collection VALUES (?,?,?,?,?,?)", approved)
            db.execute("""CREATE TABLE IF NOT EXISTS reviewed_collection_goal (
                collection_id TEXT PRIMARY KEY, target_count INTEGER NOT NULL CHECK(target_count>0))""")
            db.execute("DELETE FROM reviewed_collection_goal")
            db.executemany("INSERT INTO reviewed_collection_goal VALUES (?,?)", goals)
        return {category: sum(row[0] == category for row in approved) for category in sorted(COLLECTIONS)}
    finally:
        db.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--reviewed-csv", type=Path, required=True)
    parser.add_argument("--goals-csv", type=Path)
    args = parser.parse_args()
    print(attach(args.database, args.reviewed_csv, args.goals_csv))
