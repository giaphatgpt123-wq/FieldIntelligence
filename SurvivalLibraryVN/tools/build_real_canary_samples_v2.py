#!/usr/bin/env python3
"""Build exactly one source-backed real record per SurvivalLibraryVN category.

This v2 builder minimizes upstream API traffic by resolving Wikidata/viwiki and
Commons metadata in batches, then checks GBIF taxonomy + Viet Nam occurrence per
candidate. It downloads one compact, openly licensed real image per record and
emits schema-v2 immutable shards plus a human-auditable selection report.

No eating, medicinal efficacy, dose or treatment claim is published here.
"""

from __future__ import annotations

import hashlib
import html
import json
import re
import shutil
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, unquote, urlencode, urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
PACKAGES = DATA / "packages"
MEDIA = DATA / "media" / "canary"
REPORT = DATA / "canary-selection.json"
INDEX = DATA / "update-index.json"
RAW_ROOT = "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/"
USER_AGENT = "SurvivalLibraryVN-CanaryBuilder/2.0 (FieldIntelligence; source-backed quality review)"
TIMEOUT = 45
MAX_JSON_BYTES = 6 * 1024 * 1024
MAX_IMAGE_BYTES = 2 * 1024 * 1024
THUMB_WIDTH = 720

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
    # All three are present in data/scientific/official_medicinal_taxa.json.
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


def fetch_bytes(url: str, *, accept: str = "*/*", max_bytes: int | None = None) -> tuple[bytes, str]:
    last: Exception | None = None
    for attempt in range(6):
        try:
            request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": accept})
            with urlopen(request, timeout=TIMEOUT) as response:
                declared = response.headers.get("Content-Length")
                if max_bytes and declared and int(declared) > max_bytes:
                    raise RuntimeError(f"download quá lớn: {declared} bytes")
                data = response.read(max_bytes + 1 if max_bytes else -1)
                if max_bytes and len(data) > max_bytes:
                    raise RuntimeError(f"download vượt {max_bytes} bytes")
                return data, response.headers.get_content_type()
        except HTTPError as exc:
            last = exc
            if exc.code not in {429, 500, 502, 503, 504}:
                raise
            retry_after = exc.headers.get("Retry-After") if exc.headers else None
            delay = int(retry_after) if retry_after and retry_after.isdigit() else min(2 ** attempt, 30)
            print(f"[canary] HTTP {exc.code}; retry sau {delay}s: {urlparse(url).netloc}", file=sys.stderr)
            time.sleep(delay)
        except URLError as exc:
            last = exc
            time.sleep(min(2 ** attempt, 20))
    raise RuntimeError(f"không tải được sau retry: {last}")


def fetch_json(base: str, params: dict[str, object], accept: str = "application/json") -> dict:
    raw, _ = fetch_bytes(base + "?" + urlencode(params, doseq=True), accept=accept, max_bytes=MAX_JSON_BYTES)
    value = json.loads(raw.decode("utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("API response không phải object")
    return value


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def slugify(value: str) -> str:
    return re.sub(r"[^a-z0-9-]+", "", value.casefold().replace(" ", "-"))


def clean_html(value: str) -> str:
    return " ".join(html.unescape(re.sub(r"<[^>]+>", " ", value or "")).split())


def normalize_file(value: str) -> str:
    value = value.removeprefix("File:").replace("_", " ").strip()
    return " ".join(value.split()).casefold()


def image_title_from_url(url: str) -> str:
    path = unquote(urlparse(url).path)
    marker = "/Special:FilePath/"
    if marker in path:
        return path.split(marker, 1)[1]
    return path.rsplit("/", 1)[-1]


def wikidata_batch() -> dict[str, list[dict]]:
    names = sorted({name for values in CANDIDATES.values() for name in values})
    quoted_values = " ".join(json.dumps(name, ensure_ascii=False) for name in names)
    query = f"""
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX schema: <http://schema.org/>
SELECT ?taxon ?scientific ?image ?viTitle WHERE {{
  VALUES ?scientific {{ {quoted_values} }}
  ?taxon wdt:P225 ?scientific ;
         wdt:P18 ?image .
  ?article schema:about ?taxon ;
           schema:isPartOf <https://vi.wikipedia.org/> ;
           schema:name ?viTitle .
}}
""".strip()
    data = fetch_json(
        "https://query.wikidata.org/sparql",
        {"query": query, "format": "json"},
        accept="application/sparql-results+json",
    )
    result: dict[str, list[dict]] = {name: [] for name in names}
    for row in data.get("results", {}).get("bindings", []):
        sci = row.get("scientific", {}).get("value", "")
        taxon_url = row.get("taxon", {}).get("value", "")
        image_url = row.get("image", {}).get("value", "")
        vi_title = row.get("viTitle", {}).get("value", "").strip()
        if sci not in result or not taxon_url or not image_url or not vi_title:
            continue
        result[sci].append(
            {
                "qid": taxon_url.rsplit("/", 1)[-1],
                "viTitle": vi_title,
                "viWikiUri": "https://vi.wikipedia.org/wiki/" + quote(vi_title.replace(" ", "_"), safe="()_-"),
                "wikidataUri": taxon_url.replace("http://", "https://"),
                "imageTitle": image_title_from_url(image_url),
            }
        )
    return result


def commons_batch(wd: dict[str, list[dict]]) -> dict[str, dict]:
    titles = sorted({item["imageTitle"] for values in wd.values() for item in values if item.get("imageTitle")})
    if not titles:
        raise RuntimeError("Wikidata không trả ảnh P18 cho candidate nào")
    query_titles = "|".join("File:" + title for title in titles)
    data = fetch_json(
        "https://commons.wikimedia.org/w/api.php",
        {
            "action": "query",
            "format": "json",
            "prop": "imageinfo",
            "iiprop": "url|mime|extmetadata",
            "iiurlwidth": THUMB_WIDTH,
            "titles": query_titles,
            "redirects": 1,
        },
    )
    out: dict[str, dict] = {}
    for page in data.get("query", {}).get("pages", {}).values():
        page_title = str(page.get("title") or "")
        info_values = page.get("imageinfo") or []
        if not page_title or not info_values:
            continue
        info = info_values[0]
        meta = info.get("extmetadata", {})
        license_name = str(meta.get("LicenseShortName", {}).get("value") or "").strip()
        allowed = any(token in license_name.upper() for token in ("CC BY", "CC0", "PUBLIC DOMAIN"))
        mime = str(info.get("mime") or "")
        if not allowed or mime not in {"image/jpeg", "image/png", "image/webp"}:
            continue
        creator = (
            clean_html(str(meta.get("Artist", {}).get("value") or ""))
            or clean_html(str(meta.get("Credit", {}).get("value") or ""))
            or "Wikimedia Commons contributor"
        )
        download = str(info.get("thumburl") or info.get("url") or "")
        if not download.startswith("https://"):
            continue
        out[normalize_file(page_title)] = {
            "fileTitle": page_title.removeprefix("File:"),
            "sourceUri": str(info.get("descriptionurl") or ""),
            "download": download,
            "mime": mime,
            "license": license_name,
            "creator": creator,
        }
    return out


def resolve_gbif(scientific_name: str) -> dict:
    match = fetch_json("https://api.gbif.org/v1/species/match", {"name": scientific_name})
    if match.get("matchType") == "NONE":
        raise RuntimeError("GBIF không match taxon")
    canonical = " ".join(str(match.get("canonicalName") or "").split()).casefold()
    if canonical != scientific_name.casefold():
        raise RuntimeError(f"GBIF canonical lệch: {match.get('canonicalName')!r}")
    key = match.get("usageKey") or match.get("speciesKey")
    if not isinstance(key, int):
        raise RuntimeError("GBIF thiếu taxon key")
    occurrence = fetch_json(
        "https://api.gbif.org/v1/occurrence/search",
        {"taxon_key": key, "country": "VN", "limit": 0},
    )
    count = occurrence.get("count")
    if not isinstance(count, int) or count <= 0:
        raise RuntimeError("GBIF chưa có occurrence Việt Nam")
    return {
        "key": key,
        "count": count,
        "taxonUri": f"https://www.gbif.org/species/{key}",
        "vnUri": f"https://www.gbif.org/occurrence/search?country=VN&taxon_key={key}",
    }


def select_all(wd: dict[str, list[dict]], commons: dict[str, dict]) -> dict[str, dict]:
    selected: dict[str, dict] = {}
    for category, candidates in CANDIDATES.items():
        failures: list[str] = []
        for scientific in candidates:
            for wd_item in wd.get(scientific, []):
                media = commons.get(normalize_file(wd_item["imageTitle"]))
                if not media:
                    failures.append(f"{scientific}: P18 thiếu media open-license raster")
                    continue
                try:
                    gbif = resolve_gbif(scientific)
                    image_bytes, response_mime = fetch_bytes(media["download"], max_bytes=MAX_IMAGE_BYTES)
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


def source_rows(item: dict) -> list[dict]:
    now = int(time.time() * 1000)
    scientific = item["scientificName"]
    wd = item["wikidata"]
    gbif = item["gbif"]
    media = item["media"]
    rows = [
        {
            "sourceKey": f"viwiki-name-{wd['qid'].lower()}",
            "title": f"Wikipedia tiếng Việt: {item['vietnameseName']}",
            "publisher": "Wikimedia Foundation",
            "uri": wd["viWikiUri"],
            "checkedAt": now,
        },
        {
            "sourceKey": f"wikidata-{wd['qid'].lower()}",
            "title": f"Wikidata taxon linkage: {scientific}",
            "publisher": "Wikimedia Foundation",
            "uri": wd["wikidataUri"],
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
            "uri": gbif["vnUri"],
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
    if item["categoryId"] == "medicinal-plants":
        rows.append(
            {
                "sourceKey": "moh-traditional-medicine-listing",
                "title": "Danh mục dược liệu theo nguồn Bộ Y tế Việt Nam",
                "publisher": "Bộ Y tế Việt Nam",
                "uri": MOH_URI,
                "checkedAt": now,
            }
        )
    if item["categoryId"] == "danger":
        rows.append(
            {
                "sourceKey": "who-dengue-aedes-safety",
                "title": "Dengue and severe dengue — vector and health-risk evidence",
                "publisher": "World Health Organization",
                "uri": WHO_DENGUE_URI,
                "checkedAt": now,
            }
        )
    return rows


def ext_for(mime: str) -> str:
    return {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}[mime]


def build_record(item: dict) -> tuple[dict, dict]:
    category = item["categoryId"]
    scientific = item["scientificName"]
    slug = slugify(scientific)
    media = item["media"]
    name = f"{category}-{slug}{ext_for(media['mime'])}"
    media_path = MEDIA / name
    media_path.write_bytes(media["bytes"])
    digest = sha256(media["bytes"])
    sources = source_rows(item)
    high_risk = category == "danger"
    record_id = f"canary-{category}-{slug}"
    record = {
        "id": record_id,
        "vietnameseName": item["vietnameseName"],
        "scientificName": scientific,
        "categoryId": category,
        "usageLevel": "CHUA_PHAN_LOAI",
        "verificationState": "DA_PHAT_HANH",
        "summary": (
            f"Hồ sơ dữ liệu thật canary. Tên khoa học: {scientific}. "
            "Định danh, tên Việt, hiện diện tại Việt Nam và ảnh đã đối chiếu nguồn; "
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
        "gbifTaxonKey": item["gbif"]["key"],
        "gbifVietnamOccurrenceCount": item["gbif"]["count"],
        "wikidataId": item["wikidata"]["qid"],
        "sources": sources,
        "media": [
            {
                "mediaId": f"canary-{category}-{slug}-01",
                "sourceUri": media["sourceUri"],
                "downloadUri": RAW_ROOT + "SurvivalLibraryVN/data/media/canary/" + quote(name),
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
        "vietnameseName": item["vietnameseName"],
        "scientificName": scientific,
        "gbifTaxonKey": item["gbif"]["key"],
        "gbifVietnamOccurrenceCount": item["gbif"]["count"],
        "wikidataId": item["wikidata"]["qid"],
        "commonsFile": media["fileTitle"],
        "commonsLicense": media["license"],
        "mediaBytes": len(media["bytes"]),
        "mediaSha256": digest,
        "sourceCount": len(sources),
    }
    return record, audit


def write_json(path: Path, obj: object) -> bytes:
    raw = (json.dumps(obj, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)
    return raw


def main() -> int:
    PACKAGES.mkdir(parents=True, exist_ok=True)
    if MEDIA.exists():
        shutil.rmtree(MEDIA)
    MEDIA.mkdir(parents=True, exist_ok=True)

    print("[canary] batch resolve Wikidata + viwiki ...")
    wd = wikidata_batch()
    print("[canary] batch resolve Commons licenses/media ...")
    commons = commons_batch(wd)
    selected = select_all(wd, commons)

    records: dict[str, dict] = {}
    audits: list[dict] = []
    for category in CATEGORY_LABELS:
        record, audit = build_record(selected[category])
        records[category] = record
        audits.append(audit)

    index_rows: list[dict] = []
    for package_id, categories in PACKAGE_CATEGORIES.items():
        shard = {
            "packageId": package_id,
            "version": 1,
            "schemaVersion": 2,
            "records": [records[c] for c in categories],
        }
        raw = write_json(PACKAGES / f"{package_id}.json", shard)
        index_rows.append(
            {
                "packageId": package_id,
                "version": 1,
                "schemaVersion": 2,
                "recordCount": len(categories),
                "verifiedCount": len(categories),
                "sha256": sha256(raw),
                "packageUrl": RAW_ROOT + f"SurvivalLibraryVN/data/packages/{package_id}.json",
            }
        )

    write_json(INDEX, {"schemaVersion": 1, "channel": "beta-canary", "packages": index_rows})
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
                "Không phát hành claim ăn được, công dụng, liều dùng hoặc hướng dẫn y tế.",
                "usageLevel giữ CHUA_PHAN_LOAI cho tới khi có rule/nguồn đánh giá mức sử dụng tại Việt Nam.",
            ],
        },
    )
    print(f"[canary] built {len(audits)} real records / {len(PACKAGE_CATEGORIES)} shards")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
