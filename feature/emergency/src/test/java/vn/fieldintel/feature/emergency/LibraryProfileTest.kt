package vn.fieldintel.feature.emergency

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProfileTest {
    @Test
    fun taxonomyOnlyRecordDoesNotBecomePublishableProfile() {
        val profile = LibraryProfiles.forRecord("mangifera-indica")
        val decision = profile.publicationDecision()

        assertFalse(decision.publishable)
        assertTrue(decision.blockers.any { it.contains("Việt Nam") })
        assertTrue(decision.blockers.any { it.contains("Mức độ sử dụng") })
        assertTrue(decision.blockers.any { it.contains("Tên tiếng Việt") })
        assertTrue(decision.blockers.any { it.contains("ảnh") })
    }

    @Test
    fun classifiedUsageRequiresItsOwnEvidence() {
        val profile = LibraryProfile(
            recordId = "test-common",
            usageLevel = UsageLevel.THUONG_DUNG,
            verificationState = VerificationState.DA_KIEM_CHUNG,
            vietnamRelevant = true,
            verifiedVietnameseName = true,
            verifiedIdentitySource = true,
            verifiedMedia = true
        )

        val decision = profile.publicationDecision()
        assertFalse(decision.publishable)
        assertTrue(decision.blockers.any { it.contains("Mức độ sử dụng") })
    }

    @Test
    fun verifiedProfileCanPassWithoutInventingUsageClaim() {
        val profile = LibraryProfile(
            recordId = "test-verified",
            usageLevel = UsageLevel.THUONG_DUNG,
            verificationState = VerificationState.DA_KIEM_CHUNG,
            vietnamRelevant = true,
            verifiedVietnameseName = true,
            verifiedIdentitySource = true,
            verifiedMedia = true,
            usageSourceName = "Nguồn kiểm chứng",
            usageSourceUrl = "https://example.org/evidence",
            usageSourceScope = "Mức độ sử dụng tại Việt Nam"
        )

        val decision = profile.publicationDecision()
        assertTrue(decision.publishable)
        assertFalse(profile.hasUsageClaim)
        assertTrue(profile.hasVerifiedUsageSource)
    }
}
