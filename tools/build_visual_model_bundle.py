#!/usr/bin/env python3
"""Build a verified offline visual-model bundle for FieldIntelligence.

The input model must be a TensorFlow Lite Task Vision object detector with metadata.
This tool does not validate biological accuracy; provenance and validation notes remain mandatory.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import zipfile

MODEL_NAME = "region-model.tflite"
MANIFEST_NAME = "region-model.manifest.json"
MAX_MODEL_BYTES = 160 * 1024 * 1024
TASK_TYPE = "OBJECT_DETECTOR"
MODEL_FORMAT = "TFLITE_TASK_VISION"


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_bundle(args: argparse.Namespace) -> pathlib.Path:
    model = pathlib.Path(args.model).resolve()
    if not model.is_file():
        raise ValueError(f"model not found: {model}")
    size = model.stat().st_size
    if size <= 0 or size > MAX_MODEL_BYTES:
        raise ValueError("model size is outside allowed range")

    source_name = args.source_name.strip()
    license_name = args.license.strip()
    validation_note = args.validation_note.strip()
    if not source_name or not license_name or not validation_note:
        raise ValueError("source-name, license and validation-note are required")

    manifest = {
        "schemaVersion": 1,
        "taskType": TASK_TYPE,
        "modelFormat": MODEL_FORMAT,
        "id": args.id.strip(),
        "version": args.version.strip(),
        "sourceName": source_name,
        "license": license_name,
        "sha256": sha256(model),
        "sizeBytes": size,
        "supportedGroups": list(dict.fromkeys(args.supported_group)),
        "scoreThreshold": args.score_threshold,
        "maxResults": args.max_results,
        "validationNote": validation_note,
        "speciesSafetyClaims": False,
    }
    if not manifest["id"] or not manifest["version"]:
        raise ValueError("id and version are required")

    output = pathlib.Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(model, MODEL_NAME)
        archive.writestr(
            MANIFEST_NAME,
            json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        )
    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--id", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-name", required=True)
    parser.add_argument("--license", required=True)
    parser.add_argument("--validation-note", required=True)
    parser.add_argument("--supported-group", action="append", default=[])
    parser.add_argument("--score-threshold", type=float, default=0.45)
    parser.add_argument("--max-results", type=int, default=20)
    args = parser.parse_args()
    if not 0.05 <= args.score_threshold <= 0.99:
        parser.error("--score-threshold must be between 0.05 and 0.99")
    if not 1 <= args.max_results <= 50:
        parser.error("--max-results must be between 1 and 50")
    return args


if __name__ == "__main__":
    print(build_bundle(parse_args()))
