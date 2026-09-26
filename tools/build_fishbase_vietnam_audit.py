#!/usr/bin/env python3
"""Build an audit-only Vietnam freshwater-fish checklist from FishBase parquet snapshots.

FishBase snapshot data is CC BY-NC 4.0. This adapter is therefore deliberately an audit and
cross-check stage. Its output must not be treated as unrestricted production content or reference
media. The production taxonomy/media pack is reconciled to separately licensed COL/GBIF records.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Iterable

SOURCE_BUCKET = "us-west-2.opendata.source.coop"
SOURCE_PREFIX = "cboettig/fishbase/fb/"
DATA_LICENSE = "CC-BY-NC-4.0"
VIETNAM_C_CODE = "704"


def clean(value: object) -> str:
    return str(value or "").strip()


def folded(value: object) -> str:
    return clean(value).casefold()


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def choose_column(columns: Iterable[str], *aliases: str) -> str | None:
    by_folded = {str(name).casefold(): str(name) for name in columns}
    for alias in aliases:
        hit = by_folded.get(alias.casefold())
        if hit:
            return hit
    return None


def freshwater_value(value: object) -> bool:
    if isinstance(value, (int, float)):
        return value != 0
    return folded(value) in {"1", "y", "yes", "true", "fresh", "freshwater"}


def classify_presence(current_presence: object, status: object) -> str:
    current = folded(current_presence)
    state = folded(status)
    if current in {"present", "currently present"}:
        return "present"
    if current in {"absent", "demonstrated absent", "not present"}:
        return "excluded"
    if current in {"possible", "possibly present", "probably present", "questionable"}:
        return "review"
    if state in {"native", "endemic", "introduced", "reintroduced"}:
        return "present"
    if state in {"stray", "questionable", "possible"}:
        return "review"
    if state in {"extirpated", "not established", "misidentification", "misidentified", "error", "absent"}:
        return "excluded"
    return "review"


def discover_latest_release(timeout: int = 30) -> str:
    endpoint = (
        f"https://s3.us-west-2.amazonaws.com/{SOURCE_BUCKET}"
        f"?list-type=2&prefix={SOURCE_PREFIX}&delimiter=/"
    )
    with urllib.request.urlopen(endpoint, timeout=timeout) as response:
        root = ET.fromstring(response.read())
    versions: list[tuple[int, int, str]] = []
    for node in root.iter():
        if not node.tag.endswith("Prefix") or not node.text:
            continue
        match = re.search(r"/v(\d{2})\.(\d{2})/$", node.text)
        if match:
            versions.append((int(match.group(1)), int(match.group(2)), f"{match.group(1)}.{match.group(2)}"))
    if not versions:
        raise RuntimeError("No FishBase releases found in Source Cooperative listing")
    return max(versions)[2]


def source_url(version: str, table: str) -> str:
    safe_version = version.removeprefix("v")
    if not re.fullmatch(r"\d{2}\.\d{2}", safe_version):
        raise ValueError(f"Invalid FishBase snapshot version: {version}")
    if not re.fullmatch(r"[a-z0-9_]+", table):
        raise ValueError(f"Invalid FishBase table name: {table}")
    return f"https://data.source.coop/cboettig/fishbase/fb/v{safe_version}/parquet/{table}.parquet"


def table_columns(db, url: str) -> list[str]:
    rows = db.execute(f"DESCRIBE SELECT * FROM read_parquet({sql_literal(url)})").fetchall()
    return [str(row[0]) for row in rows]


def extract(version: str, country_code: str = VIETNAM_C_CODE) -> list[dict]:
    try:
        import duckdb  # type: ignore
    except ImportError as exc:
        raise RuntimeError("duckdb is required; install the pinned workflow dependency") from exc

    country_url = source_url(version, "country")
    species_url = source_url(version, "species")
    db = duckdb.connect(database=":memory:")
    try:
        country_cols = table_columns(db, country_url)
        species_cols = table_columns(db, species_url)
        c_spec = choose_column(country_cols, "SpecCode", "Speccode")
        c_country = choose_column(country_cols, "C_Code", "CCode", "CountryCode")
        c_fresh = choose_column(country_cols, "Freshwater", "Fresh")
        c_presence = choose_column(country_cols, "CurrentPresence", "Presence")
        c_status = choose_column(country_cols, "Status")
        s_spec = choose_column(species_cols, "SpecCode", "Speccode")
        s_genus = choose_column(species_cols, "Genus")
        s_species = choose_column(species_cols, "Species")
        missing = [
            name for name, value in {
                "country.SpecCode": c_spec, "country.C_Code": c_country,
                "country.Freshwater": c_fresh, "species.SpecCode": s_spec,
                "species.Genus": s_genus, "species.Species": s_species,
            }.items() if value is None
        ]
        if missing:
            raise RuntimeError("FishBase snapshot schema missing required fields: " + ", ".join(missing))

        select_presence = f"c.{c_presence}" if c_presence else "NULL"
        select_status = f"c.{c_status}" if c_status else "NULL"
        query = f"""
            SELECT c.{c_spec} AS SpecCode,
                   c.{c_country} AS C_Code,
                   c.{c_fresh} AS Freshwater,
                   {select_presence} AS CurrentPresence,
                   {select_status} AS Status,
                   s.{s_genus} AS Genus,
                   s.{s_species} AS Species
            FROM read_parquet({sql_literal(country_url)}) c
            JOIN read_parquet({sql_literal(species_url)}) s
              ON CAST(c.{c_spec} AS VARCHAR) = CAST(s.{s_spec} AS VARCHAR)
            WHERE CAST(c.{c_country} AS VARCHAR) = ?
        """
        rows = db.execute(query, [str(country_code)]).fetchall()
    finally:
        db.close()

    output: list[dict] = []
    seen: set[str] = set()
    for spec_code, _, freshwater, current_presence, status, genus, epithet in rows:
        if not freshwater_value(freshwater):
            continue
        scientific = f"{clean(genus)} {clean(epithet)}".strip()
        if not scientific or scientific in seen:
            continue
        seen.add(scientific)
        source_status = clean(current_presence) or clean(status)
        output.append({
            "scientificName": scientific,
            "presenceStatus": classify_presence(current_presence, status),
            "sourceStatus": source_status,
            "fishbaseSpecCode": clean(spec_code),
            "sourceRecordUrl": f"https://fishbase.se/summary/{clean(spec_code)}",
            "sourceSnapshot": f"fb/v{version}",
            "sourceLicense": DATA_LICENSE,
        })
    return sorted(output, key=lambda row: row["scientificName"].casefold())


def report_for(rows: list[dict], version: str) -> dict:
    counts = {"present": 0, "review": 0, "excluded": 0}
    for row in rows:
        counts[row["presenceStatus"]] = counts.get(row["presenceStatus"], 0) + 1
    return {
        "source": "FishBase via rfishbase Source Cooperative snapshot",
        "sourceSnapshot": f"fb/v{version}",
        "sourceLicense": DATA_LICENSE,
        "countryCode": VIETNAM_C_CODE,
        "habitat": "freshwater",
        "use": "audit-crosscheck-only",
        "total": len(rows),
        **counts,
    }


def write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = ["scientificName", "presenceStatus", "sourceStatus", "fishbaseSpecCode", "sourceRecordUrl", "sourceSnapshot", "sourceLicense"]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", default="latest")
    parser.add_argument("--country-code", default=VIETNAM_C_CODE)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--expect-total", type=int)
    parser.add_argument("--expect-present", type=int)
    args = parser.parse_args()
    version = discover_latest_release() if args.version == "latest" else args.version.removeprefix("v")
    rows = extract(version, args.country_code)
    report = report_for(rows, version)
    if args.expect_total is not None and report["total"] != args.expect_total:
        raise SystemExit(f"FishBase total mismatch: {report['total']} != {args.expect_total}; snapshot={version}")
    if args.expect_present is not None and report["present"] != args.expect_present:
        raise SystemExit(f"FishBase present mismatch: {report['present']} != {args.expect_present}; snapshot={version}")
    write_csv(args.output, rows)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
