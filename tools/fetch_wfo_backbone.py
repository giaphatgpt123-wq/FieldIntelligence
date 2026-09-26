#!/usr/bin/env python3
"""Build a streamed, provenance-preserving WFO taxonomy package from the official backbone ZIP.

This tool only imports taxonomy/classification from World Flora Online. It never derives
edibility, toxicity, medicinal efficacy, treatment advice, or image-identification claims.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import io
import json
import zipfile
from pathlib import Path

SOURCE_ID = "wfo-taxonomic-backbone"
SOURCE_AUTHORITY = "World Flora Online Consortium"
SOURCE_LICENSE = "CC0-1.0"
SOURCE_SCOPE = "taxonomy-only"
SOURCE_DOWNLOAD_PAGE = "https://www.worldfloraonline.org/downloadData"
SOURCE_ARCHIVE_URL = "https://zenodo.org/records/20782718/files/_DwC_backbone_R.zip?download=1"
SOURCE_VERSION = "2026-06"
SOURCE_VERSION_DOI = "10.5281/zenodo.20782718"


def clean(value: object) -> str:
    return str(value or "").strip()


def first(row: dict[str, str], *keys: str) -> str:
    for key in keys:
        value = clean(row.get(key))
        if value:
            return value
    return ""


def choose_taxon_member(names: list[str]) -> str:
    files = [n for n in names if not n.endswith("/")]
    ranked = []
    for name in files:
        lower = name.lower()
        base = lower.rsplit("/", 1)[-1]
        score = None
        if base == "classification.csv":
            score = 0
        elif base == "taxon.txt":
            score = 1
        elif base == "taxa.txt":
            score = 2
        elif base in {"taxon.csv", "taxa.csv"}:
            score = 3
        elif "classification" in base and base.endswith((".csv", ".txt")):
            score = 4
        elif "taxon" in base and base.endswith((".csv", ".txt")):
            score = 5
        if score is not None:
            ranked.append((score, len(name), name))
    if not ranked:
        raise SystemExit("WFO archive does not contain a recognizable taxonomy table")
    return min(ranked)[2]


def detect_dialect(sample: str) -> csv.Dialect:
    try:
        return csv.Sniffer().sniff(sample, delimiters=",\t;")
    except csv.Error:
        return csv.excel_tab if "\t" in sample else csv.excel


def iter_rows_from_zip(path: Path):
    with zipfile.ZipFile(path) as archive:
        member = choose_taxon_member(archive.namelist())
        with archive.open(member, "r") as raw:
            text = io.TextIOWrapper(raw, encoding="utf-8-sig", newline="")
            sample = text.read(8192)
            dialect = detect_dialect(sample)
            text.seek(0)
            reader = csv.DictReader(text, dialect=dialect)
            if not reader.fieldnames:
                raise SystemExit(f"WFO taxonomy table has no header: {member}")
            yield member, reader
            for row in reader:
                yield member, row


def normalize_wfo(row: dict[str, str]) -> dict:
    taxon_id = first(row, "taxonID", "taxonId", "taxonid", "id", "taxon_id")
    scientific = first(row, "scientificName", "scientificname", "scientific_name", "full_name", "name")
    return {
        "schemaVersion": 1,
        "sourceId": SOURCE_ID,
        "sourceRecordId": taxon_id,
        "scientificName": scientific,
        "acceptedNameUsageId": first(row, "acceptedNameUsageID", "acceptedNameUsageId", "accepted_name_usage_id"),
        "taxonomicStatus": first(row, "taxonomicStatus", "taxonomicstatus", "status"),
        "kingdom": first(row, "kingdom"),
        "phylum": first(row, "phylum", "division"),
        "class": first(row, "class"),
        "order": first(row, "order"),
        "family": first(row, "family"),
        "genus": first(row, "genus"),
        "specificEpithet": first(row, "specificEpithet", "specificepithet", "species"),
        "vietnameseName": "",
        "libraryGroup": "Thực vật",
        "categories": [],
        "provenance": {
            "authority": SOURCE_AUTHORITY,
            "license": SOURCE_LICENSE,
            "scope": SOURCE_SCOPE,
            "downloadPage": SOURCE_DOWNLOAD_PAGE,
            "archiveUrl": SOURCE_ARCHIVE_URL,
            "version": SOURCE_VERSION,
            "versionDoi": SOURCE_VERSION_DOI,
        },
    }


def valid(record: dict) -> bool:
    return bool(clean(record.get("sourceRecordId")) and clean(record.get("scientificName")))


def build(archive_path: Path, output_path: Path, metadata_path: Path) -> dict:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    accepted = 0
    source_member = ""
    sha = hashlib.sha256()

    with gzip.open(output_path, "wt", encoding="utf-8", newline="\n") as out:
        iterator = iter_rows_from_zip(archive_path)
        try:
            source_member, first_row = next(iterator)
        except StopIteration:
            raise SystemExit("WFO archive taxonomy table is empty")

        def emit(row: dict[str, str]) -> None:
            nonlocal count, accepted
            record = normalize_wfo(row)
            if not valid(record):
                return
            line = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
            out.write(line)
            sha.update(line.encode("utf-8"))
            count += 1
            if record["taxonomicStatus"].lower() in {"accepted", "accepted name", "acceptedname"}:
                accepted += 1

        emit(first_row)
        for _, row in iterator:
            emit(row)

    metadata = {
        "schemaVersion": 1,
        "sourceId": SOURCE_ID,
        "authority": SOURCE_AUTHORITY,
        "license": SOURCE_LICENSE,
        "scope": SOURCE_SCOPE,
        "downloadPage": SOURCE_DOWNLOAD_PAGE,
        "archiveUrl": SOURCE_ARCHIVE_URL,
        "version": SOURCE_VERSION,
        "versionDoi": SOURCE_VERSION_DOI,
        "archiveMember": source_member,
        "recordCount": count,
        "acceptedRecordCount": accepted,
        "normalizedNdjsonSha256": sha.hexdigest(),
        "safety": {
            "taxonomyDoesNotImplyEdibility": True,
            "taxonomyDoesNotImplyToxicity": True,
            "taxonomyDoesNotImplyTreatment": True,
            "taxonomyDoesNotIdentifyPhotos": True,
        },
    }
    metadata_path.parent.mkdir(parents=True, exist_ok=True)
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    args = parser.parse_args()
    metadata = build(args.archive, args.output, args.metadata)
    print(json.dumps({"recordCount": metadata["recordCount"], "acceptedRecordCount": metadata["acceptedRecordCount"]}))


if __name__ == "__main__":
    main()
