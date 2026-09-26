package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionScanLogicTest {
    private fun detection(
        label: String,
        scientific: String? = null,
        confidence: Float = 0.8f,
        left: Float = 0.1f,
        top: Float = 0.1f
    ) = VisualDetection(
        label = label,
        scientificName = scientific,
        confidence = confidence,
        box = NormalizedBox(left, top, left + 0.2f, top + 0.2f)
    )

    @Test
    fun groupsMultipleInstancesOfSameTaxon() {
        val summary = RegionScanClassifier.summarize(
            listOf(
                detection("Rau má", "Centella asiatica", 0.72f, 0.05f),
                detection("Gotu kola", "Centella asiatica", 0.88f, 0.40f),
                detection("Cỏ khác", "Plantago major", 0.67f, 0.70f)
            )
        )

        assertEquals(3, summary.totalObjects)
        assertEquals(3, summary.strongObjects)
        assertEquals(2, summary.items.size)
        val centella = summary.items.first { it.scientificName == "Centella asiatica" }
        assertEquals(2, centella.instances)
        assertEquals(0.88f, centella.bestConfidence)
    }

    @Test
    fun summaryCountsWeakObjectsButDoesNotCallThemStrong() {
        val summary = RegionScanClassifier.summarize(
            listOf(
                detection("Rau má", "Centella asiatica", 0.82f),
                detection("Không rõ", confidence = 0.31f, left = 0.5f)
            )
        )

        assertEquals(2, summary.totalObjects)
        assertEquals(1, summary.strongObjects)
    }

    @Test
    fun autoCaptureRequiresStableVisibleCompositionAndLayout() {
        val gate = RegionAutoCaptureGate(stableFramesRequired = 3, minimumIntervalNanos = 2_000_000_000L)
        val frame = listOf(detection("Rau má", "Centella asiatica"))

        assertFalse(gate.shouldCapture(1_000_000_000L, frame))
        assertFalse(gate.shouldCapture(1_300_000_000L, frame))
        assertTrue(gate.shouldCapture(1_600_000_000L, frame))
    }

    @Test
    fun cameraPanResetsStabilityEvenWhenLabelsStayTheSame() {
        val gate = RegionAutoCaptureGate(stableFramesRequired = 3)
        val left = listOf(detection("Rau má", "Centella asiatica", left = 0.05f))
        val right = listOf(detection("Rau má", "Centella asiatica", left = 0.70f))

        assertFalse(gate.shouldCapture(1_000_000_000L, left))
        assertFalse(gate.shouldCapture(1_300_000_000L, left))
        assertFalse(gate.shouldCapture(1_600_000_000L, right))
        assertFalse(gate.shouldCapture(1_900_000_000L, right))
        assertTrue(gate.shouldCapture(2_200_000_000L, right))
    }

    @Test
    fun changedInstanceCountResetsStability() {
        val gate = RegionAutoCaptureGate(stableFramesRequired = 2)
        val one = listOf(detection("Rau má", "Centella asiatica", left = 0.05f))
        val two = one + detection("Rau má", "Centella asiatica", left = 0.55f)

        assertFalse(gate.shouldCapture(1_000_000_000L, one))
        assertFalse(gate.shouldCapture(1_300_000_000L, two))
        assertTrue(gate.shouldCapture(1_600_000_000L, two))
    }

    @Test
    fun autoCaptureDoesNotFloodStorageDuringCooldown() {
        val gate = RegionAutoCaptureGate(stableFramesRequired = 2, minimumIntervalNanos = 2_000_000_000L)
        val frame = listOf(detection("Rau má", "Centella asiatica"))

        gate.shouldCapture(1_000_000_000L, frame)
        assertTrue(gate.shouldCapture(1_300_000_000L, frame))
        gate.shouldCapture(1_600_000_000L, frame)
        assertFalse(gate.shouldCapture(1_900_000_000L, frame))
        gate.shouldCapture(3_400_000_000L, frame)
        assertTrue(gate.shouldCapture(3_700_000_000L, frame))
    }

    @Test
    fun lowConfidenceObjectsDoNotTriggerCapture() {
        val gate = RegionAutoCaptureGate(stableFramesRequired = 2)
        val weak = listOf(detection("Không rõ", confidence = 0.31f))
        assertFalse(gate.shouldCapture(1_000_000_000L, weak))
        assertFalse(gate.shouldCapture(1_300_000_000L, weak))
    }
}
