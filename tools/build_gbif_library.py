#!/usr/bin/env python3
"""Normalize licensed GBIF/Darwin Core exports for the FieldIntelligence scientific library.

The adapter requires dataset/download provenance at import time. It preserves occurrence/taxonomy
metadata only and never derives danger, edibility, toxicity, medicinal use, or treatment advice.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
from pathlib import Path
from typing import Dict, Iterator

ALLOWED_LICENSES = {
    "CC0-1.0": False,
    "CC-BY-4.0": False,
    "CC-BY-NC-4.0": True,
}


def clean(value: object) -> str:
    return str(value or "").strip()


def first(row: Dict[str, str], *keys: str) -> str:
    for key in keys:
        value = clean(row.get(key))
        if value:
            return value
    return ""


def read_delimited(path: Path) -> Iterator[Dict[str, str]]:
    sample = path.read_text(encoding="utf-8-sig", errors="replace")[:16384]
    try:
        dialect = csv.Sniffer().sniff(sample, delimiters=",\t;")
    except csv.Error:
        dialect = csv.excel_tab if "\t" in sample else csv.excel
    with path.open("r", encoding="utf-8-sig", errors="replace", newline="") as handle:
        yield from csv.DictReader(handle, dialect=dialect)


def library_group(row: Dict[str, str]) -> str:
    kingdom = first(row, "kingdom").lower()
    clazz = first(row, "class", "classKey").lower()
    if kingdom == "plantae":
        return "Thực vật"
    if kingdom == "fungi":
        return "Nấm"
    # Fish must be separated before the generic Animalia bucket. GBIF exports can
    # carry either modern class names or higher-rank/order information.
    fish_classes = {"actinopterygii", "elasmobranchii", "sarcopterygii", "myxini"}
    fish_orders = {
        "cypriniformes", "siluriformes", "anabantiformes", "perciformes",
        "synbranchiformes", "osteoglossiformes", "clupeiformes",
        "beloniformes", "gobiiformes", "cichliformes"
    }
    order = first(row, "order").lower()
    if kingdom == "animalia" and (clazz in fish_classes or order in fish_orders):
        return "Cá nước ngọt"
    if kingdom == "animalia" and clazz == "insecta":
        return "Côn trùng"
    if kingdom == "animalia":
        return "Động vật"
    return "Khác"


def normalize(row: Dict[str, str], dataset_doi: str, publisher: str, license_id: str) -> dict:
    source_record_id = first(row, "occurrenceID", "gbifID", "taxonID", "taxonKey", "id")
    scientific = first(row, "scientificName", "acceptedScientificName", "species")
    return {
        "schemaVersion": 1,
        "sourceId": "gbif",
        "sourceRecordId": source_record_id,
        "scientificName": scientific,
        "acceptedNameUsageId": first(row, "acceptedTaxonKey", "acceptedNameUsageID"),
        "taxonomicStatus": first(row, "taxonomicStatus"),
        "kingdom": first(row, "kingdom"),
        "phylum": first(row, "phylum"),
        "class": first(row, "class"),
        "order": first(row, "order"),
        "family": first(row, "family"),
        "genus": first(row, "genus"),
        "species": first(row, "species"),
        "vernacularName": first(row, "vernacularName"),
        "libraryGroup": library_group(row),
        "media": {
            "mediaType": first(row, "mediaType", "type"),
            "identifier": first(row, "identifier", "accessURI"),
            "references": first(row, "references"),
            "title": first(row, "title"),
            "description": first(row, "description"),
            "creator": first(row, "creator"),
            "rightsHolder": first(row, "rightsHolder"),
            "license": first(row, "mediaLicense", "license"),
        },
        "occurrence": {
            "basisOfRecord": first(row, "basisOfRecord"),
            "countryCode": first(row, "countryCode"),
            "stateProvince": first(row, "stateProvince"),
            "locality": first(row, "locality"),
            "decimalLatitude": first(row, "decimalLatitude"),
            "decimalLongitude": first(row, "decimalLongitude"),
            "eventDate": first(row, "eventDate"),
            "recordedBy": first(row, "recordedBy"),
            "datasetKey": first(row, "datasetKey"),
        },
        "provenance": {
            "authority": "GBIF Secretariat and dataset publisher",
            "publisher": publisher,
            "datasetDoi": dataset_doi,
            "license": license_id,
            "scope": "taxonomy-occurrence-and-media-metadata",
            "nonCommercialRestriction": ALLOWED_LICENSES[license_id],
        },
    }


def valid(record: dict) -> bool:
    return bool(clean(record.get("sourceRecordId")) and clean(record.get("scientificName")))


def validate_provenance(dataset_doi: str, publisher: str, license_id: str) -> None:
    if not dataset_doi.startswith("10.") or "/" not in dataset_doi:
        raise SystemExit("GBIF import requires a DOI-like dataset/download identifier")
    if not publisher.strip():
        raise SystemExit("GBIF import requires the dataset publisher")
    if license_id not in ALLOWED_LICENSES:
        raise SystemExit("Unsupported GBIF licence; expected CC0-1.0, CC-BY-4.0 or CC-BY-NC-4.0")


def build(input_path: Path, output_path: Path, metadata_path: Path, dataset_doi: str, publisher: str, license_id: str) -> dict:
    validate_provenance(dataset_doi, publisher, license_id)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    seen = set()
    groups: dict[str, int] = {}
    count = 0
    sha = hashlib.sha256()
    with gzip.open(output_path, "wt", encoding="utf-8", newline="\n") as out:
        for row in read_delimited(input_path):
            record = normalize(row, dataset_doi, publisher, license_id)
            if not valid(record):
                continue
            key = (record["sourceRecordId"].lower(), record["scientificName"].lower())
            if key in seen:
                continue
            seen.add(key)
            line = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
            out.write(line)
            sha.update(line.encode("utf-8"))
            count += 1
            group = record["libraryGroup"]
            groups[group] = groups.get(group, 0) + 1
    metadata = {
        "schemaVersion": 1,
        "sourceId": "gbif",
        "datasetDoi": dataset_doi,
        "publisher": publisher,
        "license": license_id,
        "nonCommercialRestriction": ALLOWED_LICENSES[license_id],
        "recordCount": count,
        "groupCounts": groups,
        "normalizedNdjsonSha256": sha.hexdigest(),
        "safety": {
            "taxonomyDoesNotIdentifyPhotos": True,
            "occurrenceDoesNotProveCurrentPresence": True,
            "recordsDoNotImplyDanger": True,
            "recordsDoNotImplyEdibility": True,
            "recordsDoNotImplyTreatment": True,
        },
    }
    metadata_path.parent.mkdir(parents=True, exist_ok=True)
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--dataset-doi", required=True)
    parser.add_argument("--publisher", required=True)
    parser.add_argument("--license", required=True, choices=sorted(ALLOWED_LICENSES))
    args = parser.parse_args()
    meta = build(args.input, args.output, args.metadata, args.dataset_doi, args.publisher, args.license)
    print(json.dumps({"recordCount": meta["recordCount"], "groupCounts": meta["groupCounts"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
