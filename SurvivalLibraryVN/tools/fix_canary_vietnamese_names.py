#!/usr/bin/env python3
"""Replace scientific-only Vietnamese display names with authoritative VN names."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FAUNA = DATA / "packages" / "fauna-core-s001.json"
INDEX = DATA / "update-index.json"
REPORT = DATA / "canary-selection.json"

OVERRIDES = {
    "canary-insects-apis-cerana": {
        "name": "Ong nội",
        "sourceKey": "khuyennong-name-apis-cerana",
        "title": "Kỹ thuật nuôi ong mật (ong nội - Apis cerana)",
        "publisher": "Trung tâm Khuyến nông Quốc gia",
        "uri": "https://khuyennongvn.gov.vn/khoa-hoc-cong-nghe/khcn-trong-nuoc/ky-thuat-nuoi-ong-mat-ong-noi-apis-cerana-16705.html",
    },
    "canary-danger-aedes-aegypti": {
        "name": "Muỗi vằn",
        "sourceKey": "who-vietnam-name-aedes-aegypti",
        "title": "WHO Việt Nam: Sốt xuất huyết Dengue - muỗi vằn (Aedes aegypti)",
        "publisher": "World Health Organization",
        "uri": "https://www.who.int/vietnam/vi/news/questions-and-answers/q-a-detail/dengue-and-severe-dengue",
    },
}


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def dump(path: Path, value: object) -> bytes:
    raw = (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    path.write_bytes(raw)
    return raw


def main() -> int:
    fauna = load(FAUNA)
    seen = set()
    for record in fauna.get("records", []):
        override = OVERRIDES.get(record.get("id"))
        if not override:
            continue
        seen.add(record["id"])
        record["vietnameseName"] = override["name"]
        scientific = record.get("scientificName", "")
        record["summary"] = (
            f"Hồ sơ dữ liệu thật canary. Tên khoa học: {scientific}. "
            "Định danh, tên Việt, hiện diện tại Việt Nam và ảnh đã đối chiếu nguồn; "
            "chưa phát hành công dụng/cách dùng."
        )
        sources = record.get("sources", [])
        if not sources:
            raise RuntimeError(f"{record['id']}: thiếu sources")
        checked_at = int(sources[0].get("checkedAt", 0))
        sources[0] = {
            "sourceKey": override["sourceKey"],
            "title": override["title"],
            "publisher": override["publisher"],
            "uri": override["uri"],
            "checkedAt": checked_at,
        }
        record["sourceCount"] = len(sources)

    missing = set(OVERRIDES) - seen
    if missing:
        raise RuntimeError(f"Không tìm thấy canary cần sửa tên: {sorted(missing)}")

    fauna_raw = dump(FAUNA, fauna)
    fauna_sha = hashlib.sha256(fauna_raw).hexdigest()

    index = load(INDEX)
    found = False
    for package in index.get("packages", []):
        if package.get("packageId") == "fauna-core-s001":
            package["sha256"] = fauna_sha
            found = True
            break
    if not found:
        raise RuntimeError("update-index thiếu fauna-core-s001")
    dump(INDEX, index)

    report = load(REPORT)
    for row in report.get("records", []):
        override = OVERRIDES.get(row.get("recordId"))
        if override:
            row["vietnameseName"] = override["name"]
    dump(REPORT, report)

    print(f"[canary] corrected Vietnamese display names; fauna sha256={fauna_sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
