import csv
import tempfile
import unittest
from pathlib import Path

from tools.build_vietnam_freshwater_fish_checklist import build


class FishChecklistNormalizationTest(unittest.TestCase):
    def test_preserves_excluded_rows_from_fishbase_audit(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "audit.csv"
            output = Path(directory) / "checklist.ndjson"
            with source.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=["scientificName", "presenceStatus"])
                writer.writeheader()
                for name, status in [("Species alpha", "present"), ("Species beta", "review"), ("Species gamma", "excluded")]:
                    writer.writerow({"scientificName": name, "presenceStatus": status})
            self.assertEqual({"total": 3, "present": 1, "review": 1, "excluded": 1}, build(source, output))
            self.assertIn('"presenceStatus":"excluded"', output.read_text(encoding="utf-8"))
