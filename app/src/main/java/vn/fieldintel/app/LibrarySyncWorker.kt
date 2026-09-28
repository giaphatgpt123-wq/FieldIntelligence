package vn.fieldintel.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import vn.fieldintel.feature.emergency.ScientificLibraryImportManager

class LibrarySyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = synchronized(SYNC_LOCK) {
        val preferences = applicationContext.getSharedPreferences("scientific-library-sync", Context.MODE_PRIVATE)
        if (!DataUpdateManager(applicationContext).isWifiAvailable()) {
            preferences.edit().putString("state", "wifi-wait").apply()
            return@synchronized Result.retry()
        }
        preferences.edit().putLong("lastAttemptAt", System.currentTimeMillis()).putString("state", "checking").remove("lastError").apply()
        runCatching {
            val manifest = JSONObject(fetch(UpdateConfig.LIBRARY_MANIFEST_URL, 16 * 1024).toString(Charsets.UTF_8))
            val version = manifest.getLong("version")
            val size = manifest.getLong("sizeBytes")
            val digest = manifest.getString("sha256").lowercase()
            require(version > 0 && size in 1..MAX_PACKAGE_BYTES && digest.matches(Regex("[a-f0-9]{64}")))
            if (preferences.getLong("installedVersion", 0) >= version) {
                preferences.edit().putString("state", "ready").putLong("lastSuccessAt", System.currentTimeMillis()).apply()
                return@runCatching Result.success()
            }
            val target = File(applicationContext.cacheDir, "scientific-library-${version}.zip")
            try {
                preferences.edit().putString("state", "downloading").apply()
                val actual = downloadToFile(UpdateConfig.LIBRARY_PACKAGE_URL, target, size)
                require(actual == digest) { "Sai SHA-256 thư viện" }
                preferences.edit().putString("state", "installing").apply()
                ScientificLibraryImportManager(applicationContext).importBundle(target)
                preferences.edit().putLong("installedVersion", version).putLong("lastSuccessAt", System.currentTimeMillis()).putString("state", "ready").apply()
            } finally { target.delete() }
            Result.success()
        }.getOrElse { failure ->
            preferences.edit().putString("state", "error").putString("lastError", failure.message?.take(120) ?: "Không thể tải thư viện").apply()
            Result.retry()
        }
    }

    private fun fetch(address: String, maxBytes: Long): ByteArray {
        require(address.startsWith("https://"))
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = true
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(output.size().toLong() + n <= maxBytes) { "Gói vượt kích thước khai báo" }
                    output.write(buffer, 0, n)
                }
            }
            return output.toByteArray()
        } finally { connection.disconnect() }
    }


    private fun downloadToFile(address: String, target: File, expectedBytes: Long): String {
        require(address.startsWith("https://"))
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = true
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val hash = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= expectedBytes) { "Gói vượt kích thước khai báo" }
                    hash.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
            } }
            require(total == expectedBytes) { "Sai kích thước thư viện" }
            return hash.digest().joinToString("") { "%02x".format(it) }
        } finally { connection.disconnect() }
    }

    companion object {
        private val SYNC_LOCK = Any()
        private const val MAX_PACKAGE_BYTES = 512L * 1024L * 1024L
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
            val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork("scientific-library-wifi-sync", ExistingPeriodicWorkPolicy.KEEP, request)
            manager.enqueueUniqueWork("scientific-library-wifi-sync-on-open", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LibrarySyncWorker>().setConstraints(constraints).build())
        }
    }
}
