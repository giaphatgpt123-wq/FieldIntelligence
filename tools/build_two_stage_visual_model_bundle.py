#!/usr/bin/env python3
"""Build a verified schema-v2 detector + classifier bundle for region scanning."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import zipfile

DETECTOR_NAME = "region-detector.tflite"
CLASSIFIER_NAME = "region-classifier.tflite"
MANIFEST_NAME = "region-model.manifest.json"
MODEL_FORMAT = "TFLITE_TASK_VISION"
TASK_TYPE = "TWO_STAGE_REGION_CLASSIFIER"
MAX_MODEL_BYTES = 160 * 1024 * 1024


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def component(path: pathlib.Path, filename: str, score_threshold: float, max_results: int) -> dict:
    if not path.is_file():
        raise ValueError(f"model not found: {path}")
    size = path.stat().st_size
    if size < 8 or size > MAX_MODEL_BYTES:
        raise ValueError(f"model size is outside allowed range: {path.name}")
    with path.open("rb") as handle:
        header = handle.read(8)
    if header[4:8] != b"TFL3":
        raise ValueError(f"not a TensorFlow Lite file: {path.name}")
    return {
        "file": filename,
        "sha256": sha256(path),
        "sizeBytes": size,
        "scoreThreshold": score_threshold,
        "maxResults": max_results,
    }


def build_bundle(args: argparse.Namespace) -> pathlib.Path:
    detector = pathlib.Path(args.detector).resolve()
    classifier = pathlib.Path(args.classifier).resolve()
    source_name = args.source_name.strip()
    license_name = args.license.strip()
    validation_note = args.validation_note.strip()
    if not source_name or not license_name or not validation_note:
        raise ValueError("source-name, license and validation-note are required")
    if not args.id.strip() or not args.version.strip():
        raise ValueError("id and version are required")

    manifest = {
        "schemaVersion": 2,
        "taskType": TASK_TYPE,
        "modelFormat": MODEL_FORMAT,
        "id": args.id.strip(),
        "version": args.version.strip(),
        "sourceName": source_name,
        "license": license_name,
        "supportedGroups": list(dict.fromkeys(args.supported_group)),
        "validationNote": validation_note,
        "speciesSafetyClaims": False,
        "proposalThreshold": args.proposal_threshold,
        "classificationThreshold": args.classification_threshold,
        "maxRegions": args.max_regions,
        "maxCandidatesPerRegion": args.max_candidates_per_region,
        "detector": component(detector, DETECTOR_NAME, args.detector_score_threshold, args.detector_max_results),
        "classifier": component(classifier, CLASSIFIER_NAME, args.classifier_score_threshold, args.classifier_max_results),
    }

    output = pathlib.Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(detector, DETECTOR_NAME)
        archive.write(classifier, CLASSIFIER_NAME)
        archive.writestr(MANIFEST_NAME, json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--detector", required=True)
    parser.add_argument("--classifier", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--id", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-name", required=True)
    parser.add_argument("--license", required=True)
    parser.add_argument("--validation-note", required=True)
    parser.add_argument("--supported-group", action="append", default=[])
    parser.add_argument("--proposal-threshold", type=float, default=0.30)
    parser.add_argument("--classification-threshold", type=float, default=0.45)
    parser.add_argument("--max-regions", type=int, default=12)
    parser.add_argument("--max-candidates-per-region", type=int, default=1)
    parser.add_argument("--detector-score-threshold", type=float, default=0.25)
    parser.add_argument("--detector-max-results", type=int, default=24)
    parser.add_argument("--classifier-score-threshold", type=float, default=0.35)
    parser.add_argument("--classifier-max-results", type=int, default=3)
    args = parser.parse_args()
    for name in ("proposal_threshold", "classification_threshold", "detector_score_threshold", "classifier_score_threshold"):
        value = getattr(args, name)
        if not 0.05 <= value <= 0.99:
            parser.error(f"--{name.replace('_', '-')} must be between 0.05 and 0.99")
    if not 1 <= args.max_regions <= 32:
        parser.error("--max-regions must be between 1 and 32")
    if not 1 <= args.max_candidates_per_region <= 5:
        parser.error("--max-candidates-per-region must be between 1 and 5")
    if not 1 <= args.detector_max_results <= 50:
        parser.error("--detector-max-results must be between 1 and 50")
    if not 1 <= args.classifier_max_results <= 10:
        parser.error("--classifier-max-results must be between 1 and 10")
    return args


if __name__ == "__main__":
    print(build_bundle(parse_args()))
