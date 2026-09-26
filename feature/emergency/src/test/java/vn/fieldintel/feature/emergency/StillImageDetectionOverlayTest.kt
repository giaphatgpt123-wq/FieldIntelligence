package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StillImageDetectionOverlayTest {
    private fun detection(label: String, left: Float, top: Float, right: Float, bottom: Float) = VisualDetection(
        label = label,
        confidence = 0.8f,
        box = NormalizedBox(left, top, right, bottom)
    )

    @Test
    fun tapSelectsCandidateInsideContentScaleFitImage() {
        val first = detection("A", 0.1f, 0.1f, 0.4f, 0.4f)
        val second = detection("B", 0.6f, 0.6f, 0.9f, 0.9f)

        // 100x200 image fitted into 200x200 canvas renders as 100x200 with 50px side letterboxes.
        // (75, 50) maps to normalized image coordinate (0.25, 0.25), inside `first`.
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
}
