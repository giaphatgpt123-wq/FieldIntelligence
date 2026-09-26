import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from build_visual_model_bundle import build_bundle


class VisualModelBundleBuilderTest(unittest.TestCase):
    def args(self, root: Path, **overrides):
        model = root / "model.tflite"
        model.write_bytes(b"TFL3" + bytes(range(32)))
        values = dict(
            model=str(model),
            output=str(root / "region-model-bundle.zip"),
            id="field-test-detector",
            version="1.0.0",
            source_name="Synthetic CI fixture",
            license="TEST-ONLY",
            validation_note="Synthetic fixture only; not a biological model.",
            supported_group=["plants", "fungi"],
            score_threshold=0.45,
            max_results=20,
        )
        values.update(overrides)
        return argparse.Namespace(**values)

    def test_builds_exact_two_file_bundle_with_bound_sha(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = self.args(root)
            output = build_bundle(args)

            self.assertEqual(output, Path(args.output).resolve())
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(
                    set(archive.namelist()),
                    {"region-model.tflite", "region-model.manifest.json"},
                )
                model_bytes = archive.read("region-model.tflite")
                manifest = json.loads(archive.read("region-model.manifest.json"))

            self.assertEqual(manifest["schemaVersion"], 1)
            self.assertEqual(manifest["id"], "field-test-detector")
            self.assertEqual(manifest["supportedGroups"], ["plants", "fungi"])
            self.assertFalse(manifest["speciesSafetyClaims"])
            self.assertEqual(manifest["sizeBytes"], len(model_bytes))
            self.assertEqual(manifest["sha256"], hashlib.sha256(model_bytes).hexdigest())

    def test_duplicate_supported_groups_are_deduplicated_in_order(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = self.args(root, supported_group=["plants", "plants", "fungi", "plants"])
            output = build_bundle(args)
            with zipfile.ZipFile(output) as archive:
                manifest = json.loads(archive.read("region-model.manifest.json"))
            self.assertEqual(manifest["supportedGroups"], ["plants", "fungi"])

    def test_requires_provenance_and_validation_note(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for field in ("source_name", "license", "validation_note"):
                args = self.args(root, **{field: "   "})
                with self.assertRaises(ValueError):
                    build_bundle(args)

    def test_rejects_empty_model_id_or_version(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaises(ValueError):
                build_bundle(self.args(root, id=""))
            with self.assertRaises(ValueError):
                build_bundle(self.args(root, version=""))


if __name__ == "__main__":
    unittest.main()
