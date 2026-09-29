package vn.survivallibrary.app

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
                fun stringList(key: String): List<String> = buildList {
                    val values = row.optJSONArray(key) ?: return@buildList
                    for (itemIndex in 0 until values.length()) {
                        val value = values.optString(itemIndex).trim()
                        if (value.isNotBlank()) add(value)
                    }
                }

                val sources = buildList {
                    val values = row.optJSONArray("sources")
                    if (values != null) {
                        for (sourceIndex in 0 until values.length()) {
                            val source = values.getJSONObject(sourceIndex)
                            add(
                                LibraryRecordSource(
                                    sourceKey = source.getString("sourceKey"),
                                    title = source.getString("title"),
                                    publisher = source.getString("publisher"),
                                    uri = source.getString("uri"),
                                    checkedAt = source.optLong("checkedAt", 0L)
                                )
                            )
                        }
                    }
                }
                val media = buildList {
                    val values = row.optJSONArray("media")
                    if (values != null) {
                        for (mediaIndex in 0 until values.length()) {
                            val item = values.getJSONObject(mediaIndex)
                            add(
                                LibraryRecordMedia(
                                    mediaId = item.getString("mediaId"),
                                    sourceUri = item.getString("sourceUri"),
                                    downloadUri = item.getString("downloadUri"),
                                    verified = item.optBoolean("verified", false),
                                    angleLabel = item.getString("angleLabel"),
                                    checksum = item.getString("checksum").lowercase(),
                                    license = item.getString("license"),
                                    creator = item.optString("creator", ""),
                                    rightsHolder = item.optString("rightsHolder", ""),
                                    mimeType = item.optString("mimeType", "image/webp"),
                                    viewRole = item.optString("viewRole", "REFERENCE"),
                                    lifeStage = item.optString("lifeStage", ""),
                                    isPrimary = item.optBoolean("isPrimary", false),
                                    diagnostic = item.optBoolean("diagnostic", true)
                                )
                            )
                        }
                    }
                }
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
                        verifiedSafetySource = row.optBoolean("verifiedSafetySource", false),
                        sources = sources,
                        media = media,
                        scientificName = row.optString("scientificName", ""),
                        identificationSummary = row.optString("identificationSummary", ""),
                        keyFeatures = stringList("keyFeatures"),
                        confusableWith = stringList("confusableWith"),
                        requiredViewRoles = stringList("requiredViewRoles"),
                        primaryViewRole = row.optString("primaryViewRole", ""),
                        qualityProfile = row.optString("qualityProfile", "")
                    )
                )
            }
        }
    }
}

object LibraryUpdateEngine {
    const val UPDATE_INDEX_URL = "https://raw.githubusercontent.com/giaphatgpt123-wq/FieldIntelligence/survival-library-vn/SurvivalLibraryVN/data/update-index.json"

    private const val MAX_INDEX_BYTES = 512 * 1024
    private const val MAX_PACKAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_RECORDS_PER_PACKAGE = LibraryDataPackages.MAX_RECORDS_PER_SHARD
    private val allowedInitialHosts = setOf("raw.githubusercontent.com", "github.com")

    fun checkAndUpdate(context: Context): UpdateRunResult {
        val appContext = context.applicationContext
        val db = OfflineLibraryDb(appContext)
        return try {
            val remotePackages = LibraryUpdateIndexParser.parse(downloadText(UPDATE_INDEX_URL, MAX_INDEX_BYTES))
            require(remotePackages.map { it.packageId }.distinct().size == remotePackages.size) {
                "Chỉ mục cập nhật chứa packageId trùng lặp"
            }

            val installed = db.installedPackageStates().associateBy { it.packageId }
            val candidates = remotePackages.filter { remote ->
                val current = installed[remote.packageId]
                current == null || remote.version > current.version
            }

            if (candidates.isEmpty()) {
                val media = LibraryMediaSync.syncMissing(appContext, db)
                val mediaErrors = media.errors.map { "media: $it" }
                return UpdateRunResult(
                    checked = true,
                    updatedPackages = emptyList(),
                    installedRecords = 0,
                    message = when {
                        remotePackages.isEmpty() -> "Đã kết nối máy chủ cập nhật. Hiện chưa có gói dữ liệu thật mới được phát hành."
                        media.downloaded > 0 && mediaErrors.isEmpty() -> "Dữ liệu đã là phiên bản mới nhất. Đã tải ${media.downloaded} ảnh kiểm chứng để hiển thị trên app."
                        media.downloaded > 0 -> "Dữ liệu đã là phiên bản mới nhất. Đã tải ${media.downloaded} ảnh; ${mediaErrors.size} media cần thử lại."
                        mediaErrors.isNotEmpty() -> "Dữ liệu đã là phiên bản mới nhất nhưng có ${mediaErrors.size} media chưa tải được."
                        else -> "Dữ liệu trên thiết bị đã là phiên bản mới nhất."
                    },
                    errors = mediaErrors
                )
            }

            val updated = mutableListOf<String>()
            val errors = mutableListOf<String>()
            var installedRecords = 0

            candidates.forEach { remote ->
                try {
                    require(remote.recordCount in 0..MAX_RECORDS_PER_PACKAGE) {
                        "Gói vượt giới hạn $MAX_RECORDS_PER_PACKAGE hồ sơ; cần chia nhỏ thành shard"
                    }

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

                    validateInitialDownloadUrl(remote.packageUrl)
                    val bytes = downloadBytes(remote.packageUrl, MAX_PACKAGE_BYTES)
                    val actualSha = sha256(bytes)
                    require(actualSha.equals(remote.sha256, ignoreCase = true)) { "SHA-256 không khớp" }

                    val records = LibraryUpdateIndexParser.parseRecords(bytes, remote)
                    installedRecords += db.installVerifiedPackage(manifest, records)
                    updated += remote.packageId
                } catch (error: Exception) {
                    errors += "${remote.packageId}: ${error.message ?: "không xác định"}"
                }
            }

            val media = LibraryMediaSync.syncMissing(appContext, db)
            errors += media.errors.map { "media: $it" }

            UpdateRunResult(
                checked = true,
                updatedPackages = updated,
                installedRecords = installedRecords,
                message = when {
                    updated.isNotEmpty() && errors.isEmpty() -> {
                        val mediaText = if (media.downloaded > 0) " · ${media.downloaded} ảnh" else ""
                        "Đã cập nhật ${updated.size} gói dữ liệu, $installedRecords hồ sơ$mediaText."
                    }
                    updated.isNotEmpty() -> "Đã cập nhật dữ liệu; ${errors.size} mục bị chặn hoặc media cần thử lại."
                    media.downloaded > 0 && errors.isEmpty() -> "Không có gói dữ liệu mới. Đã tải ${media.downloaded} ảnh kiểm chứng."
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

    private fun downloadText(url: String, maxBytes: Int): String =
        downloadBytes(url, maxBytes).toString(Charsets.UTF_8)

    private fun validateInitialDownloadUrl(url: String) {
        val parsed = URL(url)
        require(parsed.protocol.equals("https", ignoreCase = true)) { "Chỉ cho phép nguồn HTTPS" }
        require(parsed.host.lowercase() in allowedInitialHosts) { "Máy chủ gói dữ liệu không được phép" }
    }

    private fun downloadBytes(url: String, maxBytes: Int): ByteArray {
        validateInitialDownloadUrl(url)
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SurvivalLibraryVN/0.17")
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val declaredLength = connection.contentLengthLong
            require(declaredLength < 0L || declaredLength <= maxBytes.toLong()) { "Gói tải về vượt giới hạn dung lượng" }

            connection.inputStream.use { input ->
                val initialCapacity = when {
                    declaredLength in 1..maxBytes.toLong() -> declaredLength.toInt()
                    else -> minOf(64 * 1024, maxBytes)
                }
                val output = ByteArrayOutputStream(initialCapacity)
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "Gói tải về vượt giới hạn dung lượng" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}