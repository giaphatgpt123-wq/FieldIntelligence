package vn.duongodau.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val STABLE_APPLICATION_ID = "vn.duongodau.app"
private const val PREFS = "duong_o_dau_update"
private const val CACHE_KEY = "last_manifest"
private const val APK_MIME = "application/vnd.android.package-archive"
private const val NETWORK_RETRIES = 3

private val MANIFEST_URLS = listOf(
    "https://github.com/giaphatgpt123-wq/FieldIntelligence/releases/latest/download/duongodau-update.json",
    "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/main/docs/duongodau/update.json"
)

data class UpdateInfo(
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val signingCertSha256: String,
    val mandatory: Boolean,
    val notes: String
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo, val installedVersionCode: Long) : UpdateCheckResult
    data class UpToDate(val installedVersionCode: Long, val installedVersionName: String) : UpdateCheckResult
    data class DevChannel(val stableInfo: UpdateInfo?) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface InstallResult {
    data object InstallerOpened : InstallResult
    data object UnknownSourcesPermissionRequired : InstallResult
    data class LegacySignatureConflict(val installedCertificateSha256: String?) : InstallResult
    data class Error(val message: String) : InstallResult
}

object AppUpdateManager {
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val installed = installedVersion(context)
        val info = loadManifest(context)
            ?: return@withContext UpdateCheckResult.Error(
                "Không lấy được kênh cập nhật. Ứng dụng vẫn dùng bình thường; có thể kiểm tra lại khi có mạng."
            )

        if (info.applicationId != STABLE_APPLICATION_ID) {
            return@withContext UpdateCheckResult.Error("Manifest cập nhật không đúng ứng dụng.")
        }

        if (context.packageName != STABLE_APPLICATION_ID) {
            return@withContext UpdateCheckResult.DevChannel(info)
        }

        if (info.versionCode > installed.first) {
            UpdateCheckResult.Available(info, installed.first)
        } else {
            UpdateCheckResult.UpToDate(installed.first, installed.second)
        }
    }

    suspend fun downloadVerifyAndInstall(context: Context, info: UpdateInfo): InstallResult = withContext(Dispatchers.IO) {
        if (info.applicationId != STABLE_APPLICATION_ID) {
            return@withContext InstallResult.Error("Từ chối cập nhật: applicationId không khớp.")
        }

        val legacyConflict = detectInstalledStableSignatureConflict(context, info.signingCertSha256)
        if (legacyConflict.first) {
            return@withContext InstallResult.LegacySignatureConflict(legacyConflict.second)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return@withContext InstallResult.UnknownSourcesPermissionRequired
        }

        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return@withContext InstallResult.Error("Không mở được thư mục tải cập nhật.")
        targetDir.mkdirs()

        val apk = File(targetDir, "duong-o-dau-${info.versionCode}.apk")
        val partial = File(targetDir, "duong-o-dau-${info.versionCode}.apk.part")
        apk.delete()
        partial.delete()

        try {
            downloadToFileWithRetry(info.apkUrl, partial)
            if (!partial.exists() || partial.length() <= 0L) throw IOException("Tệp tải về rỗng")
            if (!partial.renameTo(apk)) {
                partial.copyTo(apk, overwrite = true)
                partial.delete()
            }
        } catch (e: Exception) {
            partial.delete()
            apk.delete()
            return@withContext InstallResult.Error("Tải APK thất bại sau $NETWORK_RETRIES lần thử: ${e.message ?: "lỗi mạng"}")
        }

        val actualSha = sha256(apk)
        if (!actualSha.equals(info.sha256, ignoreCase = true)) {
            apk.delete()
            return@withContext InstallResult.Error("APK tải về không đúng SHA-256. Đã hủy cài đặt.")
        }

        val archive = archiveIdentity(context.packageManager, apk)
        if (archive.first != STABLE_APPLICATION_ID) {
            apk.delete()
            return@withContext InstallResult.Error("APK tải về không đúng package. Đã hủy cài đặt.")
        }
        if (!archive.second.equals(info.signingCertSha256, ignoreCase = true)) {
            apk.delete()
            return@withContext InstallResult.Error("Chữ ký APK không đúng kênh phát hành. Đã hủy cài đặt.")
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update.provider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        return@withContext try {
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            InstallResult.InstallerOpened
        } catch (e: Exception) {
            InstallResult.Error("Không mở được trình cài Android: ${e.message ?: "không xác định"}")
        }
    }

    fun openLegacyUninstall(context: Context) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$STABLE_APPLICATION_ID"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private suspend fun loadManifest(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (url in MANIFEST_URLS) {
            try {
                val raw = fetchTextWithRetry(url)
                val parsed = parseManifest(raw)
                prefs.edit().putString(CACHE_KEY, raw).apply()
                return parsed
            } catch (_: Exception) {
                // Try next endpoint. Cached manifest is used only after all network endpoints fail.
            }
        }
        return prefs.getString(CACHE_KEY, null)?.let {
            runCatching { parseManifest(it) }.getOrNull()
        }
    }

    private fun parseManifest(raw: String): UpdateInfo {
        val json = JSONObject(raw)
        return UpdateInfo(
            applicationId = json.getString("applicationId"),
            versionCode = json.getLong("versionCode"),
            versionName = json.getString("versionName"),
            apkUrl = json.getString("apkUrl"),
            sha256 = json.getString("sha256").lowercase(),
            signingCertSha256 = json.getString("signingCertSha256").lowercase(),
            mandatory = json.optBoolean("mandatory", false),
            notes = json.optString("notes", "")
        ).also {
            require(it.applicationId == STABLE_APPLICATION_ID)
            require(it.versionCode > 0L)
            require(it.versionName.isNotBlank())
            require(it.apkUrl.startsWith("https://"))
            require(it.sha256.matches(Regex("[0-9a-f]{64}")))
            require(it.signingCertSha256.matches(Regex("[0-9a-f]{64}")))
        }
    }

    private suspend fun fetchTextWithRetry(url: String): String = retryNetwork {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DuongODau-Android-Updater")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        connection.useConnection { conn ->
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private suspend fun downloadToFileWithRetry(url: String, target: File) {
        retryNetwork {
            target.delete()
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 35_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "DuongODau-Android-Updater")
                setRequestProperty("Accept", APK_MIME)
                setRequestProperty("Cache-Control", "no-cache")
            }
            connection.useConnection { conn ->
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                val expectedLength = conn.contentLengthLong
                conn.inputStream.use { input ->
                    target.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (target.length() <= 0L) throw IOException("Tệp tải về rỗng")
                if (expectedLength > 0L && target.length() != expectedLength) {
                    throw IOException("Tải chưa đủ dữ liệu (${target.length()}/$expectedLength byte)")
                }
            }
        }
    }

    private suspend fun <T> retryNetwork(block: () -> T): T {
        var lastError: Throwable? = null
        repeat(NETWORK_RETRIES) { attempt ->
            try {
                return block()
            } catch (t: Throwable) {
                lastError = t
                if (attempt < NETWORK_RETRIES - 1) delay(750L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Lỗi mạng không xác định")
    }

    private fun installedVersion(context: Context): Pair<Long, String> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return code to (info.versionName ?: "")
    }

    private fun detectInstalledStableSignatureConflict(context: Context, expectedCert: String): Pair<Boolean, String?> {
        val pm = context.packageManager
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(STABLE_APPLICATION_ID, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(STABLE_APPLICATION_ID, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull() ?: return false to null

        val cert = packageInfoCertificateSha256(installed)
        return (!cert.equals(expectedCert, ignoreCase = true)) to cert
    }

    private fun archiveIdentity(pm: PackageManager, apk: File): Pair<String?, String?> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return null to null
        return info.packageName to packageInfoCertificateSha256(info)
    }

    private fun packageInfoCertificateSha256(info: android.content.pm.PackageInfo): String? {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null
        return digest(bytes)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
    try {
        return block(this)
    } finally {
        disconnect()
    }
}
