import gzip
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_resolved_gbif_fish_library.py")
spec = importlib.util.spec_from_file_location("resolved_gbif_fish_builder", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def write_ndjson(path: Path, rows):
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def resolved_row(name: str, taxon_id: str, accepted: str, presence="present"):
    genus, epithet = accepted.split()[:2]
    return {
        "scientificName": name,
        "presenceStatus": presence,
        "taxonomyResolution": {
            "status": "resolved",
            "acceptedTaxonId": taxon_id,
            "acceptedScientificName": accepted,
            "acceptedCanonicalName": accepted,
            "acceptedGenericName": genus,
            "acceptedSpecificEpithet": epithet,
            "kingdom": "Animalia",
            "phylum": "Chordata",
            "class": "Actinopterygii",
            "order": "Anabantiformes",
            "family": "Channidae",
            "genus": genus,
            "evidenceSha256": "a" * 64,
        },
    }


class ResolvedGbifFishBuilderTest(unittest.TestCase):
    def test_rejects_video_even_when_type_contains_image(self):
        occurrence = {"license": "CC-BY-4.0"}
        self.assertIsNone(module.eligible_media_item(
            {"type": "MovingImage", "identifier": "https://example.org/video.mp4"}, occurrence,
        ))
        self.assertIsNotNone(module.eligible_media_item(
            {"type": "StillImage", "identifier": "https://example.org/fish.jpg"}, occurrence,
        ))

    def test_license_normalization(self):
        self.assertEqual("CC-BY-4.0", module.canonical_media_license("https://creativecommons.org/licenses/by/4.0/legalcode"))
        self.assertEqual("CC-BY-NC-4.0", module.canonical_media_license("CC BY-NC 4.0"))
        self.assertEqual("CC0-1.0", module.canonical_media_license("https://creativecommons.org/publicdomain/zero/1.0/"))
        self.assertEqual("", module.canonical_media_license("all rights reserved"))

    def test_prefers_vietnam_media_then_global_fallback(self):
        calls = []

        def searcher(taxon_key, country, limit):
            calls.append((taxon_key, country, limit))
            if country == "VN":
                return {"results": [{
                    "key": 1,
                    "countryCode": "VN",
                    "media": [{
                        "type": "StillImage",
                        "identifier": "https://images.example/vn.jpg",
                        "license": "https://creativecommons.org/licenses/by/4.0/",
                    }],
                }]}
            return {"results": [{
                "key": 2,
                "countryCode": "TH",
                "media": [{
                    "type": "StillImage",
                    "identifier": "https://images.example/global.jpg",
                    "license": "CC0-1.0",
                }],
            }]}

        items, fallback = module.discover_media("999", searcher, 2)
        self.assertTrue(fallback)
        self.assertEqual(["https://images.example/vn.jpg", "https://images.example/global.jpg"], [m["identifier"] for m in items])
        self.assertEqual([("999", "VN", 50), ("999", None, 50)], calls)

    def test_build_deduplicates_synonyms_and_looks_up_media_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "resolved.ndjson"
            output = root / "fish.ndjson.gz"
            meta_path = root / "fish.meta.json"
            rows = [
                resolved_row("Channa striata", "999", "Channa striata"),
                resolved_row("Ophicephalus striatus", "999", "Channa striata"),
                resolved_row("Possible fish", "555", "Possible fish", presence="review"),
            ]
            write_ndjson(checklist, rows)
            calls = []
            commons_calls = []

            def searcher(taxon_key, country, limit):
                calls.append((taxon_key, country, limit))
                if country == "VN":
                    return {"results": [{
                        "key": 10,
                        "countryCode": "VN",
                        "datasetKey": "dataset-1",
                        "media": [
                            {"type": "StillImage", "identifier": "https://images.example/channa.jpg", "license": "CC BY 4.0"},
                            {"type": "StillImage", "identifier": "http://images.example/reject.jpg", "license": "CC BY 4.0"},
                            {"type": "StillImage", "identifier": "https://images.example/reject-license.jpg", "license": "all rights reserved"},
                        ],
                    }]}
                return {"results": []}

            def commons_finder(name):
                commons_calls.append(name)
                return None, "should-not-be-called"

            meta = module.build(
                checklist,
                output,
                meta_path,
                fetch_media=True,
                max_media_candidates=2,
                delay_seconds=0,
                searcher=searcher,
                commons_finder=commons_finder,
            )
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                records = [json.loads(line) for line in handle if line.strip()]
            self.assertEqual(1, len(records))
            record = records[0]
            self.assertEqual("999", record["sourceRecordId"])
            self.assertEqual("Channa striata", record["scientificName"])
            self.assertEqual({"Channa striata", "Ophicephalus striatus"}, set(record["sourceScientificNames"]))
            self.assertEqual(1, len(record["mediaItems"]))
            self.assertEqual("CC-BY-4.0", record["mediaItems"][0]["license"])
            self.assertEqual(2, meta["presentChecklistRows"])
            self.assertEqual(1, meta["recordCount"])
            self.assertEqual(1, meta["recordsWithMedia"])
            self.assertEqual(0, meta["recordsWithoutMedia"])
            self.assertEqual(0, meta["resolverBlockedPresentRows"])
            self.assertEqual([("999", "VN", 50), ("999", None, 50)], calls)
            self.assertEqual([], commons_calls)

    def test_commons_fallback_fills_taxon_when_gbif_media_is_empty(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "resolved.ndjson"
            output = root / "fish.ndjson.gz"
            meta_path = root / "fish.meta.json"
            write_ndjson(checklist, [resolved_row("Channa striata", "999", "Channa striata")])
            calls = []

            def searcher(taxon_key, country, limit):
                return {"results": []}

            def commons_finder(name):
                calls.append(name)
                return ({
                    "mediaType": "StillImage",
                    "identifier": "https://upload.wikimedia.org/channa.jpg",
                    "references": "https://commons.wikimedia.org/wiki/File:Channa.jpg",
                    "creator": "Author",
                    "rightsHolder": "Collection",
                    "license": "CC-BY-4.0",
                    "sourceProvider": "Wikimedia Commons",
                    "mappingEvidence": "exact-P225-to-P18",
                }, "matched")

            meta = module.build(
                checklist,
                output,
                meta_path,
                fetch_media=True,
                max_media_candidates=2,
                delay_seconds=0,
                searcher=searcher,
                commons_finder=commons_finder,
            )
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                record = json.loads(next(line for line in handle if line.strip()))
            self.assertEqual(["Channa striata"], calls)
            self.assertEqual("Wikimedia Commons", record["mediaItems"][0]["sourceProvider"])
            self.assertEqual(1, meta["wikimediaCommonsFallbackAttempted"])
            self.assertEqual(1, meta["wikimediaCommonsFallbackAdded"])
            self.assertEqual(1, meta["recordsWithMedia"])
            self.assertEqual(0, meta["recordsWithoutMedia"])

    def test_missing_media_is_reported_by_taxon(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "resolved.ndjson"
            output = root / "fish.ndjson.gz"
            meta_path = root / "fish.meta.json"
            write_ndjson(checklist, [resolved_row("Rare fish", "777", "Rare fish")])
            meta = module.build(
                checklist,
                output,
                meta_path,
                fetch_media=True,
                delay_seconds=0,
                searcher=lambda taxon_key, country, limit: {"results": []},
                commons_finder=lambda name: (None, "wikidata-no-exact-p225"),
            )
            self.assertEqual(1, meta["recordsWithoutMedia"])
            self.assertEqual("777", meta["recordsWithoutMediaDetails"][0]["acceptedTaxonId"])
            self.assertEqual("Rare fish", meta["recordsWithoutMediaDetails"][0]["scientificName"])
            self.assertEqual({"wikidata-no-exact-p225": 1}, meta["wikimediaCommonsFallbackReasons"])

    def test_blocked_present_row_is_counted_not_silently_emitted(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            checklist = root / "resolved.ndjson"
            output = root / "fish.ndjson.gz"
            meta_path = root / "fish.meta.json"
            row = resolved_row("Blocked fish", "1", "Blocked fish")
            row["taxonomyResolution"]["status"] = "review"
            write_ndjson(checklist, [row])
            meta = module.build(checklist, output, meta_path, fetch_media=False, delay_seconds=0)
            self.assertEqual(1, meta["presentChecklistRows"])
            self.assertEqual(1, meta["resolverBlockedPresentRows"])
            self.assertEqual(0, meta["recordCount"])


if __name__ == "__main__":
    unittest.main()
