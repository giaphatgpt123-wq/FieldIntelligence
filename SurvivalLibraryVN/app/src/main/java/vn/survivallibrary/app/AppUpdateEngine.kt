package vn.survivallibrary.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val apkUrl: String,
    val apkSha256: String?,
    val checksumUrl: String?,
    val notes: String
)

data class PreparedAppUpdate(
    val release: AppReleaseInfo,
    val apkFile: File,
    val versionCode: Long
)

sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : AppUpdateCheckResult
    data class UpToDate(val currentVersion: String) : AppUpdateCheckResult
    data class Failed(val message: String) : AppUpdateCheckResult
}

enum class InstallerLaunchResult {
    LAUNCHED,
    PERMISSION_REQUIRED
}

internal object AppVersioning {
    private fun parts(value: String): List<Int> = value
        .trim()
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }

    fun compare(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        val max = maxOf(a.size, b.size, 3)
        for (index in 0 until max) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}

object AppUpdateEngine {
    private const val RELEASES_URL =
        "https://api.github.com/repos/giaphatgpt123-wq/FieldIntelligence/releases?per_page=30"
    private const val TAG_PREFIX = "survival-library-vn-v"
    private const val APK_NAME = "SurvivalLibraryVN.apk"
    private const val MAX_APK_BYTES = 160L * 1024L * 1024L

    fun currentVersionName(context: Context): String = currentPackageInfo(context).versionName ?: "?"

    fun currentVersionCode(context: Context): Long = versionCode(currentPackageInfo(context))

    fun checkForUpdate(context: Context): AppUpdateCheckResult {
        return try {
            val current = currentVersionName(context)
            val releases = JSONArray(downloadText(RELEASES_URL, "SurvivalLibraryVN/${current}"))
            val candidates = buildList {
                for (index in 0 until releases.length()) {
                    val release = releases.getJSONObject(index)
                    if (release.optBoolean("draft", false)) continue
                    val tag = release.optString("tag_name")
                    if (!tag.startsWith(TAG_PREFIX)) continue
                    val version = tag.removePrefix(TAG_PREFIX)
                    if (!AppVersioning.isNewer(version, current)) continue

                    val assets = release.optJSONArray("assets") ?: continue
                    var apkUrl: String? = null
                    var digest: String? = null
                    var checksumUrl: String? = null
                    for (assetIndex in 0 until assets.length()) {
                        val asset = assets.getJSONObject(assetIndex)
                        when (asset.optString("name")) {
                            APK_NAME -> {
                                apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                                digest = asset.optString("digest")
                                    .removePrefix("sha256:")
                                    .takeIf { it.matches(Regex("^[a-fA-F0-9]{64}$")) }
                                    ?.lowercase()
                            }
                            "$APK_NAME.sha256" -> {
                                checksumUrl = asset.optString("browser_download_url")
                                    .takeIf { it.startsWith("https://") }
                            }
                        }
                    }
                    if (apkUrl != null) {
                        add(
                            AppReleaseInfo(
                                tagName = tag,
                                versionName = version,
                                apkUrl = apkUrl,
                                apkSha256 = digest,
                                checksumUrl = checksumUrl,
                                notes = release.optString("body", "")
                            )
                        )
                    }
                }
            }

            val latest = candidates.maxWithOrNull { a, b ->
                AppVersioning.compare(a.versionName, b.versionName)
            }
            if (latest == null) {
                AppUpdateCheckResult.UpToDate(current)
            } else {
                AppUpdateCheckResult.UpdateAvailable(latest)
            }
        } catch (error: Exception) {
            AppUpdateCheckResult.Failed(error.message ?: "Không kiểm tra được phiên bản mới")
        }
    }

    fun downloadAndVerify(context: Context, release: AppReleaseInfo): PreparedAppUpdate {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(updateDir, "SurvivalLibraryVN-${release.versionName}.apk")
        val temp = File(updateDir, "${target.name}.part")
        temp.delete()

        val expectedSha = release.apkSha256 ?: release.checksumUrl?.let { url ->
            downloadText(url, "SurvivalLibraryVN/${currentVersionName(context)}")
                .trim()
                .substringBefore(' ')
                .takeIf { it.matches(Regex("^[a-fA-F0-9]{64}$")) }
                ?.lowercase()
        } ?: error("Bản phát hành thiếu SHA-256")

        val actualSha = downloadApk(release.apkUrl, temp, context)
        require(actualSha.equals(expectedSha, ignoreCase = true)) {
            "SHA-256 của APK không khớp"
        }

        if (target.exists()) target.delete()
        require(temp.renameTo(target)) { "Không thể hoàn tất tệp APK tải về" }

        try {
            val archive = archivePackageInfo(context, target)
                ?: error("APK tải về không đọc được")
            require(archive.packageName == context.packageName) {
                "APK không đúng package của ứng dụng"
            }
            val newCode = versionCode(archive)
            require(newCode > currentVersionCode(context)) {
                "APK tải về không mới hơn phiên bản đang cài"
            }
            require(signingFingerprints(archive) == signingFingerprints(currentPackageInfo(context))) {
                "Chữ ký APK không khớp ứng dụng đang cài"
            }
            return PreparedAppUpdate(release, target, newCode)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun launchInstaller(context: Context, prepared: PreparedAppUpdate): InstallerLaunchResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            return InstallerLaunchResult.PERMISSION_REQUIRED
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            prepared.apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return InstallerLaunchResult.LAUNCHED
    }

    private fun downloadApk(url: String, destination: File, context: Context): String {
        require(url.startsWith("https://")) { "Chỉ cho phép tải APK qua HTTPS" }
        val connection = openHttps(url, "SurvivalLibraryVN/${currentVersionName(context)}")
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code khi tải APK" }
            val declared = connection.contentLengthLong
            require(declared <= 0L || declared <= MAX_APK_BYTES) { "APK vượt giới hạn dung lượng" }

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_APK_BYTES) { "APK vượt giới hạn dung lượng" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            require(total > 0L) { "APK tải về rỗng" }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadText(url: String, userAgent: String): String {
        require(url.startsWith("https://")) { "Chỉ cho phép HTTPS" }
        val connection = openHttps(url, userAgent)
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttps(url: String, userAgent: String): HttpsURLConnection {
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
    }

    @Suppress("DEPRECATION")
    private fun currentPackageInfo(context: Context): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(context: Context, file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun signingFingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        require(signatures.isNotEmpty()) { "Không đọc được chữ ký APK" }
        return signatures.map { signature ->
            val bytes = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
}
