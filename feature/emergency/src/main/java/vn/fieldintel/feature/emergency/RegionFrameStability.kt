package vn.fieldintel.feature.emergency

import kotlin.math.abs

/**
 * Detects when the camera view is sufficiently still to take an automatic evidence photo.
 * It samples only luminance values and never performs species recognition.
 */
class RegionFrameStabilityGate(
    private val stableFramesRequired: Int = 3,
    private val maximumMeanDifference: Float = 8.0f,
    private val minimumIntervalNanos: Long = 2_000_000_000L,
    private val sampleGrid: Int = 12
) {
    init {
        require(stableFramesRequired >= 2)
        require(maximumMeanDifference > 0f)
        require(minimumIntervalNanos >= 500_000_000L)
        require(sampleGrid in 4..32)
    }

    private var previous: IntArray? = null
    private var stableFrames = 0
    private var lastCaptureTimestamp = Long.MIN_VALUE

    fun reset() {
        previous = null
        stableFrames = 0
        lastCaptureTimestamp = Long.MIN_VALUE
    }

    /**
     * Always observes scene motion. captureEligible=false prevents the recognition pipeline from
     * consuming the cooldown before a stable taxon candidate exists.
     */
    fun shouldCapture(
        timestampNanos: Long,
        frame: LiveVisualFrameData,
        captureEligible: Boolean = true
    ): Boolean {
        val signature = sampleLuma(frame)
        val prior = previous
        previous = signature

        if (prior == null) {
            stableFrames = 1
            return false
        }

        val difference = meanAbsoluteDifference(prior, signature)
        stableFrames = if (difference <= maximumMeanDifference) stableFrames + 1 else 1

        val intervalOk = lastCaptureTimestamp == Long.MIN_VALUE ||
            timestampNanos - lastCaptureTimestamp >= minimumIntervalNanos
        if (stableFrames >= stableFramesRequired && intervalOk && captureEligible) {
            lastCaptureTimestamp = timestampNanos
            stableFrames = 0
            return true
        }
        if (stableFrames > stableFramesRequired) stableFrames = stableFramesRequired
        return false
    }

    internal fun sampleLuma(frame: LiveVisualFrameData): IntArray {
        val plane = frame.y
        val result = IntArray(sampleGrid * sampleGrid)
        var out = 0
        for (gy in 0 until sampleGrid) {
            val y = ((gy + 0.5f) * frame.height / sampleGrid).toInt().coerceIn(0, frame.height - 1)
            for (gx in 0 until sampleGrid) {
                val x = ((gx + 0.5f) * frame.width / sampleGrid).toInt().coerceIn(0, frame.width - 1)
                val index = y * plane.rowStride + x * plane.pixelStride
                result[out++] = if (index in plane.bytes.indices) plane.bytes[index].toInt() and 0xFF else 0
            }
        }
        return result
    }

    private fun meanAbsoluteDifference(a: IntArray, b: IntArray): Float {
        require(a.size == b.size)
        if (a.isEmpty()) return Float.MAX_VALUE
        var total = 0L
        for (i in a.indices) total += abs(a[i] - b[i])
        return total.toFloat() / a.size.toFloat()
    }
}
