import gzip
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("enrich_wikidata_commons_fish_media.py")
spec = importlib.util.spec_from_file_location("commons_fish_media", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def claim(value):
    return {"mainsnak": {"snaktype": "value", "datavalue": {"value": value}}}


def item_claim(item_id):
    return claim({"entity-type": "item", "numeric-id": int(item_id[1:]), "id": item_id})


class CommonsFishMediaTest(unittest.TestCase):
    def test_only_exact_taxon_p225_and_allowed_license_is_accepted(self):
        searcher = lambda name: {"search": [{"id": "Q1"}, {"id": "Q2"}]}
        entities = lambda ids: {"entities": {
            "Q1": {"claims": {
                "P31": [item_claim("Q16521")],
                "P225": [claim("Wrong fish")],
                "P18": [claim("Wrong.jpg")],
            }},
            "Q2": {"claims": {
                "P31": [item_claim("Q16521")],
                "P225": [claim("Channa striata")],
                "P18": [claim("Channa.jpg")],
            }},
        }}
        commons = lambda filename: {"query": {"pages": [{
            "title": "File:Channa.jpg",
            "imageinfo": [{
                "url": "https://upload.wikimedia.org/channa.jpg",
                "descriptionurl": "https://commons.wikimedia.org/wiki/File:Channa.jpg",
                "extmetadata": {
                    "LicenseShortName": {"value": "CC BY 4.0"},
                    "LicenseUrl": {"value": "https://creativecommons.org/licenses/by/4.0/"},
                    "Artist": {"value": "<b>Example Author</b>"},
                    "Credit": {"value": "Example Collection"},
                    "ImageDescription": {"value": "Reference image"},
                },
            }],
        }]}}
        item, reason = module.find_exact_taxon_image("Channa striata", searcher, entities, commons)
        self.assertEqual("matched", reason)
        self.assertIsNotNone(item)
        self.assertEqual("Q2", item["wikidataItem"])
        self.assertEqual("Q16521", item["wikidataInstanceOf"])
        self.assertEqual("CC-BY-4.0", item["license"])
        self.assertEqual("Example Author", item["creator"])
        self.assertEqual("P31-taxon+exact-P225-to-P18", item["mappingEvidence"])

    def test_exact_p225_without_taxon_instance_is_rejected(self):
        searcher = lambda name: {"search": [{"id": "Q1"}]}
        entities = lambda ids: {"entities": {"Q1": {"claims": {
            "P31": [item_claim("Q5")],
            "P225": [claim("Channa striata")],
            "P18": [claim("Wrong.jpg")],
        }}}}
        commons_calls = []

        def commons(filename):
            commons_calls.append(filename)
            return {"query": {"pages": []}}

        item, reason = module.find_exact_taxon_image("Channa striata", searcher, entities, commons)
        self.assertIsNone(item)
        self.assertEqual("wikidata-no-exact-taxon-p225", reason)
        self.assertEqual([], commons_calls)

    def test_sharealike_or_nonfree_is_not_accepted_yet(self):
        searcher = lambda name: {"search": [{"id": "Q1"}]}
        entities = lambda ids: {"entities": {"Q1": {"claims": {
            "P31": [item_claim("Q16521")],
            "P225": [claim("Channa striata")],
            "P18": [claim("Channa.jpg")],
        }}}}
        commons = lambda filename: {"query": {"pages": [{"imageinfo": [{
            "url": "https://upload.wikimedia.org/channa.jpg",
            "extmetadata": {
                "LicenseShortName": {"value": "CC BY-SA 4.0"},
                "LicenseUrl": {"value": "https://creativecommons.org/licenses/by-sa/4.0/"},
            },
        }]}]}}
        item, reason = module.find_exact_taxon_image("Channa striata", searcher, entities, commons)
        self.assertIsNone(item)
        self.assertEqual("wikidata-exact-taxon-without-allowed-commons-image", reason)

    def test_enrich_only_fills_missing_media(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            input_path = root / "in.ndjson.gz"
            output_path = root / "out.ndjson.gz"
            report_path = root / "report.json"
            metadata_path = root / "meta.json"
            rows = [
                {"scientificName": "A a", "species": "A a", "mediaItems": [{"identifier": "https://example/a.jpg", "license": "CC0-1.0"}]},
                {"scientificName": "B b", "species": "B b", "mediaItems": []},
                {"scientificName": "C c", "species": "C c", "mediaItems": []},
            ]
            with gzip.open(input_path, "wt", encoding="utf-8") as handle:
                for row in rows:
                    handle.write(json.dumps(row) + "\n")
            metadata_path.write_text(json.dumps({
                "normalizedNdjsonSha256": "oldhash",
                "recordsWithMedia": 1,
                "recordsWithoutMedia": 2,
            }), encoding="utf-8")

            def finder(name):
                if name == "B b":
                    return ({
                        "mediaType": "StillImage",
                        "identifier": "https://upload.wikimedia.org/b.jpg",
                        "references": "https://commons.wikimedia.org/wiki/File:B.jpg",
                        "creator": "Author",
                        "rightsHolder": "",
                        "license": "CC0-1.0",
                    }, "matched")
                return None, "wikidata-no-exact-taxon-p225"

            report = module.enrich(
                input_path,
                output_path,
                report_path,
                metadata_path=metadata_path,
                finder=finder,
                delay_seconds=0,
            )
            self.assertEqual(3, report["recordCount"])
            self.assertEqual(1, report["alreadyHadMedia"])
            self.assertEqual(2, report["fallbackAttempted"])
            self.assertEqual(1, report["fallbackAdded"])
            self.assertEqual(1, report["remainingWithoutMedia"])
            self.assertEqual(2, report["recordsWithMedia"])
            self.assertEqual(64, len(report["normalizedNdjsonSha256"]))
            with gzip.open(output_path, "rt", encoding="utf-8") as handle:
                out = [json.loads(line) for line in handle if line.strip()]
            self.assertEqual("https://example/a.jpg", out[0]["mediaItems"][0]["identifier"])
            self.assertEqual("https://upload.wikimedia.org/b.jpg", out[1]["mediaItems"][0]["identifier"])
            self.assertEqual([], out[2]["mediaItems"])
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            self.assertEqual("oldhash", metadata["gbifOnlyNdjsonSha256"])
            self.assertEqual(report["normalizedNdjsonSha256"], metadata["normalizedNdjsonSha256"])
            self.assertEqual(2, metadata["recordsWithMedia"])
            self.assertEqual(1, metadata["recordsWithoutMedia"])
            self.assertEqual(2, metadata["wikimediaCommonsFallbackAttempted"])
            self.assertEqual(1, metadata["wikimediaCommonsFallbackAdded"])


if __name__ == "__main__":
    unittest.main()
