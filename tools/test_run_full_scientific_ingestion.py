import csv
import importlib.util
import tempfile
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("run_full_scientific_ingestion.py")
spec = importlib.util.spec_from_file_location("full_ingestion", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_csv(path: Path, fieldnames, rows):
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def test_manifest_registers_broad_scientific_domains_and_only_explicit_bulk_source():
    manifest = module.load_manifest()
    required = {
        "wfo-taxonomic-backbone",
        "prc-vietnam-genebank",
        "moh-traditional-medicine",
        "vietnam-pharmacopoeia-vi",
        "national-institute-medicinal-materials",
        "who-medicinal-plants-vietnam",
        "fao-wiews",
        "irri-rice-knowledge-bank",
        "worldveg-genebank",
        "icrisat-genebank",
        "cip-genebank",
        "gbif",
        "fda-food-drug-interactions",
        "nccih-herb-drug-interactions",
        "nih-ods",
    }
    assert required.issubset(manifest.keys())
    assert manifest["wfo-taxonomic-backbone"]["ingestion"] == "bulk_open"
    assert manifest["prc-vietnam-genebank"]["ingestion"] != "bulk_open"


def test_wfo_bulk_rows_can_be_merged_without_medical_claims():
    manifest = module.load_manifest()
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "wfo.csv"
        write_csv(
            path,
            ["taxonID", "scientificName", "family", "genus", "taxonomicStatus"],
            [
                {"taxonID": "wfo-1", "scientificName": "Oryza sativa L.", "family": "Poaceae", "genus": "Oryza", "taxonomicStatus": "Accepted"},
                {"taxonID": "wfo-2", "scientificName": "Curcuma longa L.", "family": "Zingiberaceae", "genus": "Curcuma", "taxonomicStatus": "Accepted"},
            ],
        )
        rows = list(module.import_source("wfo-taxonomic-backbone", path, manifest, set()))
        assert len(rows) == 2
        assert all(r["provenance"]["scope"] == "taxonomy-only" for r in rows)
        assert all("edible" not in r and "treatment" not in r and "medicinal" not in r for r in rows)


def test_reference_publications_are_not_misused_as_generic_bulk_taxonomy():
    manifest = module.load_manifest()
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "publication.csv"
        path.write_text("id,title\n1,test\n", encoding="utf-8")
        try:
            list(module.import_source("who-medicinal-plants-vietnam", path, manifest, set()))
            assert False, "medical publication must require a source-specific evidence adapter"
        except SystemExit as exc:
            assert "source-specific" in str(exc).lower()
