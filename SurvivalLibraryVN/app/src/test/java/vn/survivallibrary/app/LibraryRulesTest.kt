package vn.survivallibrary.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRulesTest {
    @Test
    fun blocksIncompleteRecord() {
        val decision = LibraryRules.publicationDecision(
            RecordQuality(vietnamRelevant = true)
        )
        assertFalse(decision.publishable)
        assertTrue(decision.blockers.isNotEmpty())
    }

    @Test
    fun publishesOnlyVerifiedSafeRecord() {
        val decision = LibraryRules.publicationDecision(
            RecordQuality(
                vietnamRelevant = true,
                usageLevel = UsageLevel.THUONG_DUNG,
                verificationState = VerificationState.DA_KIEM_CHUNG,
                verifiedVietnameseName = true,
                verifiedIdentitySource = true,
                verifiedMedia = true,
                hasUsageClaim = true,
                verifiedUsageSource = true,
                highRisk = true,
                verifiedSafetySource = true
            )
        )
        assertTrue(decision.publishable)
        assertTrue(decision.blockers.isEmpty())
    }

    @Test
    fun priorityFeedRejectsUnclassifiedUsage() {
        val quality = RecordQuality(
            vietnamRelevant = true,
            verificationState = VerificationState.DA_KIEM_CHUNG,
            verifiedVietnameseName = true,
            verifiedIdentitySource = true,
            verifiedMedia = true
        )
        assertFalse(LibraryRules.canEnterVietnamPriorityFeed(quality))
    }

    @Test
    fun priorityFeedRejectsUsageLevelWithoutEvidence() {
        val quality = RecordQuality(
            vietnamRelevant = true,
            usageLevel = UsageLevel.THUONG_DUNG,
            verificationState = VerificationState.DA_KIEM_CHUNG,
            verifiedVietnameseName = true,
            verifiedIdentitySource = true,
            verifiedMedia = true,
            verifiedUsageSource = false
        )
        assertFalse(LibraryRules.canEnterVietnamPriorityFeed(quality))
    }

    @Test
    fun priorityFeedAcceptsSourcedUsageLevel() {
        val quality = RecordQuality(
            vietnamRelevant = true,
            usageLevel = UsageLevel.THUONG_DUNG,
            verificationState = VerificationState.DA_KIEM_CHUNG,
            verifiedVietnameseName = true,
            verifiedIdentitySource = true,
            verifiedMedia = true,
            verifiedUsageSource = true
        )
        assertTrue(LibraryRules.canEnterVietnamPriorityFeed(quality))
    }
}
