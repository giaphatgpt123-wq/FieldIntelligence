import gzip
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("audit_resolved_fish_library_coverage.py")
spec = importlib.util.spec_from_file_location("resolved_coverage", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_ndjson(path: Path, rows):
    opener = gzip.open if path.name.endswith(".gz") else open
    with opener(path, "wt", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


class ResolvedFishLibraryCoverageTest(unittest.TestCase):
    def test_resolved_synonyms_can_share_one_production_taxon(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "resolved.ndjson"
            taxonomy = root / "fish.ndjson.gz"
            output = root / "reconciled.ndjson"
            report_path = root / "report.json"
            write_ndjson(checklist, [
                {"scientificName": "Channa striata", "presenceStatus": "present", "taxonomyResolution": {"status": "resolved", "acceptedTaxonId": "999", "acceptedScientificName": "Channa striata"}},
                {"scientificName": "Ophicephalus striatus", "presenceStatus": "present", "taxonomyResolution": {"status": "resolved", "acceptedTaxonId": "999", "acceptedScientificName": "Channa striata"}},
                {"scientificName": "Possible fish", "presenceStatus": "review", "taxonomyResolution": {"status": "resolved", "acceptedTaxonId": "888", "acceptedScientificName": "Anabas testudineus"}},
            ])
            write_ndjson(taxonomy, [
                {"sourceRecordId": "999", "acceptedNameUsageId": "999", "scientificName": "Channa striata"},
                {"sourceRecordId": "888", "acceptedNameUsageId": "888", "scientificName": "Anabas testudineus"},
            ])
            report = module.build(checklist, taxonomy, output, report_path)
            self.assertEqual(2, report["resolvedPresentChecklistCount"])
            self.assertEqual(0, report["unresolvedPresentChecklistCount"])
            self.assertEqual(1, report["reconciledPresentTaxonCount"])
            self.assertEqual(1, report["resolvedReviewChecklistCount"])

    def test_present_resolver_block_or_missing_library_taxon_is_not_silenced(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "resolved.ndjson"
            taxonomy = root / "fish.ndjson.gz"
            output = root / "reconciled.ndjson"
            report_path = root / "report.json"
            write_ndjson(checklist, [
                {"scientificName": "Fuzzy fish", "presenceStatus": "present", "taxonomyResolution": {"status": "review", "reason": "match_type_FUZZY", "acceptedTaxonId": "1"}},
                {"scientificName": "Missing fish", "presenceStatus": "present", "taxonomyResolution": {"status": "resolved", "acceptedTaxonId": "2", "acceptedScientificName": "Missing fish"}},
            ])
            write_ndjson(taxonomy, [{"sourceRecordId": "3", "acceptedNameUsageId": "3", "scientificName": "Other fish"}])
            report = module.build(checklist, taxonomy, output, report_path)
            self.assertEqual(0, report["resolvedPresentChecklistCount"])
            self.assertEqual(2, report["unresolvedPresentChecklistCount"])
            self.assertEqual(1, report["resolverBlockedPresentCount"])
            self.assertEqual(1, report["missingLibraryPresentCount"])
            self.assertEqual(["Fuzzy fish", "Missing fish"], report["unresolvedPresentNames"])


if __name__ == "__main__":
    unittest.main()
