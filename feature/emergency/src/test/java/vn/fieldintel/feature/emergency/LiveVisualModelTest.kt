package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVisualModelTest {
    @Test
    fun noVerifiedModelNeverReturnsDetections() {
        val status = NoVerifiedLiveVisualModel.status()
        val detections = NoVerifiedLiveVisualModel.detect(
            byteArrayOf(1, 2, 3),
            640,
            480,
            0,
            LiveVisualSearchTarget("rau má")
        )

        assertEquals(VisualModelAvailability.NOT_INSTALLED, status.availability)
        assertTrue(detections.isEmpty())
    }

    @Test
    fun targetMatcherRequiresExactScientificNameWhenTargetProvidesIt() {
        val target = LiveVisualSearchTarget("rau má", scientificName = "Centella asiatica")
        val correct = VisualDetection(
            label = "Gotu kola",
            scientificName = "Centella asiatica",
            confidence = 0.8f,
            box = NormalizedBox(0.1f, 0.1f, 0.4f, 0.4f)
        )
        val wrong = correct.copy(scientificName = "Hydrocotyle vulgaris")
        val missingScientificName = correct.copy(label = "rau má", scientificName = null)

        assertTrue(LiveVisualTargetMatcher.matches(target, correct))
        assertFalse(LiveVisualTargetMatcher.matches(target, wrong))
        assertFalse(LiveVisualTargetMatcher.matches(target, missingScientificName))
    }

    @Test
    fun temporalTrackerNeedsSeveralConsistentFrames() {
        val tracker = LiveVisualTemporalTracker(stableFramesRequired = 4)
        val target = LiveVisualSearchTarget("Centella asiatica")
        val detections = (0 until 4).map { i ->
            VisualDetection(
                label = "Centella asiatica",
                scientificName = "Centella asiatica",
                confidence = 0.75f + i * 0.02f,
                box = NormalizedBox(0.2f + i * 0.005f, 0.2f, 0.5f + i * 0.005f, 0.5f)
            )
        }

        detections.take(3).forEach { tracker.observe(target, listOf(it)) }
        assertFalse(tracker.isStable())
        val candidate = tracker.observe(target, listOf(detections.last()))
        assertTrue(tracker.isStable())
        assertEquals(4, candidate?.stableFrames)
    }

    @Test
    fun losingTargetResetsStability() {
        val tracker = LiveVisualTemporalTracker(stableFramesRequired = 3)
        val target = LiveVisualSearchTarget("rau má")
        val detection = VisualDetection(
            label = "rau má",
            confidence = 0.9f,
            box = NormalizedBox(0.2f, 0.2f, 0.5f, 0.5f)
        )

        tracker.observe(target, listOf(detection))
        tracker.observe(target, listOf(detection))
        assertNull(tracker.observe(target, emptyList()))
        assertFalse(tracker.isStable())
        assertEquals(1, tracker.observe(target, listOf(detection))?.stableFrames)
    }
}
