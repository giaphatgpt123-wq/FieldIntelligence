from __future__ import annotations

import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from data_engine_v2_state import (  # noqa: E402
    DEFAULT_TASKS,
    build_snapshot,
    effective_task_status,
    field_verified,
    merge_evidence,
    vietnam_distribution_evidence_verified,
    vietnamese_name_evidence_verified,
)


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

    def test_curated_official_vietnamese_name_is_verified(self):
        row = {
            "canonicalId": "taxon:x",
            "field": "VIETNAMESE_ALIASES",
            "sourceTier": "OFFICIAL_VIETNAM",
            "sourceUri": "https://example.gov.vn/species/x",
            "curatedLocalAuthority": True,
            "value": {"displayName": "Tên Việt", "language": "vi", "region": "Việt Nam"},
        }
        self.assertTrue(vietnamese_name_evidence_verified(row))
        self.assertTrue(field_verified("VIETNAMESE_ALIASES", [row]))

    def test_global_or_non_curated_vietnamese_name_is_not_verified(self):
        global_row = {
            "canonicalId": "taxon:x",
            "field": "VIETNAMESE_ALIASES",
            "sourceTier": "GLOBAL_AUTHORITY",
            "sourceUri": "https://example.org/species/x",
            "curatedLocalAuthority": False,
            "value": {"displayName": "Tên Việt", "language": "vi", "region": "Việt Nam"},
        }
        uncurated_official = dict(global_row)
        uncurated_official.update({"sourceTier": "OFFICIAL_VIETNAM", "curatedLocalAuthority": False})
        self.assertFalse(vietnamese_name_evidence_verified(global_row))
        self.assertFalse(vietnamese_name_evidence_verified(uncurated_official))
        self.assertFalse(field_verified("VIETNAMESE_ALIASES", [global_row, uncurated_official]))

    def test_curated_official_vietnam_distribution_is_verified(self):
        row = {
            "canonicalId": "taxon:x",
            "field": "VIETNAM_DISTRIBUTION",
            "sourceTier": "SPECIALIST_VIETNAM",
            "sourceUri": "https://example.gov.vn/distribution/x",
            "curatedLocalAuthority": True,
            "value": {"countryCode": "VN", "scope": "nhiều vùng miền trên cả nước"},
        }
        self.assertTrue(vietnam_distribution_evidence_verified(row))
        self.assertTrue(field_verified("VIETNAM_DISTRIBUTION", [row]))

    def test_gbif_only_vietnam_distribution_is_not_verified(self):
        row = {
            "canonicalId": "taxon:x",
            "field": "VIETNAM_DISTRIBUTION",
            "sourceTier": "GLOBAL_AUTHORITY",
            "sourceUri": "https://www.gbif.org/occurrence/search?country=VN",
            "value": {"countryCode": "VN", "occurrenceCount": 20},
        }
        self.assertFalse(vietnam_distribution_evidence_verified(row))
        self.assertFalse(field_verified("VIETNAM_DISTRIBUTION", [row]))

    def test_default_task_seed_is_active_not_canary_baseline(self):
        self.assertEqual("active-tasks.json", DEFAULT_TASKS.name)

    def test_retry_with_persisted_evidence_keeps_completed_collection_state(self):
        evidence_by_field = {
            ("taxon:x", "MEDIA_PRIMARY"): [{"sourceId": "inaturalist", "value": {"url": "https://example.org/a.jpg"}}]
        }
        self.assertEqual(
            "COMPLETED",
            effective_task_status("RETRY", "taxon:x", "MEDIA_PRIMARY", evidence_by_field),
        )

    def test_retry_without_persisted_evidence_stays_retry(self):
        self.assertEqual(
            "RETRY",
            effective_task_status("RETRY", "taxon:x", "MEDIA_PRIMARY", {}),
        )

    def test_blocked_is_never_softened_by_existing_evidence(self):
        evidence_by_field = {
            ("taxon:x", "MEDIA_PRIMARY"): [{"sourceId": "old", "value": {"url": "https://example.org/a.jpg"}}]
        }
        self.assertEqual(
            "BLOCKED",
            effective_task_status("BLOCKED", "taxon:x", "MEDIA_PRIMARY", evidence_by_field),
        )

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

    def test_snapshot_does_not_regress_task_when_refresh_retries_but_evidence_persists(self):
        tasks = [{
            "taskId": "x:media",
            "canonicalId": "taxon:x",
            "taskType": "COLLECT_MEDIA",
            "field": "MEDIA_PRIMARY",
            "scientificName": "Example species",
            "vietnameseName": "Loài ví dụ",
            "categoryId": "animals",
        }]
        evidence = [{
            "canonicalId": "taxon:x",
            "field": "MEDIA_PRIMARY",
            "sourceId": "inaturalist",
            "sourceTier": "OPEN_SCIENCE",
            "sourceUri": "https://example.org/a.jpg",
            "collectedAt": 100,
            "value": {"url": "https://example.org/a.jpg"},
        }]
        results = [{
            "taskId": "x:media",
            "status": "RETRY",
            "errors": ["HTTP 503"],
            "finishedAt": 201,
            "evidence": [],
        }]
        snapshot = build_snapshot(tasks, results, evidence, {"sources": {}}, version=4)
        task = snapshot["tasks"][0]
        self.assertEqual("COMPLETED", task["status"])
        self.assertEqual("HTTP 503", task["lastError"])

    def test_snapshot_marks_only_curated_vietnam_alias_row_verified(self):
        tasks = [{
            "taskId": "x:aliases",
            "canonicalId": "taxon:x",
            "taskType": "COLLECT_VIETNAMESE_NAMES",
            "field": "VIETNAMESE_ALIASES",
            "scientificName": "Example species",
            "vietnameseName": "Tên thử",
            "categoryId": "animals",
        }]
        evidence = [
            {
                "canonicalId": "taxon:x",
                "field": "VIETNAMESE_ALIASES",
                "sourceId": "vn-authority-local",
                "sourceTier": "OFFICIAL_VIETNAM",
                "sourceUri": "https://example.gov.vn/x",
                "curatedLocalAuthority": True,
                "collectedAt": 100,
                "value": {"displayName": "Tên chuẩn", "language": "vi", "region": "Việt Nam"},
            },
            {
                "canonicalId": "taxon:x",
                "field": "VIETNAMESE_ALIASES",
                "sourceId": "gbif",
                "sourceTier": "GLOBAL_AUTHORITY",
                "sourceUri": "https://www.gbif.org/species/x",
                "collectedAt": 101,
                "value": {"displayName": "Tên tham khảo", "language": "vi", "region": "Việt Nam"},
            },
        ]
        snapshot = build_snapshot(tasks, [], evidence, {"sources": {}}, version=1)
        other_names = {row["displayName"]: row for row in snapshot["aliases"] if row["aliasType"] == "OTHER_NAME"}
        self.assertTrue(other_names["Tên chuẩn"]["verified"])
        self.assertFalse(other_names["Tên tham khảo"]["verified"])
        field = next(row for row in snapshot["fields"] if row["field"] == "VIETNAMESE_ALIASES")
        self.assertTrue(field["verified"])

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
