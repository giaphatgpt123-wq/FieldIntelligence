package vn.fieldintel.feature.emergency

/**
 * Converts raw image-classifier scores into a conservative recognition state.
 * A model score is never treated as specimen verification.
 */
enum class RecognitionVerdict {
    UNKNOWN,
    AMBIGUOUS,
    STRONG_CANDIDATE
}

data class RecognitionScore(
    val scientificName: String,
    val score: Float
)

data class RecognitionDecision(
    val verdict: RecognitionVerdict,
    val candidates: List<RecognitionScore>
)

object PlantRecognitionPolicy {
    const val MIN_CANDIDATE_SCORE = 0.35f
    const val STRONG_SCORE = 0.70f
    const val STRONG_MARGIN = 0.15f
    const val MAX_CANDIDATES = 3

    fun evaluate(raw: List<RecognitionScore>): RecognitionDecision {
        val ranked = raw
            .asSequence()
            .filter { it.scientificName.isNotBlank() && it.score in 0f..1f }
            .distinctBy { it.scientificName.trim().lowercase() }
            .sortedByDescending { it.score }
            .filter { it.score >= MIN_CANDIDATE_SCORE }
            .take(MAX_CANDIDATES)
            .toList()

        if (ranked.isEmpty()) return RecognitionDecision(RecognitionVerdict.UNKNOWN, emptyList())

        val top = ranked.first().score
        val second = ranked.getOrNull(1)?.score ?: 0f
        val verdict = if (top >= STRONG_SCORE && top - second >= STRONG_MARGIN) {
            RecognitionVerdict.STRONG_CANDIDATE
        } else {
            RecognitionVerdict.AMBIGUOUS
        }
        return RecognitionDecision(verdict, ranked)
    }
}
