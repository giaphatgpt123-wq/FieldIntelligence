package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantRecognitionPolicyTest {
    @Test
    fun returnsUnknownWhenNoCandidateClearsMinimum() {
        val decision = PlantRecognitionPolicy.evaluate(
            listOf(RecognitionScore("Centella asiatica", 0.34f))
        )
        assertEquals(RecognitionVerdict.UNKNOWN, decision.verdict)
        assertTrue(decision.candidates.isEmpty())
    }

    @Test
    fun keepsTopThreeWhenResultIsAmbiguous() {
        val decision = PlantRecognitionPolicy.evaluate(
            listOf(
                RecognitionScore("Centella asiatica", 0.66f),
                RecognitionScore("Hydrocotyle sibthorpioides", 0.60f),
                RecognitionScore("Hydrocotyle vulgaris", 0.52f),
                RecognitionScore("Plantago major", 0.48f)
            )
        )
        assertEquals(RecognitionVerdict.AMBIGUOUS, decision.verdict)
        assertEquals(3, decision.candidates.size)
        assertEquals("Centella asiatica", decision.candidates.first().scientificName)
    }

    @Test
    fun strongCandidateNeedsBothScoreAndSeparation() {
        val strong = PlantRecognitionPolicy.evaluate(
            listOf(
                RecognitionScore("Centella asiatica", 0.84f),
                RecognitionScore("Hydrocotyle sibthorpioides", 0.55f)
            )
        )
        assertEquals(RecognitionVerdict.STRONG_CANDIDATE, strong.verdict)

        val closeRace = PlantRecognitionPolicy.evaluate(
            listOf(
                RecognitionScore("Centella asiatica", 0.84f),
                RecognitionScore("Hydrocotyle sibthorpioides", 0.76f)
            )
        )
        assertEquals(RecognitionVerdict.AMBIGUOUS, closeRace.verdict)
    }
}
