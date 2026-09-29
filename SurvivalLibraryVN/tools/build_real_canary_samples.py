#!/usr/bin/env python3
"""Build a small real-data canary library: exactly one record per app category.

The goal is quality review before scale-up. The script resolves each curated taxon
against GBIF, requires at least one GBIF occurrence in Viet Nam, resolves a
Vietnamese label and P18 image from Wikidata, verifies an open Wikimedia Commons
license, downloads a compact image copy, then emits schema-v2 immutable shards.

No food/medicine/use claim is published by this canary. The dangerous sample also
carries a WHO safety source. Medicinal-plant categorisation is backed by the
Vietnamese Ministry of Health listing already used by this repository.
"""

from __future__ import annotations

import hashlib
import html
import json
import mimetypes
import re
import shutil
import sys
import time
from pathlib import Path
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
PACKAGES = DATA / "packages"
MEDIA = DATA / "media" / "canary"
REPORT = DATA / "canary-selection.json"
INDEX = DATA / "update-index.json"
RAW_ROOT = "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/"
USER_AGENT = "SurvivalLibraryVN-CanaryBuilder/1.0 (FieldIntelligence; quality review)"
TIMEOUT = 30
THUMB_WIDTH = 720
MAX_IMAGE_BYTES = 2 * 1024 * 1024

CATEGORY_LABELS = {
    "vegetables": "Rau",
    "roots": "Củ / thân rễ",
    "fruit-crops": "Cây ăn quả",
    "flowers": "Hoa",
    "timber-trees": "Cây gỗ",
    "mushrooms": "Nấm",
    "freshwater-fish": "Cá nước ngọt",
    "marine-life": "Cá / thủy sản biển",
    "insects": "Côn trùng",
    "animals": "Động vật",
    "medicinal-plants": "Cây thuốc",
    "danger": "Nguy hiểm",
}

# Candidate fallbacks are intentional: a category is published only when one
# candidate passes ALL live source, Viet Nam occurrence and media-license gates.
CANDIDATES = {
    "vegetables": ["Ipomoea aquatica", "Brassica rapa"],
    "roots": ["Colocasia esculenta", "Manihot esculenta", "Dioscorea alata"],
    "fruit-crops": ["Mangifera indica", "Psidium guajava", "Musa acuminata"],
    "flowers": ["Nelumbo nucifera", "Jasminum sambac", "Hibiscus rosa-sinensis"],
    "timber-trees": ["Hopea odorata", "Dipterocarpus alatus", "Tectona grandis"],
    "mushrooms": ["Volvariella volvacea", "Pleurotus pulmonarius"],
    "freshwater-fish": ["Anabas testudineus", "Channa striata", "Clarias macrocephalus"],
    "marine-life": ["Penaeus monodon", "Portunus pelagicus", "Scomberomorus commerson"],
    "insects": ["Apis cerana", "Oecophylla smaragdina"],
    "animals": ["Sus scrofa", "Macaca fascicularis", "Varanus salvator"],
    # All medicinal fallbacks below are present in data/scientific/official_medicinal_taxa.json.
    "medicinal-plants": ["Zingiber officinale", "Curcuma longa", "Achyranthes aspera"],
    "danger": ["Aedes aegypti"],
}

PACKAGE_CATEGORIES = {
    "plants-core-s001": ["vegetables", "roots", "fruit-crops", "flowers", "timber-trees", "medicinal-plants"],
    "mushrooms-core-s001": ["mushrooms"],
    "aquatic-core-s001": ["freshwater-fish", "marine-life"],
    "fauna-core-s001": ["insects", "animals", "danger"],
}

MOH_URI = "https://emohbackup.moh.gov.vn/publish/attach/getfile/412855"
WHO_DENGUE_URI = "https://www.who.int/news-room/fact-sheets/detail/dengue-and-severe-dengue"


def request_bytes(url: str, max_bytes: int | None = None) -> tuple[bytes, str]:
    req = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
    with urlopen(req, timeout=TIMEOUT) as response:
        content_type = response.headers.get_content_type()
        declared = response.headers.get("Content-Length")
        if max_bytes and declared and int(declared) > max_bytes:
            raise RuntimeError(f"download quá lớn: {declared} bytes")
        data = response.read((max_bytes + 1) if max_bytes else -1)
        if max_bytes and len(data) > max_bytes:
            raise RuntimeError(f"download vượt {max_bytes} bytes")
        return data, content_type


def request_json(base: str, params: dict[str, object]) -> dict:
    raw, _ = request_bytes(base + "?" + urlencode(params, doseq=True), 4 * 1024 * 1024)
    value = json.loads(raw.decode("utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("API response không phải object")
    return value


def normalize_name(value: str) -> str:
    return " ".join(value.replace("×", "x").split()).casefold()


def strip_html(value: str) -> str:
    clean = re.sub(r"<[^>]+>", " ", value or "")
    return " ".join(html.unescape(clean).split())


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def slugify(value: str) -> str:
    value = value.casefold().replace(" ", "-")
    return re.sub(r"[^a-z0-9-]+", "", value)


def resolve_wikidata(scientific_name: str) -> dict:
    search = request_json(
        "https://www.wikidata.org/w/api.php",
        {
            "action": "wbsearchentities",
            "search": scientific_name,
            "language": "en",
            "type": "item",
            "limit": 10,
            "format": "json",
        },
    )
    for hit in search.get("search", []):
        qid = hit.get("id")
        if not qid:
            continue
        entity_resp = request_json(
            "https://www.wikidata.org/w/api.php",
            {
                "action": "wbgetentities",
                "ids": qid,
                "props": "labels|claims",
                "languages": "vi|en",
                "format": "json",
            },
        )
        entity = entity_resp.get("entities", {}).get(qid, {})
        claims = entity.get("claims", {})
        p225 = claims.get("P225", [])
        if not p225:
            continue
        try:
            wd_scientific = p225[0]["mainsnak"]["datavalue"]["value"]
        except Exception:
            continue
        if normalize_name(str(wd_scientific)) != normalize_name(scientific_name):
            continue
        vi_label = entity.get("labels", {}).get("vi", {}).get("value", "").strip()
        if not vi_label:
            raise RuntimeError(f"Wikidata {qid} thiếu nhãn tiếng Việt")
        p18 = claims.get("P18", [])
        if not p18:
            raise RuntimeError(f"Wikidata {qid} thiếu P18")
        image_title = p18[0].get("mainsnak", {}).get("datavalue", {}).get("value", "")
        if not image_title:
            raise RuntimeError(f"Wikidata {qid} P18 rỗng")
        return {
            "qid": qid,
            "vietnameseName": vi_label,
            "imageTitle": image_title,
            "uri": f"https://www.wikidata.org/wiki/{qid}",
        }
    raise RuntimeError("không tìm thấy Wikidata taxon khớp P225")


def resolve_gbif(scientific_name: str) -> dict:
    match = request_json(
        "https://api.gbif.org/v1/species/match",
        {"name": scientific_name},
    )
    if match.get("matchType") == "NONE":
        raise RuntimeError("GBIF không match được taxon")
    canonical = str(match.get("canonicalName") or "")
    if normalize_name(canonical) != normalize_name(scientific_name):
        raise RuntimeError(f"GBIF canonical lệch: {canonical!r}")
    key = match.get("usageKey") or match.get("speciesKey")
    if not isinstance(key, int):
        raise RuntimeError("GBIF thiếu usageKey/speciesKey")
    occurrences = request_json(
        "https://api.gbif.org/v1/occurrence/search",
        {"taxon_key": key, "country": "VN", "limit": 0},
    )
    count = occurrences.get("count")
    if not isinstance(count, int) or count <= 0:
        raise RuntimeError("GBIF chưa có occurrence Việt Nam cho taxon này")
    return {
        "key": key,
        "occurrenceCount": count,
        "taxonUri": f"https://www.gbif.org/species/{key}",
        "vietnamOccurrenceUri": f"https://www.gbif.org/occurrence/search?country=VN&taxon_key={key}",
    }


def resolve_commons(image_title: str) -> dict:
    title = image_title if image_title.startswith("File:") else "File:" + image_title
    result = request_json(
        "https://commons.wikimedia.org/w/api.php",
        {
            "action": "query",
            "titles": title,
            "prop": "imageinfo",
            "iiprop": "url|mime|extmetadata",
            "iiurlwidth": THUMB_WIDTH,
            "format": "json",
        },
    )
    pages = result.get("query", {}).get("pages", {})
    if not pages:
        raise RuntimeError("Commons không trả về file")
    page = next(iter(pages.values()))
    info_values = page.get("imageinfo", [])
    if not info_values:
        raise RuntimeError("Commons thiếu imageinfo")
    info = info_values[0]
    original_mime = str(info.get("mime") or "")
    if original_mime not in {"image/jpeg", "image/png", "image/webp"}:
        raise RuntimeError(f"P18 không phải ảnh raster phù hợp: {original_mime}")
    extmeta = info.get("extmetadata", {})
    license_name = str(extmeta.get("LicenseShortName", {}).get("value") or "").strip()
    license_upper = license_name.upper()
    if not ("CC BY" in license_upper or "CC0" in license_upper or "PUBLIC DOMAIN" in license_upper):
        raise RuntimeError(f"license chưa nằm trong allow-list: {license_name!r}")
    artist = strip_html(str(extmeta.get("Artist", {}).get("value") or ""))
    credit = strip_html(str(extmeta.get("Credit", {}).get("value") or ""))
    rights_holder = strip_html(str(extmeta.get("Copyrighted", {}).get("value") or ""))
    creator = artist or credit or rights_holder or "Wikimedia Commons contributor"
    download = str(info.get("thumburl") or info.get("url") or "")
    if not download.startswith("https://"):
        raise RuntimeError("Commons thiếu HTTPS image URL")
    source_page = str(info.get("descriptionurl") or f"https://commons.wikimedia.org/wiki/{quote(title.replace(' ', '_'), safe=':/')}")
    data, response_mime = request_bytes(download, MAX_IMAGE_BYTES)
    mime = response_mime if response_mime.startswith("image/") else original_mime
    if mime not in {"image/jpeg", "image/png", "image/webp"}:
        raise RuntimeError(f"thumbnail trả mime không hỗ trợ: {mime}")
    if len(data) < 8 * 1024:
        raise RuntimeError(f"ảnh quá nhỏ để đánh giá: {len(data)} bytes")
    return {
        "bytes": data,
        "mime": mime,
        "license": license_name,
        "creator": creator,
        "sourceUri": source_page,
        "originalDownloadUri": download,
        "fileTitle": title,
    }


def extension_for_mime(mime: str) -> str:
    return {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}.get(mime, mimetypes.guess_extension(mime) or ".img")


def select_candidate(category: str) -> dict:
    errors: list[str] = []
    for scientific_name in CANDIDATES[category]:
        try:
            wd = resolve_wikidata(scientific_name)
            gbif = resolve_gbif(scientific_name)
            media = resolve_commons(wd["imageTitle"])
            return {
                "categoryId": category,
                "scientificName": scientific_name,
                "wikidata": wd,
                "gbif": gbif,
                "media": media,
            }
        except Exception as exc:
            errors.append(f"{scientific_name}: {exc}")
            print(f"[canary] {category}: reject {scientific_name}: {exc}", file=sys.stderr)
    raise RuntimeError(f"{category}: không candidate nào vượt gate | " + " | ".join(errors))


def build_sources(selected: dict) -> list[dict]:
    scientific = selected["scientificName"]
    wd = selected["wikidata"]
    gbif = selected["gbif"]
    media = selected["media"]
    now = int(time.time() * 1000)
    sources = [
        {
            "sourceKey": f"wikidata-{wd['qid'].lower()}",
            "title": f"Wikidata: {scientific} — nhãn tiếng Việt và ảnh đại diện",
            "publisher": "Wikimedia Foundation",
            "uri": wd["uri"],
            "checkedAt": now,
        },
        {
            "sourceKey": f"gbif-taxon-{gbif['key']}",
            "title": f"GBIF Backbone Taxonomy: {scientific}",
            "publisher": "GBIF Secretariat",
            "uri": gbif["taxonUri"],
            "checkedAt": now,
        },
        {
            "sourceKey": f"gbif-vn-{gbif['key']}",
            "title": f"GBIF occurrences in Viet Nam: {scientific}",
            "publisher": "GBIF Secretariat",
            "uri": gbif["vietnamOccurrenceUri"],
            "checkedAt": now,
        },
        {
            "sourceKey": f"commons-{slugify(scientific)}",
            "title": f"Wikimedia Commons media: {media['fileTitle']}",
            "publisher": "Wikimedia Commons",
            "uri": media["sourceUri"],
            "checkedAt": now,
        },
    ]
    if selected["categoryId"] == "medicinal-plants":
        sources.append(
            {
                "sourceKey": "moh-traditional-medicine-listing",
                "title": "Danh mục dược liệu công bố theo nguồn Bộ Y tế Việt Nam",
                "publisher": "Bộ Y tế Việt Nam",
                "uri": MOH_URI,
                "checkedAt": now,
            }
        )
    if selected["categoryId"] == "danger":
        sources.append(
            {
                "sourceKey": "who-dengue-aedes-safety",
                "title": "Dengue and severe dengue — vector and health-risk evidence",
                "publisher": "World Health Organization",
                "uri": WHO_DENGUE_URI,
                "checkedAt": now,
            }
        )
    return sources


def build_record(selected: dict) -> tuple[dict, dict]:
    category = selected["categoryId"]
    scientific = selected["scientificName"]
    vi_name = selected["wikidata"]["vietnameseName"]
    slug = slugify(scientific)
    media = selected["media"]
    ext = extension_for_mime(media["mime"])
    media_name = f"{category}-{slug}{ext}"
    media_path = MEDIA / media_name
    media_path.write_bytes(media["bytes"])
    digest = sha256(media["bytes"])
    raw_url = RAW_ROOT + "SurvivalLibraryVN/data/media/canary/" + quote(media_name)
    sources = build_sources(selected)
    high_risk = category == "danger"
    record_id = f"canary-{category}-{slug}"
    record = {
        "id": record_id,
        "vietnameseName": vi_name,
        "scientificName": scientific,
        "categoryId": category,
        "usageLevel": "CHUA_PHAN_LOAI",
        "verificationState": "DA_PHAT_HANH",
        "summary": (
            f"Hồ sơ dữ liệu thật canary. Tên khoa học: {scientific}. "
            "Định danh, tên Việt, hiện diện tại Việt Nam và ảnh đã được đối chiếu nguồn; "
            "chưa phát hành công dụng/cách dùng."
        ),
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
        "gbifTaxonKey": selected["gbif"]["key"],
        "gbifVietnamOccurrenceCount": selected["gbif"]["occurrenceCount"],
        "wikidataId": selected["wikidata"]["qid"],
        "sources": sources,
        "media": [
            {
                "mediaId": f"canary-{category}-{slug}-01",
                "sourceUri": media["sourceUri"],
                "downloadUri": raw_url,
                "verified": True,
                "angleLabel": "Ảnh đại diện nhận dạng — nguồn mở đã kiểm tra license",
                "checksum": digest,
                "license": media["license"],
                "creator": media["creator"],
                "rightsHolder": "",
                "mimeType": media["mime"],
            }
        ],
    }
    audit = {
        "categoryId": category,
        "categoryLabel": CATEGORY_LABELS[category],
        "recordId": record_id,
        "vietnameseName": vi_name,
        "scientificName": scientific,
        "gbifTaxonKey": selected["gbif"]["key"],
        "gbifVietnamOccurrenceCount": selected["gbif"]["occurrenceCount"],
        "wikidataId": selected["wikidata"]["qid"],
        "commonsFile": media["fileTitle"],
        "commonsLicense": media["license"],
        "mediaBytes": len(media["bytes"]),
        "mediaSha256": digest,
        "sourceCount": len(sources),
    }
    return record, audit


def write_json(path: Path, value: object) -> bytes:
    raw = (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n").encode("utf-8")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)
    return raw


def main() -> int:
    PACKAGES.mkdir(parents=True, exist_ok=True)
    if MEDIA.exists():
        shutil.rmtree(MEDIA)
    MEDIA.mkdir(parents=True, exist_ok=True)

    selected_by_category: dict[str, dict] = {}
    audits: list[dict] = []
    records_by_category: dict[str, dict] = {}
    for category in CATEGORY_LABELS:
        print(f"[canary] resolving {category} ...")
        selected = select_candidate(category)
        selected_by_category[category] = selected
        record, audit = build_record(selected)
        records_by_category[category] = record
        audits.append(audit)
        print(
            f"[canary] PASS {category}: {record['vietnameseName']} / {record['scientificName']} "
            f"(GBIF VN={audit['gbifVietnamOccurrenceCount']}, {audit['commonsLicense']})"
        )

    index_packages: list[dict] = []
    for package_id, categories in PACKAGE_CATEGORIES.items():
        records = [records_by_category[c] for c in categories]
        package = {
            "packageId": package_id,
            "version": 1,
            "schemaVersion": 2,
            "records": records,
        }
        package_path = PACKAGES / f"{package_id}.json"
        package_raw = write_json(package_path, package)
        digest = sha256(package_raw)
        index_packages.append(
            {
                "packageId": package_id,
                "version": 1,
                "schemaVersion": 2,
                "recordCount": len(records),
                "verifiedCount": len(records),
                "sha256": digest,
                "packageUrl": RAW_ROOT + f"SurvivalLibraryVN/data/packages/{package_id}.json",
            }
        )

    index = {"schemaVersion": 1, "channel": "beta-canary", "packages": index_packages}
    write_json(INDEX, index)
    write_json(
        REPORT,
        {
            "schemaVersion": 1,
            "purpose": "one-real-record-per-category quality canary",
            "recordCount": len(audits),
            "generatedAt": int(time.time() * 1000),
            "records": audits,
            "limitations": [
                "Mỗi hồ sơ canary hiện chỉ có 1 ảnh đại diện để đánh giá pipeline.",
                "Không có claim ăn được, công dụng, liều dùng hoặc hướng dẫn y tế trong canary này.",
                "usageLevel để CHUA_PHAN_LOAI cho tới khi có rule/nguồn đánh giá mức sử dụng tại Việt Nam.",
            ],
        },
    )
    print(f"[canary] built {len(audits)} real records across {len(PACKAGE_CATEGORIES)} shards")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
