import sqlite3
import tempfile
import unittest
from pathlib import Path

from tools.attach_reviewed_collections import attach


class ReviewedCollectionsTest(unittest.TestCase):
    def test_partial_batches_do_not_wait_for_goal(self):
        with tempfile.TemporaryDirectory() as temporary:
            db_path = Path(temporary) / "taxonomy.sqlite"
            csv_path = Path(temporary) / "reviewed.csv"
            goals_path = Path(temporary) / "goals.csv"
            goals_path.write_text("collection_id,target_count\nflowers,1000\n")
            with sqlite3.connect(db_path) as db:
                db.execute("CREATE TABLE taxon (source_id TEXT, source_record_id TEXT, scientific_name_search TEXT, library_group TEXT, taxonomic_status TEXT)")
                db.executemany("INSERT INTO taxon VALUES ('wfo',?,?,'Thực vật','accepted')", [(str(i), f"floris species{chr(97+i//26)}{chr(97+i%26)}") for i in range(1, 52)])
            header = "collection_id,scientific_name,vietnamese_name,source_url,reviewed_by\n"
            def batch(count):
                csv_path.write_text(header + "".join(
                    f"flowers,Floris species{chr(97+i//26)}{chr(97+i%26)},Hoa {i},https://example.org/{i},reviewer\n"
                    for i in range(1, count + 1)))
                return attach(db_path, csv_path, goals_path)["flowers"]
            self.assertEqual(batch(50), 50)
            self.assertEqual(batch(51), 51)
            with sqlite3.connect(db_path) as db:
                self.assertEqual(db.execute("SELECT COUNT(*) FROM reviewed_collection").fetchone()[0], 51)
                self.assertEqual(db.execute("SELECT target_count FROM reviewed_collection_goal").fetchone()[0], 1000)

    def test_only_reviewed_exact_names_are_attached(self):
        with tempfile.TemporaryDirectory() as temporary:
            db_path = Path(temporary) / "taxonomy.sqlite"
            csv_path = Path(temporary) / "reviewed.csv"
            goals_path = Path(temporary) / "goals.csv"
            with sqlite3.connect(db_path) as db:
                db.execute("CREATE TABLE taxon (source_id TEXT, source_record_id TEXT, scientific_name_search TEXT, library_group TEXT, taxonomic_status TEXT)")
                db.execute("INSERT INTO taxon VALUES ('wfo','1','mangifera indica','Thực vật','accepted')")
            csv_path.write_text("collection_id,scientific_name,vietnamese_name,source_url,reviewed_by\n"
                                "fruit-crops,Mangifera indica L.,Xoài,https://example.org/review,nguyen\n")
            goals_path.write_text("collection_id,target_count\nfruit-crops,1000\nflowers,\n")
            self.assertEqual(attach(db_path, csv_path, goals_path)["fruit-crops"], 1)
            with sqlite3.connect(db_path) as db:
                self.assertEqual(db.execute("SELECT vietnamese_name FROM reviewed_collection").fetchone()[0], "Xoài")
                self.assertEqual(db.execute("SELECT target_count FROM reviewed_collection_goal").fetchone()[0], 1000)
            csv_path.write_text("collection_id,scientific_name,vietnamese_name,source_url,reviewed_by\n"
                                "timber-trees,Imaginary tree,Tree,https://example.org/review,nguyen\n")
            with self.assertRaises(ValueError):
                attach(db_path, csv_path, goals_path)
            with sqlite3.connect(db_path) as db:
                self.assertEqual(db.execute("SELECT COUNT(*) FROM reviewed_collection").fetchone()[0], 1)


if __name__ == "__main__":
    unittest.main()
