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
                self.assertEqual("taxonomy-media-occurrence", json.loads(meta["scope"]))
            finally:
                db.close()

    def test_preserves_fish_media_vernacular_and_occurrence(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "fish.ndjson.gz"
            output = root / "fish.sqlite"
            row = {
                "sourceId": "gbif", "sourceRecordId": "fish-1",
                "scientificName": "Channa striata (Bloch, 1793)", "taxonomicStatus": "Accepted",
                "kingdom": "Animalia", "class": "Actinopterygii", "order": "Anabantiformes",
                "family": "Channidae", "genus": "Channa", "libraryGroup": "Cá nước ngọt",
                "vernacularName": "Cá lóc",
                "media": {"mediaType": "StillImage", "identifier": "https://example.org/channa.jpg",
                          "creator": "Photographer", "rightsHolder": "Collection", "license": "CC-BY-4.0"},
                "occurrence": {"countryCode": "VN", "stateProvince": "An Giang",
                               "basisOfRecord": "HUMAN_OBSERVATION", "datasetKey": "dataset-1"},
                "provenance": {"authority": "GBIF", "license": "CC-BY-4.0",
                               "scope": "taxonomy-occurrence-and-media-metadata"}
            }
            with gzip.open(source, "wt", encoding="utf-8") as handle:
                handle.write(json.dumps(row, ensure_ascii=False) + "\n")
            result = module.build(source, output)
            self.assertEqual(1, result["mediaRecordCount"])
            self.assertEqual(1, result["vernacularNameCount"])
            self.assertEqual(1, result["occurrenceSummaryCount"])
            db = sqlite3.connect(output)
            try:
                self.assertEqual(("https://example.org/channa.jpg", "Photographer", "CC-BY-4.0"),
                    db.execute("SELECT media_identifier,creator,media_license FROM species_media").fetchone())
                self.assertEqual(("Cá lóc",), db.execute("SELECT vernacular_name FROM vernacular_name").fetchone())
                self.assertEqual(("VN", "An Giang"),
                    db.execute("SELECT country_code,state_province FROM occurrence_summary").fetchone())
            finally:
                db.close()

    def test_persists_multiple_media_and_rejects_unlicensed_media(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "fish-multi.ndjson.gz"
            output = root / "fish-multi.sqlite"
            row = {
                "sourceId": "gbif", "sourceRecordId": "fish-multi-1",
                "scientificName": "Channa striata", "taxonomicStatus": "Accepted",
                "kingdom": "Animalia", "class": "Actinopterygii",
                "libraryGroup": "Cá nước ngọt",
                "mediaItems": [
                    {"identifier": "https://example.org/1.jpg", "mediaType": "StillImage",
                     "creator": "A", "license": "CC-BY-4.0"},
                    {"identifier": "https://example.org/2.jpg", "mediaType": "StillImage",
                     "creator": "B", "license": "CC0-1.0"},
                    {"identifier": "https://example.org/3.jpg", "mediaType": "StillImage",
                     "creator": "Unknown", "license": ""},
                ],
                "provenance": {"authority": "GBIF", "license": "CC-BY-4.0",
                               "scope": "taxonomy-occurrence-and-media-metadata"}
            }
            with gzip.open(source, "wt", encoding="utf-8") as handle:
                handle.write(json.dumps(row) + "\n")
            result = module.build(source, output)
            self.assertEqual(2, result["mediaRecordCount"])
            self.assertEqual(1, result["recordsWithMedia"])
            db = sqlite3.connect(output)
            try:
                media = db.execute(
                    "SELECT media_identifier,media_license FROM species_media ORDER BY media_identifier"
                ).fetchall()
                self.assertEqual([
                    ("https://example.org/1.jpg", "CC-BY-4.0"),
                    ("https://example.org/2.jpg", "CC0-1.0"),
                ], media)
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
