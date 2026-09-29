#!/usr/bin/env python3
"""Build one multi-view, source-backed identification record per app category.

Schema v3 deliberately rejects the old one-photo canary. It requires a diagnostic
image set, distinct files, a primary recognition view and category/species-specific
view roles. No eating, dose, treatment or medicinal efficacy claim is emitted.
"""

from __future__ import annotations

import json
import shutil
import sys
import time
from pathlib import Path
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_real_canary_samples_v2 as core  # noqa: E402

SCHEMA_VERSION = 3
PACKAGE_VERSION = 2
QUALITY_PROFILE = "IDENTIFICATION_V1"

ROLE_LABELS = {
    "WHOLE": "Toàn thể",
    "ADULT_WHOLE": "Trưởng thành · toàn thân",
    "JUVENILE": "Con non",
    "LEAF": "Lá",
    "STEM": "Thân/cuống",
    "FLOWER": "Hoa",
    "FRUIT": "Quả",
    "UNDERGROUND_PART": "Củ/thân rễ/rễ",
    "CROSS_SECTION": "Mặt cắt",
    "BARK": "Vỏ/thân cây",
    "UNDERSIDE": "Mặt dưới",
    "STIPE": "Cuống",
    "HEAD": "Đầu/mõm",
    "SIDE": "Dáng nghiêng",
    "DORSAL": "Mặt lưng",
    "FINS": "Vây",
    "MOUTH": "Miệng",
    "WINGS": "Cánh",
    "ANTENNAE": "Râu",
    "HABITAT": "Trong môi trường sống",
    "REFERENCE": "Ảnh tham chiếu",
}

CATEGORY_POLICY = {
    "vegetables": {"required": ["WHOLE", "LEAF", "STEM"], "primary": "WHOLE", "min": 5},
    "roots": {"required": ["WHOLE", "UNDERGROUND_PART", "LEAF"], "primary": "UNDERGROUND_PART", "min": 5},
    "fruit-crops": {"required": ["WHOLE", "FRUIT", "LEAF"], "primary": "FRUIT", "min": 5},
    "flowers": {"required": ["WHOLE", "FLOWER", "LEAF"], "primary": "FLOWER", "min": 5},
    "timber-trees": {"required": ["WHOLE", "BARK", "LEAF"], "primary": "WHOLE", "min": 5},
    "mushrooms": {"required": ["WHOLE", "UNDERSIDE", "STIPE"], "primary": "WHOLE", "min": 6},
    "freshwater-fish": {"required": ["WHOLE", "SIDE", "HEAD"], "primary": "WHOLE", "min": 6},
    "marine-life": {"required": ["WHOLE", "SIDE", "HEAD"], "primary": "WHOLE", "min": 6},
    "insects": {"required": ["WHOLE", "DORSAL", "HEAD"], "primary": "WHOLE", "min": 6},
    "animals": {"required": ["ADULT_WHOLE", "JUVENILE", "HEAD"], "primary": "ADULT_WHOLE", "min": 6},
    "medicinal-plants": {"required": ["WHOLE", "UNDERGROUND_PART", "LEAF"], "primary": "UNDERGROUND_PART", "min": 5},
    "danger": {"required": ["WHOLE", "DORSAL", "SIDE"], "primary": "WHOLE", "min": 6},
}

# Extra views are desirable but do not block publication if Commons cannot supply them.
SPECIES_EXTRA_ROLES = {
    "Zingiber officinale": ["CROSS_SECTION", "FLOWER", "HABITAT"],
    "Curcuma longa": ["CROSS_SECTION", "FLOWER", "HABITAT"],
    "Sus scrofa": ["SIDE", "HABITAT"],
    "Ipomoea aquatica": ["FLOWER", "HABITAT"],
    "Anabas testudineus": ["FINS", "MOUTH", "HABITAT"],
    "Apis cerana": ["SIDE", "WINGS", "HABITAT"],
}

ROLE_TERMS = {
    "WHOLE": ["plant", "specimen", "whole"],
    "ADULT_WHOLE": ["adult", "wild boar", "boar"],
    "JUVENILE": ["piglet", "juvenile", "young"],
    "LEAF": ["leaf", "leaves", "foliage"],
    "STEM": ["stem", "stalk"],
    "FLOWER": ["flower", "flowers", "inflorescence"],
    "FRUIT": ["fruit", "fruits"],
    "UNDERGROUND_PART": ["rhizome", "root", "roots", "corm", "tuber"],
    "CROSS_SECTION": ["cross section", "section", "cut", "slice"],
    "BARK": ["bark", "trunk"],
    "UNDERSIDE": ["gills", "underside", "lamellae", "pores"],
    "STIPE": ["stipe", "stem"],
    "HEAD": ["head", "face"],
    "SIDE": ["side", "lateral", "profile"],
    "DORSAL": ["dorsal", "top"],
    "FINS": ["fin", "fins"],
    "MOUTH": ["mouth", "head"],
    "WINGS": ["wing", "wings"],
    "ANTENNAE": ["antenna", "antennae"],
    "HABITAT": ["habitat", "wild", "forest", "field"],
}


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
    description = " ".join(
        [
            page_title,
            core.clean_html(str(meta.get("ImageDescription", {}).get("value") or "")),
            core.clean_html(str(meta.get("ObjectName", {}).get("value") or "")),
        ]
    )
    return {
        "fileTitle": page_title.removeprefix("File:"),
        "sourceUri": source_uri,
        "download": download,
        "mime": mime,
        "license": license_name,
        "creator": creator,
        "haystack": description.casefold(),
    }


def commons_candidates(scientific_name: str, search_term: str = "", limit: int = 24) -> list[dict]:
    query = f'"{scientific_name}" {search_term}'.strip()
    data = core.fetch_json(
        "https://commons.wikimedia.org/w/api.php",
        {
            "action": "query",
            "format": "json",
            "generator": "search",
            "gsrsearch": query,
            "gsrnamespace": 6,
            "gsrlimit": limit,
            "prop": "imageinfo",
            "iiprop": "url|mime|extmetadata",
            "iiurlwidth": core.THUMB_WIDTH,
        },
    )
    genus, species = [token.casefold() for token in scientific_name.split()[:2]]
    ranked: list[tuple[int, str, dict]] = []
    for page in data.get("query", {}).get("pages", {}).values():
        title = str(page.get("title") or "")
        info_values = page.get("imageinfo") or []
        if not title or not info_values:
            continue
        media = media_from_info(title, info_values[0])
        if not media:
            continue
        haystack = media["haystack"]
        exact_score = int(genus in haystack) + int(species in haystack)
        # Search relevance may find a valid common-name image; exact scientific evidence gets priority.
        ranked.append((exact_score, title, media))
    ranked.sort(key=lambda row: (-row[0], len(row[1]), row[1]))
    return [row[2] for row in ranked]


def role_candidates(scientific_name: str, role: str) -> list[dict]:
    terms = ROLE_TERMS.get(role, [""])
    found: list[dict] = []
    seen: set[str] = set()
    for term in terms:
        try:
            values = commons_candidates(scientific_name, term, 20)
        except Exception as exc:
            print(f"[canary] Commons role search lỗi {scientific_name}/{role}/{term}: {exc}", file=sys.stderr)
            continue
        for value in values:
            key = value["sourceUri"]
            if key in seen:
                continue
            seen.add(key)
            found.append(value)
    return found


def download_media(candidate: dict) -> dict:
    raw, response_mime = core.fetch_bytes(candidate["download"], max_bytes=core.MAX_IMAGE_BYTES)
    if len(raw) < 8 * 1024:
        raise RuntimeError(f"ảnh quá nhỏ: {len(raw)} bytes")
    mime = response_mime if response_mime in {"image/jpeg", "image/png", "image/webp"} else candidate["mime"]
    return {**candidate, "bytes": raw, "mime": mime, "sha256": core.sha256(raw)}


def collect_media(scientific_name: str, category: str) -> list[dict]:
    policy = CATEGORY_POLICY[category]
    required = list(policy["required"])
    desired = required + [r for r in SPECIES_EXTRA_ROLES.get(scientific_name, []) if r not in required]
    selected: list[dict] = []
    used_sources: set[str] = set()
    used_hashes: set[str] = set()

    def add_for_role(role: str, required_role: bool) -> bool:
        for candidate in role_candidates(scientific_name, role):
            if candidate["sourceUri"] in used_sources:
                continue
            try:
                media = download_media(candidate)
            except Exception as exc:
                print(f"[canary] reject media {scientific_name}/{role}: {exc}", file=sys.stderr)
                continue
            if media["sha256"] in used_hashes:
                continue
            media["viewRole"] = role
            media["angleLabel"] = ROLE_LABELS[role]
            media["lifeStage"] = "Con non" if role == "JUVENILE" else ("Trưởng thành" if role == "ADULT_WHOLE" else "")
            selected.append(media)
            used_sources.add(media["sourceUri"])
            used_hashes.add(media["sha256"])
            return True
        if required_role:
            raise RuntimeError(f"thiếu ảnh bắt buộc cho vai trò {role}")
        return False

    for role in desired:
        add_for_role(role, role in required)

    # Fill remaining quota with distinct exact-taxon references. They do not replace required roles.
    if len(selected) < int(policy["min"]):
        for candidate in commons_candidates(scientific_name, "", 40):
            if len(selected) >= int(policy["min"]):
                break
            if candidate["sourceUri"] in used_sources:
                continue
            try:
                media = download_media(candidate)
            except Exception:
                continue
            if media["sha256"] in used_hashes:
                continue
            media["viewRole"] = "REFERENCE"
            media["angleLabel"] = f"Ảnh tham chiếu {len(selected) + 1}"
            media["lifeStage"] = ""
            selected.append(media)
            used_sources.add(media["sourceUri"])
            used_hashes.add(media["sha256"])

    if len(selected) < int(policy["min"]):
        raise RuntimeError(f"chỉ tìm được {len(selected)}/{policy['min']} ảnh khác nhau")

    # A primary image must be a real required recognition role, never a generic reference.
    primary_role = str(policy["primary"])
    primary_index = next((i for i, media in enumerate(selected) if media["viewRole"] == primary_role), None)
    if primary_index is None:
        raise RuntimeError(f"thiếu ảnh đại diện {primary_role}")
    primary = selected.pop(primary_index)
    return [primary] + selected


def viwiki_extract(title: str) -> str:
    try:
        data = core.fetch_json(
            "https://vi.wikipedia.org/w/api.php",
            {
                "action": "query",
                "format": "json",
                "prop": "extracts",
                "exintro": 1,
                "explaintext": 1,
                "redirects": 1,
                "titles": title,
            },
        )
        pages = data.get("query", {}).get("pages", {})
        for page in pages.values():
            text = " ".join(str(page.get("extract") or "").split())
            if text:
                return text[:900]
    except Exception as exc:
        print(f"[canary] không lấy được viwiki extract {title}: {exc}", file=sys.stderr)
    return ""


def select_all(wd: dict[str, list[dict]]) -> dict[str, dict]:
    selected: dict[str, dict] = {}
    for category, candidates in core.CANDIDATES.items():
        failures: list[str] = []
        for scientific in candidates:
            for wd_item in wd.get(scientific, []):
                try:
                    gbif = core.resolve_gbif(scientific)
                    media = collect_media(scientific, category)
                    selected[category] = {
                        "categoryId": category,
                        "scientificName": scientific,
                        "vietnameseName": wd_item["viTitle"],
                        "wikidata": wd_item,
                        "gbif": gbif,
                        "media": media,
                        "wikiExtract": viwiki_extract(wd_item["viTitle"]),
                    }
                    print(f"[canary] PASS {category}: {wd_item['viTitle']} / {scientific} · {len(media)} ảnh")
                    break
                except Exception as exc:
                    failures.append(f"{scientific}: {exc}")
                    print(f"[canary] reject {category}/{scientific}: {exc}", file=sys.stderr)
            if category in selected:
                break
        if category not in selected:
            raise RuntimeError(f"{category}: không candidate nào vượt gate | " + " | ".join(failures))
    return selected


def source_rows(item: dict) -> list[dict]:
    now = int(time.time() * 1000)
    scientific = item["scientificName"]
    wd = item["wikidata"]
    gbif = item["gbif"]
    first_media = item["media"][0]
    rows = [
        {"sourceKey": f"viwiki-name-{wd['qid'].lower()}", "title": f"Wikipedia tiếng Việt: {item['vietnameseName']}", "publisher": "Wikimedia Foundation", "uri": wd["viWikiUri"], "checkedAt": now},
        {"sourceKey": f"wikidata-{wd['qid'].lower()}", "title": f"Wikidata taxon linkage: {scientific}", "publisher": "Wikimedia Foundation", "uri": wd["wikidataUri"], "checkedAt": now},
        {"sourceKey": f"gbif-taxon-{gbif['key']}", "title": f"GBIF Backbone Taxonomy: {scientific}", "publisher": "GBIF Secretariat", "uri": gbif["taxonUri"], "checkedAt": now},
        {"sourceKey": f"gbif-vn-{gbif['key']}", "title": f"GBIF occurrences in Viet Nam: {scientific}", "publisher": "GBIF Secretariat", "uri": gbif["vnUri"], "checkedAt": now},
        {"sourceKey": f"commons-set-{core.slugify(scientific)}", "title": f"Wikimedia Commons diagnostic media set: {scientific}", "publisher": "Wikimedia Commons", "uri": first_media["sourceUri"], "checkedAt": now},
    ]
    if item["categoryId"] == "medicinal-plants":
        rows.append({"sourceKey": "moh-traditional-medicine-listing", "title": "Danh mục dược liệu theo nguồn Bộ Y tế Việt Nam", "publisher": "Bộ Y tế Việt Nam", "uri": core.MOH_URI, "checkedAt": now})
    if item["categoryId"] == "danger":
        rows.append({"sourceKey": "who-dengue-aedes-safety", "title": "Dengue and severe dengue — vector and health-risk evidence", "publisher": "World Health Organization", "uri": core.WHO_DENGUE_URI, "checkedAt": now})
    return rows


def build_record(item: dict) -> tuple[dict, dict]:
    category = item["categoryId"]
    scientific = item["scientificName"]
    slug = core.slugify(scientific)
    policy = CATEGORY_POLICY[category]
    sources = source_rows(item)
    high_risk = category == "danger"
    record_id = f"canary-{category}-{slug}"
    media_rows: list[dict] = []
    audit_media: list[dict] = []

    for index, media in enumerate(item["media"], start=1):
        role = media["viewRole"]
        name = f"{category}-{slug}-{index:02d}-{role.casefold().replace('_', '-')}{core.ext_for(media['mime'])}"
        path = core.MEDIA / name
        path.write_bytes(media["bytes"])
        digest = media["sha256"]
        media_rows.append(
            {
                "mediaId": f"canary-{category}-{slug}-{index:02d}",
                "sourceUri": media["sourceUri"],
                "downloadUri": core.RAW_ROOT + "SurvivalLibraryVN/data/media/canary/" + quote(name),
                "verified": True,
                "angleLabel": media["angleLabel"],
                "checksum": digest,
                "license": media["license"],
                "creator": media["creator"],
                "rightsHolder": "",
                "mimeType": media["mime"],
                "viewRole": role,
                "lifeStage": media["lifeStage"],
                "isPrimary": index == 1,
                "diagnostic": True,
            }
        )
        audit_media.append({"viewRole": role, "file": media["fileTitle"], "license": media["license"], "bytes": len(media["bytes"]), "sha256": digest})

    required = list(policy["required"])
    recognition_roles = ", ".join(ROLE_LABELS[role] for role in required)
    source_intro = item["wikiExtract"]
    identification_summary = (
        f"Đối chiếu nhận dạng theo nhiều góc/bộ phận: {recognition_roles}. "
        "Không kết luận chỉ từ một ảnh; cần so sánh đồng thời các ảnh có nhãn bên dưới."
    )
    summary = source_intro if source_intro else f"Hồ sơ nhận dạng {item['vietnameseName']} ({scientific}) đã đối chiếu tên, taxonomy, hiện diện tại Việt Nam và bộ ảnh nguồn mở."
    key_features = [f"Ưu tiên kiểm tra: {ROLE_LABELS[role]}" for role in required]

    record = {
        "id": record_id,
        "vietnameseName": item["vietnameseName"],
        "scientificName": scientific,
        "categoryId": category,
        "usageLevel": "CHUA_PHAN_LOAI",
        "verificationState": "DA_PHAT_HANH",
        "summary": summary,
        "identificationSummary": identification_summary,
        "keyFeatures": key_features,
        "confusableWith": [],
        "requiredViewRoles": required,
        "primaryViewRole": policy["primary"],
        "qualityProfile": QUALITY_PROFILE,
        "highRisk": high_risk,
        "sourceCount": len(sources),
        "published": True,
        "vietnamRelevant": True,
        "verifiedVietnameseName": True,
        "verifiedIdentitySource": True,
        "verifiedMedia": True,
        "hasUsageClaim": False,
        "verifiedUsageSource": False,
        "verifiedSafetySource": high_risk,
        "gbifTaxonKey": item["gbif"]["key"],
        "gbifVietnamOccurrenceCount": item["gbif"]["count"],
        "wikidataId": item["wikidata"]["qid"],
        "sources": sources,
        "media": media_rows,
    }
    audit = {
        "categoryId": category,
        "categoryLabel": core.CATEGORY_LABELS[category],
        "recordId": record_id,
        "vietnameseName": item["vietnameseName"],
        "scientificName": scientific,
        "gbifTaxonKey": item["gbif"]["key"],
        "gbifVietnamOccurrenceCount": item["gbif"]["count"],
        "wikidataId": item["wikidata"]["qid"],
        "requiredViewRoles": required,
        "primaryViewRole": policy["primary"],
        "mediaCount": len(media_rows),
        "media": audit_media,
        "sourceCount": len(sources),
    }
    return record, audit


def main() -> int:
    core.PACKAGES.mkdir(parents=True, exist_ok=True)
    if core.MEDIA.exists():
        shutil.rmtree(core.MEDIA)
    core.MEDIA.mkdir(parents=True, exist_ok=True)

    print("[canary] resolve Wikidata + viwiki ...")
    wd = core.wikidata_batch()
    selected = select_all(wd)

    records: dict[str, dict] = {}
    audits: list[dict] = []
    for category in core.CATEGORY_LABELS:
        record, audit = build_record(selected[category])
        records[category] = record
        audits.append(audit)

    index_rows: list[dict] = []
    for package_id, categories in core.PACKAGE_CATEGORIES.items():
        shard = {"packageId": package_id, "version": PACKAGE_VERSION, "schemaVersion": SCHEMA_VERSION, "records": [records[c] for c in categories]}
        raw = core.write_json(core.PACKAGES / f"{package_id}.json", shard)
        index_rows.append(
            {
                "packageId": package_id,
                "version": PACKAGE_VERSION,
                "schemaVersion": SCHEMA_VERSION,
                "recordCount": len(categories),
                "verifiedCount": len(categories),
                "sha256": core.sha256(raw),
                "packageUrl": core.RAW_ROOT + f"SurvivalLibraryVN/data/packages/{package_id}.json",
            }
        )

    core.write_json(core.INDEX, {"schemaVersion": 1, "channel": "beta-canary", "packages": index_rows})
    core.write_json(
        core.REPORT,
        {
            "schemaVersion": SCHEMA_VERSION,
            "purpose": "multi-view real-record identification quality canary",
            "recordCount": len(audits),
            "generatedAt": int(time.time() * 1000),
            "records": audits,
            "limitations": [
                "Bộ ảnh được chọn theo taxonomy, nguồn, license và vai trò ảnh; vẫn cần đánh giá trực quan trên thiết bị trước khi mở collector hàng loạt.",
                "Không phát hành claim ăn được, công dụng, liều dùng hoặc hướng dẫn y tế.",
                "usageLevel giữ CHUA_PHAN_LOAI cho tới khi có nguồn/rule đánh giá mức sử dụng tại Việt Nam.",
                "Dễ nhầm để trống nếu chưa có nguồn đối chiếu chuyên biệt; không tự suy diễn.",
            ],
        },
    )
    print(f"[canary] built {len(audits)} multi-view real records / {len(core.PACKAGE_CATEGORIES)} shards")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
