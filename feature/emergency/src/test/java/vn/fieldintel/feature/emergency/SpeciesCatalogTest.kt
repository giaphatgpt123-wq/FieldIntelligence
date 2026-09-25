package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCatalogTest {
    @Test fun offlineSearchHandlesVietnameseAccentAndScientificName() {
        assertEquals("mangifera-indica", SpeciesCatalog.search("xoai").single().id)
        assertEquals("musa-acuminata", SpeciesCatalog.search("MUSA ACUMINATA").single().id)
    }
    @Test fun missingGroupNeverReturnsInventedSpecies() {
        assertTrue(SpeciesCatalog.search("rắn").isEmpty())
        assertTrue(SpeciesCatalog.search("", "Nấm").isEmpty())
        assertTrue(SpeciesCatalog.records.all { it.sourceUrl.startsWith("https://") && it.sourceScope.isNotBlank() })
    }
}
