from __future__ import annotations

import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import data_engine_v2 as core  # noqa: E402
import data_engine_v2_runtime as runtime  # noqa: E402


class VietnamAuthorityAliasCoverageTest(unittest.TestCase):
    def test_all_12_pilot_taxa_have_vietnam_source_backed_alias(self):
        expected = [
            ("Ipomoea aquatica", "vegetables", "Rau muống"),
            ("Colocasia esculenta", "roots", "Khoai môn"),
            ("Mangifera indica", "fruit-crops", "Xoài"),
            ("Nelumbo nucifera", "flowers", "Sen"),
            ("Hopea odorata", "timber-trees", "Sao đen"),
            ("Pleurotus pulmonarius", "mushrooms", "Nấm sò trắng"),
            ("Anabas testudineus", "freshwater-fish", "Cá rô đồng"),
            ("Penaeus monodon", "marine-life", "Tôm sú"),
            ("Apis cerana", "insects", "Ong nội"),
            ("Sus scrofa", "animals", "Lợn rừng"),
            ("Zingiber officinale", "medicinal-plants", "Gừng"),
            ("Aedes aegypti", "danger", "Muỗi vằn"),
        ]

        registry = core.SourceRegistry.load()
        source = next(s for s in registry.sources if s.source_id == "vn-authority-local")
        adapter = runtime.CachedLocalAuthorityAdapter(
            source,
            runtime.ResilientCachingJsonTransport(timeout=5),
        )

        seen = set()
        for index, (scientific_name, category_id, display_name) in enumerate(expected):
            task = core.LoadTask(
                task_id=f"vn-alias-{index}",
                canonical_id=f"taxon:{index}",
                task_type="COLLECT_VIETNAMESE_NAMES",
                field_key="VIETNAMESE_ALIASES",
                scientific_name=scientific_name,
                category_id=category_id,
                max_sources=3,
            )
            rows = adapter.collect(task)
            self.assertTrue(rows, scientific_name)
            self.assertEqual(rows[0]["value"]["displayName"], display_name)
            self.assertEqual(rows[0]["sourceId"], "vn-authority-local")
            self.assertEqual(rows[0]["sourceTier"], "OFFICIAL_VIETNAM")
            self.assertTrue(rows[0]["sourceUri"].startswith("https://"))
            self.assertTrue(rows[0]["curatedLocalAuthority"])
            seen.add((scientific_name, category_id))

        self.assertEqual(len(seen), 12)


if __name__ == "__main__":
    unittest.main()
