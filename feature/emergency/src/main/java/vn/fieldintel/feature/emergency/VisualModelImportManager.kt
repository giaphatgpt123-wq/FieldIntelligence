package vn.fieldintel.feature.emergency

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/** Installs an offline visual-model bundle without requiring network credentials inside the APK. */
class VisualModelImportManager(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "visual-model")

    data class Result(
        val installed: Boolean,
        val message: String,
        val modelId: String? = null,
        val version: String? = null
    )

    fun install(input: InputStream): Result = runCatching {
        val staging = File(appContext.cacheDir, "visual-model-staging-${System.nanoTime()}").apply { mkdirs() }
        try {
            extractBundle(input, staging)
            val model = File(staging, TfliteRegionModelRunner.MODEL_FILE)
            val manifestFile = File(staging, TfliteRegionModelRunner.MANIFEST_FILE)
            require(model.isFile) { "Gói thiếu ${TfliteRegionModelRunner.MODEL_FILE}" }
            require(manifestFile.isFile) { "Gói thiếu ${TfliteRegionModelRunner.MANIFEST_FILE}" }

            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optInt("schemaVersion", 0) == 1) { "Model manifest schema không hỗ trợ" }
            require(manifest.optString("taskType") == TfliteRegionModelRunner.TASK_TYPE_OBJECT_DETECTOR) {
                "Gói model không phải OBJECT_DETECTOR cho quét vùng"
            }
            require(manifest.optString("modelFormat") == TfliteRegionModelRunner.MODEL_FORMAT_TFLITE_TASK_VISION) {
                "Gói model không dùng định dạng TFLITE_TASK_VISION được hỗ trợ"
            }
            val modelId = manifest.getString("id").trim()
            val version = manifest.getString("version").trim()
            val sourceName = manifest.getString("sourceName").trim()
            val license = manifest.getString("license").trim()
            require(modelId.isNotBlank() && version.isNotBlank()) { "Manifest thiếu id/version" }
            require(sourceName.isNotBlank() && license.isNotBlank()) { "Manifest thiếu source/license" }
            require(manifest.optBoolean("speciesSafetyClaims", false).not()) {
                "Gói model không được chứa tuyên bố ăn được/độc tính/y khoa như kết luận hình ảnh"
            }

            val declaredSize = manifest.optLong("sizeBytes", -1L)
            require(declaredSize == model.length()) { "Kích thước model không khớp manifest" }
            val expectedSha = manifest.getString("sha256").lowercase(Locale.ROOT)
            require(expectedSha.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 trong manifest không hợp lệ" }
            require(sha256(model) == expectedSha) { "SHA-256 model không khớp manifest" }

            val backup = File(appContext.filesDir, "visual-model.previous")
            if (backup.exists()) backup.deleteRecursively()
            if (root.exists()) require(root.renameTo(backup)) { "Không thể tạo bản sao model hiện tại" }
            val installed = staging.renameTo(root)
            if (!installed) {
                if (root.exists()) root.deleteRecursively()
                if (backup.exists()) backup.renameTo(root)
                error("Không thể kích hoạt gói model")
            }
            backup.deleteRecursively()
            Result(true, "Đã cài model quét vùng $modelId • $version", modelId, version)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }.getOrElse { failure ->
        Result(false, "Cài model thất bại: ${failure.message ?: failure.javaClass.simpleName}")
    }

    private fun extractBundle(input: InputStream, staging: File) {
        var entries = 0
        var total = 0L
        val seen = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += 1
                require(entries <= MAX_ENTRIES) { "Gói model có quá nhiều file" }
                require(!entry.isDirectory) { "Gói model không được chứa thư mục" }
                val name = entry.name
                require(name == TfliteRegionModelRunner.MODEL_FILE || name == TfliteRegionModelRunner.MANIFEST_FILE) {
                    "File không được phép trong gói model: $name"
                }
                require(seen.add(name)) { "File bị lặp trong gói model: $name" }
                val max = if (name.endsWith(".json")) MAX_MANIFEST_BYTES else MAX_MODEL_BYTES
                val output = File(staging, name)
                output.outputStream().buffered().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count <= 0) break
                        written += count
                        total += count
                        require(written <= max) { "$name vượt kích thước cho phép" }
                        require(total <= MAX_TOTAL_BYTES) { "Gói model vượt kích thước cho phép" }
                        out.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }
        require(entries == 2) { "Gói model phải gồm đúng model và manifest" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_ENTRIES = 2
        private const val MAX_MANIFEST_BYTES = 64L * 1024L
        private const val MAX_MODEL_BYTES = 160L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = MAX_MODEL_BYTES + MAX_MANIFEST_BYTES
    }
}
