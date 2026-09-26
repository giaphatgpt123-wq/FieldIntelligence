package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
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
    fun requiresRepeatedObservationBeforeEmission() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 3)
        assertTrue(stabilizer.update(listOf(detection("Centella asiatica"))).isEmpty())
        assertTrue(stabilizer.update(listOf(detection("Centella asiatica", 0.82f))).isEmpty())
        val stable = stabilizer.update(listOf(detection("Centella asiatica", 0.84f)))
        assertEquals(1, stable.size)
        assertEquals("Centella asiatica", stable.single().scientificName)
        assertTrue(stable.single().trackHint!!.startsWith("stable-"))
    }

    @Test
    fun spatiallySeparateSameTaxonCreatesSeparateTracks() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 2)
        val a = detection("Centella asiatica", left = 0.05f, top = 0.10f, right = 0.25f, bottom = 0.30f)
        val b = detection("Centella asiatica", left = 0.70f, top = 0.60f, right = 0.95f, bottom = 0.90f)
        stabilizer.update(listOf(a, b))
        val stable = stabilizer.update(listOf(a, b))
        assertEquals(2, stable.size)
        assertEquals(2, stable.mapNotNull { it.trackHint }.distinct().size)
    }

    @Test
    fun oneFrameLabelNoiseDoesNotReplaceStableIdentity() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 2, maxMissedFrames = 2)
        val centella = detection("Centella asiatica")
        stabilizer.update(listOf(centella))
        assertEquals(1, stabilizer.update(listOf(centella)).size)

        val noise = detection("Plantago major")
        assertTrue(stabilizer.update(listOf(noise)).isEmpty())
        val recovered = stabilizer.update(listOf(centella))
        assertEquals(1, recovered.size)
        assertEquals("Centella asiatica", recovered.single().scientificName)
    }

    @Test
    fun staleTracksExpireAfterMissBudget() {
        val stabilizer = RegionDetectionStabilizer(stableHitsRequired = 2, maxMissedFrames = 1)
        val d = detection("Centella asiatica")
        stabilizer.update(listOf(d))
        assertEquals(1, stabilizer.update(listOf(d)).size)
        assertTrue(stabilizer.update(emptyList()).isEmpty())
        assertTrue(stabilizer.update(emptyList()).isEmpty())
        assertTrue(stabilizer.update(listOf(d)).isEmpty())
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
        assertTrue(stable.confidence > 0.60f && stable.confidence < 1.00f)
        assertTrue(stable.box.left > 0.10f && stable.box.left < 0.14f)
    }
}
