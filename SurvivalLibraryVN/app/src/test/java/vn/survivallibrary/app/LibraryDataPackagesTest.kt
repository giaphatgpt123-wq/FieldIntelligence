package vn.survivallibrary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDataPackagesTest {
    @Test
    fun catalogStartsAsNotInstalledWhenDatabaseIsEmpty() {
        val states = LibraryDataPackages.merge(emptyList())
        assertTrue(states.isNotEmpty())
        assertTrue(states.all { it.status == PackageInstallStatus.NOT_INSTALLED })
        assertTrue(states.all { it.recordCount == 0 && it.verifiedCount == 0 })
    }

    @Test
    fun installedStateIsMergedWithoutInventingCounts() {
        val installed = InstalledPackageState(
            packageId = "plants-core",
            version = 3,
            status = PackageInstallStatus.INSTALLED,
            recordCount = 120,
            verifiedCount = 120,
            installedAt = 1234L,
            checksum = "a".repeat(64),
            sourceUri = "https://example.org/plants-core-v3.zip"
        )
        val state = LibraryDataPackages.merge(listOf(installed)).first { it.descriptor.packageId == "plants-core" }
        assertEquals(PackageInstallStatus.INSTALLED, state.status)
        assertEquals(120, state.recordCount)
        assertEquals(120, state.verifiedCount)
    }

    @Test
    fun manifestRequiresKnownPackageHttpsAndValidChecksum() {
        val result = LibraryDataPackages.validate(
            LibraryPackageManifest(
                packageId = "unknown",
                version = 0,
                schemaVersion = 999,
                recordCount = 10,
                verifiedCount = 11,
                sha256 = "bad",
                sourceUri = "http://example.org/package.zip"
            )
        )
        assertFalse(result.valid)
        assertTrue(result.blockers.size >= 5)
    }

    @Test
    fun validManifestPassesStructuralGate() {
        val result = LibraryDataPackages.validate(
            LibraryPackageManifest(
                packageId = "mushrooms-core",
                version = 1,
                schemaVersion = LibraryDataPackages.LATEST_SCHEMA_VERSION,
                recordCount = 40,
                verifiedCount = 40,
                sha256 = "b".repeat(64),
                sourceUri = "https://example.org/mushrooms-core-v1.zip"
            )
        )
        assertTrue(result.valid)
        assertTrue(result.blockers.isEmpty())
    }
}
