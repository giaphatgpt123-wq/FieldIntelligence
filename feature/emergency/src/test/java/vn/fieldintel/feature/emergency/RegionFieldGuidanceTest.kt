package vn.fieldintel.feature.emergency

import org.junit.Assert.assertTrue
import org.junit.Test

class RegionFieldGuidanceTest {
    private fun detection(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        trackHint: String? = null
    ) = VisualDetection(
        trackHint = trackHint,
        label = "Centella asiatica",
        scientificName = "Centella asiatica",
        confidence = 0.84f,
        box = NormalizedBox(left, top, right, bottom)
    )

    @Test
    fun asksUserToApproachWhenObjectsAreTiny() {
        val text = regionFieldGuidance(
            listOf(detection(0.10f, 0.10f, 0.20f, 0.20f, "verifying-1:1/3")),
            modelReady = true
        )
        assertTrue(text.contains("Tiến gần"))
    }

    @Test
    fun stableCandidateEncouragesHoldingStillForCapture() {
        val text = regionFieldGuidance(
            listOf(detection(0.10f, 0.10f, 0.45f, 0.45f, "stable-1")),
            modelReady = true
        )
        assertTrue(text.contains("auto-capture"))
    }

    @Test
    fun missingModelDoesNotPretendToClassify() {
        val text = regionFieldGuidance(emptyList(), modelReady = false)
        assertTrue(text.contains("cần model"))
    }
}
