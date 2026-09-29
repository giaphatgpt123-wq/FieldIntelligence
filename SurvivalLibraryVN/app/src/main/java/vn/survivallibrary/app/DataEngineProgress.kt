package vn.survivallibrary.app

import android.content.Context

data class DataEngineEntityProgress(
    val canonicalId: String,
    val categoryId: String,
    val scientificName: String,
    val vietnameseName: String,
    val highRisk: Boolean,
    val published: Boolean,
    val verifiedFields: Int,
    val fieldsWithData: Int,
    val totalTrackedFields: Int,
    val pendingTasks: Int,
    val runningTasks: Int,
    val retryTasks: Int,
    val completedTasks: Int,
    val blockedTasks: Int,
    val missingFields: List<DataFieldKey>,
    val updatedAt: Long
) {
    val completionPercent: Int
        get() = DataEngineProgressMath.percent(verifiedFields, totalTrackedFields)
}

data class DataEngineCategoryProgress(
    val categoryId: String,
    val label: String,
    val stagedEntities: Int,
    val publishedEntities: Int,
    val verifiedFields: Int,
    val fieldsWithData: Int,
    val totalTrackedFields: Int,
    val pendingTasks: Int,
    val runningTasks: Int,
    val retryTasks: Int,
    val completedTasks: Int,
    val blockedTasks: Int
) {
    val completionPercent: Int
        get() = DataEngineProgressMath.percent(verifiedFields, totalTrackedFields)
}

data class DataEngineSourceHealth(
    val sourceKey: String,
    val tier: String,
    val consecutiveFailures: Int,
    val cooldownUntil: Long,
    val lastError: String,
    val updatedAt: Long
) {
    fun coolingDown(now: Long = System.currentTimeMillis()): Boolean = cooldownUntil > now
}

data class DataEngineDashboardSnapshot(
    val stagedEntities: Int,
    val publishedEntities: Int,
    val verifiedFields: Int,
    val fieldsWithData: Int,
    val totalTrackedFields: Int,
    val pendingTasks: Int,
    val runningTasks: Int,
    val retryTasks: Int,
    val completedTasks: Int,
    val blockedTasks: Int,
    val categories: List<DataEngineCategoryProgress>,
    val entities: List<DataEngineEntityProgress>,
    val sourceHealth: List<DataEngineSourceHealth>
) {
    val completionPercent: Int
        get() = DataEngineProgressMath.percent(verifiedFields, totalTrackedFields)

    companion object {
        val EMPTY = DataEngineDashboardSnapshot(
            stagedEntities = 0,
            publishedEntities = 0,
            verifiedFields = 0,
            fieldsWithData = 0,
            totalTrackedFields = 0,
            pendingTasks = 0,
            runningTasks = 0,
            retryTasks = 0,
            completedTasks = 0,
            blockedTasks = 0,
            categories = emptyList(),
            entities = emptyList(),
            sourceHealth = emptyList()
        )
    }
}

object DataEngineProgressMath {
    fun trackedFields(highRisk: Boolean): List<DataFieldKey> =
        DataFieldKey.entries.filterNot { it == DataFieldKey.SAFETY && !highRisk }

    fun percent(verified: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((verified.coerceIn(0, total) * 100.0) / total).toInt().coerceIn(0, 100)
    }

    fun category(
        categoryId: String,
        label: String,
        entities: List<DataEngineEntityProgress>
    ): DataEngineCategoryProgress {
        val rows = entities.filter { it.categoryId == categoryId }
        return DataEngineCategoryProgress(
            categoryId = categoryId,
            label = label,
            stagedEntities = rows.size,
            publishedEntities = rows.count { it.published },
            verifiedFields = rows.sumOf { it.verifiedFields },
            fieldsWithData = rows.sumOf { it.fieldsWithData },
            totalTrackedFields = rows.sumOf { it.totalTrackedFields },
            pendingTasks = rows.sumOf { it.pendingTasks },
            runningTasks = rows.sumOf { it.runningTasks },
            retryTasks = rows.sumOf { it.retryTasks },
            completedTasks = rows.sumOf { it.completedTasks },
            blockedTasks = rows.sumOf { it.blockedTasks }
        )
    }
}

/**
 * Read-only view over Data Engine V2 staging. This dashboard never fabricates target counts.
 * If collector output has not yet been imported into staging, all staging counters remain zero.
 */
object DataEngineProgressRepository {
    private data class EntitySeed(
        val canonicalId: String,
        val categoryId: String,
        val scientificName: String,
        val vietnameseName: String,
        val highRisk: Boolean,
        val published: Boolean,
        val updatedAt: Long
    )

    fun snapshot(context: Context): DataEngineDashboardSnapshot {
        val store = DataEngineStore(context.applicationContext)
        return try {
            val db = store.readableDatabase
            val seeds = mutableListOf<EntitySeed>()
            db.rawQuery(
                """
                SELECT canonical_id, category_id, scientific_name, vietnamese_name,
                       high_risk, published, updated_at
                FROM staging_entities
                ORDER BY updated_at DESC, canonical_id ASC
                """.trimIndent(),
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    seeds += EntitySeed(
                        canonicalId = cursor.getString(0),
                        categoryId = cursor.getString(1),
                        scientificName = cursor.getString(2),
                        vietnameseName = cursor.getString(3),
                        highRisk = cursor.getInt(4) == 1,
                        published = cursor.getInt(5) == 1,
                        updatedAt = cursor.getLong(6)
                    )
                }
            }

            val fieldStates = mutableMapOf<String, MutableMap<DataFieldKey, StagedFieldState>>()
            db.rawQuery(
                """
                SELECT canonical_id, field_key, value_present, evidence_count, verified,
                       source_key, last_error
                FROM staging_fields
                """.trimIndent(),
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val key = runCatching { DataFieldKey.valueOf(cursor.getString(1)) }.getOrNull() ?: continue
                    fieldStates.getOrPut(cursor.getString(0)) { linkedMapOf() }[key] = StagedFieldState(
                        field = key,
                        valuePresent = cursor.getInt(2) == 1,
                        evidenceCount = cursor.getInt(3),
                        verified = cursor.getInt(4) == 1,
                        lastSourceKey = cursor.getString(5),
                        lastError = cursor.getString(6)
                    )
                }
            }

            val taskCounts = mutableMapOf<String, MutableMap<LibraryTaskStatus, Int>>()
            db.rawQuery(
                "SELECT canonical_id, status, COUNT(*) FROM library_load_tasks GROUP BY canonical_id, status",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val status = runCatching { LibraryTaskStatus.valueOf(cursor.getString(1)) }.getOrNull() ?: continue
                    taskCounts.getOrPut(cursor.getString(0)) { linkedMapOf() }[status] = cursor.getInt(2)
                }
            }

            val entities = seeds.map { seed ->
                val tracked = DataEngineProgressMath.trackedFields(seed.highRisk)
                val states = fieldStates[seed.canonicalId].orEmpty()
                val tasks = taskCounts[seed.canonicalId].orEmpty()
                DataEngineEntityProgress(
                    canonicalId = seed.canonicalId,
                    categoryId = seed.categoryId,
                    scientificName = seed.scientificName,
                    vietnameseName = seed.vietnameseName,
                    highRisk = seed.highRisk,
                    published = seed.published,
                    verifiedFields = tracked.count { states[it]?.verified == true },
                    fieldsWithData = tracked.count {
                        val state = states[it]
                        state?.valuePresent == true || (state?.evidenceCount ?: 0) > 0
                    },
                    totalTrackedFields = tracked.size,
                    pendingTasks = tasks[LibraryTaskStatus.PENDING] ?: 0,
                    runningTasks = tasks[LibraryTaskStatus.RUNNING] ?: 0,
                    retryTasks = tasks[LibraryTaskStatus.RETRY] ?: 0,
                    completedTasks = tasks[LibraryTaskStatus.COMPLETED] ?: 0,
                    blockedTasks = tasks[LibraryTaskStatus.BLOCKED] ?: 0,
                    missingFields = tracked.filter { states[it]?.verified != true },
                    updatedAt = seed.updatedAt
                )
            }

            val categoryMap = SurvivalLibraryCatalog.categories.associateBy { it.id }
            val allCategoryIds = (SurvivalLibraryCatalog.categories.map { it.id } + entities.map { it.categoryId }).distinct()
            val categories = allCategoryIds.map { id ->
                DataEngineProgressMath.category(
                    categoryId = id,
                    label = categoryMap[id]?.label ?: id,
                    entities = entities
                )
            }

            val sources = mutableListOf<DataEngineSourceHealth>()
            db.rawQuery(
                """
                SELECT source_key, tier, consecutive_failures, cooldown_until, last_error, updated_at
                FROM source_health
                ORDER BY cooldown_until DESC, consecutive_failures DESC, source_key ASC
                """.trimIndent(),
                null
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

            DataEngineDashboardSnapshot(
                stagedEntities = entities.size,
                publishedEntities = entities.count { it.published },
                verifiedFields = entities.sumOf { it.verifiedFields },
                fieldsWithData = entities.sumOf { it.fieldsWithData },
                totalTrackedFields = entities.sumOf { it.totalTrackedFields },
                pendingTasks = entities.sumOf { it.pendingTasks },
                runningTasks = entities.sumOf { it.runningTasks },
                retryTasks = entities.sumOf { it.retryTasks },
                completedTasks = entities.sumOf { it.completedTasks },
                blockedTasks = entities.sumOf { it.blockedTasks },
                categories = categories,
                entities = entities.sortedWith(
                    compareByDescending<DataEngineEntityProgress> { it.blockedTasks > 0 }
                        .thenByDescending { it.retryTasks }
                        .thenBy { it.completionPercent }
                        .thenBy { it.vietnameseName.ifBlank { it.scientificName } }
                ),
                sourceHealth = sources
            )
        } catch (_: Exception) {
            DataEngineDashboardSnapshot.EMPTY
        } finally {
            store.close()
        }
    }
}
