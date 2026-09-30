from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from data_engine_v2_progress_shards import build_progress_shards  # noqa: E402


class DataEngineProgressShardTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.output = Path(self.tmp.name) / "progress"

    def tearDown(self):
        self.tmp.cleanup()

    def snapshot(self):
        return {
            "schemaVersion": 1,
            "version": 7,
            "generatedAt": 12345,
            "entities": [
                {"canonicalId": "a", "categoryId": "vegetables", "scientificName": "A"},
                {"canonicalId": "b", "categoryId": "freshwater-fish", "scientificName": "B"},
            ],
            "aliases": [
                {"canonicalId": "a", "displayName": "Rau A"},
                {"canonicalId": "b", "displayName": "Cá B"},
            ],
            "fields": [
                {"canonicalId": "a", "field": "CANONICAL_IDENTITY"},
                {"canonicalId": "b", "field": "CANONICAL_IDENTITY"},
            ],
            "tasks": [
                {"taskId": "ta", "canonicalId": "a", "status": "COMPLETED"},
                {"taskId": "tb", "canonicalId": "b", "status": "RETRY"},
            ],
            "sourceHealth": [{"sourceKey": "gbif", "consecutiveFailures": 0}],
        }

    def test_builds_one_shard_per_category_with_integrity_metadata(self):
        manifest = build_progress_shards(self.snapshot(), self.output, "https://example.test/staging")
        self.assertEqual(2, manifest["shardCount"])
        self.assertEqual(7, manifest["version"])
        self.assertEqual({"vegetables", "freshwater-fish"}, {row["categoryId"] for row in manifest["shards"]})
        for row in manifest["shards"]:
            raw = (self.output / row["file"]).read_bytes()
            payload = json.loads(raw)
            self.assertEqual(row["sha256"], hashlib.sha256(raw).hexdigest())
            self.assertEqual(row["bytes"], len(raw))
            self.assertEqual(row["categoryId"], payload["categoryId"])
            self.assertEqual(1, row["entityCount"])
            self.assertEqual(1, row["taskCount"])

    def test_source_health_is_separate_shard(self):
        manifest = build_progress_shards(self.snapshot(), self.output, "https://example.test/staging")
        meta = manifest["sourceHealth"]
        raw = (self.output / meta["file"]).read_bytes()
        payload = json.loads(raw)
        self.assertEqual(meta["sha256"], hashlib.sha256(raw).hexdigest())
        self.assertEqual(1, meta["rowCount"])
        self.assertEqual("gbif", payload["sourceHealth"][0]["sourceKey"])

    def test_unreferenced_alias_is_rejected(self):
        snap = self.snapshot()
        snap["aliases"].append({"canonicalId": "missing", "displayName": "Sai"})
        with self.assertRaises(RuntimeError):
            build_progress_shards(snap, self.output, "https://example.test/staging")

    def test_removed_category_deletes_stale_progress_file(self):
        build_progress_shards(self.snapshot(), self.output, "https://example.test/staging")
        self.assertTrue((self.output / "freshwater-fish.json").exists())
        snap = self.snapshot()
        snap["entities"] = [snap["entities"][0]]
        snap["aliases"] = [snap["aliases"][0]]
        snap["fields"] = [snap["fields"][0]]
        snap["tasks"] = [snap["tasks"][0]]
        build_progress_shards(snap, self.output, "https://example.test/staging")
        self.assertFalse((self.output / "freshwater-fish.json").exists())

    def test_category_conflict_is_rejected(self):
        snap = self.snapshot()
        snap["entities"].append({"canonicalId": "a", "categoryId": "danger", "scientificName": "A"})
        with self.assertRaises(RuntimeError):
            build_progress_shards(snap, self.output, "https://example.test/staging")


if __name__ == "__main__":
    unittest.main()
