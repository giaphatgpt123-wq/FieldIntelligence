#!/usr/bin/env python3
"""Harden schema-v3 canary discovery for visual review.

This wrapper pins the user's review taxa so failures cannot silently swap Gừng,
Lợn rừng, Rau muống, Cá rô đồng or Ong nội for easier substitutes. It also cuts
Wikimedia API calls by reusing a per-taxon media pool and only doing one focused
fallback search per missing role.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_real_canary_samples_v3 as v3  # noqa: E402

# Do not silently replace the records explicitly used for human visual review.
PINNED_TAXA = {
    "vegetables": "Ipomoea aquatica",
    "freshwater-fish": "Anabas testudineus",
    "insects": "Apis cerana",
    "animals": "Sus scrofa",
    "medicinal-plants": "Zingiber officinale",
}
for category, scientific_name in PINNED_TAXA.items():
    v3.core.CANDIDATES[category] = [scientific_name]

# Prefer a mushroom with a richer public diagnostic image corpus.
v3.core.CANDIDATES["mushrooms"] = [
    "Pleurotus ostreatus",
    "Pleurotus pulmonarius",
    "Volvariella volvacea",
]

# Whole form + underside are hard minimum for mushrooms. STIPE remains requested
# as an extra view, but lack of a separately-labelled stipe image alone does not
# discard an otherwise rich six-image diagnostic set.
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


def generic_pool(scientific_name: str) -> list[dict]:
    if scientific_name not in _pool_cache:
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

    # One focused query only. The previous implementation could make several
    # calls per role and repeatedly hit Commons HTTP 429.
    key = (scientific_name, role)
    if key not in _focused_cache:
        focused_term = v3.ROLE_TERMS.get(role, [""])[0]
        _focused_cache[key] = v3.commons_candidates(scientific_name, focused_term, 30)
    return _focused_cache[key]


v3.role_candidates = role_candidates_cached

if __name__ == "__main__":
    raise SystemExit(v3.main())
