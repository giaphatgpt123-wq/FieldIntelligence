import sqlite3
import tempfile
import unittest
from pathlib import Path

from audit_visual_model_taxonomy import audit, read_labels


class VisualModelTaxonomyAuditTest(unittest.TestCase):
    def test_reports_coverage_without_claiming_recognition_accuracy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            labels = root / "labels.csv"
            labels.write_text("0,background\n1,Ipomoea batatas\n2,Unknown plant\n", encoding="utf-8")
            db_path = root / "taxonomy.sqlite"
            with sqlite3.connect(db_path) as db:
                db.execute("CREATE TABLE taxon(scientific_name_search TEXT)")
                db.execute("INSERT INTO taxon VALUES (?)", ("ipomoea batatas (l.) lam.",))
            report = audit(read_labels(labels), db_path)
            self.assertEqual(report["labelCount"], 2)
            self.assertEqual(report["taxonomyMatched"], 1)
            self.assertEqual(report["unmatchedLabels"], ["Unknown plant"])
            self.assertIn("not camera accuracy", report["meaning"])

    def test_duplicate_labels_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with sqlite3.connect(root / "db.sqlite") as db:
                db.execute("CREATE TABLE taxon(scientific_name_search TEXT)")
            with self.assertRaisesRegex(ValueError, "Duplicate"):
                audit(["Musa acuminata", "musa acuminata"], root / "db.sqlite")


if __name__ == "__main__":
    unittest.main()
