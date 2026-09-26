package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionCatalogTest {
    @Test fun publishedInteractionsRequireValidProvenance() {
        assertTrue(InteractionCatalog.records.size >= 9)
        assertTrue(InteractionCatalog.allValidated())
        assertTrue(InteractionCatalog.records.all { record ->
            record.sources.all { it.url.startsWith("https://") && it.authority.isNotBlank() }
        })
        assertEquals(InteractionCatalog.records.size, InteractionCatalog.records.map { it.id }.toSet().size)
    }

    @Test fun knownPairCanBeQueriedInEitherDirection() {
        val direct = InteractionCatalog.pair("grapefruit", "simvastatin")
        val reverse = InteractionCatalog.pair("simvastatin", "grapefruit")
        assertEquals("grapefruit-simvastatin-fda", direct.single().id)
        assertEquals(direct, reverse)
        assertEquals(EvidenceLevel.CONFIRMED, direct.single().evidence)
    }

    @Test fun medicinalPlantInteractionsAreLinkedByScientificName() {
        val hypericum = InteractionCatalog.findForEntity("Hypericum perforatum")
        assertTrue(hypericum.size >= 4)
        assertTrue(hypericum.any { it.id == "hypericum-cyclosporine-nccih" })
        assertTrue(hypericum.any { it.id == "hypericum-warfarin-nccih" })
        assertTrue(hypericum.any { it.id == "hypericum-oral-contraceptive-nccih" })
        assertTrue(hypericum.any { it.id == "hypericum-serotonergic-antidepressant-nccih" })

        val greenTea = InteractionCatalog.pair("Camellia sinensis", "nadolol")
        assertEquals("green-tea-nadolol-nccih", greenTea.single().id)
        assertEquals(EvidenceLevel.PROBABLE, greenTea.single().evidence)
    }

    @Test fun foodDrugSafetyDatasetIncludesVitaminKAndWarfarin() {
        val pair = InteractionCatalog.pair("vitamin K", "warfarin")
        assertEquals("vitamin-k-warfarin-nih", pair.single().id)
        assertEquals(EvidenceLevel.CONFIRMED, pair.single().evidence)
    }

    @Test fun unsupportedPairDoesNotInventConflict() {
        assertTrue(InteractionCatalog.pair("xoai", "dua").isEmpty())
        assertTrue(InteractionCatalog.findForEntity("khong-ton-tai").isEmpty())
    }

    @Test fun traditionalClaimCannotBePromotedToCriticalWithoutEvidence() {
        val bad = SafetyInteraction(
            id = "bad-traditional",
            a = InteractionEntity("a", "A", InteractionEntityType.HERB),
            b = InteractionEntity("b", "B", InteractionEntityType.FOOD),
            type = InteractionType.HERB_HERB,
            severity = InteractionSeverity.CRITICAL,
            evidence = EvidenceLevel.TRADITIONAL_CLAIM,
            summary = "Truyền miệng",
            action = "Không kết luận từ dữ liệu này.",
            sources = listOf(InteractionSource("Nguồn", "Tài liệu", "https://example.org/source"))
        )
        assertTrue(InteractionCatalog.validate(bad).any { it.contains("traditional claim") })
    }
}
