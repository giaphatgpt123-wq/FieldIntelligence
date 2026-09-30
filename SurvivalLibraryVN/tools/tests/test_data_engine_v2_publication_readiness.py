import importlib.util
import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
MODULE_PATH = TOOLS / "data_engine_v2_publication_readiness.py"
spec = importlib.util.spec_from_file_location("publication_readiness", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class PublicationReadinessTest(unittest.TestCase):
    def entity(self, *, high_risk=False, category="vegetables"):
        return module.EntityRef(
            canonical_id="taxon:test",
            category_id=category,
            scientific_name="Test species",
            vietnamese_name="Loài thử",
            high_risk=high_risk,
            published=False,
        )

    def snapshot(self, fields, *, high_risk=False, category="vegetables"):
        return {
            "version": 1,
            "entities": [{
                "canonicalId": "taxon:test",
                "categoryId": category,
                "scientificName": "Test species",
                "vietnameseName": "Loài thử",
                "highRisk": high_risk,
                "published": False,
            }],
            "fields": [
                {
                    "canonicalId": "taxon:test",
                    "field": name,
                    "valuePresent": present,
                    "evidenceCount": 1 if present else 0,
                    "verified": verified,
                }
                for name, present, verified in fields
            ],
            "tasks": [
                {
                    "canonicalId": "taxon:test",
                    "field": name,
                    "status": "COMPLETED" if present else "MISSING",
                }
                for name, present, _ in fields
            ],
        }

    @staticmethod
    def media_rows(count=5, roles=("WHOLE", "LEAF", "STEM"), primary="WHOLE"):
        rows = []
        role_values = list(roles)
        while len(role_values) < count:
            role_values.append(f"REFERENCE_{len(role_values)}")
        for index, role in enumerate(role_values[:count]):
            rows.append({
                "canonicalId": "taxon:test",
                "field": "MEDIA_DIAGNOSTIC_SET",
                "verified": True,
                "sourceUri": f"https://example.org/media/{index}",
                "value": {
                    "checksum": f"{index + 1:064x}",
                    "license": "CC BY 4.0",
                    "creator": "Tester",
                    "viewRole": role,
                    "diagnostic": True,
                    "isPrimary": role == primary,
                },
            })
        return rows

    def test_completed_but_unverified_foundation_is_blocked(self):
        fields = [
            ("CANONICAL_IDENTITY", True, True),
            ("VIETNAMESE_ALIASES", True, False),
            ("VIETNAM_DISTRIBUTION", True, False),
            ("MEDIA_PRIMARY", True, False),
        ]
        result = module.evaluate_entity(self.entity(), self.snapshot(fields), [])
        self.assertFalse(result["publicationReady"])
        joined = " | ".join(result["blockers"])
        self.assertIn("VIETNAMESE_ALIASES: đã có evidence nhưng chưa verified", joined)
        self.assertIn("MEDIA_DIAGNOSTIC_SET: chưa có evidence", joined)
        self.assertIn("IDENTIFICATION_TRAITS: chưa có evidence", joined)

    def test_schema_v3_ready_record_passes_when_all_blocking_fields_and_media_pass(self):
        required = [
            "CANONICAL_IDENTITY",
            "VIETNAMESE_ALIASES",
            "VIETNAM_DISTRIBUTION",
            "MEDIA_PRIMARY",
            "MEDIA_DIAGNOSTIC_SET",
            "IDENTIFICATION_TRAITS",
        ]
        fields = [(name, True, True) for name in required]
        result = module.evaluate_entity(
            self.entity(),
            self.snapshot(fields),
            self.media_rows(count=5),
        )
        self.assertTrue(result["publicationReady"], result["blockers"])
        self.assertEqual(5, result["mediaPolicy"]["verifiedDiagnosticMedia"])
        self.assertEqual([], result["mediaPolicy"]["missingRoles"])

    def test_high_risk_requires_verified_safety(self):
        required = [
            "CANONICAL_IDENTITY",
            "VIETNAMESE_ALIASES",
            "VIETNAM_DISTRIBUTION",
            "MEDIA_PRIMARY",
            "MEDIA_DIAGNOSTIC_SET",
            "IDENTIFICATION_TRAITS",
        ]
        fields = [(name, True, True) for name in required]
        result = module.evaluate_entity(
            self.entity(high_risk=True, category="danger"),
            self.snapshot(fields, high_risk=True, category="danger"),
            self.media_rows(count=6, roles=("WHOLE", "DORSAL", "SIDE"), primary="WHOLE"),
        )
        self.assertFalse(result["publicationReady"])
        self.assertTrue(any(blocker.startswith("SAFETY:") for blocker in result["blockers"]))

    def test_expansion_scope_excludes_canary_baseline(self):
        snapshot = {
            "version": 1,
            "entities": [
                {"canonicalId": "taxon:old", "categoryId": "vegetables", "scientificName": "Old", "vietnameseName": "Cũ", "highRisk": False, "published": False},
                {"canonicalId": "taxon:test", "categoryId": "vegetables", "scientificName": "Test species", "vietnameseName": "Loài thử", "highRisk": False, "published": False},
            ],
            "fields": [],
            "tasks": [],
        }
        active = [{"canonicalId": "taxon:old"}, {"canonicalId": "taxon:test"}]
        baseline = [{"canonicalId": "taxon:old"}]
        report = module.build_report(snapshot, [], active, baseline, "expansion")
        self.assertEqual(1, report["selectedCount"])
        self.assertEqual("taxon:test", report["records"][0]["canonicalId"])


if __name__ == "__main__":
    unittest.main()
