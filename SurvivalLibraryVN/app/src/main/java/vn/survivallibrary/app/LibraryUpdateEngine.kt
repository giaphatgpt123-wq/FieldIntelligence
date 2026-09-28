package vn.survivallibrary.app

import android.content.Context
import org.json.JSONObject
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
    val packageUrl: String
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
                add(
                    RemotePackageDescriptor(
                        packageId = item.getString("packageId"),
                        version = item.getInt("version"),
                        schemaVersion = item.getInt("schemaVersion"),
                        recordCount = item.getInt("recordCount"),
                        verifiedCount = item.getInt("verifiedCount"),
                        sha256 = item.getString("sha256").lowercase(),
                        packageUrl = item.getString("packageUrl")
                    )
                )
            }
        }
    }

    fun parseRecords(jsonBytes: ByteArray, remote: RemotePackageDescriptor): List<LibraryPackageRecord> {
        val root = JSONObject(jsonBytes.toString(Charsets.UTF_8))
        require(root.getString("packageId") == remote.packageId) { "Sai packageId trong gói dữ liệu" }
        require(root.getInt("version") == remote.version) { "Sai phiên bản trong gói dữ liệu" }
        require(root.getInt("schemaVersion") == remote.schemaVersion) { "Sai phiên bản cấu trúc trong gói dữ liệu" }
        val rows = root.getJSONArray("records")
        return buildList {
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
    }
}

object LibraryUpdateEngine {
    const val UPDATE_INDEX_URL = "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/SurvivalLibraryVN/data/update-index.json"

    fun checkAndUpdate(context: Context): UpdateRunResult {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            val remotePackages = LibraryUpdateIndexParser.parse(downloadText(UPDATE_INDEX_URL))
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

            candidates.forEach { remote ->
                try {
                    val bytes = downloadBytes(remote.packageUrl)
                    val actualSha = sha256(bytes)
                    require(actualSha.equals(remote.sha256, ignoreCase = true)) { "SHA-256 không khớp" }

                    val manifest = LibraryPackageManifest(
                        packageId = remote.packageId,
                        version = remote.version,
                        schemaVersion = remote.schemaVersion,
                        recordCount = remote.recordCount,
                        verifiedCount = remote.verifiedCount,
                        sha256 = remote.sha256,
                        sourceUri = remote.packageUrl
                    )
                    val packageDecision = LibraryDataPackages.validate(manifest)
                    require(packageDecision.valid) { packageDecision.blockers.joinToString("; ") }

                    val records = LibraryUpdateIndexParser.parseRecords(bytes, remote)
                    installedRecords += db.installVerifiedPackage(manifest, records)
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
                    updated.isNotEmpty() && errors.isEmpty() -> "Đã cập nhật ${updated.size} gói dữ liệu, $installedRecords hồ sơ."
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

    private fun downloadText(url: String): String = downloadBytes(url).toString(Charsets.UTF_8)

    private fun downloadBytes(url: String): ByteArray {
        require(url.startsWith("https://")) { "Chỉ cho phép nguồn HTTPS" }
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SurvivalLibraryVN/0.6")
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
