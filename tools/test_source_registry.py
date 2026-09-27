import json
import unittest
from pathlib import Path


class SourceRegistryTest(unittest.TestCase):
    def test_collections_have_separate_sources_and_license_rules(self):
        path = Path(__file__).parents[1] / "data/scientific/source_registry.json"
        registry = json.loads(path.read_text(encoding="utf-8"))
        self.assertTrue(registry["policy"]["downloadOnlyLicensedMedia"])
        self.assertNotEqual(
            {s["id"] for s in registry["collections"]["freshwater-fish-vietnam"]["sources"]},
            {s["id"] for s in registry["collections"]["medicinal-plants-vietnam"]["sources"]},
        )
        self.assertTrue(any(s["id"] == "tracuuduoclieu" for s in registry["collections"]["medicinal-plants-vietnam"]["sources"]))


if __name__ == "__main__":
    unittest.main()
