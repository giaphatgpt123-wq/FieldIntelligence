#!/usr/bin/env python3
"""Validate SurvivalLibraryVN data index, PUBLISHED shards and schema-v2 provenance.

The validator is independent from Android/Gradle so data-only updates can be
checked without rebuilding the APK. Schema v1 remains readable for backwards
compatibility. New production packages should use schema v2.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent
DATA_DIR = ROOT / "data"
INDEX_PATH = DATA_DIR / "update-index.json"
MAX_INDEX_BYTES = 512 * 1024
MAX_PACKAGE_BYTES = 8 * 1024 * 1024
MAX_MEDIA_BYTES = 20 * 1024 * 1024
MAX_RECORDS_PER_SHARD = 2_000
MAX_SOURCES_PER_RECORD = 32
MAX_MEDIA_PER_RECORD = 32
RAW_PREFIX = "/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/"
ALLOWED_HOST = "raw.githubusercontent.com"
SUPPORTED_PACKAGE_SCHEMAS = {1, 2}

BASE_CATEGORIES = {
    "plants-core": {"vegetables", "roots", "fruit-crops", "flowers", "timber-trees", "medicinal-plants"},
    "mushrooms-core": {"mushrooms"},
    "aquatic-core": {"freshwater-fish", "marine-life"},
    "fauna-core": {"insects", "animals", "danger"},
    "skills-core": set(),
}
SHARD_RE = re.compile(r"^(?P<base>[a-z0-9-]+)-s(?P<suffix>\d{3,6})$")
SHA_RE = re.compile(r"^[0-9a-fA-F]{64}$")
USAGE_LEVELS = {
    "THUONG_DUNG",
    "HAY_DUNG",
    "IT_DUNG",
    "HIEM_DUNG",
    "KHONG_CO_KHA_NANG_DUNG",
    "CHUA_PHAN_LOAI",
}
VERIFICATION_RANK = {
    "CHUA_CO": 0,
    "DA_THU_THAP": 1,
    "DA_DOI_CHIEU": 2,
    "DA_KIEM_CHUNG": 3,
    "DA_PHAT_HANH": 4,
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(message)


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"{path}: JSON không hợp lệ: {exc}")


def sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def is_https(value: object) -> bool:
    return isinstance(value, str) and value.lower().startswith("https://")


def base_package_id(package_id: str) -> str | None:
    if package_id in BASE_CATEGORIES:
        return package_id
    match = SHARD_RE.fullmatch(package_id)
    if match and match.group("base") in BASE_CATEGORIES:
        return match.group("base")
    return None


def local_path_from_raw_url(url: str, required_prefix: str) -> Path:
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != ALLOWED_HOST:
        fail(f"URL không thuộc raw.githubusercontent.com HTTPS: {url}")
    path = unquote(parsed.path)
    if not path.startswith(RAW_PREFIX):
        fail(f"URL phải trỏ đúng branch survival-library-vn: {url}")
    repo_relative = path[len(RAW_PREFIX):]
    if not repo_relative.startswith(required_prefix):
        fail(f"URL phải nằm dưới {required_prefix}: {url}")
    local = REPO_ROOT / repo_relative
    required_dir = (REPO_ROOT / required_prefix).resolve()
    try:
        local.resolve().relative_to(required_dir)
    except ValueError:
        fail(f"URL thoát khỏi thư mục được phép: {url}")
    if not local.is_file():
        fail(f"Không tìm thấy file cục bộ cho URL: {url}")
    return local


def require_bool(record: dict, key: str, record_id: str) -> bool:
    value = record.get(key)
    if not isinstance(value, bool):
        fail(f"{record_id}: {key} phải là boolean")
    return value


def validate_source(record_id: str, source: object) -> str:
    if not isinstance(source, dict):
        fail(f"{record_id}: source phải là object")
    source_key = source.get("sourceKey")
    if not isinstance(source_key, str) or not source_key.strip():
        fail(f"{record_id}: sourceKey trống")
    for key in ("title", "publisher"):
        value = source.get(key)
        if not isinstance(value, str) or not value.strip():
            fail(f"{record_id}: nguồn thiếu {key}")
    if not is_https(source.get("uri")):
        fail(f"{record_id}: URI nguồn phải dùng HTTPS")
    checked_at = source.get("checkedAt", 0)
    if not isinstance(checked_at, int) or isinstance(checked_at, bool) or checked_at < 0:
        fail(f"{record_id}: checkedAt không hợp lệ")
    return source_key


def validate_media(record_id: str, media: object) -> str:
    if not isinstance(media, dict):
        fail(f"{record_id}: media phải là object")
    media_id = media.get("mediaId")
    if not isinstance(media_id, str) or not media_id.strip():
        fail(f"{record_id}: mediaId trống")
    if not is_https(media.get("sourceUri")):
        fail(f"{record_id}: sourceUri media phải dùng HTTPS")
    download_uri = media.get("downloadUri")
    if not is_https(download_uri):
        fail(f"{record_id}: downloadUri media phải dùng HTTPS")
    if media.get("verified") is not True:
        fail(f"{record_id}: media PUBLISHED phải verified=true")
    angle = media.get("angleLabel")
    if not isinstance(angle, str) or not angle.strip():
        fail(f"{record_id}: media thiếu angleLabel")
    checksum = media.get("checksum")
    if not isinstance(checksum, str) or not SHA_RE.fullmatch(checksum):
        fail(f"{record_id}: media thiếu/sai SHA-256")
    license_name = media.get("license")
    if not isinstance(license_name, str) or not license_name.strip():
        fail(f"{record_id}: media thiếu license")
    creator = media.get("creator", "")
    rights_holder = media.get("rightsHolder", "")
    if not (isinstance(creator, str) and creator.strip()) and not (isinstance(rights_holder, str) and rights_holder.strip()):
        fail(f"{record_id}: media thiếu creator/rightsHolder")
    mime = media.get("mimeType", "image/webp")
    if not isinstance(mime, str) or not (mime.startswith("image/") or mime.startswith("video/")):
        fail(f"{record_id}: mimeType media không hợp lệ")

    media_path = local_path_from_raw_url(str(download_uri), "SurvivalLibraryVN/data/media/")
    raw = media_path.read_bytes()
    if len(raw) > MAX_MEDIA_BYTES:
        fail(f"{record_id}: media {media_id} vượt giới hạn {MAX_MEDIA_BYTES} bytes")
    actual = sha256_bytes(raw)
    if actual.lower() != checksum.lower():
        fail(f"{record_id}: SHA-256 media {media_id} không khớp file")
    return media_id


def validate_record(record: dict, package_id: str, allowed_categories: set[str], schema: int) -> bool:
    record_id = record.get("id")
    if not isinstance(record_id, str) or not record_id.strip() or record_id.startswith("demo-"):
        fail(f"{package_id}: ID hồ sơ không hợp lệ")

    name = record.get("vietnameseName")
    if not isinstance(name, str) or not name.strip():
        fail(f"{record_id}: thiếu tên tiếng Việt")

    category = record.get("categoryId")
    if not isinstance(category, str) or not category:
        fail(f"{record_id}: thiếu categoryId")
    if allowed_categories and category not in allowed_categories:
        fail(f"{record_id}: categoryId ngoài phạm vi package {package_id}")

    usage = record.get("usageLevel")
    if usage not in USAGE_LEVELS:
        fail(f"{record_id}: usageLevel không hợp lệ")

    verification = record.get("verificationState")
    if verification not in VERIFICATION_RANK:
        fail(f"{record_id}: verificationState không hợp lệ")
    verified = VERIFICATION_RANK[verification] >= VERIFICATION_RANK["DA_KIEM_CHUNG"]
    if not verified:
        fail(f"{record_id}: chưa đạt DA_KIEM_CHUNG")

    source_count = record.get("sourceCount")
    if not isinstance(source_count, int) or isinstance(source_count, bool) or source_count <= 0:
        fail(f"{record_id}: sourceCount phải > 0")

    published = require_bool(record, "published", record_id)
    vietnam_relevant = require_bool(record, "vietnamRelevant", record_id)
    verified_vn_name = require_bool(record, "verifiedVietnameseName", record_id)
    verified_identity = require_bool(record, "verifiedIdentitySource", record_id)
    verified_media = require_bool(record, "verifiedMedia", record_id)
    has_usage_claim = require_bool(record, "hasUsageClaim", record_id)
    verified_usage = require_bool(record, "verifiedUsageSource", record_id)
    high_risk = require_bool(record, "highRisk", record_id)
    verified_safety = require_bool(record, "verifiedSafetySource", record_id)

    blockers = []
    if not published:
        blockers.append("published=false")
    if not vietnam_relevant:
        blockers.append("chưa xác nhận liên quan Việt Nam")
    if not verified_vn_name:
        blockers.append("tên tiếng Việt chưa kiểm chứng")
    if not verified_identity:
        blockers.append("thiếu nguồn định danh")
    if not verified_media:
        blockers.append("ảnh/media chưa kiểm chứng")
    if has_usage_claim and not verified_usage:
        blockers.append("claim công dụng/cách dùng chưa có nguồn")
    if high_risk and not verified_safety:
        blockers.append("đối tượng nguy cơ cao thiếu nguồn an toàn")
    if blockers:
        fail(f"{record_id}: " + "; ".join(blockers))

    if schema >= 2:
        sources = record.get("sources")
        if not isinstance(sources, list) or not sources:
            fail(f"{record_id}: schema v2 phải có sources")
        if len(sources) != source_count:
            fail(f"{record_id}: sourceCount không khớp sources")
        if len(sources) > MAX_SOURCES_PER_RECORD:
            fail(f"{record_id}: quá nhiều nguồn")
        source_keys = [validate_source(record_id, source) for source in sources]
        if len(set(source_keys)) != len(source_keys):
            fail(f"{record_id}: sourceKey bị trùng")

        media_values = record.get("media")
        if not isinstance(media_values, list) or not media_values:
            fail(f"{record_id}: schema v2 verifiedMedia=true phải có media")
        if len(media_values) > MAX_MEDIA_PER_RECORD:
            fail(f"{record_id}: quá nhiều media")
        media_ids = [validate_media(record_id, media) for media in media_values]
        if len(set(media_ids)) != len(media_ids):
            fail(f"{record_id}: mediaId bị trùng trong hồ sơ")

    return verified


def validate_package(descriptor: dict, global_media_ids: set[str]) -> tuple[str, int]:
    required = {"packageId", "version", "schemaVersion", "recordCount", "verifiedCount", "sha256", "packageUrl"}
    missing = required - descriptor.keys()
    if missing:
        fail(f"Index package thiếu trường: {sorted(missing)}")

    package_id = descriptor["packageId"]
    if not isinstance(package_id, str):
        fail("packageId phải là chuỗi")
    base = base_package_id(package_id)
    if base is None:
        fail(f"packageId không được phép: {package_id}")

    version = descriptor["version"]
    schema = descriptor["schemaVersion"]
    record_count = descriptor["recordCount"]
    verified_count = descriptor["verifiedCount"]
    sha = descriptor["sha256"]
    url = descriptor["packageUrl"]

    if not isinstance(version, int) or isinstance(version, bool) or version <= 0:
        fail(f"{package_id}: version không hợp lệ")
    if schema not in SUPPORTED_PACKAGE_SCHEMAS:
        fail(f"{package_id}: schemaVersion không được hỗ trợ")
    if not isinstance(record_count, int) or isinstance(record_count, bool) or not (0 <= record_count <= MAX_RECORDS_PER_SHARD):
        fail(f"{package_id}: recordCount phải trong 0..{MAX_RECORDS_PER_SHARD}")
    if not isinstance(verified_count, int) or isinstance(verified_count, bool) or not (0 <= verified_count <= record_count):
        fail(f"{package_id}: verifiedCount không hợp lệ")
    if not isinstance(sha, str) or not SHA_RE.fullmatch(sha):
        fail(f"{package_id}: SHA-256 không hợp lệ")
    if not isinstance(url, str):
        fail(f"{package_id}: packageUrl phải là chuỗi")

    package_path = local_path_from_raw_url(url, "SurvivalLibraryVN/data/packages/")
    raw = package_path.read_bytes()
    if len(raw) > MAX_PACKAGE_BYTES:
        fail(f"{package_id}: file vượt {MAX_PACKAGE_BYTES} bytes")
    actual_sha = sha256_bytes(raw)
    if actual_sha.lower() != sha.lower():
        fail(f"{package_id}: SHA-256 index không khớp file ({actual_sha})")

    package = load_json(package_path)
    for key, expected in (("packageId", package_id), ("version", version), ("schemaVersion", schema)):
        if package.get(key) != expected:
            fail(f"{package_id}: {key} trong file không khớp index")

    records = package.get("records")
    if not isinstance(records, list):
        fail(f"{package_id}: records phải là mảng")
    if len(records) != record_count:
        fail(f"{package_id}: recordCount={record_count} nhưng file có {len(records)} hồ sơ")

    ids: set[str] = set()
    verified_actual = 0
    allowed_categories = BASE_CATEGORIES[base]
    for record in records:
        if not isinstance(record, dict):
            fail(f"{package_id}: record không phải object")
        record_id = record.get("id")
        if isinstance(record_id, str) and record_id in ids:
            fail(f"{package_id}: ID hồ sơ trùng: {record_id}")
        if isinstance(record_id, str):
            ids.add(record_id)
        if validate_record(record, package_id, allowed_categories, schema):
            verified_actual += 1
        if schema >= 2:
            for media in record.get("media", []):
                media_id = media.get("mediaId") if isinstance(media, dict) else None
                if isinstance(media_id, str):
                    if media_id in global_media_ids:
                        fail(f"mediaId trùng giữa các package: {media_id}")
                    global_media_ids.add(media_id)

    if verified_actual != verified_count:
        fail(f"{package_id}: verifiedCount={verified_count} nhưng thực tế={verified_actual}")
    return package_id, record_count


def main() -> int:
    try:
        if not INDEX_PATH.is_file():
            fail(f"Thiếu {INDEX_PATH}")
        if INDEX_PATH.stat().st_size > MAX_INDEX_BYTES:
            fail("update-index.json vượt giới hạn dung lượng")

        index = load_json(INDEX_PATH)
        if index.get("schemaVersion") != 1:
            fail("update-index.json chỉ hỗ trợ schemaVersion=1")
        packages = index.get("packages")
        if not isinstance(packages, list):
            fail("update-index.json: packages phải là mảng")

        package_ids = [p.get("packageId") for p in packages if isinstance(p, dict)]
        if len(package_ids) != len(packages):
            fail("update-index.json chứa package không phải object")
        if len(set(package_ids)) != len(package_ids):
            fail("update-index.json chứa packageId trùng")

        total = 0
        global_media_ids: set[str] = set()
        for descriptor in packages:
            _, count = validate_package(descriptor, global_media_ids)
            total += count

        print(f"PASS data index: {len(packages)} package/shard, {total} PUBLISHED records")
        if not packages:
            print("INFO: index hợp lệ nhưng chưa phát hành package dữ liệu thật.")
        return 0
    except ValidationError as exc:
        print(f"FAIL data validation: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
