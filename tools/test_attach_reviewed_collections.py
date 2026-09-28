import sqlite3
import tempfile
import unittest
from pathlib import Path

from tools.attach_reviewed_collections import attach


class ReviewedCollectionsTest(unittest.TestCase):
    def test_only_reviewed_exact_names_are_attached(self):
        with tempfile.TemporaryDirectory() as temporary:
            db_path = Path(temporary) / "taxonomy.sqlite"
            csv_path = Path(temporary) / "reviewed.csv"
            with sqlite3.connect(db_path) as db:
                db.execute("CREATE TABLE taxon (source_id TEXT, source_record_id TEXT, scientific_name_search TEXT)")
                db.execute("INSERT INTO taxon VALUES ('wfo','1','mangifera indica l.')")
            csv_path.write_text("collection_id,scientific_name,vietnamese_name,source_url,reviewed_by\n"
                                "fruit-crops,Mangifera indica L.,Xoài,https://example.org/review,nguyen\n")
            self.assertEqual(attach(db_path, csv_path)["fruit-crops"], 1)
            with sqlite3.connect(db_path) as db:
                self.assertEqual(db.execute("SELECT vietnamese_name FROM reviewed_collection").fetchone()[0], "Xoài")
            csv_path.write_text("collection_id,scientific_name,vietnamese_name,source_url,reviewed_by\n"
                                "timber-trees,Imaginary tree,Tree,https://example.org/review,nguyen\n")
            with self.assertRaises(ValueError):
                attach(db_path, csv_path)
            with sqlite3.connect(db_path) as db:
                self.assertEqual(db.execute("SELECT COUNT(*) FROM reviewed_collection").fetchone()[0], 1)


if __name__ == "__main__":
    unittest.main()
