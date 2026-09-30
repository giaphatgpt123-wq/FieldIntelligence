#!/usr/bin/env python3
"""Publish Data Engine V2 category progress shards without breaking legacy Android clients."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from data_engine_v2_progress_shards import build_progress_shards

ROOT = Path(__file__).resolve().parents[1]
STAGING = ROOT / "data" / "staging"
DEFAULT_SNAPSHOT = STAGING / "progress-snapshot.json"
DEFAULT_INDEX = STAGING / "progress-index.json"
DEFAULT_OUTPUT_DIR = STAGING / "progress"
RAW_ROOT = "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/SurvivalLibraryVN/data/staging"


def load_json(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise RuntimeError(f"JSON object expected: {path}")
    return payload


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Publish category progress shards")
    parser.add_argument("--snapshot", type=Path, default=DEFAULT_SNAPSHOT)
    parser.add_argument("--index", type=Path, default=DEFAULT_INDEX)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    snapshot = load_json(args.snapshot)
    index = load_json(args.index)
    progress = build_progress_shards(snapshot, args.output_dir, RAW_ROOT)
    snapshot_meta = index.get("snapshot")
    if not isinstance(snapshot_meta, dict):
        raise RuntimeError("progress-index thiếu snapshot metadata")
    if int(snapshot_meta.get("version") or 0) != int(progress["version"]):
        raise RuntimeError("progress shard version không khớp legacy snapshot")

    index["progressShards"] = progress
    write_json(args.index, index)
    print(
        f"Progress shards: version={progress['version']} shards={progress['shardCount']} "
        f"entities={sum(row['entityCount'] for row in progress['shards'])} "
        f"tasks={sum(row['taskCount'] for row in progress['shards'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
