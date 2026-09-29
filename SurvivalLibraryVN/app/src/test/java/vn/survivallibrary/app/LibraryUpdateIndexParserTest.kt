package vn.survivallibrary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUpdateIndexParserTest {
    @Test
    fun emptyIndexIsAccepted() {
        val parsed = LibraryUpdateIndexParser.parse(
            """{"schemaVersion":1,"packages":[]}"""
        )
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun remotePackageIsParsed() {
        val parsed = LibraryUpdateIndexParser.parse(
            """
            {
              "schemaVersion":1,
              "packages":[{
                "packageId":"plants-core",
                "version":2,
                "schemaVersion":1,
                "recordCount":10,
                "verifiedCount":10,
                "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "packageUrl":"https://example.org/plants.json",
                "sizeBytes":1048576,
                "minAppVersionCode":9,
                "updateMode":"DELTA"
              }]
            }
            """.trimIndent()
        )
        assertEquals(1, parsed.size)
        assertEquals("plants-core", parsed.first().packageId)
        assertEquals(2, parsed.first().version)
        assertEquals(10, parsed.first().recordCount)
        assertEquals(1_048_576L, parsed.first().sizeBytes)
        assertEquals(9, parsed.first().minAppVersionCode)
        assertEquals(PackageUpdateMode.DELTA, parsed.first().updateMode)
    }

    @Test
    fun optionalPackageMetadataRemainsBackwardCompatible() {
        val parsed = LibraryUpdateIndexParser.parse(
            """
            {
              "schemaVersion":1,
              "packages":[{
                "packageId":"mushrooms-core",
                "version":1,
                "schemaVersion":1,
                "recordCount":1,
                "verifiedCount":1,
                "sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "packageUrl":"https://example.org/mushrooms.json"
              }]
            }
            """.trimIndent()
        )
        assertEquals(0L, parsed.first().sizeBytes)
        assertEquals(1, parsed.first().minAppVersionCode)
        assertEquals(PackageUpdateMode.SNAPSHOT, parsed.first().updateMode)
    }

    @Test
    fun deltaPayloadCanCarryExplicitWithdrawals() {
        val remote = RemotePackageDescriptor(
            packageId = "plants-core",
            version = 3,
            schemaVersion = 1,
            recordCount = 1,
            verifiedCount = 1,
            sha256 = "c".repeat(64),
            packageUrl = "https://example.org/plants-v3.json",
            updateMode = PackageUpdateMode.DELTA
        )
        val payload = LibraryUpdateIndexParser.parsePackage(
            """
            {
              "packageId":"plants-core",
              "version":3,
              "schemaVersion":1,
              "updateMode":"DELTA",
              "removedRecordIds":["old-record"],
              "records":[{
                "id":"rau-muong",
                "vietnameseName":"Rau muống",
                "categoryId":"vegetables",
                "usageLevel":"THUONG_DUNG",
                "verificationState":"DA_KIEM_CHUNG",
                "summary":"Hồ sơ kiểm thử",
                "sourceCount":2,
                "published":true,
                "vietnamRelevant":true,
                "verifiedVietnameseName":true,
                "verifiedIdentitySource":true,
                "verifiedMedia":true,
                "hasUsageClaim":false,
                "verifiedUsageSource":false,
                "highRisk":false,
                "verifiedSafetySource":false
              }]
            }
            """.trimIndent().toByteArray(),
            remote
        )
        assertEquals(1, payload.records.size)
        assertEquals(listOf("old-record"), payload.removedRecordIds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedIndexSchemaIsRejected() {
        LibraryUpdateIndexParser.parse(
            """{"schemaVersion":99,"packages":[]}"""
        )
    }
}
