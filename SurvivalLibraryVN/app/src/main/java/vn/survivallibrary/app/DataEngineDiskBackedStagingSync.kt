package vn.survivallibrary.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * RAM-bounded V2-G staging synchronizer.
 *
 * Every changed shard is streamed to app cache, checked for exact byte length + SHA-256,
 * and structurally validated before any database mutation starts. Only after all required
 * files are proven valid do we enter one SQLite transaction and apply them one by one.
 */
object DataEngineDiskBackedStagingSync {
    private const val PREFS = "data_engine_staging_sync"
    private const val KEY_VERSION = "version"
    private const val KEY_CATEGORIES = "progress_categories"
    private const val CATEGORY_SHA_PREFIX = "progress_sha_"
    private const val SOURCE_SHA_KEY = "progress_source_health_sha"

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

        val tempDir = File(appContext.cacheDir, "data-engine-progress-v${remote.version}-${System.nanoTime()}")
        require(tempDir.mkdirs() || tempDir.isDirectory) { "Không tạo được thư mục tạm progress" }
        try {
            val downloaded = linkedMapOf<DataEngineRemoteProgressShard, File>()
            for (meta in changed) {
                val target = File(tempDir, safeFileName(meta.categoryId) + ".json")
                DataEngineShardFileHttp.downloadVerified(meta.url, meta.bytes, meta.sha256, target)
                validateCategoryPayload(target, meta, remote)
                downloaded[meta] = target
            }
            val sourceFile = if (sourceChanged) {
                File(tempDir, "source-health.json").also { target ->
                    DataEngineShardFileHttp.downloadVerified(
                        manifest.sourceHealth.url,
                        manifest.sourceHealth.bytes,
                        manifest.sourceHealth.sha256,
                        target
                    )
                    validateSourcePayload(target, manifest.sourceHealth, remote)
                }
            } else null

            if (downloaded.isNotEmpty() || removedCategories.isNotEmpty() || sourceFile != null) {
                val store = DataEngineStore(appContext)
                try {
                    val db = store.writableDatabase
                    db.beginTransaction()
                    try {
                        for (categoryId in removedCategories) deleteCategory(db, categoryId)
                        for ((meta, file) in downloaded) {
                            deleteCategory(db, meta.categoryId)
                            importCategory(db, JSONObject(file.readText(Charsets.UTF_8)), meta, remote)
                        }
                        if (sourceFile != null) {
                            db.delete("source_health", null, null)
                            importSourceHealth(
                                db,
                                JSONObject(sourceFile.readText(Charsets.UTF_8)),
                                manifest.sourceHealth,
                                remote
                            )
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

            val updated = downloaded.isNotEmpty() || removedCategories.isNotEmpty() || sourceFile != null
            return DataEngineStagingSyncResult(
                checked = true,
                updated = updated,
                version = remote.version,
                entityCount = remote.entityCount,
                taskCount = remote.taskCount,
                message = if (!updated) {
                    "Tiến độ AI/Data Engine đã là phiên bản mới nhất."
                } else {
                    "Đã đồng bộ tiến độ AI theo ${downloaded.size} shard thay đổi · ${remote.entityCount} hồ sơ · ${remote.taskCount} task."
                }
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun safeFileName(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-', '.', '_').ifBlank { "category" }

    private fun validateCategoryPayload(file: File, meta: DataEngineRemoteProgressShard, remote: DataEngineRemoteSnapshot) {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.optInt("schemaVersion", -1) == 1) { "Sai schema progress shard" }
        require(root.getInt("version") == remote.version) { "Sai version progress shard" }
        require(root.getString("categoryId") == meta.categoryId) { "Sai category progress shard" }
        require(root.getJSONArray("entities").length() == meta.entityCount) { "entityCount shard không khớp" }
        require(root.getJSONArray("aliases").length() == meta.aliasCount) { "aliasCount shard không khớp" }
        require(root.getJSONArray("fields").length() == meta.fieldCount) { "fieldCount shard không khớp" }
        require(root.getJSONArray("tasks").length() == meta.taskCount) { "taskCount shard không khớp" }
    }

    private fun validateSourcePayload(file: File, meta: DataEngineRemoteSourceHealthShard, remote: DataEngineRemoteSnapshot) {
        val root = JSONObject(file.readText(Charsets.UTF_8))
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
        forEachObject(root.getJSONArray("entities")) { row ->
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
        forEachObject(root.getJSONArray("aliases")) { row ->
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
        forEachObject(root.getJSONArray("fields")) { row ->
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
        forEachObject(root.getJSONArray("tasks")) { row ->
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

private object DataEngineShardFileHttp {
    private val allowedHosts = setOf("raw.githubusercontent.com", "github.com")

    fun downloadVerified(url: String, expectedBytes: Int, expectedSha256: String, target: File) {
        require(expectedBytes > 0) { "Progress payload size không hợp lệ" }
        val parsed = URL(url)
        require(parsed.protocol.equals("https", ignoreCase = true)) { "Progress sync chỉ cho phép HTTPS" }
        require(parsed.host.lowercase() in allowedHosts) { "Progress sync host không được phép" }
        val connection = parsed.openConnection() as HttpsURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SurvivalLibraryVN-DataEngineShardSync/2.1")
        try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val declared = connection.contentLengthLong
            require(declared < 0L || declared == expectedBytes.toLong()) { "Kích thước progress payload không khớp" }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= expectedBytes) { "Progress payload vượt kích thước khai báo" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            require(total == expectedBytes) { "Progress payload thiếu byte" }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha == expectedSha256.lowercase()) { "SHA-256 progress payload không khớp" }
        } finally {
            connection.disconnect()
        }
    }
}
