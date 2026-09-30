from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from data_engine_v2_shards import (  # noqa: E402
    category_lookup,
    load_all_rows,
    migrate_legacy_if_needed,
    rebuild_index,
    update_touched_shards,
)


class DataEngineV2ShardTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.shard_dir = self.root / "evidence"
        self.index_path = self.root / "evidence-index.json"
        self.legacy_path = self.root / "evidence-store.json"
        self.tasks = [
            {"taskId": "veg:name", "canonicalId": "taxon:veg", "categoryId": "vegetables"},
            {"taskId": "fish:name", "canonicalId": "taxon:fish", "categoryId": "freshwater-fish"},
        ]
        self.mapping = category_lookup(self.tasks)

    def tearDown(self):
        self.tmp.cleanup()

    @staticmethod
    def row(canonical_id: str, field: str, source: str, name: str) -> dict:
        return {
            "canonicalId": canonical_id,
            "field": field,
            "sourceId": source,
            "sourceUri": f"https://example.org/{source}/{canonical_id}",
            "collectedAt": 100,
            "value": {"displayName": name},
        }

    def test_legacy_migration_preserves_all_rows_in_category_shards(self):
        rows = [
            self.row("taxon:veg", "VIETNAMESE_ALIASES", "vn-a", "Rau A"),
            self.row("taxon:fish", "VIETNAMESE_ALIASES", "vn-b", "Cá B"),
        ]
        self.legacy_path.write_text(
            json.dumps({"schemaVersion": 1, "evidenceCount": 2, "evidence": rows}, ensure_ascii=False),
            encoding="utf-8",
        )

        self.assertTrue(
            migrate_legacy_if_needed(self.legacy_path, self.shard_dir, self.index_path, self.mapping)
        )
        migrated = load_all_rows(self.shard_dir, self.index_path)
        self.assertEqual(2, len(migrated))
        self.assertEqual({"taxon:veg", "taxon:fish"}, {row["canonicalId"] for row in migrated})
        index = json.loads(self.index_path.read_text(encoding="utf-8"))
        self.assertEqual(2, index["evidenceCount"])
        self.assertEqual(2, index["shardCount"])

    def test_duplicate_incoming_evidence_is_idempotent(self):
        row = self.row("taxon:veg", "VIETNAMESE_ALIASES", "vn-a", "Rau A")
        update_touched_shards(self.shard_dir, self.index_path, [row, row], self.mapping)
        update_touched_shards(self.shard_dir, self.index_path, [row], self.mapping)
        rows = load_all_rows(self.shard_dir, self.index_path)
        self.assertEqual(1, len(rows))
        index = json.loads(self.index_path.read_text(encoding="utf-8"))
        self.assertEqual(1, index["evidenceCount"])

    def test_updating_one_category_does_not_rewrite_other_shard(self):
        veg = self.row("taxon:veg", "VIETNAMESE_ALIASES", "vn-a", "Rau A")
        fish = self.row("taxon:fish", "VIETNAMESE_ALIASES", "vn-b", "Cá B")
        update_touched_shards(self.shard_dir, self.index_path, [veg, fish], self.mapping)
        fish_path = self.shard_dir / "freshwater-fish.json"
        fish_before = fish_path.read_bytes()
        fish_hash_before = hashlib.sha256(fish_before).hexdigest()

        veg2 = self.row("taxon:veg", "VIETNAMESE_ALIASES", "vn-c", "Rau C")
        touched = update_touched_shards(self.shard_dir, self.index_path, [veg2], self.mapping)

        self.assertEqual({"vegetables"}, touched)
        self.assertEqual(fish_hash_before, hashlib.sha256(fish_path.read_bytes()).hexdigest())

    def test_manifest_sha_and_counts_match_each_shard(self):
        rows = [
            self.row("taxon:veg", "VIETNAMESE_ALIASES", "vn-a", "Rau A"),
            self.row("taxon:fish", "VIETNAMESE_ALIASES", "vn-b", "Cá B"),
        ]
        update_touched_shards(self.shard_dir, self.index_path, rows, self.mapping)
        index = rebuild_index(self.shard_dir, self.index_path)
        total = 0
        for item in index["shards"]:
            raw = (self.shard_dir / item["file"]).read_bytes()
            payload = json.loads(raw)
            self.assertEqual(item["sha256"], hashlib.sha256(raw).hexdigest())
            self.assertEqual(item["evidenceCount"], len(payload["evidence"]))
            self.assertEqual(item["bytes"], len(raw))
            total += item["evidenceCount"]
        self.assertEqual(index["evidenceCount"], total)

    def test_conflicting_category_for_same_canonical_id_is_rejected(self):
        with self.assertRaises(RuntimeError):
            category_lookup([
                {"canonicalId": "taxon:x", "categoryId": "animals"},
                {"canonicalId": "taxon:x", "categoryId": "danger"},
            ])


if __name__ == "__main__":
    unittest.main()
