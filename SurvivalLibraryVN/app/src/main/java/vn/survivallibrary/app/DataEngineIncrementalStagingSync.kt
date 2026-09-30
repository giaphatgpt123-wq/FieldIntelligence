package vn.survivallibrary.app

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Compatibility facade for the V2-G progress transport.
 *
 * New clients prefer SHA-addressed category shards. Changed shards are staged on disk and
 * fully verified before one database transaction begins. If the remote index is still legacy,
 * or the sharded path fails validation/network/apply checks, the proven full-snapshot
 * synchronizer remains available as a rollback path.
 */
object DataEngineIncrementalStagingSync {
    private const val PREFS = "data_engine_staging_sync"
    private const val KEY_VERSION = "version"
    private const val MAX_INDEX_BYTES = 256 * 1024
    private val allowedHosts = setOf("raw.githubusercontent.com", "github.com")

    fun checkAndSync(context: Context): DataEngineStagingSyncResult {
        val appContext = context.applicationContext
        return try {
            val indexJson = downloadIndex(DataEngineStagingSync.INDEX_URL)
            val remoteIndex = DataEngineRemoteIndexParser.parse(indexJson)
            val remote = remoteIndex.snapshot
            val localVersion = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_VERSION, 0)
            if (remote.version <= localVersion) {
                val local = DataEngineProgressSqlRepository.snapshot(appContext)
                DataEngineStagingSyncResult(
                    checked = true,
                    updated = false,
                    version = localVersion,
                    entityCount = local.stagedEntities,
                    taskCount = local.pendingTasks + local.runningTasks + local.retryTasks + local.completedTasks + local.blockedTasks,
                    message = "Tiến độ AI/Data Engine đã là phiên bản mới nhất."
                )
            } else if (remoteIndex.progressShards != null) {
                runCatching { DataEngineDiskBackedStagingSync.sync(appContext, remoteIndex) }
                    .getOrElse { DataEngineStagingSync.checkAndSync(appContext) }
            } else {
                DataEngineStagingSync.checkAndSync(appContext)
            }
        } catch (_: Exception) {
            DataEngineStagingSync.checkAndSync(appContext)
        }
    }

    private fun downloadIndex(url: String): String {
        val parsed = URL(url)
        require(parsed.protocol.equals("https", ignoreCase = true)) { "Staging index chỉ cho phép HTTPS" }
        require(parsed.host.lowercase() in allowedHosts) { "Staging index host không được phép" }
        val connection = parsed.openConnection() as HttpsURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SurvivalLibraryVN-DataEngineIndex/2.1")
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val declared = connection.contentLengthLong
            require(declared < 0L || declared <= MAX_INDEX_BYTES) { "Staging index quá lớn" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(32 * 1024)
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_INDEX_BYTES) { "Staging index vượt giới hạn" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }
}
