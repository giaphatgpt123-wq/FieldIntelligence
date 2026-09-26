package vn.fieldintel.feature.emergency

import kotlin.math.abs
import kotlin.math.sqrt

data class RegionFrameQuality(
    val meanLuma: Float,
    val contrast: Float,
    val edgeStrength: Float,
    val tooDark: Boolean,
    val tooBright: Boolean,
    val tooBlurred: Boolean
) {
    val acceptable: Boolean get() = !tooDark && !tooBright && !tooBlurred

    fun guidance(): String = when {
        tooDark -> "Cảnh quá tối. Bật đèn hỗ trợ hoặc đưa mẫu ra vùng sáng hơn."
        tooBright -> "Cảnh quá sáng. Tránh nguồn sáng trực tiếp và lấy nét lại vào mẫu."
        tooBlurred -> "Ảnh chưa đủ nét. Giữ máy chắc, chạm lấy nét hoặc tiến gần mẫu hơn."
        else -> "Độ sáng và độ nét đủ điều kiện auto-capture."
    }
}

/**
 * Detects when the camera view is sufficiently still and sufficiently sharp to take an automatic
 * evidence photo. It samples only luminance values and never performs species recognition.
 */
class RegionFrameStabilityGate(
    private val stableFramesRequired: Int = 3,
    private val maximumMeanDifference: Float = 8.0f,
    private val minimumIntervalNanos: Long = 2_000_000_000L,
    private val sampleGrid: Int = 12,
    private val minimumMeanLuma: Float = 28f,
    private val maximumMeanLuma: Float = 232f,
    private val minimumEdgeStrength: Float = 5.0f
) {
    init {
        require(stableFramesRequired >= 2)
        require(maximumMeanDifference > 0f)
        require(minimumIntervalNanos >= 500_000_000L)
        require(sampleGrid in 4..32)
        require(minimumMeanLuma in 0f..254f)
        require(maximumMeanLuma in (minimumMeanLuma + 1f)..255f)
        require(minimumEdgeStrength >= 0f)
    }

    private var previous: IntArray? = null
    private var stableFrames = 0
    private var lastCaptureTimestamp = Long.MIN_VALUE

    fun reset() {
        previous = null
        stableFrames = 0
        lastCaptureTimestamp = Long.MIN_VALUE
    }

    fun assess(frame: LiveVisualFrameData): RegionFrameQuality = assess(sampleLuma(frame))

    /**
     * Always observes scene motion. captureEligible=false prevents the recognition pipeline from
     * consuming the cooldown before a stable taxon candidate exists. Poorly exposed or low-detail
     * frames never consume the cooldown either.
     */
    fun shouldCapture(
        timestampNanos: Long,
        frame: LiveVisualFrameData,
        captureEligible: Boolean = true
    ): Boolean {
        val signature = sampleLuma(frame)
        val quality = assess(signature)
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
        if (
            stableFrames >= stableFramesRequired && intervalOk && captureEligible && quality.acceptable
        ) {
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

    internal fun assess(samples: IntArray): RegionFrameQuality {
        if (samples.isEmpty()) {
            return RegionFrameQuality(0f, 0f, 0f, tooDark = true, tooBright = false, tooBlurred = true)
        }
        val mean = samples.average().toFloat()
        var variance = 0.0
        samples.forEach { value ->
            val delta = value - mean
            variance += delta * delta
        }
        variance /= samples.size.toDouble()
        val contrast = sqrt(variance).toFloat()

        var edgeTotal = 0L
        var edgeCount = 0
        for (y in 0 until sampleGrid) {
            for (x in 0 until sampleGrid) {
                val index = y * sampleGrid + x
                if (x + 1 < sampleGrid) {
                    edgeTotal += abs(samples[index] - samples[index + 1])
                    edgeCount += 1
                }
                if (y + 1 < sampleGrid) {
                    edgeTotal += abs(samples[index] - samples[index + sampleGrid])
                    edgeCount += 1
                }
            }
        }
        val edgeStrength = if (edgeCount == 0) 0f else edgeTotal.toFloat() / edgeCount.toFloat()
        return RegionFrameQuality(
            meanLuma = mean,
            contrast = contrast,
            edgeStrength = edgeStrength,
            tooDark = mean < minimumMeanLuma,
            tooBright = mean > maximumMeanLuma,
            tooBlurred = edgeStrength < minimumEdgeStrength
        )
    }

    private fun meanAbsoluteDifference(a: IntArray, b: IntArray): Float {
        require(a.size == b.size)
        if (a.isEmpty()) return Float.MAX_VALUE
        var total = 0L
        for (i in a.indices) total += abs(a[i] - b[i])
        return total.toFloat() / a.size.toFloat()
    }
}
