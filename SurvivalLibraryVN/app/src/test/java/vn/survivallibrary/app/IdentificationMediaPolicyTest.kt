package vn.survivallibrary.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationMediaPolicyTest {
    private fun source() = LibraryRecordSource(
        sourceKey = "taxonomy",
        title = "Nguồn định danh",
        publisher = "Cơ quan khoa học",
        uri = "https://example.org/taxon",
        checkedAt = 1L
    )

    private fun media(index: Int, role: String, primary: Boolean = false) = LibraryRecordMedia(
        mediaId = "ginger-$index",
        sourceUri = "https://example.org/source/$index",
        downloadUri = "https://raw.githubusercontent.com/example/media/$index.jpg",
        verified = true,
        angleLabel = IdentificationMediaPolicy.roleLabel(role),
        checksum = index.toString().padStart(64, 'a').takeLast(64),
        license = "CC BY 4.0",
        creator = "Tác giả",
        mimeType = "image/jpeg",
        viewRole = role,
        isPrimary = primary,
        diagnostic = true
    )

    private fun record(media: List<LibraryRecordMedia>) = LibraryPackageRecord(
        id = "ginger",
        vietnameseName = "Gừng",
        categoryId = "medicinal-plants",
        usageLevel = UsageLevel.CHUA_PHAN_LOAI,
        verificationState = VerificationState.DA_KIEM_CHUNG,
        summary = "Hồ sơ kiểm thử",
        highRisk = false,
        sourceCount = 1,
        published = true,
        vietnamRelevant = true,
        verifiedVietnameseName = true,
        verifiedIdentitySource = true,
        verifiedMedia = true,
        hasUsageClaim = false,
        verifiedUsageSource = false,
        verifiedSafetySource = false,
        sources = listOf(source()),
        media = media,
        scientificName = "Zingiber officinale",
        identificationSummary = "Đối chiếu cây, lá và thân rễ; không kết luận từ một ảnh.",
        requiredViewRoles = listOf("WHOLE", "UNDERGROUND_PART", "LEAF"),
        primaryViewRole = "UNDERGROUND_PART",
        qualityProfile = IdentificationMediaPolicy.PROFILE
    )

    @Test
    fun onePhotoGingerIsRejected() {
        val result = IdentificationMediaPolicy.validate(record(listOf(media(1, "UNDERGROUND_PART", true))))
        assertTrue(result.any { it.contains("1/5") })
    }

    @Test
    fun multiViewGingerPasses() {
        val values = listOf(
            media(1, "UNDERGROUND_PART", true),
            media(2, "WHOLE"),
            media(3, "LEAF"),
            media(4, "CROSS_SECTION"),
            media(5, "FLOWER")
        )
        assertTrue(IdentificationMediaPolicy.validate(record(values)).isEmpty())
    }

    @Test
    fun missingRhizomeRoleIsRejected() {
        val values = listOf(
            media(1, "WHOLE", true), media(2, "LEAF"), media(3, "FLOWER"),
            media(4, "CROSS_SECTION"), media(5, "HABITAT")
        )
        val result = IdentificationMediaPolicy.validate(record(values).copy(primaryViewRole = "WHOLE"))
        assertFalse(result.isEmpty())
        assertTrue(result.any { it.contains("UNDERGROUND_PART") })
    }
}
