import importlib.util
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("attach_scientific_media_manifest.py")
spec = importlib.util.spec_from_file_location("media_attach", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class AttachScientificMediaManifestTest(unittest.TestCase):
    def make_db(self, path: Path):
        db = sqlite3.connect(path)
        db.executescript("""
        CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL);
        CREATE TABLE taxon(
          source_id TEXT NOT NULL,source_record_id TEXT NOT NULL,scientific_name TEXT NOT NULL,
          library_group TEXT NOT NULL,PRIMARY KEY(source_id,source_record_id)
        ) WITHOUT ROWID;
        CREATE TABLE species_media(
          source_id TEXT NOT NULL,source_record_id TEXT NOT NULL,media_identifier TEXT NOT NULL,
          media_license TEXT NOT NULL,PRIMARY KEY(source_id,source_record_id,media_identifier)
        ) WITHOUT ROWID;
        """)
        db.execute("INSERT INTO meta VALUES('schemaVersion','2')")
        db.execute("INSERT INTO taxon VALUES('gbif','1','Channa striata','Cá nước ngọt')")
        db.execute("INSERT INTO species_media VALUES('gbif','1','https://example.org/fish.jpg','CC-BY-4.0')")
        db.commit(); db.close()

    def test_attaches_verified_local_media(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); database = root / "science.sqlite"; manifest = root / "manifest.json"
            self.make_db(database)
            digest = "a" * 64
            manifest.write_text(json.dumps({
                "manifestVersion": 1,
                "scope": "scientific-reference-media-offline-cache",
                "records": [{
                    "sourceId": "gbif", "sourceRecordId": "1", "scientificName": "Channa striata",
                    "sourceIdentifier": "https://example.org/fish.jpg",
                    "localPath": f"media/aa/{digest}.jpg", "sha256": digest,
                    "sizeBytes": 1234, "license": "CC-BY-4.0"
                }]
            }), encoding="utf-8")
            result = module.attach(database, manifest)
            self.assertEqual(1, result["localMediaRecordCount"])
            self.assertEqual(1, result["fishWithLocalMedia"])
            db = sqlite3.connect(database)
            try:
                self.assertEqual((f"media/aa/{digest}.jpg", digest), db.execute("SELECT local_path,sha256 FROM species_media_local").fetchone())
                meta = {k: json.loads(v) for k,v in db.execute("SELECT key,value FROM meta")}
                self.assertEqual(1, meta["localMediaRecordCount"])
            finally: db.close()

    def test_rejects_path_hash_mismatch_and_license_mismatch(self):
        bad = {"sourceId":"gbif","sourceRecordId":"1","sourceIdentifier":"https://example.org/fish.jpg",
               "localPath":"../fish.jpg","sha256":"b"*64,"sizeBytes":10,"license":"CC-BY-4.0"}
        with self.assertRaises(ValueError): module.validate_entry(bad)
        bad["localPath"] = f"media/bb/{'a'*64}.jpg"
        with self.assertRaises(ValueError): module.validate_entry(bad)

    def test_unmatched_source_is_reported_not_published(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); database = root / "science.sqlite"; manifest = root / "manifest.json"
            self.make_db(database); digest = "c" * 64
            manifest.write_text(json.dumps({"manifestVersion":1,"scope":"scientific-reference-media-offline-cache","records":[{
                "sourceId":"gbif","sourceRecordId":"1","sourceIdentifier":"https://example.org/other.jpg",
                "localPath":f"media/cc/{digest}.jpg","sha256":digest,"sizeBytes":100,"license":"CC-BY-4.0"
            }]}),encoding="utf-8")
            result=module.attach(database,manifest)
            self.assertEqual(0,result["localMediaRecordCount"]); self.assertEqual(1,result["localMediaUnmatched"])


if __name__ == "__main__":
    unittest.main()
