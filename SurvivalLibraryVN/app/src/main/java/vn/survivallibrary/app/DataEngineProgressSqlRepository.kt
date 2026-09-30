package vn.survivallibrary.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Scale-safe dashboard reader for Data Engine V2.
 *
 * The previous repository loaded every staging entity and every field state into Kotlin
 * collections before calculating dashboard totals. That is fine for the 12-record pilot,
 * but it grows linearly in RAM and GC pressure when staging reaches thousands of records.
 *
 * This reader keeps global/category aggregation inside SQLite and materializes only a small
 * priority window of entities for the UI. Published library data remains untouched.
 */
object DataEngineProgressSqlRepository {
    const val ENTITY_DETAIL_LIMIT = 60
    const val SOURCE_HEALTH_LIMIT = 50

    private data class CategoryAccumulator(
        var stagedEntities: Int = 0,
        var publishedEntities: Int = 0,
        var highRiskEntities: Int = 0,
        var verifiedFields: Int = 0,
        var fieldsWithData: Int = 0,
        val tasks: MutableMap<LibraryTaskStatus, Int> = linkedMapOf()
    )

    fun snapshot(context: Context): DataEngineDashboardSnapshot {
        val store = DataEngineStore(context.applicationContext)
        return try {
            buildSnapshot(store.readableDatabase)
        } catch (_: Exception) {
            DataEngineDashboardSnapshot.EMPTY
        } finally {
            store.close()
        }
    }

    internal fun buildSnapshot(db: SQLiteDatabase): DataEngineDashboardSnapshot {
        var stagedEntities = 0
        var publishedEntities = 0
        var highRiskEntities = 0
        db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(published), 0), COALESCE(SUM(high_risk), 0) FROM staging_entities",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                stagedEntities = cursor.getInt(0)
                publishedEntities = cursor.getInt(1)
                highRiskEntities = cursor.getInt(2)
            }
        }

        var verifiedFields = 0
        var fieldsWithData = 0
        db.rawQuery(
            """
            SELECT
                COALESCE(SUM(CASE WHEN f.verified = 1 AND (f.field_key != 'SAFETY' OR e.high_risk = 1) THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN (f.value_present = 1 OR f.evidence_count > 0)
                    AND (f.field_key != 'SAFETY' OR e.high_risk = 1) THEN 1 ELSE 0 END), 0)
            FROM staging_fields f
            JOIN staging_entities e ON e.canonical_id = f.canonical_id
            """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                verifiedFields = cursor.getInt(0)
                fieldsWithData = cursor.getInt(1)
            }
        }

        val globalTasks = LibraryTaskStatus.entries.associateWith { 0 }.toMutableMap()
        db.rawQuery("SELECT status, COUNT(*) FROM library_load_tasks GROUP BY status", null).use { cursor ->
            while (cursor.moveToNext()) {
                val status = runCatching { LibraryTaskStatus.valueOf(cursor.getString(0)) }.getOrNull() ?: continue
                globalTasks[status] = cursor.getInt(1)
            }
        }

        val categoryAccumulators = linkedMapOf<String, CategoryAccumulator>()
        db.rawQuery(
            """
            SELECT category_id, COUNT(*), COALESCE(SUM(published), 0), COALESCE(SUM(high_risk), 0)
            FROM staging_entities
            GROUP BY category_id
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                categoryAccumulators.getOrPut(cursor.getString(0)) { CategoryAccumulator() }.apply {
                    this.stagedEntities = cursor.getInt(1)
                    this.publishedEntities = cursor.getInt(2)
                    this.highRiskEntities = cursor.getInt(3)
                }
            }
        }

        db.rawQuery(
            """
            SELECT e.category_id,
                COALESCE(SUM(CASE WHEN f.verified = 1 AND (f.field_key != 'SAFETY' OR e.high_risk = 1) THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN (f.value_present = 1 OR f.evidence_count > 0)
                    AND (f.field_key != 'SAFETY' OR e.high_risk = 1) THEN 1 ELSE 0 END), 0)
            FROM staging_entities e
            LEFT JOIN staging_fields f ON f.canonical_id = e.canonical_id
            GROUP BY e.category_id
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                categoryAccumulators.getOrPut(cursor.getString(0)) { CategoryAccumulator() }.apply {
                    this.verifiedFields = cursor.getInt(1)
                    this.fieldsWithData = cursor.getInt(2)
                }
            }
        }

        db.rawQuery(
            """
            SELECT e.category_id, t.status, COUNT(*)
            FROM library_load_tasks t
            JOIN staging_entities e ON e.canonical_id = t.canonical_id
            GROUP BY e.category_id, t.status
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val status = runCatching { LibraryTaskStatus.valueOf(cursor.getString(1)) }.getOrNull() ?: continue
                categoryAccumulators.getOrPut(cursor.getString(0)) { CategoryAccumulator() }.tasks[status] = cursor.getInt(2)
            }
        }

        val categoryLabels = SurvivalLibraryCatalog.categories.associate { it.id to it.label }
        val categoryIds = (SurvivalLibraryCatalog.categories.map { it.id } + categoryAccumulators.keys).distinct()
        val categories = categoryIds.map { categoryId ->
            val acc = categoryAccumulators[categoryId] ?: CategoryAccumulator()
            val normalTracked = DataFieldKey.entries.size - 1
            val totalTrackedFields = acc.stagedEntities * normalTracked + acc.highRiskEntities
            DataEngineCategoryProgress(
                categoryId = categoryId,
                label = categoryLabels[categoryId] ?: categoryId,
                stagedEntities = acc.stagedEntities,
                publishedEntities = acc.publishedEntities,
                verifiedFields = acc.verifiedFields,
                fieldsWithData = acc.fieldsWithData,
                totalTrackedFields = totalTrackedFields,
                pendingTasks = acc.tasks[LibraryTaskStatus.PENDING] ?: 0,
                runningTasks = acc.tasks[LibraryTaskStatus.RUNNING] ?: 0,
                retryTasks = acc.tasks[LibraryTaskStatus.RETRY] ?: 0,
                completedTasks = acc.tasks[LibraryTaskStatus.COMPLETED] ?: 0,
                blockedTasks = acc.tasks[LibraryTaskStatus.BLOCKED] ?: 0
            )
        }

        val entities = mutableListOf<DataEngineEntityProgress>()
        db.rawQuery(
            """
            SELECT
                e.canonical_id, e.category_id, e.scientific_name, e.vietnamese_name,
                e.high_risk, e.published, e.updated_at,
                COUNT(DISTINCT CASE WHEN f.verified = 1
                    AND (f.field_key != 'SAFETY' OR e.high_risk = 1) THEN f.field_key END) AS verified_count,
                COUNT(DISTINCT CASE WHEN (f.value_present = 1 OR f.evidence_count > 0)
                    AND (f.field_key != 'SAFETY' OR e.high_risk = 1) THEN f.field_key END) AS data_count,
                GROUP_CONCAT(DISTINCT CASE WHEN f.verified = 1
                    AND (f.field_key != 'SAFETY' OR e.high_risk = 1) THEN f.field_key END) AS verified_keys,
                COUNT(DISTINCT CASE WHEN t.status = 'PENDING' THEN t.task_id END) AS pending_count,
                COUNT(DISTINCT CASE WHEN t.status = 'RUNNING' THEN t.task_id END) AS running_count,
                COUNT(DISTINCT CASE WHEN t.status = 'RETRY' THEN t.task_id END) AS retry_count,
                COUNT(DISTINCT CASE WHEN t.status = 'COMPLETED' THEN t.task_id END) AS completed_count,
                COUNT(DISTINCT CASE WHEN t.status = 'BLOCKED' THEN t.task_id END) AS blocked_count
            FROM staging_entities e
            LEFT JOIN staging_fields f ON f.canonical_id = e.canonical_id
            LEFT JOIN library_load_tasks t ON t.canonical_id = e.canonical_id
            GROUP BY e.canonical_id
            ORDER BY blocked_count DESC, retry_count DESC, verified_count ASC, e.updated_at DESC, e.canonical_id ASC
            LIMIT $ENTITY_DETAIL_LIMIT
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val highRisk = cursor.getInt(4) == 1
                val tracked = DataEngineProgressMath.trackedFields(highRisk)
                val verifiedKeys = cursor.getString(9)
                    ?.split(',')
                    ?.mapNotNull { raw -> runCatching { DataFieldKey.valueOf(raw) }.getOrNull() }
                    ?.toSet()
                    .orEmpty()
                entities += DataEngineEntityProgress(
                    canonicalId = cursor.getString(0),
                    categoryId = cursor.getString(1),
                    scientificName = cursor.getString(2),
                    vietnameseName = cursor.getString(3),
                    highRisk = highRisk,
                    published = cursor.getInt(5) == 1,
                    verifiedFields = cursor.getInt(7),
                    fieldsWithData = cursor.getInt(8),
                    totalTrackedFields = tracked.size,
                    pendingTasks = cursor.getInt(10),
                    runningTasks = cursor.getInt(11),
                    retryTasks = cursor.getInt(12),
                    completedTasks = cursor.getInt(13),
                    blockedTasks = cursor.getInt(14),
                    missingFields = tracked.filterNot { it in verifiedKeys },
                    updatedAt = cursor.getLong(6)
                )
            }
        }

        val sources = mutableListOf<DataEngineSourceHealth>()
        db.rawQuery(
            """
            SELECT source_key, tier, consecutive_failures, cooldown_until, last_error, updated_at
            FROM source_health
            ORDER BY (cooldown_until > ?) DESC, consecutive_failures DESC, updated_at DESC, source_key ASC
            LIMIT $SOURCE_HEALTH_LIMIT
            """.trimIndent(),
            arrayOf(System.currentTimeMillis().toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sources += DataEngineSourceHealth(
                    sourceKey = cursor.getString(0),
                    tier = cursor.getString(1),
                    consecutiveFailures = cursor.getInt(2),
                    cooldownUntil = cursor.getLong(3),
                    lastError = cursor.getString(4),
                    updatedAt = cursor.getLong(5)
                )
            }
        }

        val totalTrackedFields = stagedEntities * (DataFieldKey.entries.size - 1) + highRiskEntities
        return DataEngineDashboardSnapshot(
            stagedEntities = stagedEntities,
            publishedEntities = publishedEntities,
            verifiedFields = verifiedFields,
            fieldsWithData = fieldsWithData,
            totalTrackedFields = totalTrackedFields,
            pendingTasks = globalTasks[LibraryTaskStatus.PENDING] ?: 0,
            runningTasks = globalTasks[LibraryTaskStatus.RUNNING] ?: 0,
            retryTasks = globalTasks[LibraryTaskStatus.RETRY] ?: 0,
            completedTasks = globalTasks[LibraryTaskStatus.COMPLETED] ?: 0,
            blockedTasks = globalTasks[LibraryTaskStatus.BLOCKED] ?: 0,
            categories = categories,
            entities = entities,
            sourceHealth = sources
        )
    }
}
