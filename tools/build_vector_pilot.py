#!/usr/bin/env python3
"""Build a bounded, review-only OSM road vector pack from a small OSM XML extract."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

MAX_WAYS = 3000
MAX_VERTICES = 30000
MAX_ENTRY = 16 * 1024 * 1024
MAX_PACKAGE = 64 * 1024 * 1024
ROAD_TYPES = {
    "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
    "residential", "service", "living_street", "track", "path", "footway",
    "cycleway", "pedestrian", "motorway_link", "trunk_link", "primary_link",
    "secondary_link", "tertiary_link",
}


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build(source: Path, destination: Path, region_id: str, bounds: tuple[float, float, float, float], version: int) -> dict:
    min_lat, min_lon, max_lat, max_lon = bounds
    if not re.fullmatch(r"[a-z0-9_-]+", region_id):
        raise ValueError("region_id must use lowercase letters, digits, '-' or '_'")
    if not (-90 <= min_lat < max_lat <= 90 and -180 <= min_lon < max_lon <= 180):
        raise ValueError("Invalid bounds")
    if version < 1:
        raise ValueError("Version must be positive")

    nodes: dict[str, tuple[float, float]] = {}
    lines: list[str] = []
    places: list[str] = []
    vertices = 0
    saw_way = False
    events = ET.iterparse(source, events=("start", "end"))
    _, document = next(events)
    for event, element in events:
        if event != "end":
            continue
        if element.tag == "node":
            if saw_way:
                raise ValueError("OSM XML must list nodes before ways")
            lat, lon = float(element.attrib["lat"]), float(element.attrib["lon"])
            if min_lat <= lat <= max_lat and min_lon <= lon <= max_lon:
                nodes[element.attrib["id"]] = (lat, lon)
                tags = {tag.attrib.get("k"): tag.attrib.get("v") for tag in element.findall("tag")}
                name = tags.get("name", "").replace("\t", " ").replace("\n", " ").strip()
                if name and tags.get("place") in {"city", "town", "village"}:
                    places.append(f"{lat:.6f}\t{lon:.6f}\t{name}")
        elif element.tag == "way":
            saw_way = True
            tags = {tag.attrib.get("k"): tag.attrib.get("v") for tag in element.findall("tag")}
            if tags.get("highway") in ROAD_TYPES:
                segment: list[tuple[float, float]] = []
                refs = [node.attrib["ref"] for node in element.findall("nd")]
                for ref in refs + [""]:
                    coordinate = nodes.get(ref)
                    if coordinate is None:
                        if len(segment) >= 2:
                            lines.append(";".join(f"{lat:.6f},{lon:.6f}" for lat, lon in segment))
                            vertices += len(segment)
                            if len(lines) > MAX_WAYS or vertices > MAX_VERTICES:
                                raise ValueError("Pilot exceeds geometry limit; split into smaller regions")
                        segment = []
                    elif not segment or segment[-1] != coordinate:
                        segment.append(coordinate)
        if element.tag in {"node", "way", "relation"}:
            document.clear()

    if not lines:
        raise ValueError("No complete road segments within bounds")
    destination.mkdir(parents=True, exist_ok=True)
    entries = {
        f"{region_id}.region": (
            f"id={region_id}\nname=OSM pilot {region_id}\nminLat={min_lat}\nminLon={min_lon}"
            f"\nmaxLat={max_lat}\nmaxLon={max_lon}\n"
        ).encode(),
        f"{region_id}.lines": ("\n".join(lines) + "\n").encode(),
    }
    if places:
        entries[f"{region_id}.points"] = ("\n".join(places) + "\n").encode()
    if any(len(data) > MAX_ENTRY for data in entries.values()):
        raise ValueError("Entry exceeds app extraction limit")
    package = destination / "offline-map.pack"
    with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            archive.writestr(name, data)
    if package.stat().st_size > MAX_PACKAGE:
        package.unlink()
        raise ValueError("Package exceeds app download limit")
    manifest = {
        "version": version, "schemaVersion": 1, "minAppVersionCode": 1,
        "packageName": "vn.fieldintel.app", "sha256": file_sha256(package),
        "sizeBytes": package.stat().st_size,
    }
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (destination / "provenance.json").write_text(json.dumps({
        "source": str(source), "sourceSha256": file_sha256(source),
        "bounds": bounds, "roadSegments": len(lines), "vertices": vertices,
        "license": "ODbL 1.0", "attribution": "© OpenStreetMap contributors",
        "status": "PILOT_ONLY_NOT_FOR_NAVIGATION",
    }, indent=2) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="small, locally extracted OSM XML with nodes before ways")
    parser.add_argument("destination", type=Path)
    parser.add_argument("--id", required=True)
    parser.add_argument("--bbox", required=True, help="minLat,minLon,maxLat,maxLon")
    parser.add_argument("--version", type=int, required=True)
    args = parser.parse_args()
    build(args.source, args.destination, args.id, tuple(map(float, args.bbox.split(","))), args.version)


if __name__ == "__main__":
    main()
