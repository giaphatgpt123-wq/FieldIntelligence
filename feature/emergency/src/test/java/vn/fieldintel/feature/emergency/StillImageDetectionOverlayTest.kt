package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StillImageDetectionOverlayTest {
    private fun detection(
        label: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        confidence: Float = 0.8f,
        trackHint: String? = null,
        scientificName: String? = null
    ) = VisualDetection(
        trackHint = trackHint,
        label = label,
        scientificName = scientificName,
        confidence = confidence,
        box = NormalizedBox(left, top, right, bottom)
    )

    @Test
    fun tapSelectsCandidateInsideContentScaleFitImage() {
        val first = detection("A", 0.1f, 0.1f, 0.4f, 0.4f)
        val second = detection("B", 0.6f, 0.6f, 0.9f, 0.9f)

        val hit = stillImageHitTest(
            detections = listOf(first, second),
            tapX = 75f,
            tapY = 50f,
            canvasWidth = 200f,
            canvasHeight = 200f,
            imageWidth = 100,
            imageHeight = 200
        )

        assertEquals(first, hit)
    }

    @Test
    fun tapInLetterboxAreaReturnsNull() {
        val item = detection("A", 0f, 0f, 1f, 1f)
        assertNull(
            stillImageHitTest(
                detections = listOf(item),
                tapX = 10f,
                tapY = 100f,
                canvasWidth = 300f,
                canvasHeight = 200f,
                imageWidth = 100,
                imageHeight = 200
            )
        )
    }

    @Test
    fun smallestOverlappingBoxWins() {
        val large = detection("large", 0.1f, 0.1f, 0.9f, 0.9f)
        val small = detection("small", 0.4f, 0.4f, 0.6f, 0.6f)

        val hit = stillImageHitTest(
            detections = listOf(large, small),
            tapX = 100f,
            tapY = 100f,
            canvasWidth = 200f,
            canvasHeight = 200f,
            imageWidth = 200,
            imageHeight = 200
        )

        assertEquals(small, hit)
    }

    @Test
    fun sameTrackedRegionBecomesOneGroupWithTopThreeCandidates() {
        val box = NormalizedBox(0.1f, 0.1f, 0.6f, 0.6f)
        val detections = listOf(
            VisualDetection("region-1", "A", "Alpha species", 0.82f, box),
            VisualDetection("region-1", "B", "Beta species", 0.76f, box),
            VisualDetection("region-1", "C", "Gamma species", 0.54f, box),
            VisualDetection("region-1", "duplicate", "Alpha species", 0.50f, box)
        )

        val groups = buildRegionRecognitionGroups(detections)
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().candidates.size)
        assertEquals("Alpha species", groups.single().primary?.scientificName)
        assertEquals(RecognitionVerdict.AMBIGUOUS, groups.single().verdict)
    }

    @Test
    fun tappingGroupedRegionReturnsItsStrongestCandidate() {
        val box = NormalizedBox(0.2f, 0.2f, 0.8f, 0.8f)
        val weaker = VisualDetection("region-2", "B", "Beta species", 0.61f, box)
        val stronger = VisualDetection("region-2", "A", "Alpha species", 0.91f, box)

        val hit = stillImageHitTest(
            detections = listOf(weaker, stronger),
            tapX = 100f,
            tapY = 100f,
            canvasWidth = 200f,
            canvasHeight = 200f,
            imageWidth = 200,
            imageHeight = 200
        )

        assertEquals(stronger, hit)
        assertTrue(buildRegionRecognitionGroups(listOf(weaker, stronger)).single().candidates.size == 2)
    }
}
