#!/usr/bin/env python3
"""Audit normalized fish checklist coverage against collapsed production GBIF taxa."""
from __future__ import annotations
import argparse, gzip, json, re
from pathlib import Path


def clean(value):
    return str(value or "").strip()


def fold(value):
    return clean(value).casefold()


def canonical(name):
    tokens = re.findall(r"[A-Za-z][A-Za-z.-]*", clean(name))
    if len(tokens) < 2 or not tokens[0][:1].isupper() or not tokens[1][:1].islower():
        return ""
    return f"{tokens[0]} {tokens[1]}"


def read_ndjson(path: Path):
    opener = gzip.open if path.name.casefold().endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                yield json.loads(line)


def aliases(record):
    values = list(record.get("sourceScientificNames") or [])
    values += [record.get("scientificName"), record.get("acceptedScientificName")]
    result, seen = [], set()
    for value in values:
        value = clean(value)
        if value and fold(value) not in seen:
            result.append(value)
            seen.add(fold(value))
    return result


def build(checklist_path: Path, taxonomy_path: Path, output_path: Path, report_path: Path):
    taxa = []
    exact, canonical_index = {}, {}
    for row in read_ndjson(taxonomy_path):
        taxon_id = clean(row.get("acceptedNameUsageId") or row.get("sourceRecordId"))
        name = clean(row.get("scientificName"))
        if not taxon_id or not name:
            continue
        taxon = {"taxonId": taxon_id, "scientificName": name}
        taxa.append(taxon)
        for alias in aliases(row):
            exact.setdefault(fold(alias), set()).add(taxon_id)
            guessed = canonical(alias)
            if guessed:
                canonical_index.setdefault(fold(guessed), set()).add(taxon_id)

    by_id = {row["taxonId"]: row for row in taxa}
    report = {
        "total": 0, "presentInput": 0, "reviewInput": 0, "excludedInput": 0,
        "resolvedPresentChecklistCount": 0, "unresolvedPresentChecklistCount": 0,
        "resolvedReviewChecklistCount": 0, "resolvedExcludedChecklistCount": 0,
        "reconciledPresentTaxonCount": 0, "ambiguousCount": 0, "unmatchedCount": 0,
        "unresolvedPresentNames": []
    }
    present_taxa = set()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as out:
        for item in read_ndjson(checklist_path):
            report["total"] += 1
            status = clean(item.get("presenceStatus")).casefold()
            if status == "present": report["presentInput"] += 1
            elif status == "excluded": report["excludedInput"] += 1
            else:
                status = "review"
                report["reviewInput"] += 1
            name = clean(item.get("scientificName"))
            ids = set(exact.get(fold(name), set()))
            match_type = "exact-or-alias"
            if not ids:
                guess = canonical(name)
                ids = set(canonical_index.get(fold(guess), set())) if guess else set()
                match_type = "canonical"
            result = dict(item)
            if len(ids) == 1:
                taxon_id = next(iter(ids))
                result["reconciliation"] = {
                    "status": "matched", "matchType": match_type,
                    "acceptedTaxonId": taxon_id,
                    "acceptedScientificName": by_id[taxon_id]["scientificName"]
                }
                if status == "present":
                    report["resolvedPresentChecklistCount"] += 1
                    present_taxa.add(taxon_id)
                elif status == "excluded": report["resolvedExcludedChecklistCount"] += 1
                else: report["resolvedReviewChecklistCount"] += 1
            elif len(ids) > 1:
                result["reconciliation"] = {"status": "ambiguous", "candidateTaxonIds": sorted(ids)}
                report["ambiguousCount"] += 1
                if status == "present":
                    report["unresolvedPresentChecklistCount"] += 1
                    report["unresolvedPresentNames"].append(name)
            else:
                result["reconciliation"] = {"status": "unmatched"}
                report["unmatchedCount"] += 1
                if status == "present":
                    report["unresolvedPresentChecklistCount"] += 1
                    report["unresolvedPresentNames"].append(name)
            out.write(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n")
    report["reconciledPresentTaxonCount"] = len(present_taxa)
    report["unresolvedPresentNames"] = sorted(set(report["unresolvedPresentNames"]), key=str.casefold)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--checklist", required=True, type=Path)
    p.add_argument("--taxonomy", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    p.add_argument("--report", required=True, type=Path)
    a = p.parse_args()
    print(json.dumps(build(a.checklist, a.taxonomy, a.output, a.report), ensure_ascii=False))


if __name__ == "__main__":
    main()
