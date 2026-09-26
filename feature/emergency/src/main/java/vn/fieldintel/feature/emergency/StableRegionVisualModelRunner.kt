package vn.fieldintel.feature.emergency

import kotlin.math.max
import kotlin.math.min

/**
 * Multi-frame stabilizer for open-region camera scanning.
 *
 * Every current track is emitted so the overlay can show "ĐANG XÁC MINH" while it accumulates
 * repeated observations. A track is marked stable only after stableHitsRequired matching frames.
 * The stabilizer never creates a taxon name; it only filters, tracks and smooths model output.
 */
class RegionDetectionStabilizer(
    private val stableHitsRequired: Int = 3,
    private val maxMissedFrames: Int = 2,
    private val minIoU: Float = 0.20f,
    private val centerTolerance: Float = 0.18f,
    private val confidenceAlpha: Float = 0.45f,
    private val boxAlpha: Float = 0.40f
) {
    init {
        require(stableHitsRequired in 2..8)
        require(maxMissedFrames in 0..8)
        require(minIoU in 0f..1f)
        require(centerTolerance in 0.02f..0.5f)
        require(confidenceAlpha in 0.05f..1f)
        require(boxAlpha in 0.05f..1f)
    }

    private data class Track(
        val id: Long,
        var label: String,
        var scientificName: String?,
        var confidence: Float,
        var box: NormalizedBox,
        var hits: Int,
        var misses: Int
    )

    private val tracks = mutableListOf<Track>()
    private var nextId = 1L

    fun reset() {
        tracks.clear()
        nextId = 1L
    }

    @Synchronized
    fun update(detections: List<VisualDetection>): List<VisualDetection> {
        tracks.forEach { it.misses += 1 }
        val claimed = mutableSetOf<Long>()

        detections.sortedByDescending { it.confidence }.forEach { detection ->
            val best = tracks
                .asSequence()
                .filter { it.id !in claimed && sameIdentity(it, detection) }
                .map { it to matchScore(it.box, detection.box) }
                .filter { (_, score) -> score >= 1f }
                .maxByOrNull { (_, score) -> score }
                ?.first

            if (best == null) {
                val created = Track(
                    id = nextId++,
                    label = detection.label,
                    scientificName = detection.scientificName,
                    confidence = detection.confidence,
                    box = detection.box,
                    hits = 1,
                    misses = 0
                )
                tracks += created
                claimed += created.id
            } else {
                best.label = detection.label
                best.scientificName = detection.scientificName
                best.confidence = ema(best.confidence, detection.confidence, confidenceAlpha)
                best.box = smoothBox(best.box, detection.box, boxAlpha)
                best.hits += 1
                best.misses = 0
                claimed += best.id
            }
        }

        tracks.removeAll { it.misses > maxMissedFrames }

        return tracks
            .asSequence()
            .filter { it.misses == 0 }
            .sortedWith(compareByDescending<Track> { it.hits >= stableHitsRequired }.thenByDescending { it.confidence })
            .map { track ->
                val hint = if (track.hits >= stableHitsRequired) {
                    "stable-${track.id}"
                } else {
                    "verifying-${track.id}:${track.hits}/$stableHitsRequired"
                }
                VisualDetection(
                    trackHint = hint,
                    label = track.label,
                    scientificName = track.scientificName,
                    confidence = track.confidence.coerceIn(0f, 1f),
                    box = track.box
                )
            }
            .toList()
    }

    private fun sameIdentity(track: Track, detection: VisualDetection): Boolean {
        val trackScientific = track.scientificName?.trim().orEmpty()
        val detectionScientific = detection.scientificName?.trim().orEmpty()
        return if (trackScientific.isNotBlank() || detectionScientific.isNotBlank()) {
            trackScientific.isNotBlank() && detectionScientific.isNotBlank() &&
                trackScientific.equals(detectionScientific, ignoreCase = true)
        } else {
            track.label.trim().equals(detection.label.trim(), ignoreCase = true)
        }
    }

    /** Score >= 1 means the boxes are spatially compatible. */
    private fun matchScore(a: NormalizedBox, b: NormalizedBox): Float {
        val overlap = iou(a, b)
        val dx = a.centerX - b.centerX
        val dy = a.centerY - b.centerY
        val centerDistanceSquared = dx * dx + dy * dy
        val closeCenter = centerDistanceSquared <= centerTolerance * centerTolerance
        if (overlap < minIoU && !closeCenter) return 0f
        return 1f + overlap - centerDistanceSquared.coerceAtMost(1f)
    }

    private fun iou(a: NormalizedBox, b: NormalizedBox): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        if (intersection <= 0f) return 0f
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun ema(previous: Float, current: Float, alpha: Float): Float =
        previous * (1f - alpha) + current * alpha

    private fun smoothBox(previous: NormalizedBox, current: NormalizedBox, alpha: Float): NormalizedBox {
        fun blend(a: Float, b: Float) = (a * (1f - alpha) + b * alpha).coerceIn(0f, 1f)
        val left = blend(previous.left, current.left)
        val top = blend(previous.top, current.top)
        val right = blend(previous.right, current.right).coerceAtLeast(left + 0.0001f).coerceAtMost(1f)
        val bottom = blend(previous.bottom, current.bottom).coerceAtLeast(top + 0.0001f).coerceAtMost(1f)
        return NormalizedBox(left, top, right, bottom)
    }
}

fun VisualDetection.isStableRegionCandidate(): Boolean = trackHint?.startsWith("stable-") == true

fun VisualDetection.regionVerificationProgress(): Pair<Int, Int>? {
    val hint = trackHint ?: return null
    if (!hint.startsWith("verifying-")) return null
    val progress = hint.substringAfter(':', missingDelimiterValue = "")
    if (progress.isBlank()) return null
    val parts = progress.split('/', limit = 2)
    if (parts.size != 2) return null
    val hits = parts[0].toIntOrNull() ?: return null
    val required = parts[1].toIntOrNull() ?: return null
    return hits to required
}

/** Decorates any installed visual runner with multi-frame stabilization for region scanning. */
class StableRegionVisualModelRunner(
    private val delegate: LiveVisualModelRunner,
    private val stabilizer: RegionDetectionStabilizer = RegionDetectionStabilizer()
) : LiveVisualModelRunner, AutoCloseable {
    override fun status(): VisualModelStatus = delegate.status()

    override fun detect(
        frame: LiveVisualFrameData,
        target: LiveVisualSearchTarget
    ): List<VisualDetection> = delegate.detect(frame, target)

    override fun scanRegion(frame: LiveVisualFrameData): List<VisualDetection> =
        stabilizer.update(delegate.scanRegion(frame))

    fun reset() = stabilizer.reset()

    override fun close() {
        stabilizer.reset()
        (delegate as? AutoCloseable)?.close()
    }
}
