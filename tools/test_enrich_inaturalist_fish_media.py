import unittest

from tools.enrich_inaturalist_fish_media import find_exact_taxon_image


class InaturalistFishMediaTest(unittest.TestCase):
    def test_exact_taxon_and_photo_license_are_individually_checked(self):
        def fetch(path, params):
            if path == "/taxa/autocomplete":
                return {"results": [{"id": 42, "name": "Channa striata", "rank": "species", "iconic_taxon_name": "Actinopterygii", "is_active": True}]}
            self.assertEqual(42, params["taxon_id"])
            return {"results": [
                {"id": 5, "quality_grade": "research", "taxon": {"id": 42, "name": "Channa striata"}, "photos": [{"url": "https://example.org/photos/5/square.jpg", "license_code": None}]},
                {"id": 6, "quality_grade": "research", "taxon": {"id": 42, "name": "Channa striata"}, "photos": [{"id": 7, "url": "https://example.org/photos/7/square.jpg", "license_code": "cc-by", "attribution": "Author"}]},
            ]}
        item, reason = find_exact_taxon_image("Channa striata", fetch)
        self.assertEqual("matched", reason)
        self.assertEqual("CC-BY-4.0", item["license"])
        self.assertEqual("https://example.org/photos/7/medium.jpg", item["identifier"])
        self.assertEqual("https://www.inaturalist.org/observations/6", item["references"])

    def test_rejects_fuzzy_and_higher_rank_results(self):
        item, reason = find_exact_taxon_image("Channa striata", lambda path, params: {"results": [{"id": 42, "name": "Channa", "rank": "genus", "iconic_taxon_name": "Actinopterygii", "is_active": True}]})
        self.assertIsNone(item)
        self.assertEqual("inat-no-unique-exact-fish-taxon", reason)

    def test_rejects_child_or_nonresearch_observation(self):
        def fetch(path, params):
            if path == "/taxa/autocomplete":
                return {"results": [{"id": 42, "name": "Channa striata", "rank": "species", "iconic_taxon_name": "Actinopterygii", "is_active": True}]}
            return {"results": [{"id": 5, "quality_grade": "needs_id", "taxon": {"id": 42, "name": "Channa striata"}, "photos": [{"url": "https://example.org/5.jpg", "license_code": "cc-by"}]}]}
        item, reason = find_exact_taxon_image("Channa striata", fetch)
        self.assertIsNone(item)
        self.assertEqual("inat-no-licensed-research-photo", reason)


if __name__ == "__main__":
    unittest.main()
