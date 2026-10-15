import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from build_two_stage_visual_model_bundle import build_bundle


class TwoStageVisualModelBundleBuilderTest(unittest.TestCase):
    def args(self, root: Path, **overrides):
        detector = root / "detector.tflite"
        classifier = root / "classifier.tflite"
        detector.write_bytes(b"\x18\x00\x00\x00TFL3-detector-fixture")
        classifier.write_bytes(b"\x18\x00\x00\x00TFL3-classifier-fixture")
        values = dict(
            detector=str(detector),
            classifier=str(classifier),
            output=str(root / "bundle.zip"),
            id="field-two-stage-test",
            version="1.0.0",
            source_name="Synthetic CI fixture",
            license="TEST-ONLY",
            validation_note="Synthetic fixture only; not a biological model.",
            supported_group=["plants", "fungi"],
            proposal_threshold=0.30,
            classification_threshold=0.35,
            max_regions=12,
            max_candidates_per_region=3,
            detector_score_threshold=0.25,
            detector_max_results=24,
            classifier_score_threshold=0.35,
            classifier_max_results=3,
        )
        values.update(overrides)
        return argparse.Namespace(**values)

    def test_builds_three_file_schema_v2_bundle(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = self.args(root)
            output = build_bundle(args)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(
                    set(archive.namelist()),
                    {"region-detector.tflite", "region-classifier.tflite", "region-model.manifest.json"},
                )
                detector_bytes = archive.read("region-detector.tflite")
                classifier_bytes = archive.read("region-classifier.tflite")
                manifest = json.loads(archive.read("region-model.manifest.json"))

            self.assertEqual(manifest["schemaVersion"], 2)
            self.assertEqual(manifest["taskType"], "TWO_STAGE_REGION_CLASSIFIER")
            self.assertEqual(manifest["modelFormat"], "TFLITE_TASK_VISION")
            self.assertFalse(manifest["speciesSafetyClaims"])
            self.assertEqual(manifest["supportedGroups"], ["plants", "fungi"])
            self.assertEqual(manifest["maxCandidatesPerRegion"], 3)
            self.assertEqual(manifest["classificationThreshold"], 0.35)
            self.assertEqual(manifest["detector"]["file"], "region-detector.tflite")
            self.assertEqual(manifest["classifier"]["file"], "region-classifier.tflite")
            self.assertEqual(manifest["detector"]["sha256"], hashlib.sha256(detector_bytes).hexdigest())
            self.assertEqual(manifest["classifier"]["sha256"], hashlib.sha256(classifier_bytes).hexdigest())
            self.assertEqual(manifest["detector"]["sizeBytes"], len(detector_bytes))
            self.assertEqual(manifest["classifier"]["sizeBytes"], len(classifier_bytes))

    def test_deduplicates_supported_groups(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output = build_bundle(self.args(root, supported_group=["plants", "plants", "fungi"]))
            with zipfile.ZipFile(output) as archive:
                manifest = json.loads(archive.read("region-model.manifest.json"))
            self.assertEqual(manifest["supportedGroups"], ["plants", "fungi"])

    def test_requires_both_model_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = self.args(root)
            Path(args.classifier).unlink()
            with self.assertRaises(ValueError):
                build_bundle(args)

    def test_rejects_non_tflite_component(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = self.args(root)
            Path(args.detector).write_bytes(b"not-a-model")
            with self.assertRaisesRegex(ValueError, "not a TensorFlow Lite file"):
                build_bundle(args)

    def test_requires_provenance_and_validation_note(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for field in ("source_name", "license", "validation_note"):
                with self.assertRaises(ValueError):
                    build_bundle(self.args(root, **{field: "   "}))


if __name__ == "__main__":
    unittest.main()
