#!/usr/bin/env python3
"""Harden schema-v3 canary discovery for visual review.

Pins the user's review taxa so failures cannot silently swap Gừng, Lợn rừng,
Rau muống, Cá rô đồng or Ong nội for easier substitutes. Wikimedia calls are
cached/throttled, and recognition text is extracted from the sourced Vietnamese
article instead of being invented from an image label.
"""

from __future__ import annotations

import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_real_canary_samples_v3 as v3  # noqa: E402

PINNED_TAXA = {
    "vegetables": "Ipomoea aquatica",
    "freshwater-fish": "Anabas testudineus",
    "insects": "Apis cerana",
    "animals": "Sus scrofa",
    "medicinal-plants": "Zingiber officinale",
}
for category, scientific_name in PINNED_TAXA.items():
    v3.core.CANDIDATES[category] = [scientific_name]

v3.core.CANDIDATES["mushrooms"] = [
    "Pleurotus ostreatus",
    "Pleurotus pulmonarius",
    "Volvariella volvacea",
]

# Generic mushroom minimum: whole fruiting body + underside. A separately
# labelled stipe image remains desirable but should not falsely block a rich
# six-image set when the stipe is already visible in whole-body images.
v3.CATEGORY_POLICY["mushrooms"] = {
    "required": ["WHOLE", "UNDERSIDE"],
    "primary": "WHOLE",
    "min": 6,
}
v3.SPECIES_EXTRA_ROLES["Pleurotus ostreatus"] = ["STIPE", "HABITAT"]
v3.SPECIES_EXTRA_ROLES["Pleurotus pulmonarius"] = ["STIPE", "HABITAT"]
v3.SPECIES_EXTRA_ROLES["Volvariella volvacea"] = ["STIPE", "HABITAT"]

_pool_cache: dict[str, list[dict]] = {}
_focused_cache: dict[tuple[str, str], list[dict]] = {}
_last_commons_call = 0.0


def _throttle() -> None:
    global _last_commons_call
    elapsed = time.monotonic() - _last_commons_call
    if elapsed < 1.1:
        time.sleep(1.1 - elapsed)
    _last_commons_call = time.monotonic()


def generic_pool(scientific_name: str) -> list[dict]:
    if scientific_name not in _pool_cache:
        _throttle()
        _pool_cache[scientific_name] = v3.commons_candidates(scientific_name, "", 50)
    return _pool_cache[scientific_name]


def role_candidates_cached(scientific_name: str, role: str) -> list[dict]:
    terms = [term.casefold() for term in v3.ROLE_TERMS.get(role, []) if term]
    pool = generic_pool(scientific_name)
    matching = [
        item for item in pool
        if any(term in str(item.get("haystack", "")) for term in terms)
    ]
    if matching:
        return matching

    key = (scientific_name, role)
    if key not in _focused_cache:
        focused_term = v3.ROLE_TERMS.get(role, [""])[0]
        _throttle()
        _focused_cache[key] = v3.commons_candidates(scientific_name, focused_term, 30)
    return _focused_cache[key]


v3.role_candidates = role_candidates_cached

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
    return record, audit


v3.build_record = build_record_grounded

if __name__ == "__main__":
    raise SystemExit(v3.main())
