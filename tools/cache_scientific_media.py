#!/usr/bin/env python3
"""Cache explicitly reusable scientific reference images for offline FieldIntelligence packs.

Input is normalized scientific NDJSON(.gz). Only HTTPS image media carrying an accepted reusable
license are eligible. The cache keeps source provenance in a separate manifest and never treats a
reference image as proof of specimen identity, edibility, toxicity, or medical safety.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import ipaddress
import json
import mimetypes
import socket
import urllib.parse
import urllib.request
from pathlib import Path

ALLOWED_LICENSES = {
    "CC0-1.0",
    "CC-BY-4.0",
    "CC-BY-NC-4.0",
    "https://creativecommons.org/publicdomain/zero/1.0/",
    "https://creativecommons.org/licenses/by/4.0/",
    "https://creativecommons.org/licenses/by-nc/4.0/",
}
ALLOWED_CONTENT_TYPES = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
}
DEFAULT_MAX_BYTES = 8 * 1024 * 1024
DEFAULT_MAX_PER_RECORD = 3
USER_AGENT = "FieldIntelligence-scientific-media/1.0"


def open_text(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def is_public_address(address: str) -> bool:
    ip = ipaddress.ip_address(address)
    return not (
        ip.is_private
        or ip.is_loopback
        or ip.is_link_local
        or ip.is_multicast
        or ip.is_reserved
        or ip.is_unspecified
    )


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


def media_items(record: dict):
    items = record.get("mediaItems") or []
    if not items and record.get("media"):
        items = [record.get("media") or {}]
    for item in items:
        identifier = str(item.get("identifier") or "").strip()
        license_id = str(item.get("license") or "").strip()
        media_type = str(item.get("mediaType") or item.get("type") or "").strip().casefold()
        if identifier and license_id in ALLOWED_LICENSES and (not media_type or "image" in media_type):
            yield item


def extension_for(content_type: str, url: str) -> str | None:
    normalized = content_type.split(";", 1)[0].strip().lower()
    if normalized in ALLOWED_CONTENT_TYPES:
        return ALLOWED_CONTENT_TYPES[normalized]
    suffix = Path(urllib.parse.urlparse(url).path).suffix.lower()
    if suffix in {".jpg", ".jpeg"}:
        return ".jpg"
    if suffix in {".png", ".webp"}:
        return suffix
    guessed = mimetypes.guess_type(url)[0]
    return ALLOWED_CONTENT_TYPES.get(guessed or "")


def download_image(url: str, max_bytes: int, timeout: int = 20) -> tuple[bytes, str, str]:
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


def build(input_path: Path, media_dir: Path, manifest_path: Path, max_bytes: int = DEFAULT_MAX_BYTES, max_per_record: int = DEFAULT_MAX_PER_RECORD) -> dict:
    media_dir.mkdir(parents=True, exist_ok=True)
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    records: list[dict] = []
    attempted = 0
    cached = 0
    rejected = 0
    failed = 0
    total_bytes = 0
    seen_source_urls: dict[str, dict] = {}

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
            selected = list(media_items(record))[: max(0, max_per_record)]
            for item in selected:
                attempted += 1
                source_url = str(item.get("identifier") or "").strip()
                if source_url in seen_source_urls:
                    cached_entry = dict(seen_source_urls[source_url])
                    cached_entry.update({"sourceId": source_id, "sourceRecordId": source_record_id, "scientificName": scientific_name})
                    records.append(cached_entry)
                    cached += 1
                    continue
                try:
                    data, ext, final_url = download_image(source_url, max_bytes=max_bytes)
                    digest = hashlib.sha256(data).hexdigest()
                    relative_path = f"media/{digest[:2]}/{digest}{ext}"
                    target = media_dir / digest[:2] / f"{digest}{ext}"
                    target.parent.mkdir(parents=True, exist_ok=True)
                    if not target.exists():
                        target.write_bytes(data)
                        total_bytes += len(data)
                    entry = {
                        "sourceId": source_id,
                        "sourceRecordId": source_record_id,
                        "scientificName": scientific_name,
                        "localPath": relative_path,
                        "sha256": digest,
                        "sizeBytes": len(data),
                        "sourceIdentifier": source_url,
                        "resolvedIdentifier": final_url,
                        "references": str(item.get("references") or "").strip(),
                        "creator": str(item.get("creator") or "").strip(),
                        "rightsHolder": str(item.get("rightsHolder") or "").strip(),
                        "license": str(item.get("license") or "").strip(),
                        "mediaType": str(item.get("mediaType") or item.get("type") or "StillImage").strip(),
                    }
                    seen_source_urls[source_url] = {key: value for key, value in entry.items() if key not in {"sourceId", "sourceRecordId", "scientificName"}}
                    records.append(entry)
                    cached += 1
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
            "uniqueCachedFiles": len({item["sha256"] for item in records}),
            "failed": failed,
            "rejectedBeforeDownload": rejected,
            "totalBytes": total_bytes,
        },
        "safety": {
            "referenceMediaIsIdentificationEvidence": False,
            "edibilityInferred": False,
            "toxicityInferred": False,
            "medicalAdviceInferred": False,
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
    parser.add_argument("--max-per-record", type=int, default=DEFAULT_MAX_PER_RECORD)
    args = parser.parse_args()
    manifest = build(args.input, args.media_dir, args.manifest, args.max_bytes, args.max_per_record)
    print(json.dumps(manifest["metrics"], ensure_ascii=False))


if __name__ == "__main__":
    main()
