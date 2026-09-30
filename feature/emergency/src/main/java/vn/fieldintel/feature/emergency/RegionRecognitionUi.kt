package vn.fieldintel.feature.emergency

/** UI-safe grouping for one detected object region with up to three recognition candidates. */
data class RegionRecognitionGroup(
    val key: String,
    val box: NormalizedBox,
    val verdict: RecognitionVerdict,
    val candidates: List<VisualDetection>
) {
    val primary: VisualDetection? get() = candidates.firstOrNull()
}

fun buildRegionRecognitionGroups(detections: List<VisualDetection>): List<RegionRecognitionGroup> {
    return detections
        .groupBy { detection ->
            detection.trackHint?.takeIf { it.isNotBlank() }
                ?: listOf(detection.box.left, detection.box.top, detection.box.right, detection.box.bottom).joinToString(":")
        }
        .mapNotNull { (key, grouped) ->
            val ranked = grouped
                .asSequence()
                .sortedByDescending { it.confidence }
                .distinctBy { (it.scientificName ?: it.label).trim().lowercase() }
                .take(PlantRecognitionPolicy.MAX_CANDIDATES)
                .toList()
            if (ranked.isEmpty()) return@mapNotNull null
            val decision = PlantRecognitionPolicy.evaluate(
                ranked.map { RecognitionScore(it.scientificName ?: it.label, it.confidence) }
            )
            RegionRecognitionGroup(
                key = key,
                box = ranked.first().box,
                verdict = decision.verdict,
                candidates = ranked
            )
        }
        .sortedByDescending { it.primary?.confidence ?: 0f }
}

fun recognitionVerdictLabel(verdict: RecognitionVerdict): String = when (verdict) {
    RecognitionVerdict.UNKNOWN -> "CHƯA XÁC ĐỊNH"
    RecognitionVerdict.AMBIGUOUS -> "MƠ HỒ"
    RecognitionVerdict.STRONG_CANDIDATE -> "ỨNG VIÊN MẠNH"
}
