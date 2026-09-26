package vn.fieldintel.feature.emergency

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import org.json.JSONObject
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * On-device multi-object runner for a metadata-enabled TensorFlow Lite detector.
 *
 * Package layout under filesDir/visual-model:
 * - region-model.tflite
 * - region-model.manifest.json
 *
 * The manifest binds the exact model bytes and declares the provenance/license. The runner only
 * reports READY after SHA-256 verification and successful Task Library initialization.
 */
class TfliteRegionModelRunner(context: Context) : LiveVisualModelRunner, AutoCloseable {
    private val appContext = context.applicationContext
    private val packageDir = File(appContext.filesDir, "visual-model")
    private val modelFile = File(packageDir, MODEL_FILE)
    private val manifestFile = File(packageDir, MANIFEST_FILE)

    private var detector: ObjectDetector? = null
    private var loadedDescriptor: VisualModelDescriptor? = null
    private var initializationError: String? = null

    override fun status(): VisualModelStatus {
        if (!modelFile.isFile || !manifestFile.isFile) {
            return VisualModelStatus(
                VisualModelAvailability.NOT_INSTALLED,
                message = "Chưa cài gói model quét vùng trên thiết bị."
            )
        }
        ensureLoaded()
        val descriptor = loadedDescriptor
        return if (detector != null && descriptor != null) {
            VisualModelStatus(
                VisualModelAvailability.READY,
                descriptor,
                "Model quét vùng đã xác minh SHA-256 và sẵn sàng chạy offline."
            )
        } else {
            VisualModelStatus(
                VisualModelAvailability.INVALID,
                message = initializationError ?: "Gói model quét vùng không hợp lệ."
            )
        }
    }

    override fun detect(frame: LiveVisualFrameData, target: LiveVisualSearchTarget): List<VisualDetection> =
        scanRegion(frame).filter { LiveVisualTargetMatcher.matches(target, it) }

    override fun scanRegion(frame: LiveVisualFrameData): List<VisualDetection> {
        ensureLoaded()
        val active = detector ?: return emptyList()
        val bitmap = frame.toUprightBitmap()
        return try {
            active.detect(TensorImage.fromBitmap(bitmap))
                .flatMap { detection ->
                    val box = detection.boundingBox
                    val width = bitmap.width.toFloat().coerceAtLeast(1f)
                    val height = bitmap.height.toFloat().coerceAtLeast(1f)
                    detection.categories.mapNotNull { category ->
                        val left = (box.left / width).coerceIn(0f, 1f)
                        val top = (box.top / height).coerceIn(0f, 1f)
                        val right = (box.right / width).coerceIn(0f, 1f)
                        val bottom = (box.bottom / height).coerceIn(0f, 1f)
                        if (right <= left || bottom <= top) return@mapNotNull null
                        val rawLabel = category.label.ifBlank { "class-${category.index}" }
                        val mapped = ModelClassNameParser.parse(rawLabel)
                        VisualDetection(
                            trackHint = "${category.index}:${left.format3()}:${top.format3()}",
                            label = mapped.displayLabel,
                            scientificName = mapped.scientificName,
                            confidence = category.score.coerceIn(0f, 1f),
                            box = NormalizedBox(left, top, right, bottom)
                        )
                    }
                }
                .sortedByDescending { it.confidence }
        } finally {
            bitmap.recycle()
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (detector != null || initializationError != null) return
        runCatching {
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optInt("schemaVersion", 0) == 1) { "Model manifest schema không hỗ trợ" }
            require(manifest.optString("taskType") == TASK_TYPE_OBJECT_DETECTOR) {
                "Model đã cài không phải OBJECT_DETECTOR cho quét vùng"
            }
            require(manifest.optString("modelFormat") == MODEL_FORMAT_TFLITE_TASK_VISION) {
                "Định dạng model không phải TFLITE_TASK_VISION được hỗ trợ"
            }
            require(manifest.optBoolean("speciesSafetyClaims", false).not()) {
                "Model không được gắn tuyên bố ăn được/độc tính/y khoa như kết luận hình ảnh"
            }
            val declaredSize = manifest.optLong("sizeBytes", -1L)
            require(declaredSize == modelFile.length()) { "Kích thước model không khớp manifest" }
            val expectedSha = manifest.getString("sha256").lowercase(Locale.ROOT)
            require(expectedSha.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 trong manifest không hợp lệ" }
            val actualSha = sha256(modelFile)
            require(actualSha == expectedSha) { "SHA-256 model không khớp manifest" }
            val descriptor = VisualModelDescriptor(
                id = manifest.getString("id"),
                version = manifest.getString("version"),
                sourceName = manifest.getString("sourceName"),
                license = manifest.getString("license"),
                sha256 = actualSha,
                supportedGroups = manifest.optJSONArray("supportedGroups")?.let { array ->
                    buildSet {
                        for (i in 0 until array.length()) add(array.getString(i))
                    }
                }.orEmpty(),
                validationNote = manifest.optString(
                    "validationNote",
                    "Kết quả hình ảnh là ứng viên nhận dạng, không phải kết luận an toàn/thực phẩm/y khoa."
                )
            )
            val mapped = FileInputStream(modelFile).channel.use { channel ->
                channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(manifest.optInt("maxResults", 20).coerceIn(1, 50))
                .setScoreThreshold(manifest.optDouble("scoreThreshold", 0.45).toFloat().coerceIn(0.05f, 0.99f))
                .build()
            detector = ObjectDetector.createFromBufferAndOptions(mapped, options)
            loadedDescriptor = descriptor
        }.onFailure { failure ->
            initializationError = failure.message ?: failure.javaClass.simpleName
            detector = null
            loadedDescriptor = null
        }
    }

    override fun close() {
        runCatching { detector?.close() }
        detector = null
    }

    companion object {
        const val MODEL_FILE = "region-model.tflite"
        const val MANIFEST_FILE = "region-model.manifest.json"
        const val TASK_TYPE_OBJECT_DETECTOR = "OBJECT_DETECTOR"
        const val MODEL_FORMAT_TFLITE_TASK_VISION = "TFLITE_TASK_VISION"

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
    }
}

/**
 * Label convention supported by model metadata:
 *   "Rau má|Centella asiatica"
 * or simply "Centella asiatica" / a common label.
 */
internal data class ParsedModelClassName(
    val displayLabel: String,
    val scientificName: String?
)

internal object ModelClassNameParser {
    fun parse(raw: String): ParsedModelClassName {
        val clean = raw.trim()
        val parts = clean.split('|', limit = 2).map { it.trim() }
        if (parts.size == 2 && parts[0].isNotBlank() && looksScientific(parts[1])) {
            return ParsedModelClassName(parts[0], parts[1])
        }
        return if (looksScientific(clean)) {
            ParsedModelClassName(clean, clean)
        } else {
            ParsedModelClassName(clean.ifBlank { "Không rõ" }, null)
        }
    }

    private fun looksScientific(value: String): Boolean {
        val words = value.trim().split(Regex("\\s+"))
        return words.size >= 2 &&
            words[0].firstOrNull()?.isUpperCase() == true &&
            words[1].firstOrNull()?.isLowerCase() == true &&
            words.take(2).all { token -> token.all { it.isLetter() || it == '-' } }
    }
}

private fun Float.format3(): String = String.format(Locale.ROOT, "%.3f", this)

/** Converts the owned YUV420 frame to an upright RGB bitmap for the Task Vision runtime. */
private fun LiveVisualFrameData.toUprightBitmap(): Bitmap {
    val pixels = IntArray(width * height)
    var out = 0
    for (row in 0 until height) {
        val yRow = row * y.rowStride
        val uvRow = (row / 2) * u.rowStride
        val vvRow = (row / 2) * v.rowStride
        for (col in 0 until width) {
            val yIndex = yRow + col * y.pixelStride
            val uvCol = col / 2
            val uIndex = uvRow + uvCol * u.pixelStride
            val vIndex = vvRow + uvCol * v.pixelStride
            val yy = (y.bytes[yIndex].toInt() and 0xff)
            val uu = (u.bytes[uIndex].toInt() and 0xff) - 128
            val vv = (v.bytes[vIndex].toInt() and 0xff) - 128
            val r = (yy + 1.402f * vv).toInt().coerceIn(0, 255)
            val g = (yy - 0.344136f * uu - 0.714136f * vv).toInt().coerceIn(0, 255)
            val b = (yy + 1.772f * uu).toInt().coerceIn(0, 255)
            pixels[out++] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    val source = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    if (rotationDegrees == 0) return source
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    source.recycle()
    return rotated
}
