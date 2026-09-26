package vn.fieldintel.feature.emergency

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import org.json.JSONObject
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Schema-v2 visual runner: a coarse object detector proposes regions, then a separate image
 * classifier names each crop. This matches field scanning where several taxa can appear in one view.
 */
class TwoStageTfliteRegionModelRunner(context: Context) : LiveVisualModelRunner, AutoCloseable {
    private val appContext = context.applicationContext
    private val packageDir = File(appContext.filesDir, "visual-model")
    private val detectorFile = File(packageDir, DETECTOR_FILE)
    private val classifierFile = File(packageDir, CLASSIFIER_FILE)
    private val manifestFile = File(packageDir, MANIFEST_FILE)

    private var proposer: TfliteObjectProposalRunner? = null
    private var cropClassifier: TfliteCropClassifier? = null
    private var coordinator: TwoStageRegionScanCoordinator? = null
    private var descriptor: VisualModelDescriptor? = null
    private var initializationError: String? = null

    override fun status(): VisualModelStatus {
        if (!detectorFile.isFile || !classifierFile.isFile || !manifestFile.isFile) {
            return VisualModelStatus(
                VisualModelAvailability.NOT_INSTALLED,
                message = "Chưa cài đủ detector + classifier cho quét vùng hai tầng."
            )
        }
        ensureLoaded()
        return if (coordinator != null && descriptor != null) {
            VisualModelStatus(
                VisualModelAvailability.READY,
                descriptor,
                "Model hai tầng đã xác minh SHA-256: detector vùng + classifier từng vùng, chạy offline."
            )
        } else {
            VisualModelStatus(
                VisualModelAvailability.INVALID,
                message = initializationError ?: "Gói model hai tầng không hợp lệ."
            )
        }
    }

    override fun detect(frame: LiveVisualFrameData, target: LiveVisualSearchTarget): List<VisualDetection> =
        scanRegion(frame).filter { LiveVisualTargetMatcher.matches(target, it) }

    override fun scanRegion(frame: LiveVisualFrameData): List<VisualDetection> {
        ensureLoaded()
        val active = coordinator ?: return emptyList()
        val proposalRunner = proposer ?: return emptyList()
        return try {
            active.scan(frame)
        } finally {
            proposalRunner.releaseFrame()
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (coordinator != null || initializationError != null) return
        runCatching {
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optInt("schemaVersion", 0) == 2) { "Model manifest schema v2 bắt buộc cho quét hai tầng" }
            require(manifest.optString("taskType") == TASK_TYPE_TWO_STAGE) { "taskType phải là TWO_STAGE_REGION_CLASSIFIER" }
            require(manifest.optString("modelFormat") == TfliteRegionModelRunner.MODEL_FORMAT_TFLITE_TASK_VISION) {
                "Định dạng model không được hỗ trợ"
            }
            require(!manifest.optBoolean("speciesSafetyClaims", false)) {
                "Model không được gắn kết luận ăn được/độc tính/y khoa"
            }

            val detectorMeta = manifest.getJSONObject("detector")
            val classifierMeta = manifest.getJSONObject("classifier")
            verifyComponent(detectorFile, detectorMeta, DETECTOR_FILE)
            verifyComponent(classifierFile, classifierMeta, CLASSIFIER_FILE)

            val detector = TfliteObjectProposalRunner(
                detectorFile = detectorFile,
                maxResults = detectorMeta.optInt("maxResults", 24).coerceIn(1, 50),
                scoreThreshold = detectorMeta.optDouble("scoreThreshold", 0.25).toFloat().coerceIn(0.05f, 0.99f)
            )
            val classifier = TfliteCropClassifier(
                classifierFile = classifierFile,
                bitmapProvider = detector::currentFrameBitmap,
                maxResults = classifierMeta.optInt("maxResults", 3).coerceIn(1, 10),
                scoreThreshold = classifierMeta.optDouble("scoreThreshold", 0.35).toFloat().coerceIn(0.05f, 0.99f)
            )
            val activeCoordinator = TwoStageRegionScanCoordinator(
                proposer = detector,
                classifier = classifier,
                proposalThreshold = manifest.optDouble("proposalThreshold", 0.30).toFloat().coerceIn(0.05f, 0.99f),
                classificationThreshold = manifest.optDouble("classificationThreshold", 0.45).toFloat().coerceIn(0.05f, 0.99f),
                maxRegions = manifest.optInt("maxRegions", 12).coerceIn(1, 32),
                maxCandidatesPerRegion = manifest.optInt("maxCandidatesPerRegion", 1).coerceIn(1, 5)
            )

            proposer = detector
            cropClassifier = classifier
            coordinator = activeCoordinator
            descriptor = VisualModelDescriptor(
                id = manifest.getString("id"),
                version = manifest.getString("version"),
                sourceName = manifest.getString("sourceName"),
                license = manifest.getString("license"),
                sha256 = "${detectorMeta.getString("sha256")}:${classifierMeta.getString("sha256")}",
                supportedGroups = manifest.optJSONArray("supportedGroups")?.let { array ->
                    buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
                }.orEmpty(),
                validationNote = manifest.optString(
                    "validationNote",
                    "Kết quả camera là ứng viên nhận dạng, không phải kết luận an toàn/thực phẩm/y khoa."
                )
            )
        }.onFailure { failure ->
            initializationError = failure.message ?: failure.javaClass.simpleName
            close()
        }
    }

    private fun verifyComponent(file: File, meta: JSONObject, expectedName: String) {
        require(meta.optString("file") == expectedName) { "Tên file model không khớp manifest: $expectedName" }
        require(meta.optLong("sizeBytes", -1L) == file.length()) { "Kích thước $expectedName không khớp manifest" }
        val expectedSha = meta.getString("sha256").lowercase(Locale.ROOT)
        require(expectedSha.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 $expectedName không hợp lệ" }
        require(sha256(file) == expectedSha) { "SHA-256 $expectedName không khớp manifest" }
    }

    override fun close() {
        proposer?.close()
        cropClassifier?.close()
        proposer = null
        cropClassifier = null
        coordinator = null
    }

    companion object {
        const val DETECTOR_FILE = "region-detector.tflite"
        const val CLASSIFIER_FILE = "region-classifier.tflite"
        const val MANIFEST_FILE = "region-model.manifest.json"
        const val TASK_TYPE_TWO_STAGE = "TWO_STAGE_REGION_CLASSIFIER"

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

/** Stage-1 adapter: detector output is deliberately stripped of species identity. */
internal class TfliteObjectProposalRunner(
    detectorFile: File,
    maxResults: Int,
    scoreThreshold: Float
) : RegionProposalRunner, AutoCloseable {
    private val detector: ObjectDetector
    private var frameTimestamp: Long = Long.MIN_VALUE
    private var frameBitmap: Bitmap? = null

    init {
        val mapped = FileInputStream(detectorFile).channel.use { channel ->
            channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(maxResults)
            .setScoreThreshold(scoreThreshold)
            .build()
        detector = ObjectDetector.createFromBufferAndOptions(mapped, options)
    }

    override fun propose(frame: LiveVisualFrameData): List<RegionProposal> {
        releaseFrame()
        val bitmap = frame.toTwoStageUprightBitmap()
        frameBitmap = bitmap
        frameTimestamp = frame.timestampNanos
        val width = bitmap.width.toFloat().coerceAtLeast(1f)
        val height = bitmap.height.toFloat().coerceAtLeast(1f)
        return detector.detect(TensorImage.fromBitmap(bitmap)).mapIndexedNotNull { index, detection ->
            val box = detection.boundingBox
            val left = (box.left / width).coerceIn(0f, 1f)
            val top = (box.top / height).coerceIn(0f, 1f)
            val right = (box.right / width).coerceIn(0f, 1f)
            val bottom = (box.bottom / height).coerceIn(0f, 1f)
            if (right <= left || bottom <= top) return@mapIndexedNotNull null
            val confidence = detection.categories.maxOfOrNull { it.score }?.coerceIn(0f, 1f) ?: 0f
            RegionProposal(
                id = "r${frame.timestampNanos}-$index",
                confidence = confidence,
                box = NormalizedBox(left, top, right, bottom)
            )
        }
    }

    fun currentFrameBitmap(timestampNanos: Long): Bitmap? =
        frameBitmap?.takeIf { frameTimestamp == timestampNanos && !it.isRecycled }

    fun releaseFrame() {
        frameBitmap?.takeIf { !it.isRecycled }?.recycle()
        frameBitmap = null
        frameTimestamp = Long.MIN_VALUE
    }

    override fun close() {
        releaseFrame()
        runCatching { detector.close() }
    }
}

/** Stage-2 adapter: classify one detector crop with a metadata-enabled TFLite image classifier. */
internal class TfliteCropClassifier(
    classifierFile: File,
    private val bitmapProvider: (Long) -> Bitmap?,
    maxResults: Int,
    scoreThreshold: Float
) : RegionCropClassifier, AutoCloseable {
    private val classifier: ImageClassifier

    init {
        val mapped = FileInputStream(classifierFile).channel.use { channel ->
            channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setMaxResults(maxResults)
            .setScoreThreshold(scoreThreshold)
            .build()
        classifier = ImageClassifier.createFromBufferAndOptions(mapped, options)
    }

    override fun classify(frame: LiveVisualFrameData, proposal: RegionProposal): List<RegionClassification> {
        val full = bitmapProvider(frame.timestampNanos) ?: return emptyList()
        val crop = crop(full, proposal.box)
        return try {
            classifier.classify(TensorImage.fromBitmap(crop))
                .flatMap { it.categories }
                .filter { category ->
                    val label = category.label.trim()
                    label.isNotEmpty() &&
                        !label.equals("background", ignoreCase = true) &&
                        !label.equals("unknown", ignoreCase = true) &&
                        !label.matches(Regex("class-\\d+", RegexOption.IGNORE_CASE))
                }
                .map { category ->
                    val raw = category.label.trim()
                    val mapped = ModelClassNameParser.parse(raw)
                    RegionClassification(
                        label = mapped.displayLabel,
                        scientificName = mapped.scientificName,
                        confidence = category.score.coerceIn(0f, 1f)
                    )
                }
                .sortedByDescending { it.confidence }
        } finally {
            crop.recycle()
        }
    }

    private fun crop(source: Bitmap, box: NormalizedBox): Bitmap {
        val left = floor(box.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = floor(box.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = ceil(box.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = ceil(box.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    override fun close() {
        runCatching { classifier.close() }
    }
}

/** Chooses schema-v1 single detector or schema-v2 two-stage runner from the installed manifest. */
class InstalledVisualModelRunner(context: Context) : LiveVisualModelRunner, AutoCloseable {
    private val appContext = context.applicationContext
    private val delegate: LiveVisualModelRunner

    init {
        val manifest = File(appContext.filesDir, "visual-model/${TfliteRegionModelRunner.MANIFEST_FILE}")
        val schema = runCatching {
            if (manifest.isFile) JSONObject(manifest.readText(Charsets.UTF_8)).optInt("schemaVersion", 1) else 1
        }.getOrDefault(1)
        delegate = if (schema == 2) TwoStageTfliteRegionModelRunner(appContext) else TfliteRegionModelRunner(appContext)
    }

    override fun status(): VisualModelStatus = delegate.status()
    override fun detect(frame: LiveVisualFrameData, target: LiveVisualSearchTarget): List<VisualDetection> = delegate.detect(frame, target)
    override fun scanRegion(frame: LiveVisualFrameData): List<VisualDetection> = delegate.scanRegion(frame)
    override fun close() { (delegate as? AutoCloseable)?.close() }
}

private fun LiveVisualFrameData.toTwoStageUprightBitmap(): Bitmap {
    val pixels = IntArray(width * height)
    var out = 0
    for (row in 0 until height) {
        val yRow = row * y.rowStride
        val uRow = (row / 2) * u.rowStride
        val vRow = (row / 2) * v.rowStride
        for (col in 0 until width) {
            val yIndex = yRow + col * y.pixelStride
            val uvCol = col / 2
            val uIndex = uRow + uvCol * u.pixelStride
            val vIndex = vRow + uvCol * v.pixelStride
            val yy = y.bytes[yIndex].toInt() and 0xff
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
