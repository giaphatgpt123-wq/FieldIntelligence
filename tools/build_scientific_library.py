#!/usr/bin/env python3
"""Build normalized scientific-library rows from approved source exports.

The importer is intentionally conservative:
- WFO Taxonomic Backbone may be imported directly because its static backbone is CC0.
- Vietnamese Plant Resources Center (PRC) exports are supported, but redistribution/import
  is blocked unless the caller explicitly confirms that reuse terms have been reviewed.
- The tool never derives edibility, toxicity, medicinal efficacy or treatment claims.
"""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Dict, Iterable, Iterator

SCHEMA_VERSION = 1


def clean(value: object) -> str:
    return str(value or "").strip()


def first(row: Dict[str, str], *keys: str) -> str:
    for key in keys:
        value = clean(row.get(key))
        if value:
            return value
    return ""


def normalize_wfo(row: Dict[str, str]) -> dict:
    scientific = first(row, "scientificName", "scientificname")
    taxon_id = first(row, "taxonID", "taxonId", "id")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "sourceId": "wfo-taxonomic-backbone",
        "sourceRecordId": taxon_id,
        "scientificName": scientific,
        "acceptedNameUsageId": first(row, "acceptedNameUsageID", "acceptedNameUsageId"),
        "taxonomicStatus": first(row, "taxonomicStatus"),
        "kingdom": first(row, "kingdom"),
        "phylum": first(row, "phylum"),
        "class": first(row, "class"),
        "order": first(row, "order"),
        "family": first(row, "family"),
        "genus": first(row, "genus"),
        "specificEpithet": first(row, "specificEpithet"),
        "vietnameseName": "",
        "libraryGroup": "Thực vật",
        "categories": [],
        "provenance": {
            "authority": "World Flora Online Consortium",
            "license": "CC0-1.0",
            "scope": "taxonomy-only",
        },
    }


def normalize_prc(row: Dict[str, str]) -> dict:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "sourceId": "prc-vietnam-genebank",
        "sourceRecordId": first(row, "GBVN No", "GBVN No.", "GBVN", "gbvn"),
        "scientificName": first(row, "Tên khoa học", "Ten khoa hoc", "scientificName"),
        "acceptedNameUsageId": "",
        "taxonomicStatus": "",
        "kingdom": "Plantae",
        "phylum": "",
        "class": "",
        "order": "",
        "family": "",
        "genus": "",
        "specificEpithet": "",
        "vietnameseName": first(row, "Tên cây trồng", "Ten cay trong"),
        "libraryGroup": "Thực vật",
        "categories": [v for v in [first(row, "Nhóm", "Nhom")] if v],
        "agriculture": {
            "germplasmName": first(row, "Tên nguồn GEN", "Ten nguon GEN"),
            "germplasmNature": first(row, "Bản chất nguồn GEN", "Ban chat nguon GEN"),
            "ethnicGroup": first(row, "Dân tộc", "Dan toc"),
            "holdingInstitution": first(row, "Cơ quan lưu trữ", "Co quan luu tru"),
            "collectionPlace": first(row, "Nơi thu thập", "Noi thu thap"),
            "year": first(row, "Năm", "Nam"),
            "sourceNote": first(row, "Nguồn", "Nguon"),
            "note": first(row, "Ghi chú", "Ghi chu"),
        },
        "provenance": {
            "authority": "Trung tâm Tài nguyên thực vật",
            "license": "TERMS_CONFIRMED_BY_OPERATOR",
            "scope": "agricultural-genetic-resource-metadata",
        },
    }


def read_delimited(path: Path) -> Iterator[Dict[str, str]]:
    sample = path.read_text(encoding="utf-8-sig")[:8192]
    dialect = csv.Sniffer().sniff(sample, delimiters=",\t;")
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        yield from csv.DictReader(handle, dialect=dialect)


def valid_taxonomy_record(record: dict) -> bool:
    return bool(clean(record.get("scientificName")) and clean(record.get("sourceRecordId")))


def build(source: str, input_path: Path, terms_confirmed: bool) -> Iterable[dict]:
    if source == "prc" and not terms_confirmed:
        raise SystemExit(
            "PRC import blocked: review the source reuse/redistribution terms first, then rerun with --source-terms-confirmed."
        )
    normalizer = normalize_wfo if source == "wfo" else normalize_prc
    for row in read_delimited(input_path):
        record = normalizer(row)
        if valid_taxonomy_record(record):
            yield record


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", choices=("wfo", "prc"), required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-terms-confirmed", action="store_true")
    args = parser.parse_args()

    records = list(build(args.source, args.input, args.source_terms_confirmed))
    payload = {
        "schemaVersion": SCHEMA_VERSION,
        "source": args.source,
        "recordCount": len(records),
        "records": records,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {len(records)} records to {args.output}")


if __name__ == "__main__":
    main()
