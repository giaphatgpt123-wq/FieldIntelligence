import gzip
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("merge_scientific_ndjson.py")
spec = importlib.util.spec_from_file_location("scientific_merge", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_gz(path: Path, rows):
    with gzip.open(path, "wt", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


class ScientificNdjsonMergeTest(unittest.TestCase):
    def test_merges_wfo_and_fish_without_losing_source_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            wfo = root / "wfo.gz"
            fish = root / "fish.gz"
            output = root / "merged.gz"
            metadata = root / "merged.meta.json"
            write_gz(wfo, [{
                "sourceId": "wfo", "sourceRecordId": "w1", "scientificName": "Oryza sativa",
                "libraryGroup": "Thực vật", "provenance": {"license": "CC0-1.0"}
            }])
            write_gz(fish, [{
                "sourceId": "gbif", "sourceRecordId": "g1", "scientificName": "Channa striata",
                "libraryGroup": "Cá nước ngọt",
                "mediaItems": [{"identifier": "https://example.org/fish.jpg", "license": "CC-BY-4.0"}],
                "provenance": {"license": "CC-BY-4.0"}
            }])
            report = module.build([wfo, fish], output, metadata)
            self.assertEqual(2, report["recordCount"])
            self.assertEqual({"wfo": 1, "gbif": 1}, report["sourceCounts"])
            self.assertEqual(1, report["groupCounts"]["Cá nước ngọt"])
            self.assertEqual(1, report["recordsWithMediaBeforeLicenseGate"])
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                rows = [json.loads(line) for line in handle if line.strip()]
            self.assertEqual(["wfo", "gbif"], [row["sourceId"] for row in rows])
            self.assertEqual(report, json.loads(metadata.read_text(encoding="utf-8")))

    def test_rejects_duplicate_source_record_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            a = root / "a.gz"
            b = root / "b.gz"
            output = root / "merged.gz"
            row = {"sourceId": "gbif", "sourceRecordId": "same", "scientificName": "Channa striata"}
            write_gz(a, [row])
            write_gz(b, [row])
            with self.assertRaises(SystemExit) as ctx:
                module.build([a, b], output)
            self.assertIn("duplicate", str(ctx.exception).lower())


if __name__ == "__main__":
    unittest.main()
