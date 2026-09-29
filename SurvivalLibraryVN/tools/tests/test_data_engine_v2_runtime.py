from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import data_engine_v2 as core  # noqa: E402
import data_engine_v2_runtime as runtime  # noqa: E402


class FakeResponse:
    def __init__(self, body: bytes, headers: dict[str, str] | None = None):
        self.body = body
        self.headers = headers or {}

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self, _limit: int):
        return self.body


class DataEngineV2RuntimeTest(unittest.TestCase):
    def test_transport_caches_repeated_get(self):
        transport = runtime.ResilientCachingJsonTransport(timeout=5)
        with patch.object(runtime, "urlopen", return_value=FakeResponse(b'{"ok": true}')) as mocked:
            first = transport.get_json("https://example.org/taxon", {"name": "Apis cerana"})
            second = transport.get_json("https://example.org/taxon", {"name": "Apis cerana"})
        self.assertEqual(first, {"ok": True})
        self.assertEqual(second, {"ok": True})
        self.assertEqual(mocked.call_count, 1)
        self.assertEqual(transport.network_requests, 1)
        self.assertEqual(transport.cache_hits, 1)

    def test_transport_retries_malformed_json_then_recovers(self):
        transport = runtime.ResilientCachingJsonTransport(timeout=5)
        responses = [FakeResponse(b"<html>temporary upstream error</html>"), FakeResponse(b'{"ok": 1}')]
        with patch.object(runtime, "urlopen", side_effect=responses) as mocked, patch.object(runtime.time, "sleep"):
            value = transport.get_json("https://example.org/unstable")
        self.assertEqual(value, {"ok": 1})
        self.assertEqual(mocked.call_count, 2)
        self.assertEqual(transport.retry_count, 1)

    def test_health_is_buffered_until_flush(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "health.json"
            store = runtime.BufferedSourceHealthStore(path)
            store.success("gbif")
            self.assertFalse(path.exists())
            store.flush()
            self.assertTrue(path.exists())
            loaded = core.SourceHealthStore(path)
            self.assertTrue(loaded.available("gbif"))

    def test_verified_vietnam_alias_pack_resolves_all_retry_aliases(self):
        registry = core.SourceRegistry.load()
        source = next(source for source in registry.sources if source.source_id == "vn-authority-local")
        adapter = runtime.CachedLocalAuthorityAdapter(source, runtime.ResilientCachingJsonTransport(timeout=5))
        seeds = [
            ("Ipomoea aquatica", "vegetables", "Rau muống"),
            ("Colocasia esculenta", "roots", "Khoai môn"),
            ("Mangifera indica", "fruit-crops", "Xoài"),
            ("Nelumbo nucifera", "flowers", "Sen"),
            ("Pleurotus pulmonarius", "mushrooms", "Nấm sò trắng"),
            ("Apis cerana", "insects", "Ong nội"),
            ("Sus scrofa", "animals", "Lợn rừng"),
            ("Zingiber officinale", "medicinal-plants", "Gừng"),
            ("Aedes aegypti", "danger", "Muỗi vằn"),
        ]
        for index, (scientific_name, category_id, expected_name) in enumerate(seeds):
            task = core.LoadTask(
                task_id=f"alias-{index}",
                canonical_id=f"taxon:{index}",
                task_type="COLLECT_VIETNAMESE_NAMES",
                field_key="VIETNAMESE_ALIASES",
                scientific_name=scientific_name,
                category_id=category_id,
            )
            rows = adapter.collect(task)
            self.assertTrue(rows, scientific_name)
            self.assertEqual(rows[0]["value"]["displayName"], expected_name)
            self.assertTrue(rows[0]["sourceUri"].startswith("https://"))
            self.assertTrue(rows[0]["curatedLocalAuthority"])

    def test_curated_media_pack_resolves_five_media_retries_without_tier_inflation(self):
        registry = core.SourceRegistry.load()
        source = next(source for source in registry.sources if source.source_id == "curated-canary-media")
        self.assertEqual(source.tier, "OPEN_SCIENCE")
        adapter = runtime.CachedLocalAuthorityAdapter(source, runtime.ResilientCachingJsonTransport(timeout=5))
        seeds = [
            ("Nelumbo nucifera", "flowers"),
            ("Hopea odorata", "timber-trees"),
            ("Penaeus monodon", "marine-life"),
            ("Apis cerana", "insects"),
            ("Zingiber officinale", "medicinal-plants"),
        ]
        for index, (scientific_name, category_id) in enumerate(seeds):
            task = core.LoadTask(
                task_id=f"media-{index}",
                canonical_id=f"taxon:{index}",
                task_type="COLLECT_MEDIA",
                field_key="MEDIA_PRIMARY",
                scientific_name=scientific_name,
                category_id=category_id,
            )
            rows = adapter.collect(task)
            self.assertTrue(rows, scientific_name)
            self.assertEqual(rows[0]["sourceTier"], "OPEN_SCIENCE")
            self.assertTrue(rows[0]["sourceUri"].startswith("https://commons.wikimedia.org/"))
            self.assertTrue(str(rows[0]["rights"]).startswith("CC"))
            self.assertEqual(len(rows[0]["value"]["checksumSha256"]), 64)

    def test_collector_reuses_adapter_instance_per_source(self):
        registry = core.SourceRegistry.load()
        health = runtime.BufferedSourceHealthStore(None)
        transport = runtime.ResilientCachingJsonTransport(timeout=5)
        collector = runtime.OptimizedBatchCollector(registry, health, transport)
        source = next(source for source in registry.sources if source.source_id == "vn-authority-local")
        self.assertIs(collector._adapter(source), collector._adapter(source))


if __name__ == "__main__":
    unittest.main()
