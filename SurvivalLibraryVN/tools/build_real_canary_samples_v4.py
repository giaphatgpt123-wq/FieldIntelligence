#!/usr/bin/env python3
"""Harden schema-v3 canary discovery for visual review.

Pins the exact 12 review taxa so failures cannot silently swap a selected pilot
record for an easier substitute. Wikimedia calls and image downloads are
throttled, and recognition text is extracted from sourced Vietnamese articles
instead of being invented from image labels.
"""

from __future__ import annotations

import re
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_real_canary_samples_v3 as v3  # noqa: E402

# The 12 pilot records are part of the review contract. A failed taxon must fail
# visibly instead of being replaced by another species in the same category.
PINNED_TAXA = {
    "vegetables": "Ipomoea aquatica",
    "roots": "Colocasia esculenta",
    "fruit-crops": "Mangifera indica",
    "flowers": "Nelumbo nucifera",
    "timber-trees": "Hopea odorata",
    "mushrooms": "Pleurotus pulmonarius",
    "freshwater-fish": "Anabas testudineus",
    "marine-life": "Penaeus monodon",
    "insects": "Apis cerana",
    "animals": "Sus scrofa",
    "medicinal-plants": "Zingiber officinale",
    "danger": "Aedes aegypti",
}
for category, scientific_name in PINNED_TAXA.items():
    v3.core.CANDIDATES[category] = [scientific_name]

# Generic mushroom minimum: whole fruiting body + underside. A separately
# labelled stipe image remains desirable but should not falsely block a rich
# six-image set when the stipe is already visible in whole-body images.
v3.CATEGORY_POLICY["mushrooms"] = {
    "required": ["WHOLE", "UNDERSIDE"],
    "primary": "WHOLE",
    "min": 6,
}
v3.SPECIES_EXTRA_ROLES["Pleurotus pulmonarius"] = ["STIPE", "HABITAT"]

# Search hints are only used to discover media metadata. They do not bypass the
# distinct-file, license, checksum, required-role or minimum-image gates.
SPECIES_ROLE_HINTS: dict[tuple[str, str], list[str]] = {
    ("Colocasia esculenta", "UNDERGROUND_PART"): ["taro corm", "taro root", "taro tuber", "corm"],
    ("Colocasia esculenta", "WHOLE"): ["taro plant", "taro"],
    ("Pleurotus pulmonarius", "WHOLE"): ["oyster mushroom", "fruiting body", "mushroom"],
    ("Pleurotus pulmonarius", "UNDERSIDE"): ["oyster mushroom gills", "gills", "underside"],
    ("Penaeus monodon", "WHOLE"): ["black tiger shrimp", "giant tiger prawn", "tiger prawn", "shrimp", "prawn"],
    ("Penaeus monodon", "SIDE"): ["tiger prawn side", "shrimp side", "lateral"],
    ("Penaeus monodon", "HEAD"): ["tiger prawn head", "shrimp head", "head"],
}

_pool_cache: dict[str, list[dict]] = {}
_focused_cache: dict[tuple[str, str], list[dict]] = {}
_last_commons_call = 0.0
_last_media_download = 0.0


def fetch_bytes_bounded(url: str, *, accept: str = "*/*", max_bytes: int | None = None) -> tuple[bytes, str]:
    """Network fetch for canary discovery with bounded backoff.

    The older helper honored upstream Retry-After literally, including 600s.
    That can make a single rejected thumbnail stall the whole quality run. Media
    429s therefore fail immediately so collect_media can try another candidate;
    API/transient server failures get only short bounded retries.
    """
    last: Exception | None = None
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": v3.core.USER_AGENT, "Accept": accept})
            with urlopen(request, timeout=v3.core.TIMEOUT) as response:
                declared = response.headers.get("Content-Length")
                if max_bytes and declared and int(declared) > max_bytes:
                    raise RuntimeError(f"download quá lớn: {declared} bytes")
                data = response.read(max_bytes + 1 if max_bytes else -1)
                if max_bytes and len(data) > max_bytes:
                    raise RuntimeError(f"download vượt {max_bytes} bytes")
                return data, response.headers.get_content_type()
        except HTTPError as exc:
            last = exc
            host = urlparse(url).netloc
            if exc.code == 429 and host == "upload.wikimedia.org":
                raise RuntimeError("Wikimedia media rate-limit; bỏ candidate này") from exc
            if exc.code not in {429, 500, 502, 503, 504}:
                raise
            retry_after = exc.headers.get("Retry-After") if exc.headers else None
            requested = int(retry_after) if retry_after and retry_after.isdigit() else 2 ** attempt
            delay = min(requested, 5)
            print(f"[canary] HTTP {exc.code}; retry giới hạn {delay}s: {host}", file=sys.stderr)
            time.sleep(delay)
        except URLError as exc:
            last = exc
            time.sleep(min(2 ** attempt, 5))
    raise RuntimeError(f"không tải được sau bounded retry: {last}")


# v3/core helpers resolve this module attribute at call time, so the canary v4
# run gets bounded retry without changing the older stable builders.
v3.core.fetch_bytes = fetch_bytes_bounded


def _throttle_commons() -> None:
    global _last_commons_call
    elapsed = time.monotonic() - _last_commons_call
    if elapsed < 1.25:
        time.sleep(1.25 - elapsed)
    _last_commons_call = time.monotonic()


def _throttle_media() -> None:
    global _last_media_download
    elapsed = time.monotonic() - _last_media_download
    if elapsed < 1.35:
        time.sleep(1.35 - elapsed)
    _last_media_download = time.monotonic()


def generic_pool(scientific_name: str) -> list[dict]:
    if scientific_name not in _pool_cache:
        _throttle_commons()
        _pool_cache[scientific_name] = v3.commons_candidates(scientific_name, "", 50)
    return _pool_cache[scientific_name]


def _focused_candidates(scientific_name: str, term: str) -> list[dict]:
    key = (scientific_name, term)
    if key not in _focused_cache:
        _throttle_commons()
        _focused_cache[key] = v3.commons_candidates(scientific_name, term, 30)
    return _focused_cache[key]


def role_candidates_cached(scientific_name: str, role: str) -> list[dict]:
    hints = SPECIES_ROLE_HINTS.get((scientific_name, role), [])
    generic_terms = v3.ROLE_TERMS.get(role, [])
    terms = [term.casefold() for term in [*hints, *generic_terms] if term]
    pool = generic_pool(scientific_name)

    matching = [
        item for item in pool
        if any(term in str(item.get("haystack", "")) for term in terms)
    ]
    if matching:
        return matching

    # Query the strongest species-specific hints first, then a small number of
    # generic role terms. Stop after the first non-empty result to control API
    # traffic and avoid Wikimedia rate limiting.
    focused_terms = [*hints, *generic_terms[:2]]
    for term in focused_terms:
        values = _focused_candidates(scientific_name, term)
        if values:
            return values
    return []


v3.role_candidates = role_candidates_cached

_original_download_media = v3.download_media


def download_media_throttled(candidate: dict) -> dict:
    _throttle_media()
    return _original_download_media(candidate)


v3.download_media = download_media_throttled

MORPHOLOGY_WORDS = (
    "lá", "thân", "rễ", "củ", "thân rễ", "hoa", "quả", "hạt", "vỏ", "mũ",
    "phiến", "cuống", "mang", "vây", "miệng", "đầu", "mõm", "lông", "cánh",
    "râu", "chân", "màu", "dài", "cao", "rộng", "kích thước", "hình dạng",
)


def sourced_features(text: str) -> list[str]:
    if not text:
        return []
    cleaned = " ".join(text.split())
    sentences = re.split(r"(?<=[.!?])\s+", cleaned)
    selected: list[str] = []
    for sentence in sentences:
        lowered = sentence.casefold()
        if not any(word in lowered for word in MORPHOLOGY_WORDS):
            continue
        sentence = sentence.strip()
        if not (35 <= len(sentence) <= 260):
            continue
        if sentence not in selected:
            selected.append(sentence)
        if len(selected) >= 5:
            break
    return selected


_original_build_record = v3.build_record


def build_record_grounded(item: dict) -> tuple[dict, dict]:
    record, audit = _original_build_record(item)
    source_text = " ".join(str(item.get("wikiExtract") or "").split())
    roles = record.get("requiredViewRoles", [])
    role_text = ", ".join(v3.ROLE_LABELS.get(role, role) for role in roles)
    if source_text:
        record["identificationSummary"] = (
            source_text[:850]
            + ("…" if len(source_text) > 850 else "")
            + f"\n\nKhi đối chiếu ảnh, phải kiểm tra đồng thời: {role_text}."
        )
        record["keyFeatures"] = sourced_features(source_text)
    else:
        record["identificationSummary"] = (
            f"Chưa có mô tả hình thái tiếng Việt đủ mạnh. Chỉ cho phép đối chiếu các góc đã kiểm chứng: {role_text}; "
            "không kết luận từ một ảnh duy nhất."
        )
        record["keyFeatures"] = []
    audit["groundedFeatureCount"] = len(record["keyFeatures"])
    audit["pinnedTaxon"] = PINNED_TAXA.get(record.get("categoryId", ""), "")
    return record, audit


v3.build_record = build_record_grounded

if __name__ == "__main__":
    raise SystemExit(v3.main())
