import csv
import gzip
import importlib.util
import json
import tempfile
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("fetch_wfo_backbone.py")
spec = importlib.util.spec_from_file_location("fetch_wfo", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def make_archive(path: Path):
    rows = [
        {"taxonID": "wfo-1", "scientificName": "Oryza sativa L.", "family": "Poaceae", "genus": "Oryza", "taxonomicStatus": "Accepted"},
        {"taxonID": "wfo-2", "scientificName": "Curcuma longa L.", "family": "Zingiberaceae", "genus": "Curcuma", "taxonomicStatus": "Accepted"},
    ]
    buffer = []
    header = ["taxonID", "scientificName", "family", "genus", "taxonomicStatus"]
    import io
    text = io.StringIO()
    writer = csv.DictWriter(text, fieldnames=header)
    writer.writeheader()
    writer.writerows(rows)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("classification.csv", text.getvalue())
        archive.writestr("eml.xml", "<eml />")


def test_streamed_wfo_archive_builds_taxonomy_only_package():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        archive = root / "WFO_Backbone.zip"
        output = root / "wfo.ndjson.gz"
        metadata = root / "wfo.meta.json"
        make_archive(archive)
        result = module.build(archive, output, metadata)
        assert result["recordCount"] == 2
        assert result["acceptedRecordCount"] == 2
        assert result["license"] == "CC0-1.0"
        assert result["archiveMember"] == "classification.csv"
        with gzip.open(output, "rt", encoding="utf-8") as handle:
            records = [json.loads(line) for line in handle if line.strip()]
        assert records[0]["scientificName"] == "Oryza sativa L."
        assert all(r["provenance"]["scope"] == "taxonomy-only" for r in records)
        assert all("edible" not in r and "medicinal" not in r and "treatment" not in r for r in records)


def test_archive_without_taxonomy_table_is_rejected():
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "bad.zip"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("readme.txt", "no taxonomy")
        try:
            list(module.iter_rows_from_zip(path))
            assert False, "missing taxonomy table must fail"
        except SystemExit as exc:
            assert "taxonomy table" in str(exc).lower()
