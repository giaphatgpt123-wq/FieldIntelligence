import hashlib
import importlib.util
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("merge_scientific_sqlite.py")
spec = importlib.util.spec_from_file_location("scientific_sqlite_merge", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def make_db(path: Path, source_id: str, record_id: str, scientific: str, group: str, with_media: bool = False):
    db = sqlite3.connect(path)
    db.executescript("""
    CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL);
    CREATE TABLE taxon(
      source_id TEXT NOT NULL,source_record_id TEXT NOT NULL,scientific_name TEXT NOT NULL,
      scientific_name_search TEXT NOT NULL,accepted_name_usage_id TEXT NOT NULL DEFAULT '',
      taxonomic_status TEXT NOT NULL DEFAULT '',kingdom TEXT NOT NULL DEFAULT '',phylum TEXT NOT NULL DEFAULT '',
      class_name TEXT NOT NULL DEFAULT '',order_name TEXT NOT NULL DEFAULT '',family TEXT NOT NULL DEFAULT '',
      genus TEXT NOT NULL DEFAULT '',specific_epithet TEXT NOT NULL DEFAULT '',library_group TEXT NOT NULL DEFAULT '',
      authority TEXT NOT NULL DEFAULT '',license TEXT NOT NULL DEFAULT '',source_scope TEXT NOT NULL DEFAULT '',
      source_version TEXT NOT NULL DEFAULT '',source_doi TEXT NOT NULL DEFAULT '',
      PRIMARY KEY(source_id,source_record_id)
    ) WITHOUT ROWID;
    CREATE TABLE species_media(
      source_id TEXT NOT NULL,source_record_id TEXT NOT NULL,media_identifier TEXT NOT NULL,
      media_type TEXT NOT NULL DEFAULT '',references_url TEXT NOT NULL DEFAULT '',title TEXT NOT NULL DEFAULT '',
      description TEXT NOT NULL DEFAULT '',creator TEXT NOT NULL DEFAULT '',rights_holder TEXT NOT NULL DEFAULT '',
      media_license TEXT NOT NULL DEFAULT '',PRIMARY KEY(source_id,source_record_id,media_identifier)
    ) WITHOUT ROWID;
    CREATE TABLE vernacular_name(
      source_id TEXT NOT NULL,source_record_id TEXT NOT NULL,vernacular_name TEXT NOT NULL,
      PRIMARY KEY(source_id,source_record_id,vernacular_name)
    ) WITHOUT ROWID;
    CREATE TABLE occurrence_summary(
      source_id TEXT NOT NULL,source_record_id TEXT NOT NULL,country_code TEXT NOT NULL DEFAULT '',
      state_province TEXT NOT NULL DEFAULT '',locality TEXT NOT NULL DEFAULT '',event_date TEXT NOT NULL DEFAULT '',
      basis_of_record TEXT NOT NULL DEFAULT '',dataset_key TEXT NOT NULL DEFAULT '',
      PRIMARY KEY(source_id,source_record_id)
    ) WITHOUT ROWID;
    CREATE TABLE source_reference(
      source_id TEXT NOT NULL,source_record_id TEXT NOT NULL,source_url TEXT NOT NULL,
      retrieved_at TEXT NOT NULL DEFAULT '',content_sha256 TEXT NOT NULL DEFAULT '',
      offline_state TEXT NOT NULL DEFAULT 'metadata-only',
      PRIMARY KEY(source_id,source_record_id,source_url)
    ) WITHOUT ROWID;
    """)
    for key, value in {"schemaVersion":2,"scope":"taxonomy-media-occurrence","recordCount":1}.items():
        db.execute("INSERT INTO meta VALUES(?,?)",(key,json.dumps(value)))
    db.execute("""INSERT INTO taxon VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",(
        source_id,record_id,scientific,scientific.casefold(),"","Accepted","Animalia" if group=="Cá nước ngọt" else "Plantae",
        "","Actinopterygii" if group=="Cá nước ngọt" else "","","","","",group,
        "test-authority","CC-BY-4.0" if group=="Cá nước ngọt" else "CC0-1.0","taxonomy-media-occurrence","test-v1","10.15468/dl.test" if group=="Cá nước ngọt" else "10.5281/zenodo.test"
    ))
    db.execute("INSERT INTO source_reference(source_id,source_record_id,source_url) VALUES(?,?,?)",
               (source_id,record_id,'https://example.org/source/'+record_id))
    if with_media:
        media_url="https://example.org/fish.jpg"; payload=b"offline-fish-image"; digest=hashlib.sha256(payload).hexdigest()
        db.execute("INSERT INTO species_media VALUES(?,?,?,?,?,?,?,?,?,?)",(source_id,record_id,media_url,"StillImage","","","","Tester","Collection","CC-BY-4.0"))
        db.execute("INSERT INTO vernacular_name VALUES(?,?,?)",(source_id,record_id,"Cá lóc"))
        db.execute("INSERT INTO occurrence_summary VALUES(?,?,?,?,?,?,?,?)",(source_id,record_id,"VN","An Giang","","","HUMAN_OBSERVATION","d1"))
        db.executescript("""
        CREATE TABLE scientific_media_blob(sha256 TEXT PRIMARY KEY,mime_type TEXT NOT NULL,size_bytes INTEGER NOT NULL,media_blob BLOB NOT NULL) WITHOUT ROWID;
        CREATE TABLE species_media_local(source_id TEXT NOT NULL,source_record_id TEXT NOT NULL,source_identifier TEXT NOT NULL,sha256 TEXT NOT NULL,media_license TEXT NOT NULL,PRIMARY KEY(source_id,source_record_id,source_identifier)) WITHOUT ROWID;
        """)
        db.execute("INSERT INTO scientific_media_blob VALUES(?,?,?,?)",(digest,"image/jpeg",len(payload),sqlite3.Binary(payload)))
        db.execute("INSERT INTO species_media_local VALUES(?,?,?,?,?)",(source_id,record_id,media_url,digest,"CC-BY-4.0"))
    db.commit(); db.close()


class MergeScientificSqliteTest(unittest.TestCase):
    def test_merges_fish_and_offline_media_into_wfo_target(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); target=root/"wfo.sqlite"; fish=root/"fish.sqlite"
            make_db(target,"wfo","w1","Oryza sativa","Thực vật")
            make_db(fish,"gbif","g1","Channa striata","Cá nước ngọt",with_media=True)
            result=module.merge(target,fish,"WFO test + GBIF fish test")
            self.assertEqual(2,result["recordCount"])
            self.assertEqual(1,result["fishTaxa"])
            self.assertEqual(1,result["fishWithMedia"])
            self.assertEqual(1,result["fishWithLocalMedia"])
            self.assertEqual(0,result["fishPendingMedia"])
            self.assertEqual(1,result["localMediaBlobCount"])
            self.assertEqual("ok",result["integrity"])
            db=sqlite3.connect(target)
            try:
                self.assertEqual({("wfo","w1"),("gbif","g1")},set(db.execute("SELECT source_id,source_record_id FROM taxon")))
                self.assertEqual(("Cá lóc",),db.execute("SELECT vernacular_name FROM vernacular_name").fetchone())
                self.assertEqual(1,db.execute("SELECT COUNT(*) FROM scientific_media_blob").fetchone()[0])
                self.assertEqual(2,db.execute("SELECT COUNT(*) FROM source_reference").fetchone()[0])
                meta={k:json.loads(v) for k,v in db.execute("SELECT key,value FROM meta")}
                self.assertEqual("fieldintelligence-merged-scientific-library",meta["sourceId"])
                self.assertEqual("requires-verified-offline-media-for-main-library",meta["fishPublishRule"])
            finally:db.close()

    def test_rejects_duplicate_taxon_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); target=root/"a.sqlite"; source=root/"b.sqlite"
            make_db(target,"same","1","A a","Thực vật")
            make_db(source,"same","1","B b","Cá nước ngọt")
            with self.assertRaises(ValueError) as ctx: module.merge(target,source)
            self.assertIn("duplicate taxon key",str(ctx.exception).lower())


if __name__ == "__main__":
    unittest.main()
