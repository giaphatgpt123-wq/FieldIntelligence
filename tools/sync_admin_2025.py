#!/usr/bin/env python3
"""Validate and materialize administrative assets for the Đường ở đâu Android app.

The tool deliberately accepts a canonical CSV export rather than scraping a website.
This keeps legal/source verification separate from parsing and prevents a weak source
from silently becoming authoritative app data.

Current CSV columns:
    province_code,province_name,commune_code,commune_name
Transition CSV columns:
    old_code,new_code,effective_from,legal_source,verification
"""

from __future__ import annotations

import argparse
import csv
import io
import pathlib
import shutil
import sys
import urllib.request
from collections import defaultdict

EXPECTED_PROVINCES = 34
EXPECTED_COMMUNES = 3321
ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "duongodau-app" / "src" / "main" / "assets"


def read_source(location: str) -> str:
    if location.startswith(("https://", "http://")):
        with urllib.request.urlopen(location, timeout=30) as response:
            return response.read().decode("utf-8-sig")
    return pathlib.Path(location).read_text(encoding="utf-8-sig")


def parse_current(text: str):
    reader = csv.DictReader(io.StringIO(text))
    required = {"province_code", "province_name", "commune_code", "commune_name"}
    if not reader.fieldnames or not required.issubset(reader.fieldnames):
        raise ValueError(f"Current CSV must contain columns: {sorted(required)}")

    rows = []
    province_names = {}
    commune_codes = set()
    communes_by_province = defaultdict(set)
    for line_no, row in enumerate(reader, start=2):
        clean = {k: (v or "").strip() for k, v in row.items()}
        if any(not clean[k] for k in required):
            raise ValueError(f"Blank required field at current CSV line {line_no}")
        p_code = clean["province_code"].zfill(2)
        c_code = clean["commune_code"].zfill(5)
        p_name = clean["province_name"]
        c_name = clean["commune_name"]

        old_p_name = province_names.setdefault(p_code, p_name)
        if old_p_name != p_name:
            raise ValueError(f"Province code {p_code} has conflicting names: {old_p_name!r}, {p_name!r}")
        if c_code in commune_codes:
            raise ValueError(f"Duplicate commune code: {c_code}")
        commune_codes.add(c_code)
        communes_by_province[p_code].add(c_code)
        rows.append((p_code, p_name, c_code, c_name))

    return rows, province_names, commune_codes, communes_by_province


def write_current(rows):
    ASSETS.mkdir(parents=True, exist_ok=True)
    target = ASSETS / "admin_current.csv"
    with target.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["province_code", "province_name", "commune_code", "commune_name"])
        writer.writerows(sorted(rows, key=lambda r: (r[0], r[2])))
    return target


def validate_transitions(text: str) -> str:
    reader = csv.DictReader(io.StringIO(text))
    required = {"old_code", "new_code", "effective_from", "legal_source", "verification"}
    if not reader.fieldnames or not required.issubset(reader.fieldnames):
        raise ValueError(f"Transition CSV must contain columns: {sorted(required)}")
    output = io.StringIO()
    writer = csv.DictWriter(output, fieldnames=["old_code", "new_code", "effective_from", "legal_source", "verification"])
    writer.writeheader()
    for line_no, row in enumerate(reader, start=2):
        clean = {k: (row.get(k) or "").strip() for k in writer.fieldnames}
        if not clean["old_code"] or not clean["new_code"] or not clean["legal_source"]:
            raise ValueError(f"Invalid transition at line {line_no}")
        if clean["verification"] not in {"CONFIRMED", "HIGH_CONFIDENCE", "PROBABLE", "UNVERIFIED", "CONFLICTING"}:
            raise ValueError(f"Invalid verification state at line {line_no}: {clean['verification']}")
        writer.writerow(clean)
    return output.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--current-csv", required=True, help="Canonical current administrative CSV path or URL")
    parser.add_argument("--transitions-csv", help="Optional historical-to-current canonical CSV path or URL")
    parser.add_argument("--allow-partial", action="store_true", help="Allow incomplete data for development only")
    args = parser.parse_args()

    current_text = read_source(args.current_csv)
    rows, province_names, commune_codes, _ = parse_current(current_text)

    if not args.allow_partial:
        if len(province_names) != EXPECTED_PROVINCES:
            raise ValueError(f"Expected {EXPECTED_PROVINCES} provinces, got {len(province_names)}")
        if len(commune_codes) != EXPECTED_COMMUNES:
            raise ValueError(f"Expected {EXPECTED_COMMUNES} communes, got {len(commune_codes)}")

    current_target = write_current(rows)
    print(f"Wrote {current_target}: {len(province_names)} provinces, {len(commune_codes)} communes")

    if args.transitions_csv:
        transition_text = validate_transitions(read_source(args.transitions_csv))
        transition_target = ASSETS / "admin_transitions.csv"
        transition_target.write_text(transition_text, encoding="utf-8")
        print(f"Wrote {transition_target}")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
