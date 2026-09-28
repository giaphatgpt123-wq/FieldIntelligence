package vn.fieldintel.feature.emergency

import org.junit.Assert.assertThrows
import org.junit.Test

class FishPackGateTest {
    private fun completeMeta(): Map<String, String> = mapOf(
        "fishCoverageGateVersion" to "1",
        "fishChecklistReported" to "772",
        "fishChecklistPresent" to "736",
        "fishChecklistReview" to "29",
        "fishChecklistExcluded" to "7",
        "fishPresentChecklistResolved" to "736",
        "fishPresentChecklistUnresolved" to "0",
        "fishPresentAcceptedTaxa" to "730",
        "fishPresentTaxaWithLocalMedia" to "730",
        "fishTaxa" to "730",
        "fishWithLocalMedia" to "730"
    )

    @Test
    fun completeDeclaredFishPackPasses() {
        FishPackGate.validateIfDeclared(
            meta = completeMeta(),
            hasLocalMediaTables = true,
            observedFishTaxa = 730,
            observedFishTaxaWithLocalMedia = 730
        )
    }

    @Test
    fun verifiedPartialFishPackWithMissingImagesRemainsInstallable() {
        val partial = completeMeta().toMutableMap().apply {
            put("fishCoverageGateVersion", "0")
            put("fishPresentChecklistResolved", "720")
            put("fishPresentChecklistUnresolved", "16")
            put("fishPresentAcceptedTaxa", "715")
            put("fishPresentTaxaWithLocalMedia", "434")
            put("fishTaxa", "715")
            put("fishWithLocalMedia", "434")
        }
        FishPackGate.validateIfDeclared(partial, true, 715, 434)
        assertThrows(IllegalArgumentException::class.java) {
            FishPackGate.validateIfDeclared(partial, true, 715, 433)
        }
    }

    @Test
    fun genericTaxonomyPackWithoutFishGateIsUnaffected() {
        FishPackGate.validateIfDeclared(
            meta = mapOf("recordCount" to "2000"),
            hasLocalMediaTables = false,
            observedFishTaxa = 0,
            observedFishTaxaWithLocalMedia = 0
        )
    }

    @Test
    fun declaredFishPackRejectsMissingOfflineMedia() {
        assertThrows(IllegalArgumentException::class.java) {
            FishPackGate.validateIfDeclared(
                meta = completeMeta(),
                hasLocalMediaTables = true,
                observedFishTaxa = 730,
                observedFishTaxaWithLocalMedia = 729
            )
        }
    }

    @Test
    fun declaredFishPackRejectsUnresolvedPresentNames() {
        val broken = completeMeta().toMutableMap().apply {
            put("fishPresentChecklistResolved", "735")
            put("fishPresentChecklistUnresolved", "1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FishPackGate.validateIfDeclared(broken, true, 730, 730)
        }
    }

    @Test
    fun declaredFishPackRejectsChecklistBelowKnownBaseline() {
        val broken = completeMeta().toMutableMap().apply {
            put("fishChecklistReported", "771")
            put("fishChecklistPresent", "735")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FishPackGate.validateIfDeclared(broken, true, 730, 730)
        }
    }
}
