from __future__ import annotations

import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from data_engine_v2_state import build_snapshot, field_verified, merge_evidence  # noqa: E402


class DataEngineV2StateTests(unittest.TestCase):
    def test_only_strong_exact_identity_is_auto_verified(self):
        strong = [{
            "canonicalId": "taxon:x",
            "field": "CANONICAL_IDENTITY",
            "sourceTier": "GLOBAL_AUTHORITY",
            "value": {"matchType": "EXACT", "confidence": 100},
        }]
        weak = [{
            "canonicalId": "taxon:x",
            "field": "CANONICAL_IDENTITY",
            "sourceTier": "GLOBAL_AUTHORITY",
            "value": {"matchType": "FUZZY", "confidence": 90},
        }]
        alias = [{
            "canonicalId": "taxon:x",
            "field": "VIETNAMESE_ALIASES",
            "sourceTier": "GLOBAL_AUTHORITY",
            "value": {"displayName": "Tên Việt"},
        }]

        self.assertTrue(field_verified("CANONICAL_IDENTITY", strong))
        self.assertFalse(field_verified("CANONICAL_IDENTITY", weak))
        self.assertFalse(field_verified("VIETNAMESE_ALIASES", alias))

    def test_evidence_merge_is_idempotent(self):
        evidence = [{
            "canonicalId": "taxon:x",
            "field": "CANONICAL_IDENTITY",
            "sourceId": "gbif",
            "sourceUri": "https://example.org/x",
            "value": {"matchType": "EXACT", "confidence": 100},
        }]
        results = [{"taskId": "one", "evidence": evidence}]
        merged = merge_evidence(evidence, results)
        self.assertEqual(1, len(merged))

    def test_snapshot_keeps_completed_and_retry_tasks_independent(self):
        tasks = [
            {
                "taskId": "x:identity",
                "canonicalId": "taxon:x",
                "taskType": "RESOLVE_CANONICAL",
                "field": "CANONICAL_IDENTITY",
                "scientificName": "Example species",
                "vietnameseName": "Loài ví dụ",
                "categoryId": "animals",
            },
            {
                "taskId": "x:media",
                "canonicalId": "taxon:x",
                "taskType": "COLLECT_MEDIA",
                "field": "MEDIA_PRIMARY",
                "scientificName": "Example species",
                "vietnameseName": "Loài ví dụ",
                "categoryId": "animals",
            },
        ]
        evidence = [{
            "canonicalId": "taxon:x",
            "field": "CANONICAL_IDENTITY",
            "sourceId": "gbif",
            "sourceTier": "GLOBAL_AUTHORITY",
            "sourceUri": "https://example.org/x",
            "collectedAt": 100,
            "value": {"matchType": "EXACT", "confidence": 100},
        }]
        results = [
            {"taskId": "x:identity", "status": "COMPLETED", "errors": [], "finishedAt": 200, "evidence": evidence},
            {"taskId": "x:media", "status": "RETRY", "errors": ["source rate limit"], "finishedAt": 201, "evidence": []},
        ]

        snapshot = build_snapshot(tasks, results, evidence, {"sources": {}}, version=3)
        by_id = {row["taskId"]: row for row in snapshot["tasks"]}

        self.assertEqual("COMPLETED", by_id["x:identity"]["status"])
        self.assertEqual("RETRY", by_id["x:media"]["status"])
        self.assertEqual(1, len(snapshot["entities"]))
        self.assertEqual(2, len(snapshot["tasks"]))
        self.assertTrue(snapshot["fields"][0]["verified"])

    def test_pilot_name_is_display_candidate_not_verified_claim(self):
        tasks = [{
            "taskId": "x:identity",
            "canonicalId": "taxon:x",
            "taskType": "RESOLVE_CANONICAL",
            "field": "CANONICAL_IDENTITY",
            "scientificName": "Example species",
            "vietnameseName": "Tên thử",
            "categoryId": "animals",
        }]
        snapshot = build_snapshot(tasks, [], [], {"sources": {}}, version=1)
        primary = snapshot["aliases"][0]
        self.assertEqual("PRIMARY", primary["aliasType"])
        self.assertEqual("pilot-seed", primary["sourceKey"])
        self.assertFalse(primary["verified"])


if __name__ == "__main__":
    unittest.main()
