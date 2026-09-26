package vn.fieldintel.feature.emergency

import java.util.Locale

data class RegionClassifiedItem(
    val key: String,
    val label: String,
    val scientificName: String?,
    val bestConfidence: Float,
    val instances: Int
)

data class RegionScanSummary(
    val items: List<RegionClassifiedItem>,
    val totalObjects: Int
)

/** Groups detector output into the distinct labels currently visible in the camera region. */
object RegionScanClassifier {
    fun summarize(detections: List<VisualDetection>): RegionScanSummary {
        val grouped = detections.groupBy { detection ->
            detection.scientificName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.lowercase(Locale.ROOT)
                ?: detection.label.trim().lowercase(Locale.ROOT)
        }
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
        return RegionScanSummary(items = items, totalObjects = detections.size)
    }
}

/**
 * Controls automatic evidence snapshots. It requires the visible composition to remain similar for
 * several inference frames and enforces a cooldown so scanning does not flood local storage.
 */
class RegionAutoCaptureGate(
    private val stableFramesRequired: Int = 3,
    private val minimumIntervalNanos: Long = 2_000_000_000L
) {
    init {
        require(stableFramesRequired >= 2)
        require(minimumIntervalNanos >= 500_000_000L)
    }

    private var previousSignature: Set<String> = emptySet()
    private var stableFrames = 0
    private var lastCaptureTimestamp = Long.MIN_VALUE

    fun reset() {
        previousSignature = emptySet()
        stableFrames = 0
        lastCaptureTimestamp = Long.MIN_VALUE
    }

    fun shouldCapture(timestampNanos: Long, detections: List<VisualDetection>): Boolean {
        val signature = detections
            .asSequence()
            .filter { it.confidence >= 0.50f }
            .map {
                it.scientificName?.trim()?.lowercase(Locale.ROOT)
                    ?: it.label.trim().lowercase(Locale.ROOT)
            }
            .filter { it.isNotBlank() }
            .toSet()

        if (signature.isEmpty()) {
            previousSignature = emptySet()
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
