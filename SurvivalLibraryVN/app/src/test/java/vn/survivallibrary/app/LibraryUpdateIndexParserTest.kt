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
                "packageUrl":"https://example.org/plants.json"
              }]
            }
            """.trimIndent()
        )
        assertEquals(1, parsed.size)
        assertEquals("plants-core", parsed.first().packageId)
        assertEquals(2, parsed.first().version)
        assertEquals(10, parsed.first().recordCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedIndexSchemaIsRejected() {
        LibraryUpdateIndexParser.parse(
            """{"schemaVersion":99,"packages":[]}"""
        )
    }
}
