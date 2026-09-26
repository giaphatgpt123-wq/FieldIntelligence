package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVisualSearchStateTest {
    @Test
    fun noVerifiedModelNeverCreatesIdentification() {
        val state = LiveVisualSearchReducer.start(
            LiveVisualSearchTarget("rau má", scientificName = "Centella asiatica"),
            modelReady = false
        )

        assertEquals(LiveVisualSearchPhase.MODEL_NOT_READY, state.phase)
        assertNull(state.candidate)
        assertTrue(state.message.contains("Không tạo kết quả giả"))
    }

    @Test
    fun candidateMustPersistAcrossSeveralFramesBeforeStable() {
        val scanning = LiveVisualSearchReducer.start(LiveVisualSearchTarget("rau má"), modelReady = true)
        val early = LiveVisualSearchReducer.candidate(
            scanning,
            LiveVisualCandidate("Rau má", "Centella asiatica", score = 0.81f, stableFrames = 2)
        )
        val stable = LiveVisualSearchReducer.candidate(
            early,
            LiveVisualCandidate("Rau má", "Centella asiatica", score = 0.86f, stableFrames = 4)
        )

        assertEquals(LiveVisualSearchPhase.CANDIDATE, early.phase)
        assertEquals(LiveVisualSearchPhase.STABLE_CANDIDATE, stable.phase)
    }

    @Test
    fun cameraFrameMetadataDoesNotCreateCandidate() {
        val scanning = LiveVisualSearchReducer.start(LiveVisualSearchTarget("rau má"), modelReady = true)
        val next = LiveVisualSearchReducer.frame(scanning, LiveFrameInfo(10L, 1280, 720, 90))

        assertEquals(LiveVisualSearchPhase.SCANNING, next.phase)
        assertNull(next.candidate)
        assertEquals(1280, next.lastFrame?.width)
    }
}
