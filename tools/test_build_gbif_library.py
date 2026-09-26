import csv
import gzip
import importlib.util
import json
import tempfile
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_gbif_library.py")
spec = importlib.util.spec_from_file_location("gbif_adapter", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_tsv(path: Path, rows):
    fields = [
        "gbifID", "scientificName", "kingdom", "class", "family", "genus",
        "basisOfRecord", "countryCode", "decimalLatitude", "decimalLongitude", "datasetKey"
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)


def test_gbif_adapter_preserves_license_and_groups():
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
        assert result["recordCount"] == 2
        assert result["groupCounts"]["Côn trùng"] == 1
        assert result["groupCounts"]["Nấm"] == 1
        with gzip.open(output, "rt", encoding="utf-8") as handle:
            rows = [json.loads(line) for line in handle if line.strip()]
        assert all(r["provenance"]["license"] == "CC-BY-4.0" for r in rows)
        assert all(r["provenance"]["datasetDoi"] == "10.15468/dl.test" for r in rows)
        assert all("danger" not in r and "edible" not in r and "treatment" not in r for r in rows)


def test_noncommercial_license_is_explicit_and_missing_provenance_is_rejected():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        source = root / "occurrence.txt"
        write_tsv(source, [{"gbifID": "3", "scientificName": "Varanus salvator", "kingdom": "Animalia", "class": "Reptilia"}])
        output = root / "out.gz"
        meta = root / "meta.json"
        result = module.build(source, output, meta, "10.15468/dl.example", "Publisher", "CC-BY-NC-4.0")
        assert result["nonCommercialRestriction"] is True
        try:
            module.build(source, output, meta, "bad-doi", "Publisher", "CC0-1.0")
            assert False, "invalid DOI must fail"
        except SystemExit as exc:
            assert "doi" in str(exc).lower()
