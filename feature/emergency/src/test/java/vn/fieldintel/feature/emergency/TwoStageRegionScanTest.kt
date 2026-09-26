package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoStageRegionScanTest {
    private fun frame(): LiveVisualFrameData {
        val y = YuvPlaneData(ByteArray(16) { 120.toByte() }, 4, 1)
        val u = YuvPlaneData(ByteArray(4) { 128.toByte() }, 2, 1)
        val v = YuvPlaneData(ByteArray(4) { 128.toByte() }, 2, 1)
        return LiveVisualFrameData(1L, 4, 4, 0, y, u, v)
    }

    @Test
    fun classifiesMultipleRegionsAndPreservesBoxes() {
        val proposals = listOf(
            RegionProposal("a", 0.9f, NormalizedBox(0.0f, 0.0f, 0.4f, 0.4f)),
            RegionProposal("b", 0.8f, NormalizedBox(0.5f, 0.5f, 1.0f, 1.0f))
        )
        val coordinator = TwoStageRegionScanCoordinator(
            proposer = object : RegionProposalRunner {
                override fun propose(frame: LiveVisualFrameData) = proposals
            },
            classifier = object : RegionCropClassifier {
                override fun classify(frame: LiveVisualFrameData, proposal: RegionProposal): List<RegionClassification> =
                    if (proposal.id == "a") {
                        listOf(RegionClassification("Rau má", "Centella asiatica", 0.81f))
                    } else {
                        listOf(RegionClassification("Cây khác", "Plantago major", 0.64f))
                    }
            }
        )

        val result = coordinator.scan(frame())
        assertEquals(2, result.size)
        assertEquals("a", result[0].trackHint)
        assertEquals("Centella asiatica", result[0].scientificName)
        assertEquals(proposals[0].box, result[0].box)
        assertTrue(result[0].confidence > result[1].confidence)
    }

    @Test
    fun rejectsWeakProposalsAndWeakClassifications() {
        val coordinator = TwoStageRegionScanCoordinator(
            proposer = object : RegionProposalRunner {
                override fun propose(frame: LiveVisualFrameData) = listOf(
                    RegionProposal("weak-proposal", 0.20f, NormalizedBox(0f, 0f, 0.3f, 0.3f)),
                    RegionProposal("strong-proposal", 0.90f, NormalizedBox(0.4f, 0.4f, 0.9f, 0.9f))
                )
            },
            classifier = object : RegionCropClassifier {
                override fun classify(frame: LiveVisualFrameData, proposal: RegionProposal) =
                    listOf(RegionClassification("Không chắc", confidence = 0.30f))
            }
        )

        assertTrue(coordinator.scan(frame()).isEmpty())
    }

    @Test
    fun capsClassifierWorkForDenseScenes() {
        var classifyCalls = 0
        val coordinator = TwoStageRegionScanCoordinator(
            proposer = object : RegionProposalRunner {
                override fun propose(frame: LiveVisualFrameData) = (0 until 25).map { index ->
                    val x = (index % 5) * 0.18f
                    val y = (index / 5) * 0.18f
                    RegionProposal("p$index", 0.9f, NormalizedBox(x, y, x + 0.15f, y + 0.15f))
                }
            },
            classifier = object : RegionCropClassifier {
                override fun classify(frame: LiveVisualFrameData, proposal: RegionProposal): List<RegionClassification> {
                    classifyCalls += 1
                    return listOf(RegionClassification("Taxon", "Example species", 0.8f))
                }
            },
            maxRegions = 6
        )

        val result = coordinator.scan(frame())
        assertEquals(6, classifyCalls)
        assertEquals(6, result.size)
    }

    @Test
    fun keepsOnlyBestCandidatePerRegionByDefault() {
        val coordinator = TwoStageRegionScanCoordinator(
            proposer = object : RegionProposalRunner {
                override fun propose(frame: LiveVisualFrameData) = listOf(
                    RegionProposal("a", 0.9f, NormalizedBox(0f, 0f, 0.5f, 0.5f))
                )
            },
            classifier = object : RegionCropClassifier {
                override fun classify(frame: LiveVisualFrameData, proposal: RegionProposal) = listOf(
                    RegionClassification("Candidate B", "Beta species", 0.61f),
                    RegionClassification("Candidate A", "Alpha species", 0.91f)
                )
            }
        )

        val result = coordinator.scan(frame())
        assertEquals(1, result.size)
        assertEquals("Alpha species", result.single().scientificName)
    }
}
