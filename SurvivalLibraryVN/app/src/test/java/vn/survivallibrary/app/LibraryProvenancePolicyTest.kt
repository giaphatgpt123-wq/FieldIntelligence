package vn.survivallibrary.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProvenancePolicyTest {
    private fun manifest(schema: Int = 2) = LibraryPackageManifest(
        packageId = "plants-core-s001",
        version = 1,
        schemaVersion = schema,
        recordCount = 1,
        verifiedCount = 1,
        sha256 = "a".repeat(64),
        sourceUri = "https://raw.githubusercontent.com/example/package.json"
    )

    private fun source() = LibraryRecordSource(
        sourceKey = "taxonomy",
        title = "Nguồn định danh",
        publisher = "Cơ quan khoa học",
        uri = "https://example.org/taxon",
        checkedAt = 1L
    )

    private fun media() = LibraryRecordMedia(
        mediaId = "plant-001-whole",
        sourceUri = "https://example.org/original-image",
        downloadUri = "https://raw.githubusercontent.com/example/media.webp",
        verified = true,
        angleLabel = "Toàn cây",
        checksum = "b".repeat(64),
        license = "CC BY 4.0",
        creator = "Tác giả",
        mimeType = "image/webp"
    )

    private fun record(
        sources: List<LibraryRecordSource> = listOf(source()),
        media: List<LibraryRecordMedia> = listOf(media())
    ) = LibraryPackageRecord(
        id = "plant-001",
        vietnameseName = "Mẫu thực vật",
        categoryId = "vegetables",
        usageLevel = UsageLevel.THUONG_DUNG,
        verificationState = VerificationState.DA_KIEM_CHUNG,
        summary = "Hồ sơ kiểm thử provenance",
        highRisk = false,
        sourceCount = sources.size,
        published = true,
        vietnamRelevant = true,
        verifiedVietnameseName = true,
        verifiedIdentitySource = true,
        verifiedMedia = true,
        hasUsageClaim = false,
        verifiedUsageSource = false,
        verifiedSafetySource = false,
        sources = sources,
        media = media
    )

    @Test
    fun schema2AcceptsExplicitSourcesAndLicensedMedia() {
        assertTrue(LibraryDataPackages.validate(manifest()).valid)
        assertTrue(LibraryDataPackages.validateRecords(manifest(), listOf(record())).valid)
    }

    @Test
    fun schema2RejectsMissingDetailedSources() {
        val result = LibraryDataPackages.validateRecords(manifest(), listOf(record(sources = emptyList())))
        assertFalse(result.valid)
        assertTrue(result.blockers.any { it.contains("nguồn chi tiết") || it.contains("sourceCount") })
    }

    @Test
    fun schema2RejectsVerifiedMediaWithoutLicensedMediaDescriptor() {
        val result = LibraryDataPackages.validateRecords(manifest(), listOf(record(media = emptyList())))
        assertFalse(result.valid)
        assertTrue(result.blockers.any { it.contains("verifiedMedia=true") })
    }

    @Test
    fun schema1RemainsBackwardCompatible() {
        val legacy = record(sources = emptyList(), media = emptyList()).copy(sourceCount = 1)
        assertTrue(LibraryDataPackages.validate(manifest(schema = 1)).valid)
        assertTrue(LibraryDataPackages.validateRecords(manifest(schema = 1), listOf(legacy)).valid)
    }
}
