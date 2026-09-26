import csv
import gzip
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_gbif_library.py")
spec = importlib.util.spec_from_file_location("gbif_adapter", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_tsv(path: Path, rows):
    fields = [
        "gbifID", "scientificName", "kingdom", "class", "family", "genus",
        "basisOfRecord", "countryCode", "decimalLatitude", "decimalLongitude", "datasetKey",
        "order", "mediaType", "identifier", "creator", "rightsHolder", "mediaLicense"
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)


class GbifScientificLibraryAdapterTest(unittest.TestCase):
    def test_gbif_adapter_preserves_license_and_groups(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "occurrence.txt"
            output = root / "gbif.ndjson.gz"
            meta = root / "gbif.meta.json"
            write_tsv(source, [
                {"gbifID": "1", "scientificName": "Apis cerana Fabricius, 1793", "kingdom": "Animalia", "class": "Insecta", "family": "Apidae", "genus": "Apis", "basisOfRecord": "HUMAN_OBSERVATION", "countryCode": "VN", "decimalLatitude": "10.1", "decimalLongitude": "106.1", "datasetKey": "d1"},
                {"gbifID": "2", "scientificName": "Ganoderma lucidum (Curtis) P. Karst.", "kingdom": "Fungi", "class": "Agaricomycetes", "family": "Ganodermataceae", "genus": "Ganoderma", "basisOfRecord": "PRESERVED_SPECIMEN", "countryCode": "VN", "decimalLatitude": "11.1", "decimalLongitude": "107.1", "datasetKey": "d1"},
            ])
            result = module.build(source, output, meta, "10.15468/dl.test", "Test publisher", "CC-BY-4.0")
            self.assertEqual(2, result["recordCount"])
            self.assertEqual(1, result["groupCounts"]["Côn trùng"])
            self.assertEqual(1, result["groupCounts"]["Nấm"])
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                rows = [json.loads(line) for line in handle if line.strip()]
            self.assertTrue(all(r["provenance"]["license"] == "CC-BY-4.0" for r in rows))
            self.assertTrue(all(r["provenance"]["datasetDoi"] == "10.15468/dl.test" for r in rows))
            self.assertTrue(all("danger" not in r and "edible" not in r and "treatment" not in r for r in rows))


    def test_fish_is_grouped_separately_and_media_provenance_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "occurrence.txt"
            output = root / "fish.ndjson.gz"
            meta = root / "fish.meta.json"
            write_tsv(source, [{
                "gbifID": "fish-1",
                "scientificName": "Channa striata (Bloch, 1793)",
                "kingdom": "Animalia",
                "class": "Actinopterygii",
                "order": "Anabantiformes",
                "family": "Channidae",
                "genus": "Channa",
                "countryCode": "VN",
                "mediaType": "StillImage",
                "identifier": "https://example.org/fish.jpg",
                "creator": "Example photographer",
                "rightsHolder": "Example collection",
                "mediaLicense": "CC-BY-4.0",
            }])
            result = module.build(source, output, meta, "10.15468/dl.fish", "Fish dataset", "CC-BY-4.0")
            self.assertEqual(1, result["groupCounts"]["Cá nước ngọt"])
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                record = json.loads(next(handle))
            self.assertEqual("Cá nước ngọt", record["libraryGroup"])
            self.assertEqual("https://example.org/fish.jpg", record["media"]["identifier"])
            self.assertEqual("Example photographer", record["media"]["creator"])
            self.assertEqual("CC-BY-4.0", record["media"]["license"])

    def test_noncommercial_license_is_explicit_and_missing_provenance_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "occurrence.txt"
            write_tsv(source, [{"gbifID": "3", "scientificName": "Varanus salvator", "kingdom": "Animalia", "class": "Reptilia"}])
            output = root / "out.gz"
            meta = root / "meta.json"
            result = module.build(source, output, meta, "10.15468/dl.example", "Publisher", "CC-BY-NC-4.0")
            self.assertTrue(result["nonCommercialRestriction"])
            with self.assertRaises(SystemExit) as ctx:
                module.build(source, output, meta, "bad-doi", "Publisher", "CC0-1.0")
            self.assertIn("doi", str(ctx.exception).lower())


if __name__ == "__main__":
    unittest.main()
