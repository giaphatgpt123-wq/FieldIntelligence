package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCatalogTest {
    @Test fun offlineSearchHandlesVietnameseAccentAndScientificName() {
        assertEquals("mangifera-indica", SpeciesCatalog.search("xoai").single().id)
        assertEquals("musa-acuminata", SpeciesCatalog.search("MUSA ACUMINATA").single().id)
        assertEquals("cocos-nucifera", SpeciesCatalog.search("dua").single().id)
    }

    @Test fun sourcedGroupsReturnOnlyMatchingRecords() {
        val insects = SpeciesCatalog.search("", "Côn trùng")
        val animals = SpeciesCatalog.search("", "Động vật")
        assertTrue(insects.isNotEmpty() && insects.all { it.group == "Côn trùng" })
        assertTrue(animals.isNotEmpty() && animals.all { it.group == "Động vật" })
        assertEquals("aedes-aegypti", SpeciesCatalog.search("muoi van").single().id)
        assertEquals("varanus-salvator", SpeciesCatalog.search("ky da nuoc").single().id)
    }

    @Test fun missingGroupNeverReturnsInventedSpecies() {
        assertTrue(SpeciesCatalog.search("rắn").isEmpty())
        assertTrue(SpeciesCatalog.search("", "Nấm").isEmpty())
        assertTrue(SpeciesCatalog.records.all { it.sourceUrl.startsWith("https://") && it.sourceScope.isNotBlank() })
    }
}
