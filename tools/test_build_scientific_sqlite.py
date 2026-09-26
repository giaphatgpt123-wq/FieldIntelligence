import gzip
import importlib.util
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_scientific_sqlite.py")
spec = importlib.util.spec_from_file_location("scientific_sqlite", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class ScientificSqliteBuilderTest(unittest.TestCase):
    def test_builds_searchable_taxonomy_only_database(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "taxa.ndjson.gz"
            source_meta = root / "meta.json"
            output = root / "scientific.sqlite"
            rows = [
                {
                    "sourceId": "wfo-taxonomic-backbone", "sourceRecordId": "wfo-1",
                    "scientificName": "Oryza sativa L.", "acceptedNameUsageId": "", "taxonomicStatus": "Accepted",
                    "kingdom": "Plantae", "phylum": "Tracheophyta", "class": "Liliopsida", "order": "Poales",
                    "family": "Poaceae", "genus": "Oryza", "specificEpithet": "sativa", "libraryGroup": "Thực vật",
                    "provenance": {"authority": "World Flora Online Consortium", "license": "CC0-1.0", "scope": "taxonomy-only", "version": "2026-06", "versionDoi": "10.5281/zenodo.20782718"}
                },
                {
                    "sourceId": "wfo-taxonomic-backbone", "sourceRecordId": "wfo-2",
                    "scientificName": "Curcuma longa L.", "acceptedNameUsageId": "", "taxonomicStatus": "Accepted",
                    "kingdom": "Plantae", "family": "Zingiberaceae", "genus": "Curcuma", "specificEpithet": "longa", "libraryGroup": "Thực vật",
                    "provenance": {"authority": "World Flora Online Consortium", "license": "CC0-1.0", "scope": "taxonomy-only", "version": "2026-06", "versionDoi": "10.5281/zenodo.20782718"}
                }
            ]
            with gzip.open(source, "wt", encoding="utf-8") as handle:
                for row in rows:
                    handle.write(json.dumps(row, ensure_ascii=False) + "\n")
            source_meta.write_text(json.dumps({"sourceId": "wfo-taxonomic-backbone", "version": "2026-06", "versionDoi": "10.5281/zenodo.20782718", "license": "CC0-1.0"}), encoding="utf-8")
            result = module.build(source, output, source_meta)
            self.assertEqual(2, result["recordCount"])
            self.assertEqual(2, result["sqliteRowCount"])
            self.assertFalse(result["medicalClaimsIncluded"])
            db = sqlite3.connect(output)
            try:
                hit = db.execute("SELECT scientific_name, family FROM taxon WHERE scientific_name_search = ?", ("oryza sativa l.",)).fetchone()
                self.assertEqual(("Oryza sativa L.", "Poaceae"), hit)
                meta = dict(db.execute("SELECT key,value FROM meta"))
                self.assertEqual("taxonomy-only", json.loads(meta["scope"]))
            finally:
                db.close()

    def test_rejects_count_mismatch_via_primary_key_replacement(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "dup.ndjson.gz"
            output = root / "scientific.sqlite"
            row = {"sourceId": "wfo", "sourceRecordId": "same", "scientificName": "A a", "taxonomicStatus": "Accepted", "provenance": {}}
            with gzip.open(source, "wt", encoding="utf-8") as handle:
                handle.write(json.dumps(row) + "\n")
                handle.write(json.dumps(row) + "\n")
            with self.assertRaises(SystemExit) as ctx:
                module.build(source, output)
            self.assertIn("row-count mismatch", str(ctx.exception).lower())


if __name__ == "__main__":
    unittest.main()
