#!/usr/bin/env python3
"""Verify independently resolved checklist taxa are present in the production fish library."""
from __future__ import annotations
import argparse, gzip, json
from pathlib import Path


def clean(value):
    return str(value or "").strip()


def read_ndjson(path: Path):
    opener = gzip.open if path.name.casefold().endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                yield json.loads(line)


def build(resolved_checklist: Path, taxonomy: Path, output: Path, report_path: Path):
    library_ids = set()
    library_names = {}
    for row in read_ndjson(taxonomy):
        taxon_id = clean(row.get("acceptedNameUsageId") or row.get("sourceRecordId"))
        if not taxon_id:
            continue
        library_ids.add(taxon_id)
        library_names[taxon_id] = clean(row.get("scientificName"))

    report = {
        "total": 0,
        "presentInput": 0,
        "reviewInput": 0,
        "excludedInput": 0,
        "resolvedPresentChecklistCount": 0,
        "unresolvedPresentChecklistCount": 0,
        "resolvedReviewChecklistCount": 0,
        "resolvedExcludedChecklistCount": 0,
        "reconciledPresentTaxonCount": 0,
        "resolverBlockedPresentCount": 0,
        "missingLibraryPresentCount": 0,
        "unresolvedPresentNames": [],
    }
    present_taxa = set()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as out:
        for row in read_ndjson(resolved_checklist):
            report["total"] += 1
            presence = clean(row.get("presenceStatus")).casefold()
            if presence == "present":
                report["presentInput"] += 1
            elif presence == "excluded":
                report["excludedInput"] += 1
            else:
                presence = "review"
                report["reviewInput"] += 1

            resolution = row.get("taxonomyResolution") or {}
            resolver_status = clean(resolution.get("status")).casefold()
            taxon_id = clean(resolution.get("acceptedTaxonId"))
            result = dict(row)
            if resolver_status == "resolved" and taxon_id and taxon_id in library_ids:
                result["reconciliation"] = {
                    "status": "matched",
                    "matchType": "independent-taxonomy-resolution",
                    "acceptedTaxonId": taxon_id,
                    "acceptedScientificName": clean(resolution.get("acceptedScientificName")) or library_names.get(taxon_id, ""),
                }
                if presence == "present":
                    report["resolvedPresentChecklistCount"] += 1
                    present_taxa.add(taxon_id)
                elif presence == "excluded":
                    report["resolvedExcludedChecklistCount"] += 1
                else:
                    report["resolvedReviewChecklistCount"] += 1
            else:
                if resolver_status != "resolved":
                    status = "resolver-blocked"
                    reason = clean(resolution.get("reason")) or resolver_status or "missing-resolution"
                else:
                    status = "missing-library-taxon"
                    reason = f"accepted taxon {taxon_id or 'missing'} absent from production library"
                result["reconciliation"] = {
                    "status": status,
                    "reason": reason,
                    "acceptedTaxonId": taxon_id,
                }
                if presence == "present":
                    report["unresolvedPresentChecklistCount"] += 1
                    report["unresolvedPresentNames"].append(clean(row.get("scientificName")))
                    if status == "resolver-blocked":
                        report["resolverBlockedPresentCount"] += 1
                    else:
                        report["missingLibraryPresentCount"] += 1
            out.write(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n")

    report["reconciledPresentTaxonCount"] = len(present_taxa)
    report["unresolvedPresentNames"] = sorted(set(report["unresolvedPresentNames"]), key=str.casefold)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolved-checklist", required=True, type=Path)
    parser.add_argument("--taxonomy", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.resolved_checklist, args.taxonomy, args.output, args.report), ensure_ascii=False))


if __name__ == "__main__":
    main()
