package vn.survivallibrary.app

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class RemotePackageDescriptor(
    val packageId: String,
    val version: Int,
    val schemaVersion: Int,
    val recordCount: Int,
    val verifiedCount: Int,
    val sha256: String,
    val packageUrl: String,
    val sizeBytes: Long = 0L,
    val minAppVersionCode: Int = 1,
    val updateMode: PackageUpdateMode = PackageUpdateMode.SNAPSHOT
)

data class ParsedPackagePayload(
    val records: List<LibraryPackageRecord>,
    val removedRecordIds: List<String>
)

data class UpdateRunResult(
    val checked: Boolean,
    val updatedPackages: List<String>,
    val installedRecords: Int,
    val message: String,
    val errors: List<String> = emptyList()
)

object LibraryUpdateIndexParser {
    fun parse(json: String): List<RemotePackageDescriptor> {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", -1) == 1) { "Cấu trúc chỉ mục cập nhật không được hỗ trợ" }
        val packages = root.optJSONArray("packages") ?: return emptyList()
        return buildList {
            for (index in 0 until packages.length()) {
                val item = packages.getJSONObject(index)
                val updateMode = runCatching {
                    PackageUpdateMode.valueOf(item.optString("updateMode", "SNAPSHOT").uppercase())
                }.getOrElse {
                    throw IllegalArgumentException("Chế độ cập nhật gói không hợp lệ")
                }
                add(
                    RemotePackageDescriptor(
                        packageId = item.getString("packageId"),
                        version = item.getInt("version"),
                        schemaVersion = item.getInt("schemaVersion"),
                        recordCount = item.getInt("recordCount"),
                        verifiedCount = item.getInt("verifiedCount"),
                        sha256 = item.getString("sha256").lowercase(),
                        packageUrl = item.getString("packageUrl"),
                        sizeBytes = item.optLong("sizeBytes", 0L),
                        minAppVersionCode = item.optInt("minAppVersionCode", 1),
                        updateMode = updateMode
                    )
                )
            }
        }
    }

    fun parsePackage(jsonBytes: ByteArray, remote: RemotePackageDescriptor): ParsedPackagePayload {
        val root = JSONObject(jsonBytes.toString(Charsets.UTF_8))
        require(root.getString("packageId") == remote.packageId) { "Sai packageId trong gói dữ liệu" }
        require(root.getInt("version") == remote.version) { "Sai phiên bản trong gói dữ liệu" }
        require(root.getInt("schemaVersion") == remote.schemaVersion) { "Sai phiên bản cấu trúc trong gói dữ liệu" }

        val payloadMode = runCatching {
            PackageUpdateMode.valueOf(root.optString("updateMode", "SNAPSHOT").uppercase())
        }.getOrElse {
            throw IllegalArgumentException("Chế độ cập nhật trong gói không hợp lệ")
        }
        require(payloadMode == remote.updateMode) { "Chế độ cập nhật không khớp chỉ mục" }

        val rows = root.getJSONArray("records")
        val records = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    LibraryPackageRecord(
                        id = row.getString("id"),
                        vietnameseName = row.getString("vietnameseName"),
                        categoryId = row.getString("categoryId"),
                        usageLevel = UsageLevel.valueOf(row.getString("usageLevel")),
                        verificationState = VerificationState.valueOf(row.getString("verificationState")),
                        summary = row.optString("summary", ""),
                        highRisk = row.optBoolean("highRisk", false),
                        sourceCount = row.optInt("sourceCount", 0),
                        published = row.optBoolean("published", false),
                        vietnamRelevant = row.optBoolean("vietnamRelevant", false),
                        verifiedVietnameseName = row.optBoolean("verifiedVietnameseName", false),
                        verifiedIdentitySource = row.optBoolean("verifiedIdentitySource", false),
                        verifiedMedia = row.optBoolean("verifiedMedia", false),
                        hasUsageClaim = row.optBoolean("hasUsageClaim", false),
                        verifiedUsageSource = row.optBoolean("verifiedUsageSource", false),
                        verifiedSafetySource = row.optBoolean("verifiedSafetySource", false)
                    )
                )
            }
        }

        val removedArray = root.optJSONArray("removedRecordIds")
        val removed = buildList {
            if (removedArray != null) {
                for (index in 0 until removedArray.length()) add(removedArray.getString(index))
            }
        }
        require(remote.updateMode == PackageUpdateMode.DELTA || removed.isEmpty()) {
            "SNAPSHOT không được chứa danh sách thu hồi"
        }
        return ParsedPackagePayload(records = records, removedRecordIds = removed)
    }

    fun parseRecords(jsonBytes: ByteArray, remote: RemotePackageDescriptor): List<LibraryPackageRecord> =
        parsePackage(jsonBytes, remote).records
}

object LibraryUpdateEngine {
    const val UPDATE_INDEX_URL = "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/SurvivalLibraryVN/data/update-index.json"

    // JSON packages are capped so the library grows through small verified deltas
    // instead of one large all-or-nothing download.
    internal const val MAX_PACKAGE_BYTES = 32L * 1024L * 1024L
    private const val MAX_INDEX_BYTES = 512L * 1024L
    private const val DISK_SAFETY_RESERVE_BYTES = 32L * 1024L * 1024L
    private const val BUFFER_BYTES = 64 * 1024

    fun checkAndUpdate(context: Context): UpdateRunResult {
        val appContext = context.applicationContext
        val db = OfflineLibraryDb(appContext)
        return try {
            val remotePackages = LibraryUpdateIndexParser.parse(downloadSmallText(UPDATE_INDEX_URL))
            val installed = db.installedPackageStates().associateBy { it.packageId }
            val candidates = remotePackages.filter { remote ->
                val current = installed[remote.packageId]
                current == null || remote.version > current.version
            }

            if (candidates.isEmpty()) {
                return UpdateRunResult(
                    checked = true,
                    updatedPackages = emptyList(),
                    installedRecords = 0,
                    message = if (remotePackages.isEmpty()) {
                        "Đã kết nối máy chủ cập nhật. Hiện chưa có gói dữ liệu thật mới được phát hành."
                    } else {
                        "Dữ liệu trên thiết bị đã là phiên bản mới nhất."
                    }
                )
            }

            val updated = mutableListOf<String>()
            val errors = mutableListOf<String>()
            var installedRecords = 0
            val currentAppVersionCode = appVersionCode(appContext)

            candidates.forEach { remote ->
                try {
                    require(remote.sizeBytes >= 0L) { "Kích thước khai báo không hợp lệ" }
                    require(remote.sizeBytes == 0L || remote.sizeBytes <= MAX_PACKAGE_BYTES) {
                        "Gói quá lớn; phải chia thành gói tăng dần nhỏ hơn ${MAX_PACKAGE_BYTES / 1024 / 1024} MB"
                    }
                    require(remote.minAppVersionCode <= currentAppVersionCode) {
                        "Gói yêu cầu ứng dụng versionCode ${remote.minAppVersionCode} trở lên"
                    }

                    val bytes = downloadVerifiedPackage(appContext, remote)
                    val manifest = LibraryPackageManifest(
                        packageId = remote.packageId,
                        version = remote.version,
                        schemaVersion = remote.schemaVersion,
                        recordCount = remote.recordCount,
                        verifiedCount = remote.verifiedCount,
                        sha256 = remote.sha256,
                        sourceUri = remote.packageUrl,
                        updateMode = remote.updateMode
                    )
                    val packageDecision = LibraryDataPackages.validate(manifest)
                    require(packageDecision.valid) { packageDecision.blockers.joinToString("; ") }

                    val payload = LibraryUpdateIndexParser.parsePackage(bytes, remote)
                    installedRecords += when (remote.updateMode) {
                        PackageUpdateMode.SNAPSHOT -> db.installVerifiedPackage(manifest, payload.records)
                        PackageUpdateMode.DELTA -> db.installVerifiedDeltaPackage(
                            manifest = manifest,
                            records = payload.records,
                            removedRecordIds = payload.removedRecordIds
                        )
                    }
                    updated += remote.packageId
                } catch (error: Exception) {
                    errors += "${remote.packageId}: ${error.message ?: "không xác định"}"
                }
            }

            UpdateRunResult(
                checked = true,
                updatedPackages = updated,
                installedRecords = installedRecords,
                message = when {
                    updated.isNotEmpty() && errors.isEmpty() -> "Đã cập nhật ${updated.size} gói dữ liệu, $installedRecords hồ sơ mới/thay đổi."
                    updated.isNotEmpty() -> "Đã cập nhật một phần; ${errors.size} gói bị chặn để bảo vệ dữ liệu."
                    else -> "Không gói nào được cài vì không vượt qua kiểm tra an toàn dữ liệu."
                },
                errors = errors
            )
        } catch (error: Exception) {
            UpdateRunResult(
                checked = false,
                updatedPackages = emptyList(),
                installedRecords = 0,
                message = "Không thể kiểm tra cập nhật lúc này.",
                errors = listOf(error.message ?: "Lỗi kết nối không xác định")
            )
        } finally {
            db.close()
        }
    }

    private fun downloadSmallText(url: String): String {
        val bytes = downloadWithLimit(url, MAX_INDEX_BYTES)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun downloadVerifiedPackage(context: Context, remote: RemotePackageDescriptor): ByteArray {
        require(remote.packageUrl.startsWith("https://")) { "Chỉ cho phép nguồn HTTPS" }
        require(remote.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) { "SHA-256 không hợp lệ" }

        val downloadDir = File(context.cacheDir, "library-updates").apply { mkdirs() }
        val expectedBytes = remote.sizeBytes.takeIf { it > 0L } ?: MAX_PACKAGE_BYTES
        require(downloadDir.usableSpace >= expectedBytes + DISK_SAFETY_RESERVE_BYTES) {
            "Không đủ dung lượng trống an toàn để tải gói"
        }

        val temp = File.createTempFile("${remote.packageId}-", ".part", downloadDir)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val total = streamToFile(
                url = remote.packageUrl,
                destination = temp,
                maxBytes = MAX_PACKAGE_BYTES,
                digest = digest
            )
            require(total > 0L) { "Gói tải về rỗng" }
            if (remote.sizeBytes > 0L) {
                require(total == remote.sizeBytes) { "Kích thước gói không khớp manifest" }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha.equals(remote.sha256, ignoreCase = true)) { "SHA-256 không khớp" }
            temp.readBytes()
        } finally {
            temp.delete()
        }
    }

    private fun downloadWithLimit(url: String, maxBytes: Long): ByteArray {
        require(url.startsWith("https://")) { "Chỉ cho phép nguồn HTTPS" }
        val connection = openConnection(url)
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            require(connection.url.protocol.equals("https", ignoreCase = true)) { "Chuyển hướng ra ngoài HTTPS bị chặn" }
            val declared = connection.contentLengthLong
            require(declared <= 0L || declared <= maxBytes) { "Nội dung tải về vượt giới hạn cho phép" }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 64L * 1024L).toInt())
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "Nội dung tải về vượt giới hạn cho phép" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun streamToFile(
        url: String,
        destination: File,
        maxBytes: Long,
        digest: MessageDigest
    ): Long {
        val connection = openConnection(url)
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            require(connection.url.protocol.equals("https", ignoreCase = true)) { "Chuyển hướng ra ngoài HTTPS bị chặn" }
            val declared = connection.contentLengthLong
            require(declared <= 0L || declared <= maxBytes) {
                "Gói tải về vượt giới hạn ${maxBytes / 1024 / 1024} MB"
            }

            BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                BufferedOutputStream(FileOutputStream(destination), BUFFER_BYTES).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes) {
                            "Gói tải về vượt giới hạn ${maxBytes / 1024 / 1024} MB"
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    total
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpsURLConnection {
        require(url.startsWith("https://")) { "Chỉ cho phép nguồn HTTPS" }
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SurvivalLibraryVN/0.12")
            setRequestProperty("Accept-Encoding", "identity")
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            info.versionCode
        }
    }
}
