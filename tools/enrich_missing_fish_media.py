#!/usr/bin/env python3
"""Add licensed fish images to an existing audited taxonomy without repeating GBIF resolution."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import urllib.error
from pathlib import Path
from typing import Callable

from tools.enrich_inaturalist_fish_media import find_exact_taxon_image as find_inaturalist
from tools.enrich_wikidata_commons_fish_media import find_exact_taxon_image as find_commons


def enrich(
    source: Path,
    output: Path,
    meta_path: Path,
    inat_finder: Callable[[str], tuple[dict | None, str]] = find_inaturalist,
    commons_finder: Callable[[str], tuple[dict | None, str]] = find_commons,
) -> dict:
    metadata = json.loads(meta_path.read_text(encoding="utf-8"))
    digest = hashlib.sha256()
    existing = added_inat = added_commons = 0
    missing: list[dict] = []
    errors: list[dict] = []
    output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(source, "rt", encoding="utf-8") as src, gzip.open(output, "wt", encoding="utf-8", newline="\n") as dst:
        for line in src:
            if not line.strip():
                continue
            row = json.loads(line)
            media = list(row.get("mediaItems") or [])
            name = str(row.get("species") or row.get("scientificName") or "").strip()
            if media:
                existing += 1
            else:
                for provider, finder in (("iNaturalist", inat_finder), ("Wikimedia Commons", commons_finder)):
                    try:
                        item, reason = finder(name)
                    except (RuntimeError, urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
                        item, reason = None, "api-error"
                        errors.append({"scientificName": name, "provider": provider, "error": str(exc)})
                    if item:
                        media = [item]
                        if provider == "iNaturalist":
                            added_inat += 1
                        else:
                            added_commons += 1
                        break
                if media:
                    row["mediaItems"] = media
                    row["media"] = media[0]
                else:
                    missing.append({"acceptedTaxonId": str(row.get("sourceRecordId") or ""), "scientificName": name})
            encoded = (json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
            dst.write(encoded.decode("utf-8"))
            digest.update(encoded)
    assert existing + added_inat + added_commons + len(missing) == metadata["recordCount"]
    metadata.update({
        "normalizedNdjsonSha256": digest.hexdigest(),
        "recordsWithMedia": existing + added_inat + added_commons,
        "recordsWithoutMedia": len(missing),
        "recordsWithoutMediaDetails": missing,
        "inaturalistFallbackAdded": int(metadata.get("inaturalistFallbackAdded", 0)) + added_inat,
        "wikimediaCommonsFallbackAdded": int(metadata.get("wikimediaCommonsFallbackAdded", 0)) + added_commons,
        "mediaCandidates": int(metadata.get("mediaCandidates", 0)) + added_inat + added_commons,
    })
    meta_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"existingWithMedia": existing, "addedInaturalist": added_inat, "addedCommons": added_commons,
            "stillMissing": len(missing), "errors": errors}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    report = enrich(args.input, args.output, args.metadata)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print({k: v for k, v in report.items() if k != "errors"})


if __name__ == "__main__":
    main()
