#!/usr/bin/env python3
"""Reconcile the Vietnam freshwater-fish checklist against a taxonomy export.

The reconciler is conservative: it only auto-matches exact scientific names or a unique
canonical name. Ambiguous names remain unresolved. Synonyms resolve to the supplied accepted
name/key but remain marked as synonym matches for audit.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
from collections import defaultdict
from pathlib import Path


def clean(value: object) -> str:
    return str(value or "").strip()


def folded(value: object) -> str:
    return clean(value).casefold()


def canonical_guess(name: str) -> str:
    """Return a conservative genus + specific epithet guess, or empty when unsafe."""
    tokens = re.findall(r"[A-Za-zÀ-ỹ][A-Za-zÀ-ỹ.-]*", clean(name))
    if len(tokens) < 2:
        return ""
    genus, species = tokens[0], tokens[1]
    if not genus[:1].isupper() or not species[:1].islower():
        return ""
    return f"{genus} {species}"


def read_checklist(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                yield json.loads(line)


def read_delimited(path: Path):
    sample = path.read_text(encoding="utf-8-sig", errors="replace")[:16384]
    try:
        dialect = csv.Sniffer().sniff(sample, delimiters=",\t;")
    except csv.Error:
        dialect = csv.excel_tab if "\t" in sample else csv.excel
    with path.open("r", encoding="utf-8-sig", errors="replace", newline="") as handle:
        yield from csv.DictReader(handle, dialect=dialect)


def first(row: dict, *keys: str) -> str:
    for key in keys:
        value = clean(row.get(key))
        if value:
            return value
    return ""


def normalize_taxon(row: dict) -> dict:
    scientific = first(row, "scientificName", "scientific_name", "name")
    canonical = first(row, "canonicalName", "canonical_name") or canonical_guess(scientific)
    accepted_name = first(row, "acceptedScientificName", "acceptedName", "accepted_name")
    accepted_key = first(row, "acceptedTaxonKey", "acceptedNameUsageID", "accepted_id")
    taxon_id = first(row, "taxonID", "taxonKey", "id", "key")
    status = first(row, "taxonomicStatus", "status").casefold()
    is_synonym = status in {"synonym", "heterotypic synonym", "homotypic synonym", "proparte synonym"}
    return {
        "taxonId": taxon_id,
        "scientificName": scientific,
        "canonicalName": canonical,
        "acceptedScientificName": accepted_name or scientific,
        "acceptedTaxonId": accepted_key or taxon_id,
        "taxonomicStatus": status,
        "isSynonym": is_synonym,
    }


def build(checklist_path: Path, taxonomy_path: Path, output_path: Path, report_path: Path | None = None) -> dict:
    taxa = [normalize_taxon(row) for row in read_delimited(taxonomy_path)]
    taxa = [t for t in taxa if t["scientificName"] and t["taxonId"]]

    by_scientific: dict[str, list[dict]] = defaultdict(list)
    by_canonical: dict[str, list[dict]] = defaultdict(list)
    for taxon in taxa:
        by_scientific[folded(taxon["scientificName"])].append(taxon)
        if taxon["canonicalName"]:
            by_canonical[folded(taxon["canonicalName"])].append(taxon)

    report = {
        "total": 0,
        "presentInput": 0,
        "matched": 0,
        "matchedExact": 0,
        "matchedCanonical": 0,
        "matchedSynonym": 0,
        "ambiguous": 0,
        "unmatched": 0,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as out:
        for record in read_checklist(checklist_path):
            report["total"] += 1
            if record.get("presenceStatus") == "present":
                report["presentInput"] += 1
            scientific = clean(record.get("scientificName"))
            canonical = canonical_guess(scientific)
            candidates = by_scientific.get(folded(scientific), [])
            match_type = "exact"
            if not candidates and canonical:
                candidates = by_canonical.get(folded(canonical), [])
                match_type = "canonical"

            result = dict(record)
            if len(candidates) == 1:
                taxon = candidates[0]
                result["reconciliation"] = {
                    "status": "matched",
                    "matchType": "synonym" if taxon["isSynonym"] else match_type,
                    "taxonId": taxon["taxonId"],
                    "acceptedTaxonId": taxon["acceptedTaxonId"],
                    "matchedScientificName": taxon["scientificName"],
                    "acceptedScientificName": taxon["acceptedScientificName"],
                    "taxonomicStatus": taxon["taxonomicStatus"],
                }
                report["matched"] += 1
                if taxon["isSynonym"]:
                    report["matchedSynonym"] += 1
                elif match_type == "exact":
                    report["matchedExact"] += 1
                else:
                    report["matchedCanonical"] += 1
            elif len(candidates) > 1:
                result["reconciliation"] = {
                    "status": "ambiguous",
                    "canonicalName": canonical,
                    "candidateCount": len(candidates),
                    "candidateTaxonIds": sorted({c["taxonId"] for c in candidates}),
                }
                report["ambiguous"] += 1
            else:
                result["reconciliation"] = {"status": "unmatched", "canonicalName": canonical}
                report["unmatched"] += 1
            out.write(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n")

    if report_path:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checklist", required=True, type=Path)
    parser.add_argument("--taxonomy", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.checklist, args.taxonomy, args.output, args.report), ensure_ascii=False))


if __name__ == "__main__":
    main()
