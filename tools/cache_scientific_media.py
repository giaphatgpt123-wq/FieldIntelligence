#!/usr/bin/env python3
"""Cache explicitly reusable scientific reference images for offline FieldIntelligence packs.

Only public HTTPS JPEG/PNG/WebP media with an accepted reusable licence are eligible. Reference
media remains evidence for visual comparison only; it is never specimen-identification, edibility,
toxicity or treatment evidence.

For GBIF occurrence media, bytes are downloaded through GBIF's official crop/resize cache at a
mobile-friendly maximum width. The publisher's original media identifier remains the provenance
identity stored in the manifest/SQLite so licence matching and attribution are not weakened.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import ipaddress
import json
import mimetypes
import socket
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ALLOWED_LICENSES = {
    "CC0-1.0", "CC-BY-4.0", "CC-BY-NC-4.0",
    "https://creativecommons.org/publicdomain/zero/1.0/",
    "https://creativecommons.org/licenses/by/4.0/",
    "https://creativecommons.org/licenses/by-nc/4.0/",
}
ALLOWED_CONTENT_TYPES = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}
DEFAULT_MAX_BYTES = 8 * 1024 * 1024
DEFAULT_MAX_TOTAL_BYTES = 256 * 1024 * 1024
DEFAULT_MAX_PER_RECORD = 2
GBIF_IMAGE_CACHE_WIDTH = 1600
GBIF_IMAGE_CACHE_PREFIX = "https://api.gbif.org/v1/image/cache"
USER_AGENT = "FieldIntelligence-scientific-media/1.0"


def open_text(path: Path):
    return gzip.open(path, "rt", encoding="utf-8") if path.suffix == ".gz" else path.open("r", encoding="utf-8")


def is_public_address(address: str) -> bool:
    ip = ipaddress.ip_address(address)
    return not (ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_reserved or ip.is_unspecified)


def validate_public_https_url(url: str) -> urllib.parse.ParseResult:
    parsed = urllib.parse.urlparse(str(url).strip())
    if parsed.scheme.lower() != "https" or not parsed.hostname:
        raise ValueError("media URL must use HTTPS and contain a hostname")
    if parsed.username or parsed.password:
        raise ValueError("media URL must not contain credentials")
    try:
        addresses = {item[4][0] for item in socket.getaddrinfo(parsed.hostname, parsed.port or 443, type=socket.SOCK_STREAM)}
    except OSError as exc:
        raise ValueError(f"media host cannot be resolved: {parsed.hostname}") from exc
    if not addresses or any(not is_public_address(address) for address in addresses):
        raise ValueError("media URL resolves to a non-public address")
    return parsed


class SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_public_https_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def raw_media_items(record: dict) -> list[dict]:
    items = record.get("mediaItems") or []
    if not items and record.get("media"):
        items = [record.get("media") or {}]
    return [item for item in items if isinstance(item, dict)]


def is_eligible_media(item: dict) -> bool:
    identifier = str(item.get("identifier") or "").strip()
    license_id = str(item.get("license") or "").strip()
    media_type = str(item.get("mediaType") or item.get("type") or "").strip().casefold()
    is_image = media_type in {"stillimage", "image"} or media_type in ALLOWED_CONTENT_TYPES
    if not media_type:
        is_image = Path(urllib.parse.urlparse(identifier).path).suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
    return bool(identifier and license_id in ALLOWED_LICENSES and is_image)


def media_items(record: dict):
    yield from (item for item in raw_media_items(record) if is_eligible_media(item))


def gbif_resized_download_url(item: dict, width: int = GBIF_IMAGE_CACHE_WIDTH) -> str:
    """Return a deterministic GBIF resize-cache URL while preserving the original identifier.

    GBIF documents image-cache keys as md5(media.identifier) and supports resize prefixes such as
    `200x`. We only derive this URL for media discovered from a concrete GBIF occurrence key.
    """
    identifier = str(item.get("identifier") or "").strip()
    provider = str(item.get("sourceProvider") or "").strip().casefold()
    occurrence_key = str(item.get("gbifOccurrenceKey") or "").strip()
    if provider != "gbif occurrence media" or not occurrence_key or not identifier.startswith("https://"):
        return identifier
    if width <= 0 or width > 1600:
        raise ValueError("GBIF image cache width must be between 1 and 1200")
    digest = hashlib.md5(identifier.encode("utf-8")).hexdigest()
    safe_key = urllib.parse.quote(occurrence_key, safe="")
    return f"{GBIF_IMAGE_CACHE_PREFIX}/{width}x/occurrence/{safe_key}/media/{digest}"


def download_url_for_item(item: dict) -> str:
    original = str(item.get("identifier") or "").strip()
    if not original:
        return ""
    return gbif_resized_download_url(item) or original


def extension_for(content_type: str, url: str) -> str | None:
    normalized = content_type.split(";", 1)[0].strip().lower()
    if normalized in ALLOWED_CONTENT_TYPES:
        return ALLOWED_CONTENT_TYPES[normalized]
    suffix = Path(urllib.parse.urlparse(url).path).suffix.lower()
    if suffix in {".jpg", ".jpeg"}:
        return ".jpg"
    if suffix in {".png", ".webp"}:
        return suffix
    return ALLOWED_CONTENT_TYPES.get(mimetypes.guess_type(url)[0] or "")


def download_image(url: str, max_bytes: int, timeout: int = 8) -> tuple[bytes, str, str]:
    validate_public_https_url(url)
    opener = urllib.request.build_opener(SafeRedirectHandler())
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "image/jpeg,image/png,image/webp"})
    with opener.open(request, timeout=timeout) as response:
        final_url = response.geturl()
        validate_public_https_url(final_url)
        content_type = response.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
        declared = response.headers.get("Content-Length")
        if declared and int(declared) > max_bytes:
            raise ValueError("media exceeds declared byte limit")
        data = response.read(max_bytes + 1)
        if len(data) > max_bytes:
            raise ValueError("media exceeds byte limit")
        if not data:
            raise ValueError("media response is empty")
        ext = extension_for(content_type, final_url)
        if ext is None:
            raise ValueError(f"unsupported media content type: {content_type or 'unknown'}")
        return data, ext, final_url


def build(
    input_path: Path,
    media_dir: Path,
    manifest_path: Path,
    max_bytes: int = DEFAULT_MAX_BYTES,
    max_per_record: int = DEFAULT_MAX_PER_RECORD,
    max_total_bytes: int = DEFAULT_MAX_TOTAL_BYTES,
) -> dict:
    if max_bytes <= 0 or max_total_bytes <= 0 or max_per_record < 0:
        raise ValueError("media cache limits must be positive")
    media_dir.mkdir(parents=True, exist_ok=True)
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    records: list[dict] = []
    attempted = cached = rejected = failed = skipped_limit = total_bytes = resized_downloads = 0
    seen_downloads: dict[tuple[str, str], dict] = {}
    stored_hashes: set[str] = set()

    with open_text(input_path) as handle:
        for line in handle:
            if not line.strip():
                continue
            record = json.loads(line)
            source_id = str(record.get("sourceId") or "").strip()
            source_record_id = str(record.get("sourceRecordId") or "").strip()
            scientific_name = str(record.get("scientificName") or "").strip()
            if not source_id or not source_record_id or not scientific_name:
                continue
            raw = raw_media_items(record)
            eligible = [item for item in raw if is_eligible_media(item)]
            rejected += len(raw) - len(eligible)
            if len(eligible) > max_per_record:
                skipped_limit += len(eligible) - max_per_record
            for item in eligible[:max_per_record]:
                attempted += 1
                source_url = str(item.get("identifier") or "").strip()
                download_url = download_url_for_item(item)
                if not download_url:
                    failed += 1
                    continue
                cache_key = (source_url, download_url)
                if cache_key in seen_downloads:
                    cached_entry = dict(seen_downloads[cache_key])
                    cached_entry.update({"sourceId": source_id, "sourceRecordId": source_record_id, "scientificName": scientific_name})
                    records.append(cached_entry)
                    cached += 1
                    if download_url != source_url:
                        resized_downloads += 1
                    continue
                try:
                    data, ext, final_url = download_image(download_url, max_bytes=max_bytes)
                    digest = hashlib.sha256(data).hexdigest()
                    is_new_file = digest not in stored_hashes
                    if is_new_file and total_bytes + len(data) > max_total_bytes:
                        raise SystemExit(
                            f"scientific media pack would exceed total byte limit: {total_bytes + len(data)} > {max_total_bytes}"
                        )
                    relative_path = f"media/{digest[:2]}/{digest}{ext}"
                    target = media_dir / digest[:2] / f"{digest}{ext}"
                    target.parent.mkdir(parents=True, exist_ok=True)
                    if is_new_file:
                        target.write_bytes(data)
                        stored_hashes.add(digest)
                        total_bytes += len(data)
                    entry = {
                        "sourceId": source_id,
                        "sourceRecordId": source_record_id,
                        "scientificName": scientific_name,
                        "localPath": relative_path,
                        "sha256": digest,
                        "sizeBytes": len(data),
                        "sourceIdentifier": source_url,
                        "downloadIdentifier": download_url,
                        "resolvedIdentifier": final_url,
                        "references": str(item.get("references") or "").strip(),
                        "creator": str(item.get("creator") or "").strip(),
                        "rightsHolder": str(item.get("rightsHolder") or "").strip(),
                        "license": str(item.get("license") or "").strip(),
                        "mediaType": str(item.get("mediaType") or item.get("type") or "StillImage").strip(),
                    }
                    seen_downloads[cache_key] = {k: v for k, v in entry.items() if k not in {"sourceId", "sourceRecordId", "scientificName"}}
                    records.append(entry)
                    cached += 1
                    if download_url != source_url:
                        resized_downloads += 1
                except (ValueError, OSError, urllib.error.URLError, urllib.error.HTTPError, socket.timeout):
                    failed += 1

    manifest = {
        "manifestVersion": 1,
        "scope": "scientific-reference-media-offline-cache",
        "input": input_path.name,
        "records": records,
        "metrics": {
            "attempted": attempted,
            "cachedReferences": cached,
            "uniqueCachedFiles": len(stored_hashes),
            "failed": failed,
            "rejectedBeforeDownload": rejected,
            "skippedPerRecordLimit": skipped_limit,
            "gbifResizedDownloads": resized_downloads,
            "gbifResizeWidth": GBIF_IMAGE_CACHE_WIDTH,
            "totalBytes": total_bytes,
            "maxTotalBytes": max_total_bytes,
            "maxBytesPerFile": max_bytes,
            "maxPerRecord": max_per_record,
        },
        "safety": {
            "referenceMediaIsIdentificationEvidence": False,
            "edibilityInferred": False,
            "toxicityInferred": False,
            "medicalAdviceInferred": False,
            "publisherIdentifierPreserved": True,
        },
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--media-dir", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--max-bytes", type=int, default=DEFAULT_MAX_BYTES)
    parser.add_argument("--max-total-bytes", type=int, default=DEFAULT_MAX_TOTAL_BYTES)
    parser.add_argument("--max-per-record", type=int, default=DEFAULT_MAX_PER_RECORD)
    args = parser.parse_args()
    manifest = build(args.input, args.media_dir, args.manifest, args.max_bytes, args.max_per_record, args.max_total_bytes)
    print(json.dumps(manifest["metrics"], ensure_ascii=False))


if __name__ == "__main__":
    main()
