#!/usr/bin/env python3
"""Validate scientific source registry invariants used by FieldIntelligence."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.parse import urlparse

ALLOWED_POLICIES = {
    "bulk_allowed",
    "metadata_reference",
    "dataset_license_required",
    "operator_review_required",
    "curated_reference_only",
    "dataset_terms_required",
}

HIGH_RISK_DOMAINS = {
    "traditional_medicine",
    "medicinal_materials",
    "medicinal_plants",
    "pharmacopoeia",
    "herbal_medicines",
    "safety",
    "evidence",
}


def validate(payload: dict) -> list[str]:
    errors: list[str] = []
    sources = payload.get("sources")
    if not isinstance(sources, list) or not sources:
        return ["sources must be a non-empty list"]

    seen: set[str] = set()
    for index, source in enumerate(sources):
        prefix = f"sources[{index}]"
        if not isinstance(source, dict):
            errors.append(f"{prefix} must be an object")
            continue

        source_id = str(source.get("id") or "").strip()
        if not source_id:
            errors.append(f"{prefix}.id is required")
        elif source_id in seen:
            errors.append(f"duplicate source id: {source_id}")
        else:
            seen.add(source_id)

        name = str(source.get("name") or "").strip()
        authority = str(source.get("authority") or "").strip()
        if not name:
            errors.append(f"{prefix}.name is required")
        if not authority:
            errors.append(f"{prefix}.authority is required")

        url = str(source.get("url") or "").strip()
        parsed = urlparse(url)
        if parsed.scheme != "https" or not parsed.netloc:
            errors.append(f"{prefix}.url must be https")

        policy = str(source.get("ingestPolicy") or "").strip()
        if policy not in ALLOWED_POLICIES:
            errors.append(f"{prefix}.ingestPolicy is invalid: {policy}")

        rights = str(source.get("rightsStatus") or "").strip()
        if not rights:
            errors.append(f"{prefix}.rightsStatus is required")

        domains = source.get("domains")
        if not isinstance(domains, list) or not all(isinstance(v, str) and v.strip() for v in domains):
            errors.append(f"{prefix}.domains must be a non-empty string list")
            domains = []
        elif not domains:
            errors.append(f"{prefix}.domains must not be empty")

        # High-risk medicinal/safety sources are never auto-bulk imported merely because
        # the source is authoritative. Claims need separate curation and evidence records.
        if HIGH_RISK_DOMAINS.intersection(domains) and policy == "bulk_allowed":
            errors.append(f"{prefix}: high-risk medicinal/safety source cannot use bulk_allowed")

        # Rights that still require review cannot be paired with bulk import.
        if rights.startswith("REVIEW_") and policy == "bulk_allowed":
            errors.append(f"{prefix}: bulk_allowed requires explicit reusable rights")

    return errors


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("registry", type=Path)
    args = parser.parse_args()
    payload = json.loads(args.registry.read_text(encoding="utf-8"))
    errors = validate(payload)
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"validated {len(payload['sources'])} scientific sources")


if __name__ == "__main__":
    main()
