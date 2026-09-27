import gzip
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name("cache_scientific_media.py")
spec = importlib.util.spec_from_file_location("media_cache", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class ScientificMediaCacheTest(unittest.TestCase):
    def test_private_and_special_addresses_are_rejected(self):
        for address in ("127.0.0.1", "10.0.0.1", "192.168.1.1", "169.254.1.1", "::1", "fc00::1"):
            self.assertFalse(module.is_public_address(address), address)
        self.assertTrue(module.is_public_address("1.1.1.1"))
        self.assertTrue(module.is_public_address("2606:4700:4700::1111"))

    def test_media_items_require_reusable_license_and_image_type(self):
        record = {
            "mediaItems": [
                {"identifier": "https://example.org/a.jpg", "license": "CC-BY-4.0", "mediaType": "StillImage"},
                {"identifier": "https://example.org/b.jpg", "license": "", "mediaType": "StillImage"},
                {"identifier": "https://example.org/c.mp4", "license": "CC-BY-4.0", "mediaType": "MovingImage"},
                {"identifier": "https://example.org/d.webp", "license": "CC0-1.0", "mediaType": "image/webp"},
            ]
        }
        accepted = list(module.media_items(record))
        self.assertEqual(["https://example.org/a.jpg", "https://example.org/d.webp"], [x["identifier"] for x in accepted])

    def test_gbif_media_uses_deterministic_1200px_cache_url(self):
        item = {
            "identifier": "https://example.org/a.jpg",
            "license": "CC-BY-4.0",
            "mediaType": "StillImage",
            "sourceProvider": "GBIF occurrence media",
            "gbifOccurrenceKey": "123",
        }
        self.assertEqual(
            "https://api.gbif.org/v1/image/cache/1600x/occurrence/123/media/14959baaa98af1141f91775766c5008d",
            module.download_url_for_item(item),
        )
        with self.assertRaises(ValueError):
            module.gbif_resized_download_url(item, width=1601)

    def test_non_gbif_media_keeps_original_download_url(self):
        item = {
            "identifier": "https://upload.wikimedia.org/example.jpg",
            "license": "CC-BY-4.0",
            "mediaType": "StillImage",
            "sourceProvider": "Wikimedia Commons",
        }
        self.assertEqual(item["identifier"], module.download_url_for_item(item))

    def test_extension_gate(self):
        self.assertEqual(".jpg", module.extension_for("image/jpeg", "https://example.org/noext"))
        self.assertEqual(".png", module.extension_for("application/octet-stream", "https://example.org/pic.png"))
        self.assertEqual(".webp", module.extension_for("image/webp", "https://example.org/pic"))
        self.assertIsNone(module.extension_for("application/pdf", "https://example.org/a.pdf"))

    def test_https_url_syntax_rejects_credentials_before_dns(self):
        with self.assertRaises(ValueError):
            module.validate_public_https_url("http://example.org/a.jpg")
        with self.assertRaises(ValueError):
            module.validate_public_https_url("https://user:pass@example.org/a.jpg")

    def test_build_counts_rejected_media_and_per_record_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "fish.ndjson.gz"
            media_dir = root / "media"
            manifest = root / "manifest.json"
            record = {
                "sourceId": "gbif", "sourceRecordId": "1", "scientificName": "Channa striata",
                "mediaItems": [
                    {"identifier":"https://example.org/a.jpg","license":"CC-BY-4.0","mediaType":"StillImage"},
                    {"identifier":"https://example.org/b.jpg","license":"CC0-1.0","mediaType":"StillImage"},
                    {"identifier":"https://example.org/c.jpg","license":"CC-BY-4.0","mediaType":"StillImage"},
                    {"identifier":"https://example.org/no-license.jpg","license":"","mediaType":"StillImage"},
                    {"identifier":"https://example.org/video.mp4","license":"CC-BY-4.0","mediaType":"MovingImage"},
                ],
            }
            with gzip.open(source, "wt", encoding="utf-8") as handle:
                handle.write(json.dumps(record) + "\n")
            def fake_download(url, max_bytes, timeout=20):
                return (url.encode("utf-8"), ".jpg", url)
            with patch.object(module, "download_image", side_effect=fake_download):
                result = module.build(source, media_dir, manifest, max_bytes=1024, max_per_record=2, max_total_bytes=4096)
            metrics = result["metrics"]
            self.assertEqual(2, metrics["attempted"])
            self.assertEqual(2, metrics["cachedReferences"])
            self.assertEqual(2, metrics["rejectedBeforeDownload"])
            self.assertEqual(1, metrics["skippedPerRecordLimit"])
            self.assertEqual(2, metrics["uniqueCachedFiles"])
            self.assertEqual(0, metrics["gbifResizedDownloads"])

    def test_build_downloads_gbif_derivative_but_preserves_original_source_identifier(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "fish.ndjson.gz"
            media_dir = root / "media"
            manifest_path = root / "manifest.json"
            original = "https://example.org/a.jpg"
            item = {
                "identifier": original,
                "license": "CC-BY-4.0",
                "mediaType": "StillImage",
                "sourceProvider": "GBIF occurrence media",
                "gbifOccurrenceKey": "123",
            }
            record = {"sourceId":"gbif","sourceRecordId":"1","scientificName":"Channa striata","mediaItems":[item]}
            with gzip.open(source, "wt", encoding="utf-8") as handle:
                handle.write(json.dumps(record) + "\n")
            expected_download = module.download_url_for_item(item)
            with patch.object(module, "download_image", return_value=(b"fish-image", ".jpg", expected_download)) as mocked:
                result = module.build(source, media_dir, manifest_path, max_bytes=1024, max_per_record=1, max_total_bytes=4096)
            mocked.assert_called_once_with(expected_download, max_bytes=1024)
            entry = result["records"][0]
            self.assertEqual(original, entry["sourceIdentifier"])
            self.assertEqual(expected_download, entry["downloadIdentifier"])
            self.assertEqual("CC-BY-4.0", entry["license"])
            self.assertEqual(1, result["metrics"]["gbifResizedDownloads"])
            self.assertTrue(result["safety"]["publisherIdentifierPreserved"])

    def test_build_aborts_before_exceeding_total_pack_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "fish.ndjson.gz"
            record = {
                "sourceId":"gbif","sourceRecordId":"1","scientificName":"Channa striata",
                "mediaItems":[{"identifier":"https://example.org/a.jpg","license":"CC-BY-4.0","mediaType":"StillImage"}]
            }
            with gzip.open(source,"wt",encoding="utf-8") as handle:
                handle.write(json.dumps(record)+"\n")
            with patch.object(module,"download_image",return_value=(b"1234567890",".jpg","https://example.org/a.jpg")):
                with self.assertRaises(SystemExit) as ctx:
                    module.build(source,root/"media",root/"manifest.json",max_bytes=100,max_per_record=1,max_total_bytes=9)
            self.assertIn("total byte limit",str(ctx.exception).lower())


if __name__ == "__main__":
    unittest.main()
