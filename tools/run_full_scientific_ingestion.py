#!/usr/bin/env python3
"""Merge approved scientific-source exports into one normalized library package.

This runner is intentionally strict. It supports bulk loading where reuse is explicit, while
keeping medical/traditional claims out of taxonomy ingestion. Source rights are checked through
full_scientific_sources.json before any input is accepted.

Example:
  python tools/run_full_scientific_ingestion.py \
    --input wfo-taxonomic-backbone=/data/wfo_taxon.txt \
    --output build/scientific-library.json

PRC example after terms are reviewed:
  python tools/run_full_scientific_ingestion.py \
    --input prc-vietnam-genebank=/data/prc.csv \
    --source-terms-confirmed prc-vietnam-genebank \
    --output build/scientific-library.json
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

import build_scientific_library as base

MANIFEST = Path(__file__).with_name("full_scientific_sources.json")


def load_manifest() -> Dict[str, dict]:
    raw = json.loads(MANIFEST.read_text(encoding="utf-8"))
    result = {}
    for source in raw.get("sources", []):
        source_id = str(source.get("id", "")).strip()
        if not source_id:
            raise SystemExit("source manifest contains an empty id")
        if source_id in result:
            raise SystemExit(f"duplicate source id in manifest: {source_id}")
        result[source_id] = source
    return result


def parse_input(value: str) -> Tuple[str, Path]:
    if "=" not in value:
        raise argparse.ArgumentTypeError("--input must use sourceId=/path/to/export")
    source_id, path = value.split("=", 1)
    source_id = source_id.strip()
    if not source_id or not path.strip():
        raise argparse.ArgumentTypeError("--input must use sourceId=/path/to/export")
    return source_id, Path(path).expanduser()


def import_source(source_id: str, path: Path, manifest: Dict[str, dict], confirmed: set[str]) -> Iterable[dict]:
    if source_id not in manifest:
        raise SystemExit(f"source is not registered: {source_id}")
    if not path.is_file():
        raise SystemExit(f"input file not found: {path}")

    policy = manifest[source_id].get("ingestion")
    if source_id == "wfo-taxonomic-backbone":
        yield from base.build("wfo", path, terms_confirmed=True)
        return
    if source_id == "prc-vietnam-genebank":
        yield from base.build("prc", path, terms_confirmed=source_id in confirmed)
        return

    if policy in {
        "reference_metadata_only_until_terms_verified",
        "reference_and_targeted_records",
        "curated_evidence_records",
    }:
        raise SystemExit(
            f"{source_id} is not a generic bulk source. Import it through a source-specific, "
            "evidence-preserving adapter instead of treating publication text as taxonomy data."
        )

    raise SystemExit(
        f"{source_id} requires a source-specific adapter and/or dataset-level licence review before bulk ingestion"
    )


def stable_key(record: dict) -> Tuple[str, str, str]:
    return (
        str(record.get("sourceId", "")).strip().lower(),
        str(record.get("sourceRecordId", "")).strip().lower(),
        str(record.get("scientificName", "")).strip().lower(),
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", action="append", default=[], type=parse_input, help="sourceId=/path/to/export")
    parser.add_argument("--source-terms-confirmed", action="append", default=[])
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    manifest = load_manifest()
    confirmed = set(args.source_terms_confirmed)
    merged: List[dict] = []
    seen = set()
    counts: Dict[str, int] = {}

    for source_id, path in args.input:
        imported = 0
        for record in import_source(source_id, path, manifest, confirmed):
            key = stable_key(record)
            if key in seen:
                continue
            seen.add(key)
            merged.append(record)
            imported += 1
        counts[source_id] = counts.get(source_id, 0) + imported

    payload = {
        "schemaVersion": base.SCHEMA_VERSION,
        "recordCount": len(merged),
        "sourceCounts": counts,
        "records": merged,
        "safety": {
            "taxonomyDoesNotImplyEdibility": True,
            "taxonomyDoesNotImplyToxicity": True,
            "taxonomyDoesNotImplyTreatment": True,
            "medicalClaimsRequireSeparateEvidence": True,
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"recordCount": len(merged), "sourceCounts": counts}, ensure_ascii=False))


if __name__ == "__main__":
    main()
