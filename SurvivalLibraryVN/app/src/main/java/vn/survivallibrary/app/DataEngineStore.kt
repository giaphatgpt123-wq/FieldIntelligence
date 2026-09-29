package vn.survivallibrary.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Separate staging database for Data Engine V2.
 *
 * It deliberately does not replace the published offline library database. Collector/AI
 * work can therefore fail, retry or remain partial without corrupting data already shown
 * to the user.
 */
class DataEngineStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) = createSchema(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = createSchema(db)

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS staging_entities (
                canonical_id TEXT PRIMARY KEY,
                category_id TEXT NOT NULL,
                scientific_name TEXT NOT NULL,
                vietnamese_name TEXT NOT NULL DEFAULT '',
                high_risk INTEGER NOT NULL DEFAULT 0,
                published INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS staging_aliases (
                canonical_id TEXT NOT NULL,
                normalized_name TEXT NOT NULL,
                display_name TEXT NOT NULL,
                alias_type TEXT NOT NULL,
                region TEXT NOT NULL DEFAULT '',
                source_key TEXT NOT NULL DEFAULT '',
                verified INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(canonical_id, normalized_name, region)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS staging_fields (
                canonical_id TEXT NOT NULL,
                field_key TEXT NOT NULL,
                value_json TEXT NOT NULL DEFAULT '',
                value_present INTEGER NOT NULL DEFAULT 0,
                evidence_count INTEGER NOT NULL DEFAULT 0,
                verified INTEGER NOT NULL DEFAULT 0,
                source_key TEXT NOT NULL DEFAULT '',
                last_error TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(canonical_id, field_key)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS library_load_tasks (
                task_id TEXT PRIMARY KEY,
                canonical_id TEXT NOT NULL,
                task_type TEXT NOT NULL,
                field_key TEXT NOT NULL DEFAULT '',
                priority INTEGER NOT NULL DEFAULT 0,
                preferred_source_tiers TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'PENDING',
                attempts INTEGER NOT NULL DEFAULT 0,
                retry_after INTEGER NOT NULL DEFAULT 0,
                last_error TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS source_health (
                source_key TEXT PRIMARY KEY,
                tier TEXT NOT NULL DEFAULT '',
                consecutive_failures INTEGER NOT NULL DEFAULT 0,
                cooldown_until INTEGER NOT NULL DEFAULT 0,
                last_error TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_staging_category ON staging_entities(category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_alias_normalized ON staging_aliases(normalized_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_field_verified ON staging_fields(verified, field_key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_ready ON library_load_tasks(status, retry_after, priority)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_entity ON library_load_tasks(canonical_id)")
    }

    fun upsertEntity(entity: StagedLibraryEntity) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("canonical_id", entity.canonicalId)
            put("category_id", entity.categoryId)
            put("scientific_name", entity.scientificName)
            put("vietnamese_name", entity.vietnameseName)
            put("high_risk", if (entity.highRisk) 1 else 0)
            put("published", if (entity.published) 1 else 0)
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("staging_entities", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        entity.aliases.forEach { upsertAlias(entity.canonicalId, it, now) }
        entity.fields.values.forEach { upsertField(entity.canonicalId, it, "", now) }
    }

    fun upsertAlias(canonicalId: String, alias: VietnameseAlias, now: Long = System.currentTimeMillis()) {
        if (alias.normalizedName.isBlank()) return
        val values = ContentValues().apply {
            put("canonical_id", canonicalId)
            put("normalized_name", alias.normalizedName)
            put("display_name", alias.displayName)
            put("alias_type", alias.aliasType.name)
            put("region", alias.region)
            put("source_key", alias.sourceKey)
            put("verified", if (alias.verified) 1 else 0)
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("staging_aliases", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertField(
        canonicalId: String,
        state: StagedFieldState,
        valueJson: String,
        now: Long = System.currentTimeMillis()
    ) {
        val values = ContentValues().apply {
            put("canonical_id", canonicalId)
            put("field_key", state.field.name)
            put("value_json", valueJson)
            put("value_present", if (state.valuePresent) 1 else 0)
            put("evidence_count", state.evidenceCount)
            put("verified", if (state.verified) 1 else 0)
            put("source_key", state.lastSourceKey)
            put("last_error", state.lastError)
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("staging_fields", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Idempotent enqueue: planning the same entity repeatedly never duplicates work.
     * A completed/blocked task is intentionally preserved and is not reset silently.
     */
    fun enqueue(tasks: List<LibraryLoadTask>): Int {
        val db = writableDatabase
        var inserted = 0
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            tasks.forEach { task ->
                val values = ContentValues().apply {
                    put("task_id", task.taskId)
                    put("canonical_id", task.canonicalId)
                    put("task_type", task.taskType.name)
                    put("field_key", task.field?.name.orEmpty())
                    put("priority", task.priority)
                    put("preferred_source_tiers", task.preferredSourceTiers.joinToString(",") { it.name })
                    put("status", task.status.name)
                    put("attempts", task.attempts)
                    put("retry_after", task.retryAfter)
                    put("last_error", task.lastError)
                    put("updated_at", now)
                }
                val row = db.insertWithOnConflict("library_load_tasks", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (row != -1L) inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun readyTasks(limit: Int = 50, now: Long = System.currentTimeMillis()): List<LibraryLoadTask> {
        require(limit in 1..500) { "limit phải trong 1..500" }
        val result = mutableListOf<LibraryLoadTask>()
        readableDatabase.query(
            "library_load_tasks",
            arrayOf(
                "task_id", "canonical_id", "task_type", "field_key", "priority",
                "preferred_source_tiers", "status", "attempts", "retry_after", "last_error"
            ),
            "status IN (?, ?) AND retry_after <= ?",
            arrayOf(LibraryTaskStatus.PENDING.name, LibraryTaskStatus.RETRY.name, now.toString()),
            null,
            null,
            "priority DESC, attempts ASC, updated_at ASC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val taskType = LibraryTaskType.valueOf(cursor.getString(2))
                val rawField = cursor.getString(3)
                val tiers = cursor.getString(5)
                    .split(',')
                    .filter { it.isNotBlank() }
                    .mapNotNull { raw -> SourceTier.entries.firstOrNull { it.name == raw } }
                result += LibraryLoadTask(
                    taskId = cursor.getString(0),
                    canonicalId = cursor.getString(1),
                    taskType = taskType,
                    field = rawField.takeIf { it.isNotBlank() }?.let(DataFieldKey::valueOf),
                    priority = cursor.getInt(4),
                    preferredSourceTiers = tiers,
                    status = LibraryTaskStatus.valueOf(cursor.getString(6)),
                    attempts = cursor.getInt(7),
                    retryAfter = cursor.getLong(8),
                    lastError = cursor.getString(9)
                )
            }
        }
        return result
    }

    fun markRunning(taskId: String) = updateTask(taskId, LibraryTaskStatus.RUNNING, null, 0L, incrementAttempts = true)

    fun markCompleted(taskId: String) = updateTask(taskId, LibraryTaskStatus.COMPLETED, "", 0L, incrementAttempts = false)

    fun markRetry(taskId: String, error: String, retryAfter: Long) =
        updateTask(taskId, LibraryTaskStatus.RETRY, error, retryAfter, incrementAttempts = false)

    fun markBlocked(taskId: String, error: String) =
        updateTask(taskId, LibraryTaskStatus.BLOCKED, error, 0L, incrementAttempts = false)

    private fun updateTask(
        taskId: String,
        status: LibraryTaskStatus,
        error: String?,
        retryAfter: Long,
        incrementAttempts: Boolean
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", status.name)
            put("retry_after", retryAfter)
            if (error != null) put("last_error", error)
            put("updated_at", System.currentTimeMillis())
        }
        if (incrementAttempts) {
            db.execSQL(
                "UPDATE library_load_tasks SET attempts = attempts + 1 WHERE task_id = ?",
                arrayOf(taskId)
            )
        }
        db.update("library_load_tasks", values, "task_id = ?", arrayOf(taskId))
    }

    fun taskCounts(canonicalId: String): Map<LibraryTaskStatus, Int> {
        val result = LibraryTaskStatus.entries.associateWith { 0 }.toMutableMap()
        readableDatabase.rawQuery(
            "SELECT status, COUNT(*) FROM library_load_tasks WHERE canonical_id = ? GROUP BY status",
            arrayOf(canonicalId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val status = LibraryTaskStatus.entries.firstOrNull { it.name == cursor.getString(0) } ?: continue
                result[status] = cursor.getInt(1)
            }
        }
        return result
    }

    /**
     * Portable Android implementation: minSdk 26 may expose SQLite versions older
     * than native `ON CONFLICT ... DO UPDATE`. Update first; insert only when absent.
     */
    fun markSourceFailure(
        sourceKey: String,
        tier: SourceTier,
        error: String,
        cooldownUntil: Long
    ) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val currentFailures = db.query(
                "source_health",
                arrayOf("consecutive_failures"),
                "source_key = ?",
                arrayOf(sourceKey),
                null,
                null,
                null,
                "1"
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }

            val values = ContentValues().apply {
                put("source_key", sourceKey)
                put("tier", tier.name)
                put("consecutive_failures", (currentFailures ?: 0) + 1)
                put("cooldown_until", cooldownUntil)
                put("last_error", error)
                put("updated_at", now)
            }
            if (currentFailures == null) {
                val row = db.insertWithOnConflict("source_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                require(row != -1L) { "Không thể ghi source health cho $sourceKey" }
            } else {
                val rows = db.update("source_health", values, "source_key = ?", arrayOf(sourceKey))
                require(rows == 1) { "Không thể cập nhật source health cho $sourceKey" }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markSourceHealthy(sourceKey: String, tier: SourceTier) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("source_key", sourceKey)
            put("tier", tier.name)
            put("consecutive_failures", 0)
            put("cooldown_until", 0)
            put("last_error", "")
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("source_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isSourceCoolingDown(sourceKey: String, now: Long = System.currentTimeMillis()): Boolean =
        readableDatabase.query(
            "source_health",
            arrayOf("cooldown_until"),
            "source_key = ?",
            arrayOf(sourceKey),
            null,
            null,
            null,
            "1"
        ).use { cursor -> cursor.moveToFirst() && cursor.getLong(0) > now }

    companion object {
        private const val DB_NAME = "survival_library_data_engine_v2.db"
        private const val DB_VERSION = 1
    }
}
