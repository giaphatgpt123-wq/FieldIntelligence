#!/usr/bin/env python3
"""Fill missing fish reference images using an exact Wikidata taxon -> Commons mapping.

This is a conservative media fallback only. Taxonomy remains GBIF-authoritative. A Wikimedia image
is accepted only when a Wikidata item is explicitly a taxon (P31=Q16521), has P225 exactly equal to
the GBIF canonical scientific name, has a P18 image claim, and Commons reports a machine-readable
CC0 or CC BY 4.0 licence.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import html
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Callable

WIKIDATA_API = "https://www.wikidata.org/w/api.php"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "FieldIntelligence/1.0 scientific-library-builder"
ALLOWED_LICENSES = {"CC0-1.0", "CC-BY-4.0"}
TAXON_ENTITY_ID = "Q16521"


def clean(value: object) -> str:
    return str(value or "").strip()


def plain(value: object) -> str:
    text = html.unescape(clean(value))
    text = re.sub(r"<br\s*/?>", "; ", text, flags=re.I)
    text = re.sub(r"<[^>]+>", "", text)
    return re.sub(r"\s+", " ", text).strip()


def canonical_license(short_name: object, url: object) -> str:
    short = clean(short_name).casefold().replace("_", "-").replace(" ", "-")
    raw_url = clean(url).casefold().rstrip("/")
    if short in {"cc0", "cc0-1.0", "cc-zero-1.0"} or "/publicdomain/zero/1.0" in raw_url:
        return "CC0-1.0"
    if short in {"cc-by-4.0", "ccby-4.0", "cc-by-4.0-international"} or "/licenses/by/4.0" in raw_url:
        return "CC-BY-4.0"
    return ""


def _request_json(endpoint: str, params: dict, retries: int = 4, timeout: int = 30) -> dict:
    url = endpoint + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT})
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                if response.status != 200:
                    raise RuntimeError(f"Wikimedia API returned HTTP {response.status}")
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            last_error = exc
            if exc.code != 429 and exc.code < 500:
                raise
        except (urllib.error.URLError, TimeoutError) as exc:
            last_error = exc
        if attempt + 1 < retries:
            time.sleep(min(8, 2 ** attempt))
    raise RuntimeError(f"Wikimedia API failed after {retries} attempts: {last_error}")


def _search_wikidata(scientific_name: str) -> dict:
    return _request_json(WIKIDATA_API, {
        "action": "wbsearchentities",
        "format": "json",
        "formatversion": "2",
        "search": scientific_name,
        "language": "en",
        "uselang": "en",
        "type": "item",
        "limit": "10",
    })


def _get_wikidata_entities(ids: list[str]) -> dict:
    if not ids:
        return {"entities": {}}
    return _request_json(WIKIDATA_API, {
        "action": "wbgetentities",
        "format": "json",
        "formatversion": "2",
        "ids": "|".join(ids),
        "props": "claims",
    })


def _get_commons_file(filename: str) -> dict:
    title = filename if filename.casefold().startswith("file:") else f"File:{filename}"
    return _request_json(COMMONS_API, {
        "action": "query",
        "format": "json",
        "formatversion": "2",
        "prop": "imageinfo",
        "titles": title,
        "iiprop": "url|extmetadata",
        "iiextmetadatalanguage": "en",
        "iimetadataversion": "latest",
        "iiextmetadatafilter": "LicenseShortName|LicenseUrl|Artist|Credit|Attribution|ImageDescription|ObjectName|Copyrighted",
    })


def _search_commons_depicts(entity_id: str) -> dict:
    return _request_json(COMMONS_API, {
        "action": "query",
        "format": "json",
        "formatversion": "2",
        "list": "search",
        "srsearch": f"haswbstatement:P180={entity_id}",
        "srnamespace": "6",
        "srlimit": "10",
    }, retries=2, timeout=8)


def _claim_values(entity: dict, property_id: str) -> list[object]:
    values: list[object] = []
    for claim in (entity.get("claims") or {}).get(property_id) or []:
        mainsnak = claim.get("mainsnak") or {}
        datavalue = mainsnak.get("datavalue") or {}
        if mainsnak.get("snaktype") != "value" or "value" not in datavalue:
            continue
        values.append(datavalue["value"])
    return values


def _is_taxon_entity(entity: dict) -> bool:
    for value in _claim_values(entity, "P31"):
        if isinstance(value, dict) and clean(value.get("id")) == TAXON_ENTITY_ID:
            return True
    return False


def _licensed_commons_file(filename: str, entity_id: str, get_commons: Callable[[str], dict], mapping: str) -> dict | None:
    commons = get_commons(filename)
    pages = ((commons.get("query") or {}).get("pages") or [])
    for page in pages:
        infos = page.get("imageinfo") or []
        if not infos:
            continue
        info = infos[0]
        url = clean(info.get("url"))
        if not url.startswith("https://"):
            continue
        meta = info.get("extmetadata") or {}

        def mv(key: str) -> str:
            value = meta.get(key) or {}
            return clean(value.get("value") if isinstance(value, dict) else value)

        license_id = canonical_license(mv("LicenseShortName"), mv("LicenseUrl"))
        if license_id not in ALLOWED_LICENSES:
            continue
        return {
            "mediaType": "StillImage",
            "identifier": url,
            "references": clean(info.get("descriptionurl")),
            "title": plain(mv("ObjectName")) or clean(page.get("title")) or filename,
            "description": plain(mv("ImageDescription")),
            "creator": plain(mv("Attribution")) or plain(mv("Artist")),
            "rightsHolder": plain(mv("Credit")),
            "license": license_id,
            "licenseOriginal": mv("LicenseShortName") or mv("LicenseUrl"),
            "sourceProvider": "Wikimedia Commons",
            "wikidataItem": entity_id,
            "wikidataInstanceOf": TAXON_ENTITY_ID,
            "wikidataScientificNameProperty": "P225",
            "wikimediaImageProperty": mapping,
            "mappingEvidence": f"P31-taxon+exact-P225-to-{mapping}",
        }


def find_exact_taxon_image(
    scientific_name: str,
    search_wikidata: Callable[[str], dict] = _search_wikidata,
    get_entities: Callable[[list[str]], dict] = _get_wikidata_entities,
    get_commons: Callable[[str], dict] = _get_commons_file,
    search_depicts: Callable[[str], dict] = _search_commons_depicts,
) -> tuple[dict | None, str]:
    search = search_wikidata(scientific_name)
    ids = [clean(item.get("id")) for item in search.get("search") or [] if clean(item.get("id"))]
    if not ids:
        return None, "wikidata-no-search-result"
    payload = get_entities(ids)
    entities = payload.get("entities") or {}
    exact_candidates: list[tuple[str, dict]] = []
    wanted = scientific_name.casefold()
    for entity_id in ids:
        entity = entities.get(entity_id) or {}
        names = [clean(v) for v in _claim_values(entity, "P225") if clean(v)]
        if _is_taxon_entity(entity) and any(name.casefold() == wanted for name in names):
            exact_candidates.append((entity_id, entity))
    if not exact_candidates:
        return None, "wikidata-no-exact-taxon-p225"

    for entity_id, entity in exact_candidates:
        images = [clean(v) for v in _claim_values(entity, "P18") if clean(v)]
        for filename in images:
            item = _licensed_commons_file(filename, entity_id, get_commons, "P18")
            if item:
                return item, "matched"
        depicts = search_depicts(entity_id)
        for result in ((depicts.get("query") or {}).get("search") or []):
            title = clean(result.get("title"))
            if not title.startswith("File:"):
                continue
            item = _licensed_commons_file(title, entity_id, get_commons, "P180")
            if item:
                return item, "matched"
    return None, "wikidata-exact-taxon-without-allowed-commons-image"


def _open_input(path: Path):
    if path.name.casefold().endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def _open_output(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.name.casefold().endswith(".gz"):
        return gzip.open(path, "wt", encoding="utf-8", newline="\n")
    return path.open("w", encoding="utf-8", newline="\n")


def enrich(
    input_path: Path,
    output_path: Path,
    report_path: Path,
    metadata_path: Path | None = None,
    finder: Callable[[str], tuple[dict | None, str]] = find_exact_taxon_image,
    delay_seconds: float = 0.05,
) -> dict:
    report = {
        "recordCount": 0,
        "alreadyHadMedia": 0,
        "fallbackAttempted": 0,
        "fallbackAdded": 0,
        "remainingWithoutMedia": 0,
        "reasons": {},
    }
    digest = hashlib.sha256()
    with _open_input(input_path) as source, _open_output(output_path) as target:
        for line in source:
            if not line.strip():
                continue
            record = json.loads(line)
            report["recordCount"] += 1
            media_items = list(record.get("mediaItems") or [])
            if not media_items and record.get("media"):
                media_items = [record.get("media") or {}]
            media_items = [item for item in media_items if clean((item or {}).get("identifier"))]
            if media_items:
                report["alreadyHadMedia"] += 1
                record["mediaItems"] = media_items
                record["media"] = media_items[0]
            else:
                report["fallbackAttempted"] += 1
                scientific_name = clean(record.get("species") or record.get("scientificName"))
                item, reason = finder(scientific_name)
                if item:
                    record["mediaItems"] = [item]
                    record["media"] = item
                    report["fallbackAdded"] += 1
                else:
                    record["mediaItems"] = []
                    record["media"] = {}
                    report["remainingWithoutMedia"] += 1
                    report["reasons"][reason] = int(report["reasons"].get(reason, 0)) + 1
                if delay_seconds > 0:
                    time.sleep(delay_seconds)
            encoded = (json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
            target.write(encoded.decode("utf-8"))
            digest.update(encoded)

    report["recordsWithMedia"] = report["recordCount"] - report["remainingWithoutMedia"]
    report["normalizedNdjsonSha256"] = digest.hexdigest()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if metadata_path is not None:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        previous_hash = clean(metadata.get("normalizedNdjsonSha256"))
        if previous_hash:
            metadata["gbifOnlyNdjsonSha256"] = previous_hash
        metadata["normalizedNdjsonSha256"] = report["normalizedNdjsonSha256"]
        metadata["recordsWithMedia"] = report["recordsWithMedia"]
        metadata["recordsWithoutMedia"] = report["remainingWithoutMedia"]
        metadata["wikimediaCommonsFallbackAttempted"] = report["fallbackAttempted"]
        metadata["wikimediaCommonsFallbackAdded"] = report["fallbackAdded"]
        metadata["wikimediaCommonsFallbackRemainingWithoutMedia"] = report["remainingWithoutMedia"]
        metadata["wikimediaCommonsFallbackPolicy"] = "P31=Q16521 taxon; exact-P225-to-P18; CC0-1.0 or CC-BY-4.0 only"
        metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--delay-seconds", type=float, default=0.05)
    args = parser.parse_args()
    print(json.dumps(enrich(
        args.input,
        args.output,
        args.report,
        metadata_path=args.metadata,
        delay_seconds=args.delay_seconds,
    ), ensure_ascii=False))


if __name__ == "__main__":
    main()
