package vn.survivallibrary.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class DataEngineRemoteProgressShard(
    val categoryId: String,
    val url: String,
    val sha256: String,
    val bytes: Int,
    val entityCount: Int,
    val aliasCount: Int,
    val fieldCount: Int,
    val taskCount: Int
)

data class DataEngineRemoteSourceHealthShard(
    val url: String,
    val sha256: String,
    val bytes: Int,
    val rowCount: Int
)

data class DataEngineRemoteProgressManifest(
    val schemaVersion: Int,
    val version: Int,
    val shardCount: Int,
    val maxShardBytes: Int,
    val shards: List<DataEngineRemoteProgressShard>,
    val sourceHealth: DataEngineRemoteSourceHealthShard
)

data class DataEngineRemoteIndex(
    val snapshot: DataEngineRemoteSnapshot,
    val progressShards: DataEngineRemoteProgressManifest?
)

object DataEngineRemoteIndexParser {
    private const val MAX_PROGRESS_SHARDS = 2_000
    private const val MAX_PROGRESS_SHARD_BYTES = 2 * 1024 * 1024
    private const val MAX_SOURCE_HEALTH_BYTES = 512 * 1024

    fun parse(json: String): DataEngineRemoteIndex {
        val legacy = DataEngineStagingIndexParser.parse(json)
        val root = JSONObject(json)
        val progress = root.optJSONObject("progressShards") ?: return DataEngineRemoteIndex(legacy, null)
        require(progress.optInt("schemaVersion", -1) == 1) { "Progress shard schema không được hỗ trợ" }
        require(progress.getInt("version") == legacy.version) { "Progress shard version không khớp snapshot" }
        val declaredShardCount = progress.getInt("shardCount")
        require(declaredShardCount in 1..MAX_PROGRESS_SHARDS) { "Số progress shard không hợp lệ" }
        val maxShardBytes = progress.getInt("maxShardBytes")
        require(maxShardBytes in 1..MAX_PROGRESS_SHARD_BYTES) { "Giới hạn progress shard không hợp lệ" }
        val array = progress.getJSONArray("shards")
        require(array.length() == declaredShardCount) { "Progress shardCount không khớp manifest" }

        val categories = linkedSetOf<String>()
        val shards = buildList {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                val categoryId = row.getString("categoryId").trim()
                require(categoryId.isNotBlank() && categories.add(categoryId)) { "Progress category trống hoặc trùng" }
                val bytes = row.getInt("bytes")
                val entityCount = row.getInt("entityCount")
                val aliasCount = row.getInt("aliasCount")
                val fieldCount = row.getInt("fieldCount")
                val taskCount = row.getInt("taskCount")
                require(bytes in 1..maxShardBytes) { "Progress shard vượt giới hạn" }
                require(entityCount in 0..DataEngineStagingIndexParser.MAX_ENTITIES) { "Progress entityCount không hợp lệ" }
                require(aliasCount in 0..DataEngineStagingIndexParser.MAX_ALIASES) { "Progress aliasCount không hợp lệ" }
                require(fieldCount in 0..DataEngineStagingIndexParser.MAX_FIELDS) { "Progress fieldCount không hợp lệ" }
                require(taskCount in 0..DataEngineStagingIndexParser.MAX_TASKS) { "Progress taskCount không hợp lệ" }
                val sha = row.getString("sha256").lowercase()
                require(sha.matches(Regex("^[a-f0-9]{64}$"))) { "SHA-256 progress shard không hợp lệ" }
                add(
                    DataEngineRemoteProgressShard(
                        categoryId = categoryId,
                        url = row.getString("url"),
                        sha256 = sha,
                        bytes = bytes,
                        entityCount = entityCount,
                        aliasCount = aliasCount,
                        fieldCount = fieldCount,
                        taskCount = taskCount
                    )
                )
            }
        }
        require(shards.sumOf { it.entityCount } == legacy.entityCount) { "Tổng entity progress shard không khớp index" }
        require(shards.sumOf { it.taskCount } == legacy.taskCount) { "Tổng task progress shard không khớp index" }
        require(shards.sumOf { it.aliasCount } <= DataEngineStagingIndexParser.MAX_ALIASES) { "Tổng alias progress vượt giới hạn" }
        require(shards.sumOf { it.fieldCount } <= DataEngineStagingIndexParser.MAX_FIELDS) { "Tổng field progress vượt giới hạn" }

        val source = progress.getJSONObject("sourceHealth")
        val sourceBytes = source.getInt("bytes")
        val sourceCount = source.getInt("rowCount")
        val sourceSha = source.getString("sha256").lowercase()
        require(sourceBytes in 1..MAX_SOURCE_HEALTH_BYTES) { "Source-health shard vượt giới hạn" }
        require(sourceCount in 0..DataEngineStagingIndexParser.MAX_SOURCES) { "Source-health count vượt giới hạn" }
        require(sourceSha.matches(Regex("^[a-f0-9]{64}$"))) { "SHA-256 source-health không hợp lệ" }

        return DataEngineRemoteIndex(
            snapshot = legacy,
            progressShards = DataEngineRemoteProgressManifest(
                schemaVersion = 1,
                version = legacy.version,
                shardCount = declaredShardCount,
                maxShardBytes = maxShardBytes,
                shards = shards,
                sourceHealth = DataEngineRemoteSourceHealthShard(
                    url = source.getString("url"),
                    sha256 = sourceSha,
                    bytes = sourceBytes,
                    rowCount = sourceCount
                )
            )
        )
    }
}

object DataEngineShardedStagingSync {
    private const val PREFS = "data_engine_staging_sync"
    private const val KEY_VERSION = "version"
    private const val KEY_CATEGORIES = "progress_categories"
    private const val CATEGORY_SHA_PREFIX = "progress_sha_"
    private const val SOURCE_SHA_KEY = "progress_source_health_sha"
    private val allowedHosts = setOf("raw.githubusercontent.com", "github.com")

    fun sync(context: Context, remoteIndex: DataEngineRemoteIndex): DataEngineStagingSyncResult {
        val manifest = requireNotNull(remoteIndex.progressShards) { "Progress shard manifest không tồn tại" }
        val remote = remoteIndex.snapshot
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val localCategories = prefs.getStringSet(KEY_CATEGORIES, emptySet()).orEmpty().toSet()
        val remoteCategories = manifest.shards.map { it.categoryId }.toSet()
        val removedCategories = localCategories - remoteCategories
        val changed = manifest.shards.filter { prefs.getString(CATEGORY_SHA_PREFIX + it.categoryId, "") != it.sha256 }
        val sourceChanged = prefs.getString(SOURCE_SHA_KEY, "") != manifest.sourceHealth.sha256

        val downloaded = changed.associateWith { meta ->
            val raw = DataEngineShardHttp.download(meta.url, meta.bytes)
            require(raw.size == meta.bytes) { "Kích thước progress shard ${meta.categoryId} không khớp" }
            require(DataEngineShardHttp.sha256(raw) == meta.sha256) { "SHA-256 progress shard ${meta.categoryId} không khớp" }
            validateCategoryPayload(raw, meta, remote)
            raw
        }
        val sourceRaw = if (sourceChanged) {
            DataEngineShardHttp.download(manifest.sourceHealth.url, manifest.sourceHealth.bytes).also { raw ->
                require(raw.size == manifest.sourceHealth.bytes) { "Kích thước source-health shard không khớp" }
                require(DataEngineShardHttp.sha256(raw) == manifest.sourceHealth.sha256) { "SHA-256 source-health không khớp" }
                validateSourcePayload(raw, manifest.sourceHealth, remote)
            }
        } else null

        if (changed.isNotEmpty() || removedCategories.isNotEmpty() || sourceRaw != null) {
            val store = DataEngineStore(appContext)
            try {
                val db = store.writableDatabase
                db.beginTransaction()
                try {
                    for (categoryId in removedCategories) deleteCategory(db, categoryId)
                    for ((meta, raw) in downloaded) {
                        deleteCategory(db, meta.categoryId)
                        importCategory(db, JSONObject(raw.toString(Charsets.UTF_8)), meta, remote)
                    }
                    if (sourceRaw != null) {
                        db.delete("source_health", null, null)
                        importSourceHealth(db, JSONObject(sourceRaw.toString(Charsets.UTF_8)), manifest.sourceHealth, remote)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } finally {
                store.close()
            }
        }

        val editor = prefs.edit()
            .putInt(KEY_VERSION, remote.version)
            .putStringSet(KEY_CATEGORIES, remoteCategories)
        for (categoryId in removedCategories) editor.remove(CATEGORY_SHA_PREFIX + categoryId)
        for (meta in manifest.shards) editor.putString(CATEGORY_SHA_PREFIX + meta.categoryId, meta.sha256)
        editor.putString(SOURCE_SHA_KEY, manifest.sourceHealth.sha256).apply()

        return DataEngineStagingSyncResult(
            checked = true,
            updated = changed.isNotEmpty() || removedCategories.isNotEmpty() || sourceRaw != null,
            version = remote.version,
            entityCount = remote.entityCount,
            taskCount = remote.taskCount,
            message = if (changed.isEmpty() && removedCategories.isEmpty() && sourceRaw == null) {
                "Tiến độ AI/Data Engine đã là phiên bản mới nhất."
            } else {
                "Đã đồng bộ tiến độ AI theo ${changed.size} shard thay đổi · ${remote.entityCount} hồ sơ · ${remote.taskCount} task."
            }
        )
    }

    private fun validateCategoryPayload(raw: ByteArray, meta: DataEngineRemoteProgressShard, remote: DataEngineRemoteSnapshot) {
        val root = JSONObject(raw.toString(Charsets.UTF_8))
        require(root.optInt("schemaVersion", -1) == 1) { "Sai schema progress shard" }
        require(root.getInt("version") == remote.version) { "Sai version progress shard" }
        require(root.getString("categoryId") == meta.categoryId) { "Sai category progress shard" }
        require(root.getJSONArray("entities").length() == meta.entityCount) { "entityCount shard không khớp" }
        require(root.getJSONArray("aliases").length() == meta.aliasCount) { "aliasCount shard không khớp" }
        require(root.getJSONArray("fields").length() == meta.fieldCount) { "fieldCount shard không khớp" }
        require(root.getJSONArray("tasks").length() == meta.taskCount) { "taskCount shard không khớp" }
    }

    private fun validateSourcePayload(raw: ByteArray, meta: DataEngineRemoteSourceHealthShard, remote: DataEngineRemoteSnapshot) {
        val root = JSONObject(raw.toString(Charsets.UTF_8))
        require(root.optInt("schemaVersion", -1) == 1) { "Sai schema source-health shard" }
        require(root.getInt("version") == remote.version) { "Sai version source-health shard" }
        require(root.getJSONArray("sourceHealth").length() == meta.rowCount) { "source-health rowCount không khớp" }
    }

    private fun deleteCategory(db: SQLiteDatabase, categoryId: String) {
        val where = "canonical_id IN (SELECT canonical_id FROM staging_entities WHERE category_id = ?)"
        val args = arrayOf(categoryId)
        db.delete("staging_aliases", where, args)
        db.delete("staging_fields", where, args)
        db.delete("library_load_tasks", where, args)
        db.delete("staging_entities", "category_id = ?", args)
    }

    private fun importCategory(
        db: SQLiteDatabase,
        root: JSONObject,
        meta: DataEngineRemoteProgressShard,
        remote: DataEngineRemoteSnapshot
    ) {
        val entities = root.getJSONArray("entities")
        val aliases = root.getJSONArray("aliases")
        val fields = root.getJSONArray("fields")
        val tasks = root.getJSONArray("tasks")
        forEachObject(entities) { row ->
            val canonicalId = row.getString("canonicalId").trim()
            val categoryId = row.getString("categoryId").trim()
            val scientificName = row.getString("scientificName").trim()
            require(canonicalId.isNotBlank() && categoryId == meta.categoryId && scientificName.isNotBlank()) {
                "Progress entity không hợp lệ"
            }
            insert(db, "staging_entities", ContentValues().apply {
                put("canonical_id", canonicalId)
                put("category_id", categoryId)
                put("scientific_name", scientificName)
                put("vietnamese_name", row.optString("vietnameseName", ""))
                put("high_risk", if (row.optBoolean("highRisk", false)) 1 else 0)
                put("published", if (row.optBoolean("published", false)) 1 else 0)
                put("updated_at", row.optLong("updatedAt", remote.generatedAt))
            })
        }
        forEachObject(aliases) { row ->
            val type = AliasType.valueOf(row.getString("aliasType"))
            val display = row.getString("displayName").trim()
            val normalized = VietnameseNameKey.normalize(display)
            require(normalized.isNotBlank()) { "Alias progress không hợp lệ" }
            insert(db, "staging_aliases", ContentValues().apply {
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
        forEachObject(fields) { row ->
            val field = DataFieldKey.valueOf(row.getString("field"))
            insert(db, "staging_fields", ContentValues().apply {
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
        forEachObject(tasks) { row ->
            val taskType = LibraryTaskType.valueOf(row.getString("taskType"))
            val status = LibraryTaskStatus.valueOf(row.getString("status"))
            val rawField = row.optString("field", "")
            if (rawField.isNotBlank()) DataFieldKey.valueOf(rawField)
            insert(db, "library_load_tasks", ContentValues().apply {
                put("task_id", row.getString("taskId"))
                put("canonical_id", row.getString("canonicalId"))
                put("task_type", taskType.name)
                put("field_key", rawField)
                put("priority", row.optInt("priority", 0))
                put("preferred_source_tiers", row.optJSONArray("preferredSourceTiers")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) add(SourceTier.valueOf(array.getString(index)).name)
                    }.joinToString(",")
                }.orEmpty())
                put("status", status.name)
                put("attempts", row.optInt("attempts", 0).coerceAtLeast(0))
                put("retry_after", row.optLong("retryAfter", 0L).coerceAtLeast(0L))
                put("last_error", row.optString("lastError", ""))
                put("updated_at", row.optLong("updatedAt", remote.generatedAt))
            })
        }
    }

    private fun importSourceHealth(
        db: SQLiteDatabase,
        root: JSONObject,
        meta: DataEngineRemoteSourceHealthShard,
        remote: DataEngineRemoteSnapshot
    ) {
        val sources = root.getJSONArray("sourceHealth")
        require(sources.length() == meta.rowCount)
        forEachObject(sources) { row ->
            val rawTier = row.optString("tier", "")
            if (rawTier.isNotBlank()) SourceTier.valueOf(rawTier)
            insert(db, "source_health", ContentValues().apply {
                put("source_key", row.getString("sourceKey"))
                put("tier", rawTier)
                put("consecutive_failures", row.optInt("consecutiveFailures", 0).coerceAtLeast(0))
                put("cooldown_until", row.optLong("cooldownUntil", 0L).coerceAtLeast(0L))
                put("last_error", row.optString("lastError", ""))
                put("updated_at", row.optLong("updatedAt", remote.generatedAt))
            })
        }
    }

    private inline fun forEachObject(array: JSONArray, block: (JSONObject) -> Unit) {
        for (index in 0 until array.length()) block(array.getJSONObject(index))
    }

    private fun insert(db: SQLiteDatabase, table: String, values: ContentValues) {
        require(db.insertOrThrow(table, null, values) != -1L) { "Không thể ghi $table" }
    }
}

private object DataEngineShardHttp {
    private val allowedHosts = setOf("raw.githubusercontent.com", "github.com")

    fun download(url: String, exactMaxBytes: Int): ByteArray {
        require(exactMaxBytes > 0) { "Progress payload size không hợp lệ" }
        val parsed = URL(url)
        require(parsed.protocol.equals("https", ignoreCase = true)) { "Progress sync chỉ cho phép HTTPS" }
        require(parsed.host.lowercase() in allowedHosts) { "Progress sync host không được phép" }
        val connection = parsed.openConnection() as HttpsURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SurvivalLibraryVN-DataEngineShardSync/2.1")
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val declared = connection.contentLengthLong
            require(declared < 0L || declared <= exactMaxBytes.toLong()) { "Progress payload quá lớn" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(minOf(exactMaxBytes, 64 * 1024))
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= exactMaxBytes) { "Progress payload vượt giới hạn" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
