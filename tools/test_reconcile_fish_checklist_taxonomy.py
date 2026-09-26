import csv
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("reconcile_fish_checklist_taxonomy.py")
spec = importlib.util.spec_from_file_location("fish_reconcile", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_checklist(path: Path, rows):
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def write_taxonomy(path: Path, rows):
    fields = ["taxonID", "scientificName", "canonicalName", "acceptedScientificName", "acceptedTaxonKey", "taxonomicStatus"]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)


class FishChecklistTaxonomyReconciliationTest(unittest.TestCase):
    def test_exact_canonical_synonym_ambiguous_and_unmatched(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "checklist.ndjson"
            taxonomy = root / "taxonomy.tsv"
            output = root / "reconciled.ndjson"
            report_path = root / "report.json"
            write_checklist(checklist, [
                {"scientificName": "Channa striata", "presenceStatus": "present"},
                {"scientificName": "Anabas testudineus (Bloch, 1792)", "presenceStatus": "present"},
                {"scientificName": "Puntius gonionotus", "presenceStatus": "present"},
                {"scientificName": "Mystus ambiguus", "presenceStatus": "review"},
                {"scientificName": "Unknownus piscis", "presenceStatus": "present"},
            ])
            write_taxonomy(taxonomy, [
                {"taxonID": "1", "scientificName": "Channa striata", "canonicalName": "Channa striata", "acceptedScientificName": "Channa striata", "acceptedTaxonKey": "1", "taxonomicStatus": "accepted"},
                {"taxonID": "2", "scientificName": "Anabas testudineus", "canonicalName": "Anabas testudineus", "acceptedScientificName": "Anabas testudineus", "acceptedTaxonKey": "2", "taxonomicStatus": "accepted"},
                {"taxonID": "3", "scientificName": "Puntius gonionotus", "canonicalName": "Puntius gonionotus", "acceptedScientificName": "Barbonymus gonionotus", "acceptedTaxonKey": "30", "taxonomicStatus": "synonym"},
                {"taxonID": "4", "scientificName": "Mystus ambiguus Smith", "canonicalName": "Mystus ambiguus", "acceptedScientificName": "Mystus ambiguus", "acceptedTaxonKey": "4", "taxonomicStatus": "accepted"},
                {"taxonID": "5", "scientificName": "Mystus ambiguus Jones", "canonicalName": "Mystus ambiguus", "acceptedScientificName": "Mystus ambiguus", "acceptedTaxonKey": "5", "taxonomicStatus": "accepted"},
            ])
            report = module.build(checklist, taxonomy, output, report_path)
            self.assertEqual(5, report["total"])
            self.assertEqual(3, report["matched"])
            self.assertEqual(1, report["matchedExact"])
            self.assertEqual(1, report["matchedCanonical"])
            self.assertEqual(1, report["matchedSynonym"])
            self.assertEqual(1, report["ambiguous"])
            self.assertEqual(1, report["unmatched"])

            rows = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines() if line.strip()]
            by_name = {row["scientificName"]: row for row in rows}
            self.assertEqual("matched", by_name["Channa striata"]["reconciliation"]["status"])
            self.assertEqual("canonical", by_name["Anabas testudineus (Bloch, 1792)"]["reconciliation"]["matchType"])
            self.assertEqual("synonym", by_name["Puntius gonionotus"]["reconciliation"]["matchType"])
            self.assertEqual("30", by_name["Puntius gonionotus"]["reconciliation"]["acceptedTaxonId"])
            self.assertEqual("ambiguous", by_name["Mystus ambiguus"]["reconciliation"]["status"])
            self.assertEqual(2, by_name["Mystus ambiguus"]["reconciliation"]["candidateCount"])
            self.assertEqual("unmatched", by_name["Unknownus piscis"]["reconciliation"]["status"])

    def test_canonical_guess_rejects_non_binomial_noise(self):
        self.assertEqual("", module.canonical_guess("cf. Channa striata"))
        self.assertEqual("Channa striata", module.canonical_guess("Channa striata (Bloch, 1793)"))


if __name__ == "__main__":
    unittest.main()
