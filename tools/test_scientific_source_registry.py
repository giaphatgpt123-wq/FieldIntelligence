import importlib.util
import json
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("validate_scientific_source_registry.py")
spec = importlib.util.spec_from_file_location("source_registry_validator", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)

REGISTRY_PATH = Path(__file__).parents[1] / "data" / "scientific" / "source_registry.json"


def load_registry():
    return json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))


def test_registry_is_valid_and_has_diverse_official_domains():
    payload = load_registry()
    assert module.validate(payload) == []
    ids = {item["id"] for item in payload["sources"]}
    assert "wfo-taxonomic-backbone" in ids
    assert "prc-vietnam-genebank" in ids
    assert "moh-traditional-medicine" in ids
    assert "vietnam-pharmacopoeia" in ids
    assert "nimm-vietnam" in ids
    assert "who-medicinal-plants" in ids
    assert "fao-wiews-glis" in ids
    assert "irri" in ids


def test_medicinal_sources_cannot_be_auto_bulk_imported():
    payload = load_registry()
    medicinal = [
        item for item in payload["sources"]
        if {"traditional_medicine", "medicinal_materials", "medicinal_plants", "pharmacopoeia", "herbal_medicines"}.intersection(item["domains"])
    ]
    assert medicinal
    assert all(item["ingestPolicy"] != "bulk_allowed" for item in medicinal)


def test_review_rights_never_pair_with_bulk_allowed():
    payload = load_registry()
    assert all(
        not (item["rightsStatus"].startswith("REVIEW_") and item["ingestPolicy"] == "bulk_allowed")
        for item in payload["sources"]
    )
