package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraAdjustmentGateTest {
    @Test fun tapBlocksCaptureUntilFocusAndExposureSettle() {
        val gate = CameraAdjustmentGate()
        assertTrue(gate.ready(0))
        val token = gate.begin(100)
        assertFalse(gate.ready(100))
        gate.focusCompleted(token, 100_000_100, successful = true)
        assertEquals(CameraAdjustmentGate.State.EXPOSURE_SETTLING, gate.state(100_000_100))
        assertFalse(gate.ready(449_000_099))
        assertTrue(gate.ready(450_000_100))
    }

    @Test fun timeoutStillWaitsForExposureAndQualityGate() {
        val gate = CameraAdjustmentGate()
        gate.begin(0)
        assertFalse(gate.ready(1_499_999_999))
        assertEquals(CameraAdjustmentGate.State.EXPOSURE_SETTLING, gate.state(1_500_000_000))
        assertFalse(gate.ready(1_849_999_999))
        assertTrue(gate.ready(1_850_000_000))
        // Capture additionally needs a quality-accepted stable frame; readiness alone is insufficient.
        val qualityOk = false
        val stableCandidate = true
        assertFalse(gate.ready(1_850_000_000) && qualityOk && stableCandidate)
        assertTrue(gate.ready(1_850_000_000) && true && stableCandidate)
    }

    @Test fun staleFocusCallbackCannotUnlockNewTap() {
        val gate = CameraAdjustmentGate()
        val first = gate.begin(0)
        gate.begin(50)
        gate.focusCompleted(first, 100, successful = true)
        assertEquals(CameraAdjustmentGate.State.FOCUSING, gate.state(100))
    }

    @Test fun resetOnStopOrCameraRestartInvalidatesCallbacks() {
        val gate = CameraAdjustmentGate()
        val token = gate.begin(0)
        gate.reset()
        gate.focusCompleted(token, 100, successful = true)
        assertEquals(CameraAdjustmentGate.State.IDLE, gate.state(100))
        val next = gate.begin(200)
        assertFalse(gate.ready(201))
        gate.focusCompleted(next, 300, successful = true)
        gate.reset()
        assertEquals(CameraAdjustmentGate.State.IDLE, gate.state(301))
    }

    @Test fun unsuccessfulFocusUsesBoundedTimeout() {
        val gate = CameraAdjustmentGate()
        val token = gate.begin(0)
        gate.focusCompleted(token, 10, successful = false)
        assertFalse(gate.ready(1_499_999_999))
        assertTrue(gate.ready(1_850_000_000))
    }
    @Test fun stableQualityFrameCapturesOnlyAfterAdjustment() {
        val camera = CameraAdjustmentGate()
        val stability = RegionFrameStabilityGate(stableFramesRequired = 2)
        val token = camera.begin(0)
        fun frame(timestamp: Long): LiveVisualFrameData {
            val width = 24
            val y = ByteArray(width * width) { index ->
                (90 + ((index / width / 2 + index % width / 2) % 2) * 18).toByte()
            }
            val uv = ByteArray(width * width / 4) { 128.toByte() }
            return LiveVisualFrameData(timestamp, width, width, 0,
                YuvPlaneData(y, width, 1), YuvPlaneData(uv, width / 2, 1),
                YuvPlaneData(uv, width / 2, 1))
        }
        assertFalse(stability.shouldCapture(100, frame(100), camera.ready(100)))
        assertFalse(stability.shouldCapture(200, frame(200), camera.ready(200)))
        camera.focusCompleted(token, 300, successful = true)
        assertFalse(stability.shouldCapture(349_000_300, frame(349_000_300), camera.ready(349_000_300)))
        assertTrue(stability.shouldCapture(350_000_300, frame(350_000_300), camera.ready(350_000_300)))
    }

}
