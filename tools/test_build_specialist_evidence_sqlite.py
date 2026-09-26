import importlib.util
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_specialist_evidence_sqlite.py")
spec = importlib.util.spec_from_file_location("specialist_evidence_sqlite", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class SpecialistEvidenceSqliteBuilderTest(unittest.TestCase):
    def test_builds_separate_bounded_evidence_database(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "evidence.json"
            output = root / "specialist-evidence.sqlite"
            source.write_text(
                json.dumps(
                    {
                        "records": [
                            {
                                "evidenceId": "cdc-ricin",
                                "speciesId": "ricinus-communis",
                                "domain": "TOXICOLOGY",
                                "evidenceClass": "PUBLIC_HEALTH_TOXICOLOGY",
                                "title": "Ricin in castor beans",
                                "statement": "Ricin is a naturally occurring toxin in castor beans.",
                                "plantPart": "Hạt",
                                "sourceName": "U.S. Centers for Disease Control and Prevention",
                                "sourceUrl": "https://www.cdc.gov/example",
                                "sourceRecord": "Ricin fact sheet",
                                "scopeNote": "Bounded toxicology statement only.",
                                "sourceId": "cdc-toxicology",
                                "sourceRecordId": "ricin-fact-sheet",
                                "jurisdiction": "US",
                                "sourceDate": "2026-01-01",
                                "lastVerified": "2026-09-26",
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            result = module.build(source, output)
            self.assertEqual(1, result["recordCount"])
            self.assertFalse(result["taxonomyIncluded"])
            self.assertFalse(result["treatmentRecommendationsIncluded"])
            db = sqlite3.connect(output)
            try:
                row = db.execute(
                    "SELECT species_id, domain, evidence_class, source_id FROM evidence WHERE evidence_id=?",
                    ("cdc-ricin",),
                ).fetchone()
                self.assertEqual(
                    ("ricinus-communis", "TOXICOLOGY", "PUBLIC_HEALTH_TOXICOLOGY", "cdc-toxicology"),
                    row,
                )
                meta = dict(db.execute("SELECT key,value FROM meta"))
                self.assertEqual("specialist-evidence-only", json.loads(meta["scope"]))
            finally:
                db.close()

    def test_rejects_duplicate_ids_and_insecure_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            output = root / "e.sqlite"
            base = {
                "evidenceId": "dup",
                "speciesId": "x",
                "domain": "TOXICOLOGY",
                "evidenceClass": "PUBLIC_HEALTH_TOXICOLOGY",
                "title": "Title",
                "statement": "Statement",
                "sourceName": "Authority",
                "sourceUrl": "https://example.org/source",
                "scopeNote": "Bounded statement.",
            }
            duplicate = root / "duplicate.json"
            duplicate.write_text(json.dumps([base, base]), encoding="utf-8")
            with self.assertRaises(SystemExit):
                module.build(duplicate, output)

            insecure = root / "insecure.json"
            insecure_record = dict(base)
            insecure_record["evidenceId"] = "http-source"
            insecure_record["sourceUrl"] = "http://example.org/source"
            insecure.write_text(json.dumps([insecure_record]), encoding="utf-8")
            with self.assertRaises(SystemExit):
                module.build(insecure, output)

    def test_rejects_unknown_domain_and_class(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            output = root / "e.sqlite"
            base = {
                "evidenceId": "bad",
                "speciesId": "x",
                "domain": "UNBOUNDED_MEDICAL_CLAIM",
                "evidenceClass": "PUBLIC_HEALTH_TOXICOLOGY",
                "title": "Title",
                "statement": "Statement",
                "sourceName": "Authority",
                "sourceUrl": "https://example.org/source",
                "scopeNote": "Bounded statement.",
            }
            source = root / "bad.json"
            source.write_text(json.dumps([base]), encoding="utf-8")
            with self.assertRaises(SystemExit):
                module.build(source, output)

            base["domain"] = "TOXICOLOGY"
            base["evidenceClass"] = "UNVERIFIED_FOLK_CLAIM"
            source.write_text(json.dumps([base]), encoding="utf-8")
            with self.assertRaises(SystemExit):
                module.build(source, output)


if __name__ == "__main__":
    unittest.main()
