package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionCatalogTest {
    @Test fun publishedInteractionsRequireValidProvenance() {
        assertTrue(InteractionCatalog.records.isNotEmpty())
        assertTrue(InteractionCatalog.allValidated())
        assertTrue(InteractionCatalog.records.all { record ->
            record.sources.all { it.url.startsWith("https://") && it.authority.isNotBlank() }
        })
    }

    @Test fun knownPairCanBeQueriedInEitherDirection() {
        val direct = InteractionCatalog.pair("grapefruit", "simvastatin")
        val reverse = InteractionCatalog.pair("simvastatin", "grapefruit")
        assertEquals("grapefruit-simvastatin-fda", direct.single().id)
        assertEquals(direct, reverse)
        assertEquals(EvidenceLevel.CONFIRMED, direct.single().evidence)
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
