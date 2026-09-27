#!/usr/bin/env python3
"""Validate fish checklist coverage/media and store release-gate metrics in scientific SQLite."""
from __future__ import annotations
import argparse, json, sqlite3
from pathlib import Path


def clean(value):
    return str(value or "").strip()


def attach(database: Path, reconciled_path: Path, coverage_path: Path, expected_total: int, expected_present: int, expected_review: int, expected_excluded: int, allow_incomplete: bool = False):
    coverage = json.loads(coverage_path.read_text(encoding="utf-8"))
    expected = {
        "total": expected_total,
        "presentInput": expected_present,
        "reviewInput": expected_review,
        "excludedInput": expected_excluded,
    }
    for key, value in expected.items():
        if int(coverage.get(key, -1)) != value:
            raise ValueError(f"coverage mismatch for {key}: {coverage.get(key)} != {value}")
    resolved_count = int(coverage.get("resolvedPresentChecklistCount", 0))
    unresolved_count = int(coverage.get("unresolvedPresentChecklistCount", -1))
    if resolved_count + unresolved_count != expected_present or resolved_count <= 0:
        raise ValueError("confirmed-present checklist coverage is inconsistent")
    if not allow_incomplete and resolved_count != expected_present:
        raise ValueError("not every confirmed-present checklist row resolved")
    if not allow_incomplete and unresolved_count != 0:
        raise ValueError("confirmed-present checklist still has unresolved names")

    present_taxa = set()
    with reconciled_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            if clean(row.get("presenceStatus")).casefold() != "present":
                continue
            reconciliation = row.get("reconciliation") or {}
            if reconciliation.get("status") != "matched" and allow_incomplete:
                continue
            if reconciliation.get("status") != "matched":
                raise ValueError(f"unresolved confirmed-present taxon: {row.get('scientificName')}")
            taxon_id = clean(reconciliation.get("acceptedTaxonId"))
            if not taxon_id:
                raise ValueError("matched checklist row is missing acceptedTaxonId")
            present_taxa.add(taxon_id)

    if len(present_taxa) != int(coverage.get("reconciledPresentTaxonCount", -1)):
        raise ValueError("coverage report unique accepted-taxon count is inconsistent")

    db = sqlite3.connect(database)
    try:
        if str(db.execute("PRAGMA integrity_check").fetchone()[0]).casefold() != "ok":
            raise ValueError("scientific SQLite integrity_check failed")
        tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if not {"meta", "taxon", "species_media_local"}.issubset(tables):
            raise ValueError("scientific SQLite is missing required coverage/media tables")
        missing_taxa = []
        taxa_with_local_media = 0
        for taxon_id in sorted(present_taxa):
            row = db.execute(
                "SELECT source_id,source_record_id FROM taxon WHERE accepted_name_usage_id=? OR source_record_id=? LIMIT 1",
                (taxon_id, taxon_id),
            ).fetchone()
            if row is None:
                missing_taxa.append(taxon_id)
                continue
            has_media = db.execute(
                "SELECT 1 FROM species_media_local WHERE source_id=? AND source_record_id=? LIMIT 1",
                (row[0], row[1]),
            ).fetchone()
            if has_media is not None:
                taxa_with_local_media += 1
        if missing_taxa:
            raise ValueError(f"reconciled present taxa missing from SQLite: {len(missing_taxa)}")
        metrics = {
            "fishChecklistReported": expected_total,
            "fishChecklistPresent": expected_present,
            "fishChecklistReview": expected_review,
            "fishChecklistExcluded": expected_excluded,
            "fishPresentChecklistResolved": resolved_count,
            "fishPresentChecklistUnresolved": unresolved_count,
            "fishPresentAcceptedTaxa": len(present_taxa),
            "fishPresentTaxaWithLocalMedia": taxa_with_local_media,
            "fishCoverageGateVersion": 1 if unresolved_count == 0 and taxa_with_local_media == len(present_taxa) else 0,
        }
        for key, value in metrics.items():
            db.execute("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", (key, json.dumps(value)))
        db.commit()
        return metrics
    finally:
        db.close()


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--database", required=True, type=Path)
    p.add_argument("--reconciled", required=True, type=Path)
    p.add_argument("--coverage", required=True, type=Path)
    p.add_argument("--expect-total", required=True, type=int)
    p.add_argument("--expect-present", required=True, type=int)
    p.add_argument("--expect-review", required=True, type=int)
    p.add_argument("--expect-excluded", required=True, type=int)
    p.add_argument("--allow-incomplete", action="store_true")
    a = p.parse_args()
    metrics = attach(a.database, a.reconciled, a.coverage, a.expect_total, a.expect_present, a.expect_review, a.expect_excluded, a.allow_incomplete)
    if not a.allow_incomplete and metrics["fishPresentTaxaWithLocalMedia"] != metrics["fishPresentAcceptedTaxa"]:
        raise SystemExit(
            f"offline-image coverage incomplete: {metrics['fishPresentTaxaWithLocalMedia']}/{metrics['fishPresentAcceptedTaxa']} confirmed-present accepted taxa"
        )
    print(json.dumps(metrics, ensure_ascii=False))


if __name__ == "__main__":
    main()
