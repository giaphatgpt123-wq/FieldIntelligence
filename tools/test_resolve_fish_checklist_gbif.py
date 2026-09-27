import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("resolve_fish_checklist_gbif.py")
spec = importlib.util.spec_from_file_location("fish_resolver", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_ndjson(path: Path, rows):
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def response(usage_name, usage_key, accepted_name=None, accepted_key=None, match_type="EXACT", confidence=99, rank="SPECIES", kingdom="Animalia", synonym=False):
    usage = {"key": str(usage_key), "name": usage_name, "rank": rank, "status": "SYNONYM" if synonym else "ACCEPTED"}
    data = {
        "usage": usage,
        "classification": [{"key": "1", "name": kingdom, "rank": "KINGDOM"}],
        "diagnostics": {"matchType": match_type, "confidence": confidence},
        "synonym": synonym,
    }
    if accepted_key is not None:
        data["acceptedUsage"] = {"key": str(accepted_key), "name": accepted_name, "rank": rank, "status": "ACCEPTED"}
    return data


class GbifFishResolverTest(unittest.TestCase):
    def test_exact_synonym_resolves_to_accepted_taxon(self):
        evidence = module.classify_match(
            "Ophicephalus striatus",
            response("Ophicephalus striatus", 111, "Channa striata", 999, synonym=True),
            module.GBIF_BACKBONE_KEY,
        )
        self.assertEqual("resolved", evidence["status"])
        self.assertEqual("999", evidence["acceptedTaxonId"])
        self.assertEqual("Channa striata", evidence["acceptedScientificName"])
        self.assertTrue(evidence["synonym"])
        self.assertEqual(64, len(evidence["evidenceSha256"]))

    def test_fuzzy_or_wrong_rank_stays_review(self):
        fuzzy = module.classify_match(
            "Channa striata",
            response("Channa striata", 999, match_type="FUZZY", confidence=96),
            module.GBIF_BACKBONE_KEY,
        )
        self.assertEqual("review", fuzzy["status"])
        higher = module.classify_match(
            "Channa striata",
            response("Channa", 100, match_type="EXACT", confidence=99, rank="GENUS"),
            module.GBIF_BACKBONE_KEY,
        )
        self.assertEqual("review", higher["status"])

    def test_build_counts_present_blockers_without_auto_accepting_them(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "checklist.ndjson"
            output = root / "resolved.ndjson"
            report_path = root / "report.json"
            write_ndjson(checklist, [
                {"scientificName": "Channa striata", "presenceStatus": "present"},
                {"scientificName": "Oldus synonymus", "presenceStatus": "present"},
                {"scientificName": "Possible fish", "presenceStatus": "review"},
                {"scientificName": "Rejected fish", "presenceStatus": "excluded"},
            ])
            answers = {
                "Channa striata": response("Channa striata", 999),
                "Oldus synonymus": response("Oldus synonymus", 111, "Acceptedus fish", 222, match_type="FUZZY", confidence=90, synonym=True),
                "Possible fish": response("Possible fish", 333),
                "Rejected fish": response("Rejected fish", 444),
            }
            report = module.build(
                checklist, output, report_path,
                matcher=lambda name, key: answers[name],
                delay_seconds=0,
            )
            self.assertEqual(4, report["total"])
            self.assertEqual(2, report["presentInput"])
            self.assertEqual(1, report["presentResolved"])
            self.assertEqual(1, report["presentNeedsReview"])
            self.assertEqual(0, report["presentUnmatched"])
            self.assertEqual(1, report["uniqueAcceptedPresentTaxa"])
            self.assertEqual("Oldus synonymus", report["presentBlockers"][0]["scientificName"])


if __name__ == "__main__":
    unittest.main()
