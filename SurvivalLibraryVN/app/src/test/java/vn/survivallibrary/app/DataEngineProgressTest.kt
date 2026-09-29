package vn.survivallibrary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataEngineProgressTest {
    @Test
    fun nonRiskRecordDoesNotCountSafetyField() {
        val fields = DataEngineProgressMath.trackedFields(highRisk = false)
        assertFalse(fields.contains(DataFieldKey.SAFETY))
        assertEquals(DataFieldKey.entries.size - 1, fields.size)
    }

    @Test
    fun highRiskRecordCountsSafetyField() {
        val fields = DataEngineProgressMath.trackedFields(highRisk = true)
        assertTrue(fields.contains(DataFieldKey.SAFETY))
        assertEquals(DataFieldKey.entries.size, fields.size)
    }

    @Test
    fun percentNeverExceedsBounds() {
        assertEquals(0, DataEngineProgressMath.percent(3, 0))
        assertEquals(0, DataEngineProgressMath.percent(-2, 10))
        assertEquals(50, DataEngineProgressMath.percent(5, 10))
        assertEquals(100, DataEngineProgressMath.percent(20, 10))
    }

    @Test
    fun categoryAggregationUsesRealEntityCountsAndTasks() {
        val entities = listOf(
            entity(
                id = "a",
                category = "flowers",
                verified = 5,
                withData = 7,
                total = 10,
                pending = 2,
                retry = 1,
                blocked = 0,
                published = false
            ),
            entity(
                id = "b",
                category = "flowers",
                verified = 10,
                withData = 10,
                total = 10,
                pending = 0,
                retry = 0,
                blocked = 1,
                published = true
            ),
            entity(
                id = "c",
                category = "freshwater-fish",
                verified = 8,
                withData = 9,
                total = 10,
                pending = 1,
                retry = 0,
                blocked = 0,
                published = false
            )
        )

        val flowers = DataEngineProgressMath.category("flowers", "Hoa", entities)

        assertEquals(2, flowers.stagedEntities)
        assertEquals(1, flowers.publishedEntities)
        assertEquals(15, flowers.verifiedFields)
        assertEquals(17, flowers.fieldsWithData)
        assertEquals(20, flowers.totalTrackedFields)
        assertEquals(75, flowers.completionPercent)
        assertEquals(2, flowers.pendingTasks)
        assertEquals(1, flowers.retryTasks)
        assertEquals(1, flowers.blockedTasks)
    }

    private fun entity(
        id: String,
        category: String,
        verified: Int,
        withData: Int,
        total: Int,
        pending: Int,
        retry: Int,
        blocked: Int,
        published: Boolean
    ) = DataEngineEntityProgress(
        canonicalId = id,
        categoryId = category,
        scientificName = "Example species",
        vietnameseName = "Tên Việt",
        highRisk = false,
        published = published,
        verifiedFields = verified,
        fieldsWithData = withData,
        totalTrackedFields = total,
        pendingTasks = pending,
        runningTasks = 0,
        retryTasks = retry,
        completedTasks = 3,
        blockedTasks = blocked,
        missingFields = emptyList(),
        updatedAt = 0L
    )
}
