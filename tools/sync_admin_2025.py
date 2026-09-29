#!/usr/bin/env python3
"""Validate and materialize administrative assets for the Đường ở đâu Android app.

Two input modes are supported:
1) --current-csv: a canonical, reviewed CSV.
2) --open-admin-data: download the CC-BY-4.0 Open Admin Data Vietnam dataset and
   transform it to the app's canonical CSV. This is classified as a technical S4
   source; Decision 19/2025/QĐ-TTg remains the legal basis for current codes.

Current CSV columns:
    province_code,province_name,commune_code,commune_name
Transition CSV columns:
    old_code,new_code,effective_from,legal_source,verification
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import pathlib
import sys
import urllib.request
from collections import defaultdict

EXPECTED_PROVINCES = 34
EXPECTED_COMMUNES = 3321
OFFICIAL_PROVINCE_CODES = {
    "01", "04", "08", "11", "12", "14", "15", "19", "20", "22", "24", "25",
    "31", "33", "37", "38", "40", "42", "44", "46", "48", "51", "52", "56",
    "66", "68", "75", "79", "80", "82", "86", "91", "92", "96",
}
ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "duongodau-app" / "src" / "main" / "assets"
OPEN_ADMIN_BASE = "https://raw.githubusercontent.com/open-admin-data/vietnam-administrative-divisions/main/data"
OPEN_ADMIN_PROVINCES = f"{OPEN_ADMIN_BASE}/all-province.json"
OPEN_ADMIN_WARDS = f"{OPEN_ADMIN_BASE}/all-ward.json"


def read_source(location: str) -> str:
    if location.startswith(("https://", "http://")):
        request = urllib.request.Request(location, headers={"User-Agent": "DuongODau-AdminSync/1.0"})
        with urllib.request.urlopen(request, timeout=60) as response:
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


def canonical_csv_from_open_admin_data() -> str:
    provinces = json.loads(read_source(OPEN_ADMIN_PROVINCES))
    wards = json.loads(read_source(OPEN_ADMIN_WARDS))

    province_names = {}
    for province in provinces:
        code = str(province.get("code", {}).get("id") or province.get("id") or "").zfill(2)
        name = str(province.get("name", {}).get("local") or "").strip()
        if code and name:
            province_names[code] = name

    if set(province_names) != OFFICIAL_PROVINCE_CODES:
        missing = sorted(OFFICIAL_PROVINCE_CODES - set(province_names))
        extra = sorted(set(province_names) - OFFICIAL_PROVINCE_CODES)
        raise ValueError(f"Open Admin province codes differ from official code set. missing={missing}, extra={extra}")

    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(["province_code", "province_name", "commune_code", "commune_name"])

    seen = set()
    for ward in wards:
        c_code = str(ward.get("code", {}).get("id") or ward.get("id") or "").zfill(5)
        c_name = str(ward.get("name", {}).get("local") or "").strip()
        parent = ward.get("parent") or {}
        p_code = str(parent.get("id") or "").zfill(2)
        p_name = province_names.get(p_code)
        if not c_code or not c_name or not p_name:
            raise ValueError(f"Invalid ward record: id={ward.get('id')!r}, parent={parent!r}")
        if c_code in seen:
            raise ValueError(f"Duplicate commune code in Open Admin Data: {c_code}")
        seen.add(c_code)
        writer.writerow([p_code, p_name, c_code, c_name])

    return output.getvalue()


def write_current(rows):
    ASSETS.mkdir(parents=True, exist_ok=True)
    target = ASSETS / "admin_current.csv"
    with target.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["province_code", "province_name", "commune_code", "commune_name"])
        writer.writerows(sorted(rows, key=lambda r: (r[0], r[2])))
    return target


def write_open_admin_notice():
    notice = ASSETS / "NOTICE_ADMIN_DATA.txt"
    notice.write_text(
        "Administrative selector technical dataset (S4): Open Admin Data - Vietnam Administrative Divisions\n"
        "Repository: https://github.com/open-admin-data/vietnam-administrative-divisions\n"
        "License: CC-BY-4.0\n"
        "Legal basis for current administrative codes: Decision 19/2025/QD-TTg, effective 2025-07-01.\n"
        "The technical dataset is not treated as authoritative boundary geometry.\n",
        encoding="utf-8",
    )


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


def validate_counts(province_names, commune_codes, allow_partial: bool):
    if set(province_names) - OFFICIAL_PROVINCE_CODES:
        raise ValueError(f"Unknown province codes: {sorted(set(province_names) - OFFICIAL_PROVINCE_CODES)}")
    if not allow_partial:
        if len(province_names) != EXPECTED_PROVINCES:
            raise ValueError(f"Expected {EXPECTED_PROVINCES} provinces, got {len(province_names)}")
        if len(commune_codes) != EXPECTED_COMMUNES:
            raise ValueError(f"Expected {EXPECTED_COMMUNES} communes, got {len(commune_codes)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    source_group = parser.add_mutually_exclusive_group(required=True)
    source_group.add_argument("--current-csv", help="Canonical current administrative CSV path or URL")
    source_group.add_argument("--open-admin-data", action="store_true", help="Use CC-BY-4.0 Open Admin Data Vietnam source")
    parser.add_argument("--transitions-csv", help="Optional historical-to-current canonical CSV path or URL")
    parser.add_argument("--allow-partial", action="store_true", help="Allow incomplete data for development only")
    args = parser.parse_args()

    current_text = canonical_csv_from_open_admin_data() if args.open_admin_data else read_source(args.current_csv)
    rows, province_names, commune_codes, _ = parse_current(current_text)
    validate_counts(province_names, commune_codes, args.allow_partial)

    current_target = write_current(rows)
    if args.open_admin_data:
        write_open_admin_notice()
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
