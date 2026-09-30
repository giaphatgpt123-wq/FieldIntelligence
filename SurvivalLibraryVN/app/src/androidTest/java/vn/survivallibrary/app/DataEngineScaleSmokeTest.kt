package vn.survivallibrary.app

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataEngineScaleSmokeTest {
    @Test
    fun dashboardAggregatesAllRowsButMaterializesBoundedEntityWindow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DataEngineStore(context)
        val db = store.writableDatabase
        try {
            db.beginTransaction()
            try {
                db.delete("staging_aliases", null, null)
                db.delete("staging_fields", null, null)
                db.delete("library_load_tasks", null, null)
                db.delete("source_health", null, null)
                db.delete("staging_entities", null, null)

                repeat(250) { index ->
                    val canonicalId = "scale:$index"
                    db.insertOrThrow("staging_entities", null, ContentValues().apply {
                        put("canonical_id", canonicalId)
                        put("category_id", "vegetables")
                        put("scientific_name", "Example species $index")
                        put("vietnamese_name", "Mẫu $index")
                        put("high_risk", 0)
                        put("published", 0)
                        put("updated_at", index.toLong())
                    })
                    db.insertOrThrow("staging_fields", null, ContentValues().apply {
                        put("canonical_id", canonicalId)
                        put("field_key", DataFieldKey.CANONICAL_IDENTITY.name)
                        put("value_json", "{}")
                        put("value_present", 1)
                        put("evidence_count", 1)
                        put("verified", 1)
                        put("source_key", "scale-source")
                        put("last_error", "")
                        put("updated_at", index.toLong())
                    })
                    db.insertOrThrow("library_load_tasks", null, ContentValues().apply {
                        put("task_id", "scale-task-$index")
                        put("canonical_id", canonicalId)
                        put("task_type", LibraryTaskType.RESOLVE_CANONICAL.name)
                        put("field_key", DataFieldKey.CANONICAL_IDENTITY.name)
                        put("priority", 100)
                        put("preferred_source_tiers", SourceTier.GLOBAL_AUTHORITY.name)
                        put("status", LibraryTaskStatus.COMPLETED.name)
                        put("attempts", 1)
                        put("retry_after", 0)
                        put("last_error", "")
                        put("updated_at", index.toLong())
                    })
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            val snapshot = DataEngineProgressSqlRepository.snapshot(context)
            assertEquals(250, snapshot.stagedEntities)
            assertEquals(250, snapshot.verifiedFields)
            assertEquals(250, snapshot.completedTasks)
            assertEquals(DataEngineProgressSqlRepository.ENTITY_DETAIL_LIMIT, snapshot.entities.size)
            assertTrue(snapshot.entities.size < snapshot.stagedEntities)
            val vegetables = snapshot.categories.first { it.categoryId == "vegetables" }
            assertEquals(250, vegetables.stagedEntities)
            assertEquals(250, vegetables.verifiedFields)
            assertEquals(250, vegetables.completedTasks)
        } finally {
            db.delete("staging_aliases", null, null)
            db.delete("staging_fields", null, null)
            db.delete("library_load_tasks", null, null)
            db.delete("source_health", null, null)
            db.delete("staging_entities", null, null)
            store.close()
        }
    }
}
