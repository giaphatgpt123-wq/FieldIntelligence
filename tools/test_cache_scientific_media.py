import importlib.util
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
