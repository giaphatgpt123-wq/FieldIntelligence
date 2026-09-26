import csv
import importlib.util
import tempfile
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_scientific_library.py")
spec = importlib.util.spec_from_file_location("scientific_importer", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_csv(path: Path, fieldnames, rows):
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def test_wfo_normalization_keeps_taxonomy_scope_and_cc0():
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "taxon.csv"
        write_csv(path, ["taxonID", "scientificName", "family", "genus", "taxonomicStatus"], [
            {"taxonID": "wfo-1", "scientificName": "Mangifera indica L.", "family": "Anacardiaceae", "genus": "Mangifera", "taxonomicStatus": "Accepted"}
        ])
        records = list(module.build("wfo", path, False))
        assert len(records) == 1
        assert records[0]["sourceId"] == "wfo-taxonomic-backbone"
        assert records[0]["provenance"]["license"] == "CC0-1.0"
        assert records[0]["provenance"]["scope"] == "taxonomy-only"
        assert "edible" not in records[0]
        assert "medicinal" not in records[0]


def test_prc_import_requires_explicit_terms_confirmation():
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "prc.csv"
        write_csv(path, ["GBVN No", "Tên cây trồng", "Tên khoa học", "Nhóm"], [
            {"GBVN No": "GBVN000001", "Tên cây trồng": "Lúa", "Tên khoa học": "Oryza sativa L.", "Nhóm": "Lúa"}
        ])
        try:
            list(module.build("prc", path, False))
            assert False, "PRC import must be blocked until terms are confirmed"
        except SystemExit as exc:
            assert "terms" in str(exc).lower()
        records = list(module.build("prc", path, True))
        assert records[0]["sourceRecordId"] == "GBVN000001"
        assert records[0]["categories"] == ["Lúa"]
        assert records[0]["provenance"]["scope"] == "agricultural-genetic-resource-metadata"
