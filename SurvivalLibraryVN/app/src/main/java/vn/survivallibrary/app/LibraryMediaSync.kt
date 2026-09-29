package vn.survivallibrary.app

import android.content.ContentValues
import android.content.Context
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class MediaSyncResult(
    val downloaded: Int,
    val reused: Int,
    val errors: List<String>
)

/**
 * Downloads verified media separately from the APK and text package.
 *
 * Media is written atomically into the bounded app media cache, SHA-256 is checked
 * before the database receives a local path, and a failed image never invalidates
 * an already verified text record. A later sync can repair missing/evicted media.
 */
object LibraryMediaSync {
    private const val MAX_MEDIA_BYTES = 4 * 1024 * 1024
    private const val MAX_DOWNLOAD_BYTES_PER_RUN = 24L * 1024L * 1024L
    private val allowedHosts = setOf("raw.githubusercontent.com", "github.com")

    fun syncMissing(context: Context, db: OfflineLibraryDb): MediaSyncResult {
        val rows = pendingRows(db)
        if (rows.isEmpty()) return MediaSyncResult(0, 0, emptyList())

        val root = File(MediaCachePolicy.cacheDirectory(context), "published").apply { mkdirs() }
        var downloaded = 0
        var reused = 0
        var downloadedBytes = 0L
        val errors = mutableListOf<String>()

        rows.forEach { media ->
            try {
                val existing = media.localPath.takeIf { it.isNotBlank() }?.let(::File)
                if (existing != null && existing.isFile && sha256(existing).equals(media.checksum, ignoreCase = true)) {
                    reused++
                    return@forEach
                }

                val safeName = media.mediaId.replace(Regex("[^A-Za-z0-9._-]"), "_")
                require(safeName.isNotBlank()) { "mediaId không hợp lệ" }
                val target = File(root, "$safeName.${extensionFor(media.mimeType)}")

                if (target.isFile && sha256(target).equals(media.checksum, ignoreCase = true)) {
                    updateLocalPath(db, media.mediaId, media.checksum, target)
                    reused++
                    return@forEach
                }

                require(downloadedBytes < MAX_DOWNLOAD_BYTES_PER_RUN) { "Đã đạt giới hạn media của lần đồng bộ" }
                val remaining = (MAX_DOWNLOAD_BYTES_PER_RUN - downloadedBytes)
                    .coerceAtMost(MAX_MEDIA_BYTES.toLong())
                    .toInt()
                require(remaining > 0) { "Không còn ngân sách tải media" }

                val temp = File(root, ".${safeName}.${System.nanoTime()}.tmp")
                try {
                    val size = downloadToFile(media.downloadUri, temp, remaining)
                    require(sha256(temp).equals(media.checksum, ignoreCase = true)) {
                        "SHA-256 ảnh không khớp"
                    }
                    if (target.exists()) target.delete()
                    require(temp.renameTo(target)) { "Không thể hoàn tất ghi ảnh" }
                    updateLocalPath(db, media.mediaId, media.checksum, target)
                    target.setLastModified(System.currentTimeMillis())
                    downloadedBytes += size
                    downloaded++
                } finally {
                    if (temp.exists()) temp.delete()
                }
            } catch (error: Exception) {
                errors += "${media.mediaId}: ${error.message ?: "lỗi media không xác định"}"
            }
        }

        runCatching { MediaCachePolicy.trimToBudget(context.applicationContext) }
        return MediaSyncResult(downloaded, reused, errors)
    }

    private data class PendingMedia(
        val mediaId: String,
        val downloadUri: String,
        val checksum: String,
        val mimeType: String,
        val localPath: String
    )

    private fun pendingRows(db: OfflineLibraryDb): List<PendingMedia> {
        val result = mutableListOf<PendingMedia>()
        db.readableDatabase.query(
            "record_media",
            arrayOf("media_id", "download_uri", "checksum", "mime_type", "local_path"),
            "verified = 1",
            null,
            null,
            null,
            "media_id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val row = PendingMedia(
                    mediaId = cursor.getString(0),
                    downloadUri = cursor.getString(1),
                    checksum = cursor.getString(2).lowercase(),
                    mimeType = cursor.getString(3),
                    localPath = cursor.getString(4)
                )
                val local = row.localPath.takeIf { it.isNotBlank() }?.let(::File)
                if (local == null || !local.isFile) result += row
            }
        }
        return result
    }

    private fun updateLocalPath(db: OfflineLibraryDb, mediaId: String, checksum: String, file: File) {
        val values = ContentValues().apply { put("local_path", file.absolutePath) }
        val count = db.writableDatabase.update(
            "record_media",
            values,
            "media_id = ? AND checksum = ? AND verified = 1",
            arrayOf(mediaId, checksum.lowercase())
        )
        require(count == 1) { "Không thể liên kết ảnh với hồ sơ" }
    }

    private fun downloadToFile(url: String, target: File, maxBytes: Int): Long {
        validateUrl(url)
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SurvivalLibraryVN/0.16-media")
        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val declared = connection.contentLengthLong
            require(declared < 0L || declared <= maxBytes.toLong()) { "Ảnh vượt giới hạn dung lượng" }

            target.outputStream().buffered().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes.toLong()) { "Ảnh vượt giới hạn dung lượng" }
                        output.write(buffer, 0, read)
                    }
                    total
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateUrl(value: String) {
        val url = URL(value)
        require(url.protocol.equals("https", ignoreCase = true)) { "Media phải dùng HTTPS" }
        require(url.host.lowercase() in allowedHosts) { "Máy chủ media không được phép" }
    }

    private fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "media"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
