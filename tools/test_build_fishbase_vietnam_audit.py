import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_fishbase_vietnam_audit.py")
spec = importlib.util.spec_from_file_location("fishbase_audit", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class FishBaseVietnamAuditTest(unittest.TestCase):
    def test_presence_mapping_is_conservative(self):
        self.assertEqual("present", module.classify_presence("Present", "native"))
        self.assertEqual("present", module.classify_presence("", "introduced"))
        self.assertEqual("review", module.classify_presence("Possible", "questionable"))
        self.assertEqual("review", module.classify_presence("", "stray"))
        self.assertEqual("excluded", module.classify_presence("Absent", "misidentification"))
        self.assertEqual("excluded", module.classify_presence("", "not established"))
        self.assertEqual("review", module.classify_presence("", "unknown value"))

    def test_freshwater_values(self):
        for value in (1, 1.0, "1", "Y", "yes", "true", "Freshwater"):
            self.assertTrue(module.freshwater_value(value), value)
        for value in (0, "0", "N", "no", "Saltwater", ""):
            self.assertFalse(module.freshwater_value(value), value)

    def test_column_alias_matching_and_source_url(self):
        columns = ["autoctr", "SpecCode", "C_Code", "Freshwater", "CurrentPresence"]
        self.assertEqual("SpecCode", module.choose_column(columns, "speccode"))
        self.assertEqual("C_Code", module.choose_column(columns, "CCode", "C_Code"))
        self.assertEqual(
            "https://data.source.coop/cboettig/fishbase/fb/v26.07/parquet/country.parquet",
            module.source_url("26.07", "country"),
        )
        with self.assertRaises(ValueError):
            module.source_url("latest", "country")

    def test_report_preserves_audit_only_license_boundary(self):
        rows = [
            {"presenceStatus": "present"},
            {"presenceStatus": "review"},
            {"presenceStatus": "excluded"},
        ]
        report = module.report_for(rows, "26.07")
        self.assertEqual(3, report["total"])
        self.assertEqual(1, report["present"])
        self.assertEqual("CC-BY-NC-4.0", report["sourceLicense"])
        self.assertEqual("audit-crosscheck-only", report["use"])


if __name__ == "__main__":
    unittest.main()
