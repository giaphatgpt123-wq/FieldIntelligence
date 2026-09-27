import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.enrich_missing_fish_media import enrich


class MissingFishMediaTest(unittest.TestCase):
    def test_only_missing_records_are_queried_and_digest_is_updated(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, output, metadata = root / "source.gz", root / "out.gz", root / "meta.json"
            rows = [
                {"sourceRecordId": "1", "scientificName": "Fish one", "mediaItems": [{"identifier": "https://example.org/1.jpg"}]},
                {"sourceRecordId": "2", "scientificName": "Fish two", "mediaItems": []},
            ]
            with gzip.open(source, "wt", encoding="utf-8") as dst:
                for row in rows:
                    dst.write(json.dumps(row) + "\n")
            metadata.write_text(json.dumps({"recordCount": 2, "mediaCandidates": 1}), encoding="utf-8")
            calls = []
            def finder(name):
                calls.append(name)
                return ({"identifier": "https://example.org/2.jpg", "license": "CC-BY-4.0"}, "matched")
            report = enrich(source, output, metadata, inat_finder=finder,
                            commons_finder=lambda name: (None, "unused"))
            self.assertEqual(["Fish two"], calls)
            self.assertEqual(1, report["addedInaturalist"])
            updated = json.loads(metadata.read_text())
            self.assertEqual(2, updated["recordsWithMedia"])
            self.assertEqual(64, len(updated["normalizedNdjsonSha256"]))
            with gzip.open(output, "rt", encoding="utf-8") as src:
                self.assertEqual(2, len([line for line in src if line.strip()]))


if __name__ == "__main__":
    unittest.main()
