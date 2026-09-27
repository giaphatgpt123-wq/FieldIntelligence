import gzip
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("audit_fish_checklist_coverage.py")
spec = importlib.util.spec_from_file_location("fish_coverage", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_ndjson(path: Path, rows):
    opener = gzip.open if path.name.endswith(".gz") else open
    with opener(path, "wt", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


class FishChecklistCoverageTest(unittest.TestCase):
    def test_synonym_and_accepted_names_can_resolve_to_one_taxon(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "checklist.ndjson"
            taxonomy = root / "fish.ndjson.gz"
            output = root / "reconciled.ndjson"
            report_path = root / "report.json"
            write_ndjson(checklist, [
                {"scientificName": "Channa striata", "presenceStatus": "present"},
                {"scientificName": "Ophicephalus striatus", "presenceStatus": "present"},
                {"scientificName": "Anabas testudineus", "presenceStatus": "review"},
                {"scientificName": "Rejectedus piscis", "presenceStatus": "excluded"},
            ])
            write_ndjson(taxonomy, [
                {"sourceRecordId": "999", "acceptedNameUsageId": "999", "scientificName": "Channa striata", "sourceScientificNames": ["Channa striata", "Ophicephalus striatus"]},
                {"sourceRecordId": "888", "acceptedNameUsageId": "888", "scientificName": "Anabas testudineus", "sourceScientificNames": ["Anabas testudineus"]},
            ])
            report = module.build(checklist, taxonomy, output, report_path)
            self.assertEqual(4, report["total"])
            self.assertEqual(2, report["presentInput"])
            self.assertEqual(2, report["resolvedPresentChecklistCount"])
            self.assertEqual(0, report["unresolvedPresentChecklistCount"])
            self.assertEqual(1, report["reconciledPresentTaxonCount"])
            self.assertEqual(1, report["resolvedReviewChecklistCount"])
            self.assertEqual(0, report["resolvedExcludedChecklistCount"])

    def test_missing_present_name_fails_coverage_accounting(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "checklist.ndjson"
            taxonomy = root / "fish.ndjson.gz"
            output = root / "reconciled.ndjson"
            report_path = root / "report.json"
            write_ndjson(checklist, [{"scientificName": "Missingus piscis", "presenceStatus": "present"}])
            write_ndjson(taxonomy, [{"sourceRecordId": "1", "acceptedNameUsageId": "1", "scientificName": "Channa striata", "sourceScientificNames": ["Channa striata"]}])
            report = module.build(checklist, taxonomy, output, report_path)
            self.assertEqual(1, report["unresolvedPresentChecklistCount"])
            self.assertEqual(["Missingus piscis"], report["unresolvedPresentNames"])


if __name__ == "__main__":
    unittest.main()
