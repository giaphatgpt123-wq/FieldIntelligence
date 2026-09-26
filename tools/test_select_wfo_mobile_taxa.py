import gzip
import json
import tempfile
import unittest
from pathlib import Path

from select_wfo_mobile_taxa import select


class SelectWfoMobileTaxaTest(unittest.TestCase):
    def test_preserves_provenance_and_only_selected_genera(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "source.gz"
            output = Path(temp) / "selected.gz"
            records = [
                {"genus": "Musa", "scientificName": "Musa acuminata", "provenance": {"license": "CC0-1.0"}},
                {"genus": "Amanita", "scientificName": "Amanita muscaria"},
            ]
            with gzip.open(source, "wt", encoding="utf-8") as handle:
                for record in records:
                    handle.write(json.dumps(record) + "\n")
            report = select(source, output, frozenset({"Musa"}))
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                selected = [json.loads(line) for line in handle]
            self.assertEqual(report["recordCount"], 1)
            self.assertEqual(selected, records[:1])


if __name__ == "__main__":
    unittest.main()
