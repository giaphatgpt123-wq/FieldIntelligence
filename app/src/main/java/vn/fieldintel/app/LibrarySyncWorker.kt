package vn.fieldintel.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
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
    override suspend fun doWork(): Result {
        if (!DataUpdateManager(applicationContext).isWifiAvailable()) return Result.retry()
        return runCatching {
            val preferences = applicationContext.getSharedPreferences("scientific-library-sync", Context.MODE_PRIVATE)
            val manifest = JSONObject(fetch(UpdateConfig.LIBRARY_MANIFEST_URL, 16 * 1024).toString(Charsets.UTF_8))
            val version = manifest.getLong("version")
            val size = manifest.getLong("sizeBytes")
            val digest = manifest.getString("sha256").lowercase()
            require(version > 0 && size in 1..MAX_PACKAGE_BYTES && digest.matches(Regex("[a-f0-9]{64}")))
            if (preferences.getLong("installedVersion", 0) >= version) return@runCatching Result.success()
            val target = File(applicationContext.cacheDir, "scientific-library-${version}.zip")
            try {
                val data = fetch(UpdateConfig.LIBRARY_PACKAGE_URL, size)
                require(data.size.toLong() == size) { "Sai kích thước thư viện" }
                require(MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) } == digest) { "Sai SHA-256 thư viện" }
                target.writeBytes(data)
                ScientificLibraryImportManager(applicationContext).importBundle(target)
                preferences.edit().putLong("installedVersion", version).apply()
            } finally { target.delete() }
            Result.success()
        }.getOrElse { Result.retry() }
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

    companion object {
        private const val MAX_PACKAGE_BYTES = 512L * 1024L * 1024L
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("scientific-library-wifi-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
