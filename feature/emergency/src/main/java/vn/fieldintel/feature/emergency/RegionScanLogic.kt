package vn.fieldintel.feature.emergency

import java.util.Locale
import kotlin.math.floor

data class RegionClassifiedItem(
    val key: String,
    val label: String,
    val scientificName: String?,
    val bestConfidence: Float,
    val instances: Int
)

data class RegionScanSummary(
    val items: List<RegionClassifiedItem>,
    val totalObjects: Int,
    val strongObjects: Int
)

/** Groups detector output into the distinct labels currently visible in the camera region. */
object RegionScanClassifier {
    private const val STRONG_CONFIDENCE = 0.50f

    fun summarize(detections: List<VisualDetection>): RegionScanSummary {
        val grouped = detections.groupBy { detection -> identityKey(detection) }
        val items = grouped.map { (key, group) ->
            val best = group.maxBy { it.confidence }
            RegionClassifiedItem(
                key = key,
                label = best.label,
                scientificName = best.scientificName,
                bestConfidence = best.confidence,
                instances = group.size
            )
        }.sortedByDescending { it.bestConfidence }
        return RegionScanSummary(
            items = items,
            totalObjects = detections.size,
            strongObjects = detections.count { it.confidence >= STRONG_CONFIDENCE }
        )
    }

    internal fun identityKey(detection: VisualDetection): String = detection.scientificName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.lowercase(Locale.ROOT)
        ?: detection.label.trim().lowercase(Locale.ROOT)
}

/** Compact scene fingerprint used only to decide whether a photo should be auto-captured. */
data class RegionSceneSignature(
    val classCounts: Map<String, Int>,
    val spatialCells: Set<String>
) {
    val isEmpty: Boolean get() = classCounts.isEmpty()
}

object RegionSceneSignatureBuilder {
    private const val MIN_CONFIDENCE = 0.50f
    private const val GRID_SIZE = 4

    fun build(detections: List<VisualDetection>): RegionSceneSignature {
        val accepted = detections.filter { it.confidence >= MIN_CONFIDENCE }
        val counts = accepted
            .groupingBy(RegionScanClassifier::identityKey)
            .eachCount()
            .toSortedMap()
        val cells = accepted.map { detection ->
            val x = floor(detection.box.centerX * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            val y = floor(detection.box.centerY * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            "${RegionScanClassifier.identityKey(detection)}@$x,$y"
        }.toSet()
        return RegionSceneSignature(counts, cells)
    }
}

/**
 * Controls automatic evidence snapshots. A photo is taken only when both class composition and the
 * coarse object layout remain stable for several inference frames. This prevents a quick camera pan
 * from being treated as a stable scene merely because the same labels are still visible.
 */
class RegionAutoCaptureGate(
    private val stableFramesRequired: Int = 3,
    private val minimumIntervalNanos: Long = 2_000_000_000L
) {
    init {
        require(stableFramesRequired >= 2)
        require(minimumIntervalNanos >= 500_000_000L)
    }

    private var previousSignature = RegionSceneSignature(emptyMap(), emptySet())
    private var stableFrames = 0
    private var lastCaptureTimestamp = Long.MIN_VALUE

    fun reset() {
        previousSignature = RegionSceneSignature(emptyMap(), emptySet())
        stableFrames = 0
        lastCaptureTimestamp = Long.MIN_VALUE
    }

    fun shouldCapture(timestampNanos: Long, detections: List<VisualDetection>): Boolean {
        val signature = RegionSceneSignatureBuilder.build(detections)

        if (signature.isEmpty) {
            previousSignature = RegionSceneSignature(emptyMap(), emptySet())
            stableFrames = 0
            return false
        }

        stableFrames = if (signature == previousSignature) stableFrames + 1 else 1
        previousSignature = signature

        val intervalOk = lastCaptureTimestamp == Long.MIN_VALUE ||
            timestampNanos - lastCaptureTimestamp >= minimumIntervalNanos
        if (stableFrames >= stableFramesRequired && intervalOk) {
            lastCaptureTimestamp = timestampNanos
            stableFrames = 0
            return true
        }
        return false
    }
}
