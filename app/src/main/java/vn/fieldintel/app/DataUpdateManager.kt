package vn.fieldintel.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class DataUpdateManifest(
    val version: Int,
    val schemaVersion: Int,
    val minAppVersionCode: Int,
    val packageName: String,
    val sha256: String,
    val sizeBytes: Long
)

data class DataUpdateResult(val applied: Boolean, val message: String)

class DataUpdateManager(private val context: Context) {
    private val root = File(context.filesDir, "updates").apply { mkdirs() }
    private val staging = File(root, "staging").apply { mkdirs() }
    private val active = File(root, "active").apply { mkdirs() }
    private val previous = File(root, "previous").apply { mkdirs() }

    /** Updates are deliberately restricted to an unmetered Wi‑Fi network. */
    fun isWifiAvailable(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun shouldCheckOnWifi(): Boolean = isWifiAvailable()

    fun parseManifest(json: String): DataUpdateManifest {
        val o = JSONObject(json)
        return DataUpdateManifest(
            version = o.getInt("version"),
            schemaVersion = o.getInt("schemaVersion"),
            minAppVersionCode = o.getInt("minAppVersionCode"),
            packageName = o.getString("packageName"),
            sha256 = o.getString("sha256").lowercase(),
            sizeBytes = o.getLong("sizeBytes")
        )
    }

    fun verify(file: File, manifest: DataUpdateManifest, appVersionCode: Int): DataUpdateResult {
        if (manifest.version <= 0) return DataUpdateResult(false, "Version không hợp lệ")
        if (manifest.schemaVersion != 1) return DataUpdateResult(false, "Schema dữ liệu không được hỗ trợ")
        if (manifest.sizeBytes <= 0 || manifest.sizeBytes > 64L * 1024L * 1024L) return DataUpdateResult(false, "Kích thước manifest không hợp lệ")
        if (!manifest.sha256.matches(Regex("^[0-9a-f]{64}$"))) return DataUpdateResult(false, "SHA-256 manifest không hợp lệ")
        if (manifest.packageName != context.packageName) return DataUpdateResult(false, "Sai package")
        if (manifest.minAppVersionCode > appVersionCode) return DataUpdateResult(false, "Cần cập nhật ứng dụng")
        if (file.length() != manifest.sizeBytes) return DataUpdateResult(false, "Sai kích thước")
        if (sha256(file) != manifest.sha256) return DataUpdateResult(false, "SHA-256 không khớp")
        return DataUpdateResult(true, "Gói hợp lệ")
    }

    fun fetchText(url: String, connectTimeoutMs: Int = 10000, readTimeoutMs: Int = 15000): String {
        require(url.startsWith("https://")) { "Chỉ cho phép HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    fun download(url: String, manifest: DataUpdateManifest, appVersionCode: Int): DataUpdateResult {
        if (!url.startsWith("https://")) return DataUpdateResult(false, "Chỉ cho phép HTTPS")
        val target = File(staging, "data-${manifest.version}.download")
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            total += n
                            if (total > manifest.sizeBytes || total > 64L * 1024L * 1024L) error("Dữ liệu tải vượt kích thước khai báo")
                            output.write(buffer, 0, n)
                        }
                    }
                }
            } finally { connection.disconnect() }
            val verified = verify(target, manifest, appVersionCode)
            if (!verified.applied) { target.delete(); verified }
            else { stage(target, manifest); target.delete(); DataUpdateResult(true, "Đã tải và xác minh") }
        }.getOrElse { target.delete(); DataUpdateResult(false, "Tải thất bại: ${it.message}") }
    }

    fun stage(source: File, manifest: DataUpdateManifest): File {
        val target = File(staging, "data-${manifest.version}.pack")
        source.copyTo(target, overwrite = true)
        return target
    }

    fun activate(staged: File): DataUpdateResult = runCatching {
        val current = File(active, "current.pack")
        val old = File(previous, "current.pack")
        val next = File(active, "next.pack")
        val oldReady = File(previous, "previous-ready.pack")
        try {
            staged.copyTo(next, overwrite = true)
            require(next.length() == staged.length()) { "Không sao chép đủ gói mới" }
            if (current.exists()) {
                current.copyTo(oldReady, overwrite = true)
                require(oldReady.length() == current.length()) { "Không sao chép đủ gói cũ" }
            }
            // A failed rename must leave current.pack intact.
            if (current.exists()) {
                atomicReplace(oldReady, old)
            }
            atomicReplace(next, current)
            DataUpdateResult(true, "Đã kích hoạt")
        } finally {
            next.delete()
            oldReady.delete()
        }
    }.getOrElse { DataUpdateResult(false, "Kích hoạt thất bại: ${it.message}") }

    fun stagedPackage(version: Int): File? =
        File(staging, "data-${version}.pack").takeIf { it.exists() }

    fun activateVersion(version: Int): DataUpdateResult {
        val staged = File(staging, "data-${version}.pack")
        if (!staged.exists()) return DataUpdateResult(false, "Không tìm thấy gói đã xác minh")
        return activate(staged)
    }

    private val pendingMapSwap = File(root, "map-swap-pending")

    fun markMapSwapPending() { pendingMapSwap.writeText("pending") }
    fun needsMapRecovery(): Boolean = pendingMapSwap.exists()
    fun finishMapSwap() { require(!pendingMapSwap.exists() || pendingMapSwap.delete()) { "Không thể xóa dấu khôi phục bản đồ" } }

    fun previousPackage(): File? = File(previous, "current.pack").takeIf { it.exists() }

    fun activePackage(): File? = File(active, "current.pack").takeIf { it.exists() }

    fun rollback(): DataUpdateResult = runCatching {
        val old = File(previous, "current.pack")
        if (!old.exists()) return DataUpdateResult(false, "Không có bản để khôi phục")
        val ready = File(active, "rollback-ready.pack")
        try {
            old.copyTo(ready, overwrite = true)
            require(ready.length() == old.length()) { "Không sao chép đủ gói khôi phục" }
            atomicReplace(ready, File(active, "current.pack"))
            DataUpdateResult(true, "Đã khôi phục")
        } finally { ready.delete() }
    }.getOrElse { DataUpdateResult(false, "Khôi phục thất bại: ${it.message}") }

    private fun atomicReplace(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
