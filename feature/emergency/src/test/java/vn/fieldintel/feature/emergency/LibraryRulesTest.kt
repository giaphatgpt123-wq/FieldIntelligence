package vn.fieldintel.feature.emergency

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRulesTest {
    @Test
    fun publishGateRejectsMissingVietnameseNameAndMedia() {
        val decision = LibraryRules.publicationDecision(
            LibraryQualitySnapshot(
                vietnamRelevant = true,
                verificationState = VerificationState.DA_KIEM_CHUNG,
                verifiedVietnameseName = false,
                verifiedIdentitySource = true,
                verifiedMedia = false
            )
        )

        assertFalse(decision.publishable)
        assertTrue(decision.blockers.any { it.contains("Tên tiếng Việt") })
        assertTrue(decision.blockers.any { it.contains("ảnh") })
    }

    @Test
    fun highRiskRecordRequiresSafetyEvidence() {
        val decision = LibraryRules.publicationDecision(
            LibraryQualitySnapshot(
                vietnamRelevant = true,
                verificationState = VerificationState.DA_KIEM_CHUNG,
                verifiedVietnameseName = true,
                verifiedIdentitySource = true,
                verifiedMedia = true,
                highRisk = true,
                verifiedSafetySource = false
            )
        )

        assertFalse(decision.publishable)
        assertTrue(decision.blockers.any { it.contains("nguy cơ cao") })
    }

    @Test
    fun priorityFeedDoesNotGuessUnclassifiedRecords() {
        val snapshot = LibraryQualitySnapshot(
            vietnamRelevant = true,
            usageLevel = UsageLevel.CHUA_PHAN_LOAI,
            verificationState = VerificationState.DA_KIEM_CHUNG,
            verifiedVietnameseName = true,
            verifiedIdentitySource = true,
            verifiedMedia = true
        )

        assertFalse(LibraryRules.mayAppearInVietnamPriorityFeed(snapshot))
    }

    @Test
    fun priorityFeedRejectsUnsourcedUsageClassification() {
        val snapshot = LibraryQualitySnapshot(
            vietnamRelevant = true,
            usageLevel = UsageLevel.THUONG_DUNG,
            verificationState = VerificationState.DA_KIEM_CHUNG,
            verifiedVietnameseName = true,
            verifiedIdentitySource = true,
            verifiedMedia = true,
            verifiedUsageSource = false
        )

        assertFalse(LibraryRules.mayAppearInVietnamPriorityFeed(snapshot))
    }

    @Test
    fun verifiedCommonRecordMayAppearInPriorityFeed() {
        val snapshot = LibraryQualitySnapshot(
            vietnamRelevant = true,
            usageLevel = UsageLevel.THUONG_DUNG,
            verificationState = VerificationState.DA_KIEM_CHUNG,
            verifiedVietnameseName = true,
            verifiedIdentitySource = true,
            verifiedMedia = true,
            verifiedUsageSource = true
        )

        assertTrue(LibraryRules.mayAppearInVietnamPriorityFeed(snapshot))
    }

    @Test
    fun collectionOrderPutsUserFacingVietnamGroupsBeforeGlobalTaxonomy() {
        val vegetable = LibraryCollection("vegetables", "Rau", "🥬", "", emptySet())
        val global = LibraryCollection("wfo-plants", "Thực vật", "🌿", "", emptySet())
        val fish = LibraryCollection("freshwater-fish", "Cá nước ngọt", "🐟", "", emptySet())

        val ordered = LibraryRules.orderedCollections(listOf(global, fish, vegetable))

        assertTrue(ordered.indexOf(vegetable) < ordered.indexOf(fish))
        assertTrue(ordered.indexOf(fish) < ordered.indexOf(global))
    }
}
