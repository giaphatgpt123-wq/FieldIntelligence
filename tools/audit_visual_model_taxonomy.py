#!/usr/bin/env python3
"""Audit a candidate species classifier's labels against the installed WFO taxonomy.

This is a release gate for label coverage, not a measurement of image accuracy.
It does not claim that a model detects regions or identifies field specimens.
"""
from __future__ import annotations

import argparse
import csv
import json
import sqlite3
from pathlib import Path


def read_labels(path: Path) -> list[str]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        labels = []
        for row in csv.reader(handle):
            cells = [cell.strip() for cell in row if cell.strip()]
            if not cells:
                continue
            label = cells[1] if len(cells) > 1 and cells[0].isdigit() else cells[0]
            if label.casefold() in {"label", "name", "scientificname", "background", "unknown"}:
                continue
            labels.append(label)
    return labels


def audit(labels: list[str], database: Path) -> dict:
    if not labels:
        raise ValueError("No species labels found")
    unique = {label.casefold() for label in labels}
    if len(unique) != len(labels):
        raise ValueError("Duplicate labels in classifier")
    with sqlite3.connect(f"file:{database.resolve()}?mode=ro", uri=True) as db:
        matched = []
        missing = []
        for label in labels:
            # Authorship may follow a scientific binomial; avoid substring matches.
            key = label.casefold()
            row = db.execute(
                """SELECT 1 FROM taxon WHERE scientific_name_search = ?
                   OR scientific_name_search LIKE ? LIMIT 1""",
                (key, key + " %"),
            ).fetchone()
            (matched if row else missing).append(label)
    return {
        "labelCount": len(labels),
        "taxonomyMatched": len(matched),
        "taxonomyMissing": len(missing),
        "coverage": round(len(matched) / len(labels), 4),
        "unmatchedLabels": missing,
        "meaning": "Taxonomic name overlap only; not camera accuracy, safety or geographic coverage.",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = audit(read_labels(args.labels), args.taxonomy)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "unmatchedLabels"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
