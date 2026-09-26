package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionInferencePacerTest {
    private fun detection(trackHint: String?): VisualDetection = VisualDetection(
        trackHint = trackHint,
        label = "candidate",
        confidence = 0.8f,
        box = NormalizedBox(0.1f, 0.1f, 0.5f, 0.5f)
    )

    @Test
    fun verifyingCandidatesKeepFastCadence() {
        val interval = RegionInferencePacer.nextIntervalNanos(
            inferenceDurationNanos = 40_000_000L,
            detections = listOf(detection("verifying-1:1/3"))
        )
        assertEquals(250_000_000L, interval)
    }

    @Test
    fun stableCandidatesBackOffModerately() {
        val interval = RegionInferencePacer.nextIntervalNanos(
            inferenceDurationNanos = 40_000_000L,
            detections = listOf(detection("stable-1"))
        )
        assertEquals(350_000_000L, interval)
    }

    @Test
    fun emptySceneUsesLowerDutyCycle() {
        val interval = RegionInferencePacer.nextIntervalNanos(
            inferenceDurationNanos = 40_000_000L,
            detections = emptyList()
        )
        assertEquals(400_000_000L, interval)
    }

    @Test
    fun slowModelIsCappedAtHalfSecond() {
        val interval = RegionInferencePacer.nextIntervalNanos(
            inferenceDurationNanos = 400_000_000L,
            detections = emptyList()
        )
        assertEquals(RegionInferencePacer.MAX_INTERVAL_NANOS, interval)
    }

    @Test
    fun schedulerHonorsInterval() {
        val last = 1_000_000_000L
        assertFalse(RegionInferencePacer.shouldRun(1_200_000_000L, last, 350_000_000L))
        assertTrue(RegionInferencePacer.shouldRun(1_350_000_000L, last, 350_000_000L))
        assertTrue(RegionInferencePacer.shouldRun(900_000_000L, last, 350_000_000L))
    }
}
