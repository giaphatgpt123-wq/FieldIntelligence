package vn.survivallibrary.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class DataEngineStagingSyncResult(
    val checked: Boolean,
    val updated: Boolean,
    val version: Int,
    val entityCount: Int,
    val taskCount: Int,
    val message: String,
    val errors: List<String> = emptyList()
)

data class DataEngineRemoteSnapshot(
    val version: Int,
    val schemaVersion: Int,
    val generatedAt: Long,
    val sha256: String,
    val snapshotUrl: String,
    val entityCount: Int,
    val taskCount: Int
)

object DataEngineStagingIndexParser {
    fun parse(json: String): DataEngineRemoteSnapshot {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", -1) == 1) { "Staging index schema không được hỗ trợ" }
        val snapshot = root.getJSONObject("snapshot")
        val result = DataEngineRemoteSnapshot(
            version = snapshot.getInt("version"),
            schemaVersion = snapshot.getInt("schemaVersion"),
            generatedAt = snapshot.optLong("generatedAt", 0L),
            sha256 = snapshot.getString("sha256").lowercase(),
            snapshotUrl = snapshot.getString("snapshotUrl"),
            entityCount = snapshot.getInt("entityCount"),
            taskCount = snapshot.getInt("taskCount")
        )
        require(result.version > 0) { "Staging version không hợp lệ" }
        require(result.schemaVersion == 1) { "Snapshot schema không được hỗ trợ" }
        require(result.sha256.matches(Regex("^[a-f0-9]{64}$"))) { "SHA-256 staging không hợp lệ" }
        require(result.entityCount in 0..MAX_ENTITIES) { "Số entity staging vượt giới hạn" }
        require(result.taskCount in 0..MAX_TASKS) { "Số task staging vượt giới hạn" }
        return result
    }

    const val MAX_ENTITIES = 20_000
    const val MAX_TASKS = 250_000
    const val MAX_FIELDS = 250_000
    const val MAX_ALIASES = 250_000
    const val MAX_SOURCES = 5_000
}

/**
 * Mirrors pipeline staging/progress to the Android device.
 *
 * The device never treats this mirror as PUBLISHED library content. It is used only
 * for progress/diagnostics. Published records still go through LibraryUpdateEngine +
 * LibraryRules and remain in OfflineLibraryDb.
 */
object DataEngineStagingSync {
    const val INDEX_URL = "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/SurvivalLibraryVN/data/staging/progress-index.json"

    private const val PREFS = "data_engine_staging_sync"
    private const val KEY_VERSION = "version"
    private const val MAX_INDEX_BYTES = 256 * 1024
    private const val MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024
    private val allowedHosts = setOf("raw.githubusercontent.com", "github.com")

    fun checkAndSync(context: Context): DataEngineStagingSyncResult {
        val appContext = context.applicationContext
        return try {
            val remote = DataEngineStagingIndexParser.parse(downloadText(INDEX_URL, MAX_INDEX_BYTES))
            val localVersion = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_VERSION, 0)
            if (remote.version <= localVersion) {
                return DataEngineStagingSyncResult(
                    checked = true,
                    updated = false,
                    version = localVersion,
                    entityCount = DataEngineProgressRepository.snapshot(appContext).stagedEntities,
                    taskCount = 0,
                    message = "Tiến độ AI/Data Engine đã là phiên bản mới nhất."
                )
            }

            validateUrl(remote.snapshotUrl)
            val bytes = downloadBytes(remote.snapshotUrl, MAX_SNAPSHOT_BYTES)
            require(sha256(bytes) == remote.sha256) { "SHA-256 staging snapshot không khớp" }
            val counts = importSnapshot(appContext, bytes, remote)
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_VERSION, remote.version)
                .apply()

            DataEngineStagingSyncResult(
                checked = true,
                updated = true,
                version = remote.version,
                entityCount = counts.first,
                taskCount = counts.second,
                message = "Đã đồng bộ tiến độ AI: ${counts.first} hồ sơ staging · ${counts.second} task."
            )
        } catch (error: Exception) {
            DataEngineStagingSyncResult(
                checked = false,
                updated = false,
                version = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_VERSION, 0),
                entityCount = 0,
                taskCount = 0,
                message = "Không thể đồng bộ tiến độ AI lúc này.",
                errors = listOf(error.message ?: "Lỗi staging sync không xác định")
            )
        }
    }

    private fun importSnapshot(
        context: Context,
        bytes: ByteArray,
        remote: DataEngineRemoteSnapshot
    ): Pair<Int, Int> {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optInt("schemaVersion", -1) == remote.schemaVersion) { "Sai schemaVersion staging snapshot" }
        require(root.getInt("version") == remote.version) { "Sai version staging snapshot" }
        val entities = root.optJSONArray("entities") ?: throw IllegalArgumentException("Snapshot thiếu entities")
        val aliases = root.optJSONArray("aliases") ?: throw IllegalArgumentException("Snapshot thiếu aliases")
        val fields = root.optJSONArray("fields") ?: throw IllegalArgumentException("Snapshot thiếu fields")
        val tasks = root.optJSONArray("tasks") ?: throw IllegalArgumentException("Snapshot thiếu tasks")
        val sources = root.optJSONArray("sourceHealth") ?: throw IllegalArgumentException("Snapshot thiếu sourceHealth")

        require(entities.length() == remote.entityCount) { "entityCount staging không khớp index" }
        require(tasks.length() == remote.taskCount) { "taskCount staging không khớp index" }
        require(entities.length() <= DataEngineStagingIndexParser.MAX_ENTITIES) { "Quá nhiều staging entities" }
        require(aliases.length() <= DataEngineStagingIndexParser.MAX_ALIASES) { "Quá nhiều staging aliases" }
        require(fields.length() <= DataEngineStagingIndexParser.MAX_FIELDS) { "Quá nhiều staging fields" }
        require(tasks.length() <= DataEngineStagingIndexParser.MAX_TASKS) { "Quá nhiều staging tasks" }
        require(sources.length() <= DataEngineStagingIndexParser.MAX_SOURCES) { "Quá nhiều source-health rows" }

        val store = DataEngineStore(context.applicationContext)
        try {
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                // This is a mirror replacement. Published data lives in OfflineLibraryDb and is untouched.
                db.delete("staging_aliases", null, null)
                db.delete("staging_fields", null, null)
                db.delete("library_load_tasks", null, null)
                db.delete("source_health", null, null)
                db.delete("staging_entities", null, null)

                for (index in 0 until entities.length()) {
                    val row = entities.getJSONObject(index)
                    val canonicalId = row.getString("canonicalId").trim()
                    val categoryId = row.getString("categoryId").trim()
                    val scientificName = row.getString("scientificName").trim()
                    require(canonicalId.isNotBlank() && categoryId.isNotBlank() && scientificName.isNotBlank()) {
                        "Staging entity thiếu canonical/category/scientific name"
                    }
                    insertOrThrow(db, "staging_entities", ContentValues().apply {
                        put("canonical_id", canonicalId)
                        put("category_id", categoryId)
                        put("scientific_name", scientificName)
                        put("vietnamese_name", row.optString("vietnameseName", ""))
                        put("high_risk", if (row.optBoolean("highRisk", false)) 1 else 0)
                        put("published", if (row.optBoolean("published", false)) 1 else 0)
                        put("updated_at", row.optLong("updatedAt", remote.generatedAt))
                    })
                }

                for (index in 0 until aliases.length()) {
                    val row = aliases.getJSONObject(index)
                    val type = AliasType.valueOf(row.getString("aliasType"))
                    val display = row.getString("displayName").trim()
                    val normalized = VietnameseNameKey.normalize(display)
                    require(normalized.isNotBlank()) { "Alias staging không hợp lệ" }
                    insertOrThrow(db, "staging_aliases", ContentValues().apply {
                        put("canonical_id", row.getString("canonicalId"))
                        put("normalized_name", normalized)
                        put("display_name", display)
                        put("alias_type", type.name)
                        put("region", row.optString("region", ""))
                        put("source_key", row.optString("sourceKey", ""))
                        put("verified", if (row.optBoolean("verified", false)) 1 else 0)
                        put("updated_at", row.optLong("updatedAt", remote.generatedAt))
                    })
                }

                for (index in 0 until fields.length()) {
                    val row = fields.getJSONObject(index)
                    val field = DataFieldKey.valueOf(row.getString("field"))
                    insertOrThrow(db, "staging_fields", ContentValues().apply {
                        put("canonical_id", row.getString("canonicalId"))
                        put("field_key", field.name)
                        put("value_json", row.optString("valueJson", ""))
                        put("value_present", if (row.optBoolean("valuePresent", false)) 1 else 0)
                        put("evidence_count", row.optInt("evidenceCount", 0).coerceAtLeast(0))
                        put("verified", if (row.optBoolean("verified", false)) 1 else 0)
                        put("source_key", row.optString("sourceKey", ""))
                        put("last_error", row.optString("lastError", ""))
                        put("updated_at", row.optLong("updatedAt", remote.generatedAt))
                    })
                }

                for (index in 0 until tasks.length()) {
                    val row = tasks.getJSONObject(index)
                    val taskType = LibraryTaskType.valueOf(row.getString("taskType"))
                    val status = LibraryTaskStatus.valueOf(row.getString("status"))
                    val rawField = row.optString("field", "")
                    if (rawField.isNotBlank()) DataFieldKey.valueOf(rawField)
                    insertOrThrow(db, "library_load_tasks", ContentValues().apply {
                        put("task_id", row.getString("taskId"))
                        put("canonical_id", row.getString("canonicalId"))
                        put("task_type", taskType.name)
                        put("field_key", rawField)
                        put("priority", row.optInt("priority", 0))
                        put("preferred_source_tiers", row.optJSONArray("preferredSourceTiers")?.let { array ->
                            buildList {
                                for (tierIndex in 0 until array.length()) add(SourceTier.valueOf(array.getString(tierIndex)).name)
                            }.joinToString(",")
                        }.orEmpty())
                        put("status", status.name)
                        put("attempts", row.optInt("attempts", 0).coerceAtLeast(0))
                        put("retry_after", row.optLong("retryAfter", 0L).coerceAtLeast(0L))
                        put("last_error", row.optString("lastError", ""))
                        put("updated_at", row.optLong("updatedAt", remote.generatedAt))
                    })
                }

                for (index in 0 until sources.length()) {
                    val row = sources.getJSONObject(index)
                    val rawTier = row.optString("tier", "")
                    if (rawTier.isNotBlank()) SourceTier.valueOf(rawTier)
                    insertOrThrow(db, "source_health", ContentValues().apply {
                        put("source_key", row.getString("sourceKey"))
                        put("tier", rawTier)
                        put("consecutive_failures", row.optInt("consecutiveFailures", 0).coerceAtLeast(0))
                        put("cooldown_until", row.optLong("cooldownUntil", 0L).coerceAtLeast(0L))
                        put("last_error", row.optString("lastError", ""))
                        put("updated_at", row.optLong("updatedAt", remote.generatedAt))
                    })
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            store.close()
        }
        return entities.length() to tasks.length()
    }

    private fun insertOrThrow(db: SQLiteDatabase, table: String, values: ContentValues) {
        require(db.insertOrThrow(table, null, values) != -1L) { "Không thể ghi $table" }
    }

    private fun downloadText(url: String, maxBytes: Int): String = downloadBytes(url, maxBytes).toString(Charsets.UTF_8)

    private fun validateUrl(url: String) {
        val parsed = URL(url)
        require(parsed.protocol.equals("https", ignoreCase = true)) { "Staging chỉ cho phép HTTPS" }
        require(parsed.host.lowercase() in allowedHosts) { "Staging host không được phép" }
    }

    private fun downloadBytes(url: String, maxBytes: Int): ByteArray {
        validateUrl(url)
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SurvivalLibraryVN-DataEngineSync/2.0")
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val declared = connection.contentLengthLong
            require(declared < 0L || declared <= maxBytes.toLong()) { "Staging payload quá lớn" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "Staging payload vượt giới hạn" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
