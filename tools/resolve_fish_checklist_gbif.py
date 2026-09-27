#!/usr/bin/env python3
"""Resolve fish checklist names to accepted GBIF Backbone taxa with auditable evidence.

This resolver is intentionally independent from occurrence data: occurrence rows must not decide
whether a checklist synonym exists. Only conservative exact species matches are auto-resolved;
fuzzy, higher-rank, non-Animalia and low-confidence matches remain review blockers.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

API_URL = "https://api.gbif.org/v2/species/match"
GBIF_BACKBONE_KEY = "d7dddbf4-2cf0-4f39-9b2a-bb099caae36c"
USER_AGENT = "FieldIntelligence/1.0 scientific-library-builder"
BLOCKING_PROCESSING_FLAGS = {
    "NO_MATCH",
    "MULTIPLE_MATCHES_SAME_CONFIDENCE",
    "LOW_CONFIDENCE",
    "NO_LOWEST_DENOMINATOR",
}


def clean(value: object) -> str:
    return str(value or "").strip()


def read_ndjson(path: Path):
    opener = gzip.open if path.name.casefold().endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                yield json.loads(line)


def _http_match(scientific_name: str, checklist_key: str, retries: int = 4) -> dict:
    query = urllib.parse.urlencode({
        "scientificName": scientific_name,
        "kingdom": "Animalia",
        "taxonRank": "SPECIES",
        "strict": "true",
        "checklistKey": checklist_key,
    })
    request = urllib.request.Request(
        f"{API_URL}?{query}",
        headers={"Accept": "application/json", "User-Agent": USER_AGENT},
    )
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                if response.status != 200:
                    raise RuntimeError(f"GBIF species match returned HTTP {response.status}")
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            last_error = exc
            if exc.code != 429 and exc.code < 500:
                raise
        except (urllib.error.URLError, TimeoutError) as exc:
            last_error = exc
        if attempt + 1 < retries:
            time.sleep(min(8, 2 ** attempt))
    raise RuntimeError(f"GBIF species match failed after {retries} attempts: {last_error}")


def _classification_name(response: dict, rank: str) -> str:
    wanted = rank.upper()
    for item in response.get("classification") or []:
        if clean(item.get("rank")).upper() == wanted:
            return clean(item.get("name"))
    return ""


def classify_match(scientific_name: str, response: dict, checklist_key: str) -> dict:
    usage = response.get("usage") or {}
    accepted = response.get("acceptedUsage") or usage
    diagnostics = response.get("diagnostics") or {}
    match_type = clean(diagnostics.get("matchType")).upper()
    issues = [clean(v) for v in diagnostics.get("issues") or [] if clean(v)]
    processing_flags = [clean(v).upper() for v in diagnostics.get("processingFlags") or [] if clean(v)]
    blocking_flags = sorted(set(processing_flags) & BLOCKING_PROCESSING_FLAGS)
    try:
        confidence = int(diagnostics.get("confidence", 0) or 0)
    except (TypeError, ValueError):
        confidence = 0
    accepted_key = clean(accepted.get("key"))
    accepted_name = clean(accepted.get("name"))
    accepted_rank = clean(accepted.get("rank") or usage.get("rank")).upper()
    kingdom = _classification_name(response, "KINGDOM")

    if not usage or not accepted_key:
        status = "unmatched"
        reason = "no_usage_or_accepted_taxon"
    elif blocking_flags:
        status = "review"
        reason = "processing_flags_" + "+".join(blocking_flags)
    elif accepted_rank != "SPECIES":
        status = "review"
        reason = f"accepted_rank_{accepted_rank or 'missing'}"
    elif kingdom.casefold() != "animalia":
        status = "review"
        reason = f"kingdom_{kingdom or 'missing'}"
    elif match_type != "EXACT":
        status = "review"
        reason = f"match_type_{match_type or 'missing'}"
    elif confidence < 95:
        status = "review"
        reason = f"confidence_{confidence}"
    else:
        status = "resolved"
        reason = "exact_species_match"

    evidence = {
        "resolver": "GBIF species match API v2",
        "endpoint": API_URL,
        "checklistKey": checklist_key,
        "queryScientificName": scientific_name,
        "status": status,
        "reason": reason,
        "acceptedTaxonId": accepted_key,
        "acceptedScientificName": accepted_name,
        "acceptedCanonicalName": clean(accepted.get("canonicalName")),
        "acceptedAuthorship": clean(accepted.get("authorship")),
        "acceptedGenericName": clean(accepted.get("genericName")) or _classification_name(response, "GENUS"),
        "acceptedSpecificEpithet": clean(accepted.get("specificEpithet")),
        "usageTaxonId": clean(usage.get("key")),
        "usageScientificName": clean(usage.get("name")),
        "usageStatus": clean(usage.get("status")),
        "acceptedRank": accepted_rank,
        "kingdom": kingdom,
        "phylum": _classification_name(response, "PHYLUM"),
        "class": _classification_name(response, "CLASS"),
        "order": _classification_name(response, "ORDER"),
        "family": _classification_name(response, "FAMILY"),
        "genus": _classification_name(response, "GENUS"),
        "matchType": match_type,
        "confidence": confidence,
        "synonym": bool(response.get("synonym", False)),
        "issues": issues,
        "processingFlags": processing_flags,
        "classification": response.get("classification") or [],
    }
    digest_payload = json.dumps(evidence, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    evidence["evidenceSha256"] = hashlib.sha256(digest_payload).hexdigest()
    return evidence


def build(
    checklist_path: Path,
    output_path: Path,
    report_path: Path,
    checklist_key: str = GBIF_BACKBONE_KEY,
    matcher: Callable[[str, str], dict] | None = None,
    delay_seconds: float = 0.05,
) -> dict:
    matcher = matcher or _http_match
    retrieved_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    report = {
        "resolver": "GBIF species match API v2",
        "endpoint": API_URL,
        "checklistKey": checklist_key,
        "retrievedAtUtc": retrieved_at,
        "total": 0,
        "presentInput": 0,
        "reviewInput": 0,
        "excludedInput": 0,
        "resolved": 0,
        "needsReview": 0,
        "unmatched": 0,
        "presentResolved": 0,
        "presentNeedsReview": 0,
        "presentUnmatched": 0,
        "uniqueAcceptedPresentTaxa": 0,
        "presentBlockers": [],
    }
    accepted_present: set[str] = set()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as out:
        for item in read_ndjson(checklist_path):
            scientific_name = clean(item.get("scientificName"))
            if not scientific_name:
                raise ValueError("checklist row missing scientificName")
            presence = clean(item.get("presenceStatus")).casefold()
            if presence == "present":
                report["presentInput"] += 1
            elif presence == "excluded":
                report["excludedInput"] += 1
            else:
                presence = "review"
                report["reviewInput"] += 1
            report["total"] += 1

            response = matcher(scientific_name, checklist_key)
            evidence = classify_match(scientific_name, response, checklist_key)
            result = dict(item)
            result["taxonomyResolution"] = evidence
            result["taxonomyResolutionRetrievedAtUtc"] = retrieved_at
            out.write(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n")

            if evidence["status"] == "resolved":
                report["resolved"] += 1
                if presence == "present":
                    report["presentResolved"] += 1
                    accepted_present.add(evidence["acceptedTaxonId"])
            elif evidence["status"] == "review":
                report["needsReview"] += 1
                if presence == "present":
                    report["presentNeedsReview"] += 1
                    report["presentBlockers"].append({
                        "scientificName": scientific_name,
                        "status": "review",
                        "reason": evidence["reason"],
                    })
            else:
                report["unmatched"] += 1
                if presence == "present":
                    report["presentUnmatched"] += 1
                    report["presentBlockers"].append({
                        "scientificName": scientific_name,
                        "status": "unmatched",
                        "reason": evidence["reason"],
                    })
            if delay_seconds > 0:
                time.sleep(delay_seconds)

    report["uniqueAcceptedPresentTaxa"] = len(accepted_present)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checklist", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--checklist-key", default=GBIF_BACKBONE_KEY)
    parser.add_argument("--delay-seconds", type=float, default=0.05)
    args = parser.parse_args()
    report = build(args.checklist, args.output, args.report, args.checklist_key, delay_seconds=args.delay_seconds)
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
