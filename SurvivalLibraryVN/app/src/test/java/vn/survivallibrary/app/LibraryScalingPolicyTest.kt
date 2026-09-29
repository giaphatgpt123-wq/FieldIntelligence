package vn.survivallibrary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScalingPolicyTest {
    @Test
    fun shardIdsResolveToBasePackage() {
        assertEquals("plants-core", LibraryDataPackages.basePackageId("plants-core-s001"))
        assertEquals("aquatic-core", LibraryDataPackages.basePackageId("aquatic-core-s000123"))
        assertTrue(LibraryDataPackages.isShard("plants-core-s001"))
        assertFalse(LibraryDataPackages.isShard("plants-core"))
    }

    @Test
    fun invalidShardIdsAreRejected() {
        assertEquals(null, LibraryDataPackages.basePackageId("plants-core-s1"))
        assertEquals(null, LibraryDataPackages.basePackageId("plants-core-part001"))
        assertEquals(null, LibraryDataPackages.basePackageId("unknown-core-s001"))
    }

    @Test
    fun shardManifestIsAcceptedWithinBound() {
        val manifest = LibraryPackageManifest(
            packageId = "plants-core-s001",
            version = 1,
            schemaVersion = 1,
            recordCount = 1000,
            verifiedCount = 1000,
            sha256 = "a".repeat(64),
            sourceUri = "https://raw.githubusercontent.com/example/data.json"
        )
        assertTrue(LibraryDataPackages.validate(manifest).valid)
    }

    @Test
    fun oversizedShardIsBlocked() {
        val manifest = LibraryPackageManifest(
            packageId = "plants-core-s001",
            version = 1,
            schemaVersion = 1,
            recordCount = LibraryDataPackages.MAX_RECORDS_PER_SHARD + 1,
            verifiedCount = LibraryDataPackages.MAX_RECORDS_PER_SHARD + 1,
            sha256 = "b".repeat(64),
            sourceUri = "https://raw.githubusercontent.com/example/data.json"
        )
        val result = LibraryDataPackages.validate(manifest)
        assertFalse(result.valid)
        assertTrue(result.blockers.any { it.contains("chia shard") })
    }

    @Test
    fun uiPageSizeRemainsBounded() {
        assertTrue(PagingPolicy.PAGE_SIZE in 20..50)
        assertTrue(PagingPolicy.MAX_PAGE_SIZE <= 100)
    }
}
