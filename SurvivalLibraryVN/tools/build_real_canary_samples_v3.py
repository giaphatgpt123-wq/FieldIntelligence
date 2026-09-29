#!/usr/bin/env python3
"""Compatibility wrapper around v2 canary builder with Commons-search fallback."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_real_canary_samples_v2 as core  # noqa: E402


def media_from_info(page_title: str, info: dict) -> dict | None:
    meta = info.get("extmetadata", {})
    license_name = str(meta.get("LicenseShortName", {}).get("value") or "").strip()
    if not any(token in license_name.upper() for token in ("CC BY", "CC0", "PUBLIC DOMAIN")):
        return None
    mime = str(info.get("mime") or "")
    if mime not in {"image/jpeg", "image/png", "image/webp"}:
        return None
    download = str(info.get("thumburl") or info.get("url") or "")
    source_uri = str(info.get("descriptionurl") or "")
    if not download.startswith("https://") or not source_uri.startswith("https://"):
        return None
    creator = (
        core.clean_html(str(meta.get("Artist", {}).get("value") or ""))
        or core.clean_html(str(meta.get("Credit", {}).get("value") or ""))
        or "Wikimedia Commons contributor"
    )
    return {
        "fileTitle": page_title.removeprefix("File:"),
        "sourceUri": source_uri,
        "download": download,
        "mime": mime,
        "license": license_name,
        "creator": creator,
    }


def commons_search(scientific_name: str) -> dict | None:
    data = core.fetch_json(
        "https://commons.wikimedia.org/w/api.php",
        {
            "action": "query",
            "format": "json",
            "generator": "search",
            "gsrsearch": f'"{scientific_name}"',
            "gsrnamespace": 6,
            "gsrlimit": 10,
            "prop": "imageinfo",
            "iiprop": "url|mime|extmetadata",
            "iiurlwidth": core.THUMB_WIDTH,
        },
    )
    pages = data.get("query", {}).get("pages", {})
    wanted = {token.casefold() for token in scientific_name.split()}
    ranked: list[tuple[int, str, dict]] = []
    for page in pages.values():
        title = str(page.get("title") or "")
        info_values = page.get("imageinfo") or []
        if not title or not info_values:
            continue
        media = media_from_info(title, info_values[0])
        if not media:
            continue
        title_tokens = set(title.casefold().replace("_", " ").split())
        score = sum(1 for token in wanted if token in title_tokens or token in title.casefold())
        ranked.append((score, title, media))
    if not ranked:
        return None
    ranked.sort(key=lambda row: (-row[0], len(row[1]), row[1]))
    return ranked[0][2]


def select_all_with_fallback(wd: dict[str, list[dict]], commons: dict[str, dict]) -> dict[str, dict]:
    selected: dict[str, dict] = {}
    fallback_cache: dict[str, dict | None] = {}
    for category, candidates in core.CANDIDATES.items():
        failures: list[str] = []
        for scientific in candidates:
            wd_values = wd.get(scientific, [])
            if not wd_values:
                failures.append(f"{scientific}: thiếu liên kết viwiki/Wikidata")
                continue
            for wd_item in wd_values:
                media = commons.get(core.normalize_file(wd_item["imageTitle"]))
                if media is None:
                    if scientific not in fallback_cache:
                        try:
                            fallback_cache[scientific] = commons_search(scientific)
                        except Exception as exc:
                            fallback_cache[scientific] = None
                            failures.append(f"{scientific}: Commons search lỗi {exc}")
                    media = fallback_cache[scientific]
                if not media:
                    failures.append(f"{scientific}: không có ảnh Commons open-license raster phù hợp")
                    continue
                try:
                    gbif = core.resolve_gbif(scientific)
                    image_bytes, response_mime = core.fetch_bytes(media["download"], max_bytes=core.MAX_IMAGE_BYTES)
                    if len(image_bytes) < 8 * 1024:
                        raise RuntimeError(f"ảnh quá nhỏ: {len(image_bytes)} bytes")
                    mime = response_mime if response_mime in {"image/jpeg", "image/png", "image/webp"} else media["mime"]
                    selected[category] = {
                        "categoryId": category,
                        "scientificName": scientific,
                        "vietnameseName": wd_item["viTitle"],
                        "wikidata": wd_item,
                        "gbif": gbif,
                        "media": {**media, "bytes": image_bytes, "mime": mime},
                    }
                    print(
                        f"[canary] PASS {category}: {wd_item['viTitle']} / {scientific} "
                        f"(GBIF VN={gbif['count']}, {media['license']})"
                    )
                    break
                except Exception as exc:
                    failures.append(f"{scientific}: {exc}")
                    print(f"[canary] reject {category}/{scientific}: {exc}", file=sys.stderr)
            if category in selected:
                break
        if category not in selected:
            raise RuntimeError(f"{category}: không candidate nào vượt gate | " + " | ".join(failures))
    return selected


core.select_all = select_all_with_fallback
raise SystemExit(core.main())
