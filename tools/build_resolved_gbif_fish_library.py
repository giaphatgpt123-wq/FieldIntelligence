#!/usr/bin/env python3
"""Build one freshwater-fish library profile per independently resolved GBIF Backbone taxon.

Taxonomy comes from independent GBIF resolver evidence, never occurrence rows. Reference media is
first discovered through GBIF occurrences (Vietnam preferred, then global). If no reusable GBIF
image exists, a conservative Wikidata -> Wikimedia Commons fallback may provide one image when the
accepted canonical scientific name maps exactly to Wikidata P225 and Commons reports an allowed
licence. Synonyms resolving to the same accepted taxon share one profile and one media lookup.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import importlib.util
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Callable

OCCURRENCE_SEARCH_URL = "https://api.gbif.org/v1/occurrence/search"
GBIF_BACKBONE_KEY = "d7dddbf4-2cf0-4f39-9b2a-bb099caae36c"
GBIF_BACKBONE_DOI = "10.15468/39omei"
GBIF_BACKBONE_VERSION = "2023-08-28-frozen"
USER_AGENT = "FieldIntelligence/1.0 scientific-library-builder"
CANONICAL_MEDIA_LICENSES = {"CC0-1.0", "CC-BY-4.0", "CC-BY-NC-4.0"}
_COMMONS_MODULE = None


def clean(value: object) -> str:
    return str(value or "").strip()


def read_ndjson(path: Path):
    opener = gzip.open if path.name.casefold().endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                yield json.loads(line)


def canonical_media_license(value: object) -> str:
    raw = clean(value)
    if not raw:
        return ""
    compact = raw.casefold().replace("_", "-").replace(" ", "-")
    if compact in {"cc0-1.0", "cc0", "cc-zero-1.0"}:
        return "CC0-1.0"
    if compact in {"cc-by-4.0", "ccby-4.0"}:
        return "CC-BY-4.0"
    if compact in {"cc-by-nc-4.0", "ccbync-4.0"}:
        return "CC-BY-NC-4.0"
    parsed = urllib.parse.urlparse(raw)
    host = (parsed.hostname or "").casefold()
    path = (parsed.path or "").casefold().rstrip("/")
    if host in {"creativecommons.org", "www.creativecommons.org"}:
        if "/publicdomain/zero/1.0" in path:
            return "CC0-1.0"
        if "/licenses/by-nc/4.0" in path:
            return "CC-BY-NC-4.0"
        if "/licenses/by/4.0" in path:
            return "CC-BY-4.0"
    return ""


def eligible_media_item(media: dict, occurrence: dict) -> dict | None:
    identifier = clean(media.get("identifier") or media.get("accessURI"))
    if not identifier.startswith("https://"):
        return None
    media_type = clean(media.get("type") or media.get("mediaType") or "StillImage")
    if media_type.casefold() not in {"stillimage", "image"} and not media_type.casefold().startswith("image/"):
        return None
    license_id = canonical_media_license(media.get("license") or occurrence.get("license"))
    if license_id not in CANONICAL_MEDIA_LICENSES:
        return None
    return {
        "mediaType": "StillImage",
        "identifier": identifier,
        "references": clean(media.get("references") or occurrence.get("references")),
        "title": clean(media.get("title")),
        "description": clean(media.get("description")),
        "creator": clean(media.get("creator") or occurrence.get("recordedBy")),
        "rightsHolder": clean(media.get("rightsHolder") or occurrence.get("rightsHolder")),
        "license": license_id,
        "licenseOriginal": clean(media.get("license") or occurrence.get("license")),
        "sourceProvider": "GBIF occurrence media",
        "gbifOccurrenceKey": clean(occurrence.get("key") or occurrence.get("gbifID")),
        "datasetKey": clean(occurrence.get("datasetKey")),
        "countryCode": clean(occurrence.get("countryCode")),
    }


def _http_occurrence_search(taxon_key: str, country: str | None, limit: int = 50, retries: int = 4) -> dict:
    params = {
        "taxonKey": taxon_key,
        "checklistKey": GBIF_BACKBONE_KEY,
        "mediaType": "StillImage",
        "limit": str(max(1, min(limit, 300))),
    }
    if country:
        params["country"] = country
    request = urllib.request.Request(
        f"{OCCURRENCE_SEARCH_URL}?{urllib.parse.urlencode(params)}",
        headers={"Accept": "application/json", "User-Agent": USER_AGENT},
    )
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                if response.status != 200:
                    raise RuntimeError(f"GBIF occurrence search returned HTTP {response.status}")
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            last_error = exc
            if exc.code != 429 and exc.code < 500:
                raise
        except (urllib.error.URLError, TimeoutError) as exc:
            last_error = exc
        if attempt + 1 < retries:
            time.sleep(min(8, 2 ** attempt))
    raise RuntimeError(f"GBIF occurrence search failed after {retries} attempts: {last_error}")


def discover_media(
    taxon_key: str,
    searcher: Callable[[str, str | None, int], dict],
    max_candidates: int,
) -> tuple[list[dict], bool]:
    chosen: list[dict] = []
    seen_urls: set[str] = set()

    def collect(payload: dict) -> None:
        for occurrence in payload.get("results") or []:
            for raw in occurrence.get("media") or []:
                if not isinstance(raw, dict):
                    continue
                item = eligible_media_item(raw, occurrence)
                if not item:
                    continue
                url = item["identifier"]
                if url in seen_urls:
                    continue
                seen_urls.add(url)
                chosen.append(item)
                if len(chosen) >= max_candidates:
                    return
            if len(chosen) >= max_candidates:
                return

    collect(searcher(taxon_key, "VN", 50))
    used_global_fallback = False
    if len(chosen) < max_candidates:
        used_global_fallback = True
        collect(searcher(taxon_key, None, 50))
    return chosen[:max_candidates], used_global_fallback


def _default_commons_finder(scientific_name: str) -> tuple[dict | None, str]:
    global _COMMONS_MODULE
    if _COMMONS_MODULE is None:
        module_path = Path(__file__).with_name("enrich_wikidata_commons_fish_media.py")
        spec = importlib.util.spec_from_file_location("fieldintel_commons_fish_media", module_path)
        if spec is None or spec.loader is None:
            raise RuntimeError("cannot load Wikimedia Commons fish media fallback")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        _COMMONS_MODULE = module
    return _COMMONS_MODULE.find_exact_taxon_image(scientific_name)


def profile_from_resolution(row: dict, media_items: list[dict]) -> dict:
    resolution = row.get("taxonomyResolution") or {}
    accepted_id = clean(resolution.get("acceptedTaxonId"))
    accepted_name = clean(resolution.get("acceptedScientificName"))
    if not accepted_id or not accepted_name:
        raise ValueError("resolved checklist row lacks accepted GBIF taxon evidence")
    canonical = clean(resolution.get("acceptedCanonicalName")) or accepted_name
    genus = clean(resolution.get("acceptedGenericName") or resolution.get("genus"))
    epithet = clean(resolution.get("acceptedSpecificEpithet"))
    return {
        "schemaVersion": 1,
        "sourceId": "gbif",
        "sourceRecordId": accepted_id,
        "scientificName": accepted_name,
        "acceptedNameUsageId": accepted_id,
        "sourceScientificNames": [clean(row.get("scientificName"))],
        "taxonomicStatus": "accepted",
        "kingdom": clean(resolution.get("kingdom")) or "Animalia",
        "phylum": clean(resolution.get("phylum")),
        "class": clean(resolution.get("class")),
        "order": clean(resolution.get("order")),
        "family": clean(resolution.get("family")),
        "genus": genus,
        "specificEpithet": epithet,
        "species": canonical,
        "vernacularName": "",
        "libraryGroup": "Cá nước ngọt",
        "media": media_items[0] if media_items else {},
        "mediaItems": media_items,
        "occurrence": {},
        "provenance": {
            "authority": "GBIF Backbone Taxonomy",
            "publisher": "GBIF Secretariat",
            "datasetDoi": GBIF_BACKBONE_DOI,
            "license": "CC-BY-4.0",
            "scope": "accepted-taxonomy-and-reference-media-metadata",
            "version": GBIF_BACKBONE_VERSION,
            "nonCommercialRestriction": False,
        },
        "taxonomyResolutionEvidenceSha256": clean(resolution.get("evidenceSha256")),
    }


def _merge_alias(base: dict, row: dict) -> None:
    name = clean(row.get("scientificName"))
    aliases = list(base.get("sourceScientificNames") or [])
    seen_aliases = {clean(v).casefold() for v in aliases if clean(v)}
    if name and name.casefold() not in seen_aliases:
        aliases.append(name)
    base["sourceScientificNames"] = aliases


def build(
    resolved_checklist: Path,
    output_path: Path,
    metadata_path: Path,
    fetch_media: bool = True,
    max_media_candidates: int = 4,
    delay_seconds: float = 0.05,
    searcher: Callable[[str, str | None, int], dict] | None = None,
    commons_finder: Callable[[str], tuple[dict | None, str]] | None = None,
) -> dict:
    if max_media_candidates < 0:
        raise ValueError("max_media_candidates cannot be negative")
    searcher = searcher or _http_occurrence_search
    commons_finder = commons_finder or _default_commons_finder
    grouped: dict[str, dict] = {}
    present_input = resolver_blocked = media_candidates = media_vn = global_fallback_taxa = 0
    commons_attempted = commons_added = 0
    commons_reasons: dict[str, int] = {}
    media_errors: list[dict] = []

    for row in read_ndjson(resolved_checklist):
        if clean(row.get("presenceStatus")).casefold() != "present":
            continue
        present_input += 1
        resolution = row.get("taxonomyResolution") or {}
        if clean(resolution.get("status")).casefold() != "resolved":
            resolver_blocked += 1
            continue
        taxon_key = clean(resolution.get("acceptedTaxonId"))
        if not taxon_key:
            resolver_blocked += 1
            continue

        if taxon_key in grouped:
            _merge_alias(grouped[taxon_key], row)
            continue

        items: list[dict] = []
        if fetch_media and max_media_candidates:
            try:
                items, used_global = discover_media(taxon_key, searcher, max_media_candidates)
                if used_global:
                    global_fallback_taxa += 1
                media_candidates += len(items)
                media_vn += sum(1 for item in items if clean(item.get("countryCode")).upper() == "VN")
            except (RuntimeError, urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
                media_errors.append({
                    "acceptedTaxonId": taxon_key,
                    "scientificName": clean(row.get("scientificName")),
                    "error": str(exc),
                })

            if not items:
                commons_attempted += 1
                canonical_name = clean(resolution.get("acceptedCanonicalName")) or clean(resolution.get("acceptedScientificName"))
                try:
                    commons_item, commons_reason = commons_finder(canonical_name)
                except (RuntimeError, urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
                    commons_item, commons_reason = None, "wikimedia-api-error"
                    media_errors.append({
                        "acceptedTaxonId": taxon_key,
                        "scientificName": canonical_name,
                        "error": str(exc),
                    })
                if commons_item:
                    items = [commons_item]
                    commons_added += 1
                    media_candidates += 1
                else:
                    commons_reasons[commons_reason] = int(commons_reasons.get(commons_reason, 0)) + 1

        grouped[taxon_key] = profile_from_resolution(row, items)
        if delay_seconds > 0 and fetch_media:
            time.sleep(delay_seconds)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    records_with_media = 0
    missing_media_records: list[dict] = []
    with gzip.open(output_path, "wt", encoding="utf-8", newline="\n") as out:
        for key in sorted(grouped, key=lambda v: (len(v), v)):
            record = grouped[key]
            if record.get("mediaItems"):
                records_with_media += 1
            else:
                missing_media_records.append({
                    "acceptedTaxonId": key,
                    "scientificName": clean(record.get("species") or record.get("scientificName")),
                })
            line = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
            out.write(line)
            digest.update(line.encode("utf-8"))

    metadata = {
        "schemaVersion": 1,
        "sourceId": "gbif",
        "source": "GBIF Backbone Taxonomy + GBIF occurrence media + exact Wikidata/Commons fallback",
        "datasetDoi": GBIF_BACKBONE_DOI,
        "version": GBIF_BACKBONE_VERSION,
        "license": "CC-BY-4.0",
        "scope": "accepted-taxonomy-and-reference-media-metadata",
        "presentChecklistRows": present_input,
        "resolverBlockedPresentRows": resolver_blocked,
        "recordCount": len(grouped),
        "recordsWithMedia": records_with_media,
        "recordsWithoutMedia": len(grouped) - records_with_media,
        "recordsWithoutMediaDetails": missing_media_records,
        "mediaCandidates": media_candidates,
        "vietnamMediaCandidates": media_vn,
        "globalFallbackTaxa": global_fallback_taxa,
        "wikimediaCommonsFallbackAttempted": commons_attempted,
        "wikimediaCommonsFallbackAdded": commons_added,
        "wikimediaCommonsFallbackReasons": commons_reasons,
        "wikimediaCommonsFallbackPolicy": "exact P225 -> P18; Commons CC0-1.0 or CC-BY-4.0 only",
        "mediaSearchErrors": len(media_errors),
        "mediaSearchErrorDetails": media_errors[:100],
        "normalizedNdjsonSha256": digest.hexdigest(),
        "safety": {
            "referenceMediaIsIdentificationEvidence": False,
            "occurrenceDoesNotProveFreshwaterStatus": True,
            "occurrenceDoesNotProveCurrentVietnamPresence": True,
            "recordsDoNotImplyEdibility": True,
            "recordsDoNotImplyToxicity": True,
            "recordsDoNotImplyTreatment": True,
        },
    }
    metadata_path.parent.mkdir(parents=True, exist_ok=True)
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolved-checklist", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--no-fetch-media", action="store_true")
    parser.add_argument("--max-media-candidates", type=int, default=4)
    parser.add_argument("--delay-seconds", type=float, default=0.05)
    args = parser.parse_args()
    meta = build(
        args.resolved_checklist,
        args.output,
        args.metadata,
        fetch_media=not args.no_fetch_media,
        max_media_candidates=args.max_media_candidates,
        delay_seconds=args.delay_seconds,
    )
    print(json.dumps({
        "recordCount": meta["recordCount"],
        "recordsWithMedia": meta["recordsWithMedia"],
        "recordsWithoutMedia": meta["recordsWithoutMedia"],
        "mediaCandidates": meta["mediaCandidates"],
        "wikimediaCommonsFallbackAdded": meta["wikimediaCommonsFallbackAdded"],
        "resolverBlockedPresentRows": meta["resolverBlockedPresentRows"],
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
