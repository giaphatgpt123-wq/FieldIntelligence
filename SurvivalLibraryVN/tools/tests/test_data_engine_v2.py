from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from data_engine_v2 import (  # noqa: E402
    BatchCollector,
    GbifAdapter,
    INaturalistAdapter,
    JsonTransport,
    LoadTask,
    SourceAdapter,
    SourceDefinition,
    SourceHealthStore,
    SourceRegistry,
)


class FakeTransport(JsonTransport):
    def __init__(self, responder):
        self.responder = responder

    def get_json(self, url, params=None):
        return self.responder(url, params or {})


class ConditionalAdapter(SourceAdapter):
    def __init__(self, source, transport, *, fail_task_ids=None, label="ok"):
        super().__init__(source, transport)
        self.fail_task_ids = set(fail_task_ids or [])
        self.label = label

    def collect(self, task):
        if task.task_id in self.fail_task_ids:
            raise RuntimeError(f"forced failure {self.source.source_id}")
        return [
            self.evidence(
                task,
                {"result": self.label, "scientificName": task.scientific_name},
                f"https://example.org/{self.source.source_id}/{task.task_id}",
            )
        ]


def source(source_id, tier, adapter="gbif", capabilities=None, categories=None):
    return SourceDefinition(
        source_id=source_id,
        label=source_id,
        tier=tier,
        automated=True,
        adapter=adapter,
        capabilities=tuple(capabilities or ["VIETNAMESE_ALIASES"]),
        categories=tuple(categories or ["*"]),
        base_url="https://example.org",
    )


def task(task_id="t1", field="VIETNAMESE_ALIASES", category="freshwater-fish"):
    return LoadTask(
        task_id=task_id,
        canonical_id="taxon:anabas-testudineus",
        task_type="COLLECT_VIETNAMESE_NAMES",
        field_key=field,
        scientific_name="Anabas testudineus",
        category_id=category,
        vietnamese_name="Cá rô đồng",
    )


class DataEngineV2SourceTests(unittest.TestCase):
    def test_vietnam_source_is_routed_before_global_for_vietnamese_names(self):
        registry = SourceRegistry(
            [
                source("gbif", "GLOBAL_AUTHORITY"),
                source("vn", "OFFICIAL_VIETNAM"),
                source("inat", "OPEN_SCIENCE"),
            ]
        )
        ids = [row.source_id for row in registry.candidates(task())]
        self.assertEqual(["vn", "gbif", "inat"], ids)

    def test_source_failure_falls_through_to_next_source(self):
        first = source("vn", "OFFICIAL_VIETNAM")
        second = source("gbif", "GLOBAL_AUTHORITY")
        registry = SourceRegistry([first, second])
        with tempfile.TemporaryDirectory() as tmp:
            health = SourceHealthStore(Path(tmp) / "health.json")
            collector = BatchCollector(
                registry,
                health,
                FakeTransport(lambda *_: {}),
                adapter_overrides={
                    "vn": ConditionalAdapter(first, FakeTransport(lambda *_: {}), fail_task_ids={"t1"}),
                    "gbif": ConditionalAdapter(second, FakeTransport(lambda *_: {}), label="fallback"),
                },
            )
            result = collector.collect_task(task())

        self.assertEqual("COMPLETED", result["status"])
        self.assertEqual(["vn", "gbif"], result["attemptedSources"])
        self.assertTrue(any("forced failure" in error for error in result["errors"]))
        self.assertEqual("fallback", result["evidence"][0]["value"]["result"])

    def test_bad_task_does_not_abort_later_task(self):
        only = source("vn", "OFFICIAL_VIETNAM")
        registry = SourceRegistry([only])
        adapter = ConditionalAdapter(only, FakeTransport(lambda *_: {}), fail_task_ids={"bad"})
        with tempfile.TemporaryDirectory() as tmp:
            collector = BatchCollector(
                registry,
                SourceHealthStore(Path(tmp) / "health.json"),
                FakeTransport(lambda *_: {}),
                adapter_overrides={"vn": adapter},
            )
            results = collector.collect_many([task("bad"), task("good")])

        self.assertEqual(2, len(results))
        self.assertEqual("RETRY", results[0]["status"])
        # Health cooldown on source vn would normally skip it for task 2. Use a fresh source ID
        # is not the behavior we want: source-level failures intentionally cool down the source.
        # The key invariant here is that task 2 still receives a result row rather than vanishing.
        self.assertIn(results[1]["status"], {"RETRY", "COMPLETED"})
        self.assertEqual("good", results[1]["taskId"])

    def test_gbif_parses_vietnamese_alias_and_vietnam_occurrence(self):
        def responder(url, params):
            if url.endswith("/v1/species/match"):
                return {
                    "usageKey": 2393099,
                    "scientificName": "Anabas testudineus",
                    "canonicalName": "Anabas testudineus",
                    "rank": "SPECIES",
                    "status": "ACCEPTED",
                    "matchType": "EXACT",
                    "confidence": 100,
                }
            if url.endswith("/vernacularNames"):
                return {
                    "results": [
                        {"vernacularName": "Cá rô đồng", "language": "vie", "country": "VN"},
                        {"vernacularName": "Climbing perch", "language": "eng"},
                    ]
                }
            if url.endswith("/v1/occurrence/search"):
                self.assertEqual("VN", params["country"])
                return {"count": 42, "results": []}
            raise AssertionError(url)

        src = SourceDefinition(
            source_id="gbif",
            label="GBIF",
            tier="GLOBAL_AUTHORITY",
            automated=True,
            adapter="gbif",
            capabilities=("VIETNAMESE_ALIASES", "VIETNAM_DISTRIBUTION"),
            categories=("*",),
            base_url="https://api.gbif.org",
        )
        adapter = GbifAdapter(src, FakeTransport(responder))

        aliases = adapter.collect(task(field="VIETNAMESE_ALIASES"))
        distribution = adapter.collect(task(field="VIETNAM_DISTRIBUTION"))

        self.assertEqual(1, len(aliases))
        self.assertEqual("Cá rô đồng", aliases[0]["value"]["displayName"])
        self.assertEqual(42, distribution[0]["value"]["occurrenceCount"])

    def test_inaturalist_rejects_non_redistributable_default_photo(self):
        src = SourceDefinition(
            source_id="inaturalist",
            label="iNaturalist",
            tier="OPEN_SCIENCE",
            automated=True,
            adapter="inaturalist",
            capabilities=("MEDIA_PRIMARY",),
            categories=("*",),
            base_url="https://api.inaturalist.org",
        )

        def response_with_license(license_code):
            return {
                "results": [
                    {
                        "id": 123,
                        "name": "Anabas testudineus",
                        "default_photo": {
                            "id": 99,
                            "license_code": license_code,
                            "medium_url": "https://static.inaturalist.org/photo.jpg",
                            "attribution": "A photographer",
                        },
                    }
                ]
            }

        blocked = INaturalistAdapter(src, FakeTransport(lambda *_: response_with_license("cc-by-nc")))
        allowed = INaturalistAdapter(src, FakeTransport(lambda *_: response_with_license("cc-by")))

        media_task = task(field="MEDIA_PRIMARY")
        self.assertEqual([], blocked.collect(media_task))
        self.assertEqual(1, len(allowed.collect(media_task)))
        self.assertEqual("cc-by", allowed.collect(media_task)[0]["value"]["license"])

    def test_publish_task_is_always_blocked_in_collector(self):
        registry = SourceRegistry([source("gbif", "GLOBAL_AUTHORITY")])
        publish = LoadTask(
            task_id="publish",
            canonical_id="taxon:x",
            task_type="PUBLISH_RECORD",
            field_key="",
            scientific_name="Example species",
            category_id="animals",
        )
        with tempfile.TemporaryDirectory() as tmp:
            result = BatchCollector(
                registry,
                SourceHealthStore(Path(tmp) / "health.json"),
            ).collect_task(publish)
        self.assertEqual("BLOCKED", result["status"])
        self.assertTrue(any("LibraryRules" in error for error in result["errors"]))


if __name__ == "__main__":
    unittest.main()
