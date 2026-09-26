package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableRegionInferencePacingTest {
    private class CountingRunner : LiveVisualModelRunner {
        var scans = 0

        override fun status(): VisualModelStatus = VisualModelStatus(
            availability = VisualModelAvailability.READY,
            message = "ready"
        )

        override fun detect(
            frame: LiveVisualFrameData,
            target: LiveVisualSearchTarget
        ): List<VisualDetection> = emptyList()

        override fun scanRegion(frame: LiveVisualFrameData): List<VisualDetection> {
            scans += 1
            return listOf(
                VisualDetection(
                    label = "candidate",
                    scientificName = "Example species",
                    confidence = 0.9f,
                    box = NormalizedBox(0.1f, 0.1f, 0.5f, 0.5f)
                )
            )
        }
    }

    private fun frame(timestamp: Long): LiveVisualFrameData {
        val width = 8
        val height = 8
        val y = ByteArray(width * height) { 100.toByte() }
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
    fun cachedFrameDoesNotAdvanceVerificationHits() {
        val delegate = CountingRunner()
        val runner = StableRegionVisualModelRunner(delegate)

        val first = runner.scanRegion(frame(1_000_000_000L)).single()
        assertEquals(1, delegate.scans)
        assertEquals(1 to 3, first.regionVerificationProgress())

        val cached = runner.scanRegion(frame(1_100_000_000L)).single()
        assertEquals(1, delegate.scans)
        assertEquals(1 to 3, cached.regionVerificationProgress())
        assertFalse(cached.isStableRegionCandidate())

        val second = runner.scanRegion(frame(1_300_000_000L)).single()
        assertEquals(2, delegate.scans)
        assertEquals(2 to 3, second.regionVerificationProgress())

        runner.scanRegion(frame(1_600_000_000L))
        val stable = runner.scanRegion(frame(1_900_000_000L)).single()
        assertTrue(stable.isStableRegionCandidate())
    }
}
