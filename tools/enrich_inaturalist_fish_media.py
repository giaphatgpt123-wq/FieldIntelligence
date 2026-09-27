#!/usr/bin/env python3
"""Find reusable observation photos for an exact, research-grade fish taxon."""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Callable

API = "https://api.inaturalist.org/v1"
USER_AGENT = "FieldIntelligence/1.0 scientific-library-builder"
LICENSES = {"cc0": "CC0-1.0", "cc-by": "CC-BY-4.0", "cc-by-nc": "CC-BY-NC-4.0"}


def request_json(path: str, params: dict, retries: int = 3) -> dict:
    url = API + path + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=12) as response:
                return json.load(response)
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError):
            if attempt + 1 == retries:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError("iNaturalist request failed")


def find_exact_taxon_image(
    scientific_name: str,
    fetch: Callable[[str, dict], dict] = request_json,
) -> tuple[dict | None, str]:
    taxa = fetch("/taxa/autocomplete", {"q": scientific_name, "per_page": 10})
    exact = [t for t in taxa.get("results", [])
             if str(t.get("name", "")).casefold() == scientific_name.casefold()
             and t.get("rank") == "species"
             and t.get("iconic_taxon_name") == "Actinopterygii"
             and t.get("is_active") is True]
    if len(exact) != 1:
        return None, "inat-no-unique-exact-fish-taxon"
    taxon_id = exact[0]["id"]
    observations = fetch("/observations", {
        "taxon_id": taxon_id,
        "quality_grade": "research",
        "photos": "true",
        "photo_license": ",".join(LICENSES),
        "per_page": 20,
        "order_by": "observed_on",
        "order": "desc",
    })
    for observation in observations.get("results", []):
        taxon = observation.get("taxon") or {}
        if (str(taxon.get("id")) != str(taxon_id)
                or str(taxon.get("name", "")).casefold() != scientific_name.casefold()
                or observation.get("quality_grade") != "research"):
            continue
        for photo in observation.get("photos") or []:
            license_id = LICENSES.get(str(photo.get("license_code", "")).lower())
            url = str(photo.get("url") or "")
            if not license_id or not url.startswith("https://"):
                continue
            url = url.replace("/square.", "/medium.")
            reference = f"https://www.inaturalist.org/observations/{observation['id']}"
            return ({
                "mediaType": "StillImage",
                "identifier": url,
                "references": reference,
                "creator": str(photo.get("attribution") or ""),
                "license": license_id,
                "licenseOriginal": str(photo["license_code"]),
                "sourceProvider": "iNaturalist research-grade observation",
                "inaturalistTaxonId": str(taxon_id),
                "inaturalistObservationId": str(observation["id"]),
                "inaturalistPhotoId": str(photo.get("id") or ""),
                "mappingEvidence": "exact-active-species-taxon+research-grade-observation+photo-license",
            }, "matched")
    return None, "inat-no-licensed-research-photo"
