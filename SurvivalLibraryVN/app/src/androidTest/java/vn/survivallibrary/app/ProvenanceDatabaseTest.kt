package vn.survivallibrary.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProvenanceDatabaseTest {
    @Test
    fun schema2PackagePersistsSourcesAndMediaMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("survival_library_vn.db")
        val db = OfflineLibraryDb(context)
        try {
            val source = LibraryRecordSource(
                sourceKey = "taxonomy",
                title = "Nguồn định danh",
                publisher = "Cơ quan khoa học",
                uri = "https://example.org/taxon",
                checkedAt = 123L
            )
            val media = LibraryRecordMedia(
                mediaId = "plant-db-001-whole",
                sourceUri = "https://example.org/original",
                downloadUri = "https://raw.githubusercontent.com/example/thumb.webp",
                verified = true,
                angleLabel = "Toàn cây",
                checksum = "c".repeat(64),
                license = "CC BY 4.0",
                creator = "Tác giả",
                mimeType = "image/webp"
            )
            val record = LibraryPackageRecord(
                id = "plant-db-001",
                vietnameseName = "Mẫu kiểm thử",
                categoryId = "vegetables",
                usageLevel = UsageLevel.THUONG_DUNG,
                verificationState = VerificationState.DA_KIEM_CHUNG,
                summary = "Kiểm thử lưu provenance",
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
                sources = listOf(source),
                media = listOf(media)
            )
            val manifest = LibraryPackageManifest(
                packageId = "plants-core-s998",
                version = 1,
                schemaVersion = 2,
                recordCount = 1,
                verifiedCount = 1,
                sha256 = "d".repeat(64),
                sourceUri = "https://raw.githubusercontent.com/example/package.json"
            )

            assertEquals(1, db.installVerifiedPackage(manifest, listOf(record)))
            val storedSources = db.recordSources(record.id)
            val storedMedia = db.recordMedia(record.id)
            assertEquals(1, storedSources.size)
            assertEquals("Nguồn định danh", storedSources.first().title)
            assertEquals(1, storedMedia.size)
            assertEquals("CC BY 4.0", storedMedia.first().license)
            assertTrue(storedMedia.first().verified)
        } finally {
            db.close()
            context.deleteDatabase("survival_library_vn.db")
        }
    }
}
