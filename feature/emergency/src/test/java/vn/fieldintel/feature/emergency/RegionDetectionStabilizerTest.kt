package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionDetectionStabilizerTest {
    private fun detection(
        scientificName: String,
        confidence: Float = 0.8f,
        left: Float = 0.10f,
        top: Float = 0.10f,
        right: Float = 0.40f,
        bottom: Float = 0.40f
    ) = VisualDetection(
        label = scientificName,
        scientificName = scientificName,
        confidence = confidence,
        box = NormalizedBox(left, top, right, bottom)
    )

    @Test
    fun emitsVerifyingStateBeforeStableState() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 3)
        val first = stabilizer.update(listOf(detection("Centella asiatica"))).single()
        assertFalse(first.isStableRegionCandidate())
        assertEquals(1 to 3, first.regionVerificationProgress())

        val second = stabilizer.update(listOf(detection("Centella asiatica", 0.82f))).single()
        assertFalse(second.isStableRegionCandidate())
        assertEquals(2 to 3, second.regionVerificationProgress())

        val stable = stabilizer.update(listOf(detection("Centella asiatica", 0.84f))).single()
        assertEquals("Centella asiatica", stable.scientificName)
        assertTrue(stable.isStableRegionCandidate())
        assertEquals(null, stable.regionVerificationProgress())
    }

    @Test
    fun spatiallySeparateSameTaxonCreatesSeparateTracks() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 2)
        val a = detection("Centella asiatica", left = 0.05f, top = 0.10f, right = 0.25f, bottom = 0.30f)
        val b = detection("Centella asiatica", left = 0.70f, top = 0.60f, right = 0.95f, bottom = 0.90f)
        stabilizer.update(listOf(a, b))
        val stable = stabilizer.update(listOf(a, b))
        assertEquals(2, stable.size)
        assertTrue(stable.all { it.isStableRegionCandidate() })
        assertEquals(2, stable.mapNotNull { it.trackHint }.distinct().size)
    }

    @Test
    fun oneFrameLabelNoiseDoesNotReplaceStableIdentity() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 2, maxMissedFrames = 2)
        val centella = detection("Centella asiatica")
        stabilizer.update(listOf(centella))
        assertTrue(stabilizer.update(listOf(centella)).single().isStableRegionCandidate())

        val noise = detection("Plantago major")
        val noisyFrame = stabilizer.update(listOf(noise))
        assertEquals(1, noisyFrame.size)
        assertFalse(noisyFrame.single().isStableRegionCandidate())
        assertEquals("Plantago major", noisyFrame.single().scientificName)

        val recovered = stabilizer.update(listOf(centella))
        assertEquals(1, recovered.size)
        assertTrue(recovered.single().isStableRegionCandidate())
        assertEquals("Centella asiatica", recovered.single().scientificName)
    }

    @Test
    fun staleTracksExpireAfterMissBudget() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 2, maxMissedFrames = 1)
        val d = detection("Centella asiatica")
        stabilizer.update(listOf(d))
        assertTrue(stabilizer.update(listOf(d)).single().isStableRegionCandidate())
        assertTrue(stabilizer.update(emptyList()).isEmpty())
        assertTrue(stabilizer.update(emptyList()).isEmpty())
        val restarted = stabilizer.update(listOf(d)).single()
        assertFalse(restarted.isStableRegionCandidate())
        assertEquals(1 to 2, restarted.regionVerificationProgress())
    }

    @Test
    fun smoothsConfidenceAndBoundingBox() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 2)
        stabilizer.update(listOf(detection("Centella asiatica", confidence = 0.60f)))
        val stable = stabilizer.update(
            listOf(
                detection(
                    "Centella asiatica",
                    confidence = 1.00f,
                    left = 0.14f,
                    top = 0.14f,
                    right = 0.44f,
                    bottom = 0.44f
                )
            )
        ).single()
        assertTrue(stable.isStableRegionCandidate())
        assertTrue(stable.confidence > 0.60f && stable.confidence < 1.00f)
        assertTrue(stable.box.left > 0.10f && stable.box.left < 0.14f)
    }
}
