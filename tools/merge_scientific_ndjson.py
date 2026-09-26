#!/usr/bin/env python3
"""Merge normalized scientific NDJSON streams into one deterministic input.

Records remain source-scoped. Duplicate (sourceId, sourceRecordId) keys are rejected rather than
silently overwritten so a packaged scientific database stays auditable and reproducible.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path


def open_text(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def build(inputs: list[Path], output: Path, metadata: Path | None = None) -> dict:
    if len(inputs) < 1:
        raise SystemExit("At least one normalized NDJSON input is required")
    for path in inputs:
        if not path.is_file():
            raise SystemExit(f"Missing input: {path}")

    output.parent.mkdir(parents=True, exist_ok=True)
    seen: set[tuple[str, str]] = set()
    groups: dict[str, int] = {}
    sources: dict[str, int] = {}
    input_reports = []
    count = 0
    records_with_media = 0
    digest = hashlib.sha256()

    with gzip.open(output, "wt", encoding="utf-8", newline="\n") as target:
        for path in inputs:
            input_count = 0
            input_sha = hashlib.sha256()
            with open_text(path) as handle:
                for line_number, line in enumerate(handle, start=1):
                    if not line.strip():
                        continue
                    record = json.loads(line)
                    source_id = str(record.get("sourceId") or "").strip()
                    record_id = str(record.get("sourceRecordId") or "").strip()
                    scientific = str(record.get("scientificName") or "").strip()
                    if not source_id or not record_id or not scientific:
                        raise SystemExit(f"Invalid normalized record at {path}:{line_number}")
                    key = (source_id, record_id)
                    if key in seen:
                        raise SystemExit(f"Duplicate scientific record key: {source_id}/{record_id}")
                    seen.add(key)

                    encoded = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
                    target.write(encoded)
                    payload = encoded.encode("utf-8")
                    digest.update(payload)
                    input_sha.update(payload)
                    count += 1
                    input_count += 1
                    group = str(record.get("libraryGroup") or "Khác").strip() or "Khác"
                    groups[group] = groups.get(group, 0) + 1
                    sources[source_id] = sources.get(source_id, 0) + 1
                    media_items = record.get("mediaItems") or []
                    legacy_media = record.get("media") or {}
                    if any(str(item.get("identifier") or "").strip() for item in media_items) or str(legacy_media.get("identifier") or "").strip():
                        records_with_media += 1
            input_reports.append({"path": path.name, "recordCount": input_count, "normalizedSha256": input_sha.hexdigest()})

    report = {
        "schemaVersion": 1,
        "sourceId": "fieldintelligence-merged-scientific-library",
        "version": "merged-normalized-input",
        "license": "mixed-see-record-provenance",
        "recordCount": count,
        "recordsWithMediaBeforeLicenseGate": records_with_media,
        "groupCounts": groups,
        "sourceCounts": sources,
        "inputs": input_reports,
        "normalizedNdjsonSha256": digest.hexdigest(),
    }
    if metadata:
        metadata.parent.mkdir(parents=True, exist_ok=True)
        metadata.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", action="append", required=True, type=Path, dest="inputs")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--metadata", type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.inputs, args.output, args.metadata), ensure_ascii=False))


if __name__ == "__main__":
    main()
