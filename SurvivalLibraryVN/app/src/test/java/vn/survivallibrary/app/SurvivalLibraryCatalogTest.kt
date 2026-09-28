package vn.survivallibrary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurvivalLibraryCatalogTest {
    @Test
    fun categoryIdsAreUnique() {
        val ids = SurvivalLibraryCatalog.categories.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun progressCoversEveryCategoryWithoutInventedTargets() {
        val categoryIds = SurvivalLibraryCatalog.categories.map { it.id }.toSet()
        val progressIds = SurvivalLibraryCatalog.progress.map { it.categoryId }.toSet()
        assertEquals(categoryIds, progressIds)
        SurvivalLibraryCatalog.progress.forEach { item ->
            assertNull(item.targetCount)
            assertEquals(0, item.collected)
            assertEquals(0, item.withVerifiedMedia)
            assertEquals(0, item.vietnameseNameReviewed)
            assertEquals(0, item.verified)
            assertEquals(0, item.published)
        }
    }

    @Test
    fun demoRecordsCannotMasqueradeAsPublishedData() {
        assertTrue(SurvivalLibraryCatalog.demoRecords.isNotEmpty())
        SurvivalLibraryCatalog.demoRecords.forEach { record ->
            assertTrue(record.isDemo)
            assertEquals(UsageLevel.CHUA_PHAN_LOAI, record.usageLevel)
            assertEquals(VerificationState.CHUA_CO, record.verificationState)
        }
    }
}
