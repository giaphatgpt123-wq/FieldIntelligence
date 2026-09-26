package vn.fieldintel.feature.emergency

/** Normalized camera-space bounding box in [0, 1]. */
data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class YuvPlaneData(
    val bytes: ByteArray,
    val rowStride: Int,
    val pixelStride: Int
) {
    init {
        require(rowStride > 0)
        require(pixelStride > 0)
    }
}

data class LiveVisualFrameData(
    val timestampNanos: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val y: YuvPlaneData,
    val u: YuvPlaneData,
    val v: YuvPlaneData
) {
    init {
        require(width > 0 && height > 0)
        require(rotationDegrees in setOf(0, 90, 180, 270))
    }
}

data class VisualDetection(
    val trackHint: String? = null,
    val label: String,
    val scientificName: String? = null,
    val confidence: Float,
    val box: NormalizedBox
) {
    init { require(confidence in 0f..1f) }
}

data class VisualModelDescriptor(
    val id: String,
    val version: String,
    val sourceName: String,
    val license: String,
    val sha256: String,
    val supportedGroups: Set<String>,
    val validationNote: String
)

enum class VisualModelAvailability {
    NOT_INSTALLED,
    INVALID,
    READY
}

data class VisualModelStatus(
    val availability: VisualModelAvailability,
    val descriptor: VisualModelDescriptor? = null,
    val message: String
)

/**
 * Boundary for a verified on-device detector/retrieval model.
 * Implementations must never attach edibility, toxicity or treatment conclusions to detections.
 */
interface LiveVisualModelRunner {
    fun status(): VisualModelStatus

    /** Runs inference on an owned copy of a YUV_420_888 camera frame. */
    fun detect(
        frame: LiveVisualFrameData,
        target: LiveVisualSearchTarget
    ): List<VisualDetection>
}

object NoVerifiedLiveVisualModel : LiveVisualModelRunner {
    override fun status(): VisualModelStatus = VisualModelStatus(
        availability = VisualModelAvailability.NOT_INSTALLED,
        message = "Chưa cài mô hình nhận dạng thực địa đã được kiểm chứng."
    )

    override fun detect(
        frame: LiveVisualFrameData,
        target: LiveVisualSearchTarget
    ): List<VisualDetection> = emptyList()
}

/**
 * Small target matcher used after model inference. It is intentionally strict and does not turn a
 * weak visual resemblance into an identification claim.
 */
object LiveVisualTargetMatcher {
    fun matches(target: LiveVisualSearchTarget, detection: VisualDetection): Boolean {
        val q = normalize(target.normalizedQuery)
        if (q.length < 2) return false
        val label = normalize(detection.label)
        val scientific = normalize(detection.scientificName.orEmpty())
        val requestedScientific = normalize(target.scientificName.orEmpty())

        return when {
            requestedScientific.isNotBlank() -> scientific.isNotBlank() && requestedScientific == scientific
            q == label -> true
            q == scientific -> true
            else -> false
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}

/**
 * Frame-to-frame stabilizer. A candidate must stay spatially close and keep the same taxon label
 * before the UI may call it a stable candidate.
 */
class LiveVisualTemporalTracker(
    private val stableFramesRequired: Int = 4,
    private val maxCenterDrift: Float = 0.16f
) {
    init {
        require(stableFramesRequired >= 2)
        require(maxCenterDrift in 0.01f..0.5f)
    }

    private var previous: VisualDetection? = null
    private var stableFrames: Int = 0

    fun reset() {
        previous = null
        stableFrames = 0
    }

    fun observe(target: LiveVisualSearchTarget, detections: List<VisualDetection>): LiveVisualCandidate? {
        val best = detections
            .asSequence()
            .filter { LiveVisualTargetMatcher.matches(target, it) }
            .maxByOrNull { it.confidence }
            ?: run {
                reset()
                return null
            }

        val prior = previous
        stableFrames = if (prior != null && sameIdentity(prior, best) && closeEnough(prior.box, best.box)) {
            stableFrames + 1
        } else {
            1
        }
        previous = best

        return LiveVisualCandidate(
            label = best.label,
            scientificName = best.scientificName,
            score = best.confidence,
            stableFrames = stableFrames,
            evidenceReady = false
        )
    }

    fun isStable(): Boolean = stableFrames >= stableFramesRequired

    private fun sameIdentity(a: VisualDetection, b: VisualDetection): Boolean =
        a.scientificName?.takeIf { it.isNotBlank() }?.equals(b.scientificName, ignoreCase = true)
            ?: a.label.equals(b.label, ignoreCase = true)

    private fun closeEnough(a: NormalizedBox, b: NormalizedBox): Boolean {
        val dx = a.centerX - b.centerX
        val dy = a.centerY - b.centerY
        return (dx * dx + dy * dy) <= maxCenterDrift * maxCenterDrift
    }
}
