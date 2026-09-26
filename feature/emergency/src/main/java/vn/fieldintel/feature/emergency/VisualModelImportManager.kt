package vn.fieldintel.feature.emergency

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/** Installs verified offline visual-model bundles without network credentials inside the APK. */
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
            val manifestFile = File(staging, TfliteRegionModelRunner.MANIFEST_FILE)
            require(manifestFile.isFile) { "Gói thiếu ${TfliteRegionModelRunner.MANIFEST_FILE}" }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            val schema = manifest.optInt("schemaVersion", 0)
            val modelId = manifest.getString("id").trim()
            val version = manifest.getString("version").trim()
            val sourceName = manifest.getString("sourceName").trim()
            val license = manifest.getString("license").trim()
            require(modelId.isNotBlank() && version.isNotBlank()) { "Manifest thiếu id/version" }
            require(sourceName.isNotBlank() && license.isNotBlank()) { "Manifest thiếu source/license" }
            require(manifest.optBoolean("speciesSafetyClaims", false).not()) {
                "Gói model không được chứa tuyên bố ăn được/độc tính/y khoa như kết luận hình ảnh"
            }

            when (schema) {
                1 -> validateSingleStageBundle(staging, manifest)
                2 -> validateTwoStageBundle(staging, manifest)
                else -> error("Model manifest schema không hỗ trợ: $schema")
            }

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
            val mode = if (schema == 2) "hai tầng detector + classifier" else "một tầng"
            Result(true, "Đã cài model quét vùng $modelId • $version • $mode", modelId, version)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }.getOrElse { failure ->
        Result(false, "Cài model thất bại: ${failure.message ?: failure.javaClass.simpleName}")
    }

    private fun validateSingleStageBundle(staging: File, manifest: JSONObject) {
        require(manifest.optString("taskType") == TfliteRegionModelRunner.TASK_TYPE_OBJECT_DETECTOR) {
            "Gói schema v1 phải là OBJECT_DETECTOR"
        }
        require(manifest.optString("modelFormat") == TfliteRegionModelRunner.MODEL_FORMAT_TFLITE_TASK_VISION) {
            "Gói model không dùng định dạng TFLITE_TASK_VISION được hỗ trợ"
        }
        val model = File(staging, TfliteRegionModelRunner.MODEL_FILE)
        require(model.isFile) { "Gói thiếu ${TfliteRegionModelRunner.MODEL_FILE}" }
        verifyFile(model, manifest, "model")
        val expected = setOf(TfliteRegionModelRunner.MODEL_FILE, TfliteRegionModelRunner.MANIFEST_FILE)
        require(staging.list()?.toSet() == expected) { "Gói schema v1 phải gồm đúng model và manifest" }
    }

    private fun validateTwoStageBundle(staging: File, manifest: JSONObject) {
        require(manifest.optString("taskType") == TwoStageTfliteRegionModelRunner.TASK_TYPE_TWO_STAGE) {
            "Gói schema v2 phải là TWO_STAGE_REGION_CLASSIFIER"
        }
        require(manifest.optString("modelFormat") == TfliteRegionModelRunner.MODEL_FORMAT_TFLITE_TASK_VISION) {
            "Gói model không dùng định dạng TFLITE_TASK_VISION được hỗ trợ"
        }
        val detector = File(staging, TwoStageTfliteRegionModelRunner.DETECTOR_FILE)
        val classifier = File(staging, TwoStageTfliteRegionModelRunner.CLASSIFIER_FILE)
        require(detector.isFile) { "Gói thiếu ${TwoStageTfliteRegionModelRunner.DETECTOR_FILE}" }
        require(classifier.isFile) { "Gói thiếu ${TwoStageTfliteRegionModelRunner.CLASSIFIER_FILE}" }
        val detectorMeta = manifest.getJSONObject("detector")
        val classifierMeta = manifest.getJSONObject("classifier")
        require(detectorMeta.optString("file") == TwoStageTfliteRegionModelRunner.DETECTOR_FILE) {
            "Manifest detector trỏ sai file"
        }
        require(classifierMeta.optString("file") == TwoStageTfliteRegionModelRunner.CLASSIFIER_FILE) {
            "Manifest classifier trỏ sai file"
        }
        verifyFile(detector, detectorMeta, "detector")
        verifyFile(classifier, classifierMeta, "classifier")
        val expected = setOf(
            TwoStageTfliteRegionModelRunner.DETECTOR_FILE,
            TwoStageTfliteRegionModelRunner.CLASSIFIER_FILE,
            TwoStageTfliteRegionModelRunner.MANIFEST_FILE
        )
        require(staging.list()?.toSet() == expected) { "Gói schema v2 phải gồm đúng detector, classifier và manifest" }
    }

    private fun verifyFile(file: File, meta: JSONObject, label: String) {
        val declaredSize = meta.optLong("sizeBytes", -1L)
        require(declaredSize == file.length()) { "Kích thước $label không khớp manifest" }
        val expectedSha = meta.getString("sha256").lowercase(Locale.ROOT)
        require(expectedSha.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 $label trong manifest không hợp lệ" }
        require(sha256(file) == expectedSha) { "SHA-256 $label không khớp manifest" }
        val header = ByteArray(8)
        file.inputStream().use { stream -> require(stream.read(header) == header.size) { "$label quá ngắn" } }
        require(header.copyOfRange(4, 8).contentEquals("TFL3".toByteArray(Charsets.US_ASCII))) {
            "$label không phải tệp TensorFlow Lite hợp lệ"
        }
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
                require(name in ALLOWED_NAMES) { "File không được phép trong gói model: $name" }
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
        require(entries in 2..3) { "Gói model phải có 2 hoặc 3 file theo schema" }
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
        private const val MAX_ENTRIES = 3
        private const val MAX_MANIFEST_BYTES = 64L * 1024L
        private const val MAX_MODEL_BYTES = 160L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = MAX_MODEL_BYTES * 2 + MAX_MANIFEST_BYTES
        private val ALLOWED_NAMES = setOf(
            TfliteRegionModelRunner.MODEL_FILE,
            TfliteRegionModelRunner.MANIFEST_FILE,
            TwoStageTfliteRegionModelRunner.DETECTOR_FILE,
            TwoStageTfliteRegionModelRunner.CLASSIFIER_FILE
        )
    }
}
