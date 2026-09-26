package vn.fieldintel.feature.emergency

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionFrameStabilityTest {
    private fun frame(timestamp: Long, baseLuma: Int, textured: Boolean = true): LiveVisualFrameData {
        val width = 24
        val height = 24
        val y = ByteArray(width * height) { index ->
            val row = index / width
            val col = index % width
            val detail = if (textured) ((row / 2 + col / 2) % 2) * 18 - 9 else 0
            (baseLuma + detail).coerceIn(0, 255).toByte()
        }
        val uv = ByteArray(width * height / 4) { 128.toByte() }
        return LiveVisualFrameData(
            timestampNanos = timestamp,
            width = width,
            height = height,
            rotationDegrees = 0,
            y = YuvPlaneData(y, width, 1),
            u = YuvPlaneData(uv, width / 2, 1),
            v = YuvPlaneData(uv, width / 2, 1)
        )
    }

    @Test
    fun stableCameraAutomaticallyTriggersCapture() {
        val gate = RegionFrameStabilityGate(stableFramesRequired = 3)

        assertFalse(gate.shouldCapture(1_000_000_000L, frame(1_000_000_000L, 90)))
        assertFalse(gate.shouldCapture(1_300_000_000L, frame(1_300_000_000L, 92)))
        assertTrue(gate.shouldCapture(1_600_000_000L, frame(1_600_000_000L, 91)))
    }

    @Test
    fun largeVisualChangeResetsStableSequence() {
        val gate = RegionFrameStabilityGate(stableFramesRequired = 3, maximumMeanDifference = 5f)

        assertFalse(gate.shouldCapture(1_000_000_000L, frame(1_000_000_000L, 70)))
        assertFalse(gate.shouldCapture(1_300_000_000L, frame(1_300_000_000L, 71)))
        assertFalse(gate.shouldCapture(1_600_000_000L, frame(1_600_000_000L, 150)))
        assertFalse(gate.shouldCapture(1_900_000_000L, frame(1_900_000_000L, 151)))
        assertTrue(gate.shouldCapture(2_200_000_000L, frame(2_200_000_000L, 150)))
    }

    @Test
    fun cooldownPreventsPhotoFlooding() {
        val gate = RegionFrameStabilityGate(stableFramesRequired = 2, minimumIntervalNanos = 2_000_000_000L)

        assertFalse(gate.shouldCapture(1_000_000_000L, frame(1_000_000_000L, 90)))
        assertTrue(gate.shouldCapture(1_300_000_000L, frame(1_300_000_000L, 90)))
        assertFalse(gate.shouldCapture(1_600_000_000L, frame(1_600_000_000L, 90)))
        assertFalse(gate.shouldCapture(1_900_000_000L, frame(1_900_000_000L, 90)))
        assertFalse(gate.shouldCapture(3_200_000_000L, frame(3_200_000_000L, 90)))
        assertTrue(gate.shouldCapture(3_500_000_000L, frame(3_500_000_000L, 90)))
    }

    @Test
    fun ineligibleRecognitionDoesNotConsumeCaptureCooldown() {
        val gate = RegionFrameStabilityGate(stableFramesRequired = 2, minimumIntervalNanos = 2_000_000_000L)

        assertFalse(gate.shouldCapture(1_000_000_000L, frame(1_000_000_000L, 90), captureEligible = false))
        assertFalse(gate.shouldCapture(1_300_000_000L, frame(1_300_000_000L, 90), captureEligible = false))
        assertTrue(gate.shouldCapture(1_600_000_000L, frame(1_600_000_000L, 90), captureEligible = true))
    }

    @Test
    fun blurredSceneDoesNotAutoCaptureOrConsumeCooldown() {
        val gate = RegionFrameStabilityGate(stableFramesRequired = 2, minimumIntervalNanos = 2_000_000_000L)

        assertFalse(gate.shouldCapture(1_000_000_000L, frame(1_000_000_000L, 100, textured = false)))
        assertFalse(gate.shouldCapture(1_300_000_000L, frame(1_300_000_000L, 100, textured = false)))
        assertFalse(gate.assess(frame(1_300_000_000L, 100, textured = false)).acceptable)

        assertFalse(gate.shouldCapture(1_600_000_000L, frame(1_600_000_000L, 100)))
        assertTrue(gate.shouldCapture(1_900_000_000L, frame(1_900_000_000L, 100)))
    }

    @Test
    fun poorExposureIsRejected() {
        val gate = RegionFrameStabilityGate(stableFramesRequired = 2)
        assertTrue(gate.assess(frame(1L, 10)).tooDark)
        assertTrue(gate.assess(frame(2L, 245)).tooBright)
        assertFalse(gate.shouldCapture(1_000_000_000L, frame(1_000_000_000L, 10)))
        assertFalse(gate.shouldCapture(1_300_000_000L, frame(1_300_000_000L, 10)))
    }
}
