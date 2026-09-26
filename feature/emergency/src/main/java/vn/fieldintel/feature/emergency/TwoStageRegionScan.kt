package vn.fieldintel.feature.emergency

import kotlin.math.sqrt

/** Coarse object proposal produced before species classification. */
data class RegionProposal(
    val id: String,
    val confidence: Float,
    val box: NormalizedBox
) {
    init {
        require(id.isNotBlank())
        require(confidence in 0f..1f)
    }
}

/** Species/taxon candidate returned for one cropped proposal. */
data class RegionClassification(
    val label: String,
    val scientificName: String? = null,
    val confidence: Float
) {
    init {
        require(label.isNotBlank())
        require(confidence in 0f..1f)
    }
}

/**
 * Stage 1: find multiple object regions in the full camera frame.
 * This stage must not claim species identity.
 */
interface RegionProposalRunner {
    fun propose(frame: LiveVisualFrameData): List<RegionProposal>
}

/**
 * Stage 2: classify a single proposal using the same owned camera frame plus the proposal bounds.
 * Implementations are responsible for cropping/resizing internally.
 */
interface RegionCropClassifier {
    fun classify(
        frame: LiveVisualFrameData,
        proposal: RegionProposal
    ): List<RegionClassification>
}

/**
 * Coordinates detector -> crop classifier -> normalized VisualDetection output.
 * The coordinator deliberately caps region count and applies confidence thresholds so a dense scene
 * cannot trigger unbounded classifier work on a phone.
 */
class TwoStageRegionScanCoordinator(
    private val proposer: RegionProposalRunner,
    private val classifier: RegionCropClassifier,
    private val proposalThreshold: Float = 0.35f,
    private val classificationThreshold: Float = 0.45f,
    private val maxRegions: Int = 12,
    private val maxCandidatesPerRegion: Int = 1
) {
    init {
        require(proposalThreshold in 0.05f..0.99f)
        require(classificationThreshold in 0.05f..0.99f)
        require(maxRegions in 1..32)
        require(maxCandidatesPerRegion in 1..5)
    }

    fun scan(frame: LiveVisualFrameData): List<VisualDetection> {
        return proposer.propose(frame)
            .asSequence()
            .filter { it.confidence >= proposalThreshold }
            .sortedByDescending { it.confidence }
            .take(maxRegions)
            .flatMap { proposal ->
                classifier.classify(frame, proposal)
                    .asSequence()
                    .filter { it.confidence >= classificationThreshold }
                    .sortedByDescending { it.confidence }
                    .take(maxCandidatesPerRegion)
                    .map { candidate ->
                        VisualDetection(
                            trackHint = proposal.id,
                            label = candidate.label,
                            scientificName = candidate.scientificName,
                            confidence = fusedConfidence(proposal.confidence, candidate.confidence),
                            box = proposal.box
                        )
                    }
            }
            .sortedByDescending { it.confidence }
            .toList()
    }

    private fun fusedConfidence(proposal: Float, classification: Float): Float =
        sqrt((proposal * classification).coerceIn(0f, 1f))
}
