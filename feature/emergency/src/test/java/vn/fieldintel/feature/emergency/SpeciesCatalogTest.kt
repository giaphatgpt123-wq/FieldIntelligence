package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCatalogTest {
    @Test fun offlineSearchHandlesVietnameseAccentAndScientificName() {
        assertEquals("mangifera-indica", SpeciesCatalog.search("xoai").single().id)
        assertEquals("musa-acuminata", SpeciesCatalog.search("MUSA ACUMINATA").single().id)
        assertEquals("cocos-nucifera", SpeciesCatalog.search("dua").single().id)
        assertEquals("centella-asiatica", SpeciesCatalog.search("rau ma").single().id)
    }

    @Test fun sourcedGroupsReturnOnlyMatchingRecords() {
        SpeciesCatalog.groups.forEach { group ->
            val matches = SpeciesCatalog.search("", group)
            assertTrue("Expected records for $group", matches.isNotEmpty())
            assertTrue(matches.all { it.group == group })
            assertEquals(matches.size, SpeciesCatalog.countByGroup(group))
        }
        assertEquals("aedes-aegypti", SpeciesCatalog.search("muoi van").single().id)
        assertEquals("varanus-salvator", SpeciesCatalog.search("ky da nuoc").single().id)
        assertEquals("ganoderma-lucidum", SpeciesCatalog.search("ganoderma lucidum").single().id)
    }

    @Test fun medicinalPlantTaxonomyCanLinkToIndependentSafetyEvidence() {
        val hypericum = SpeciesCatalog.search("Hypericum perforatum").single()
        val tea = SpeciesCatalog.search("Camellia sinensis").single()
        assertTrue(InteractionCatalog.findForEntity(hypericum.scientificName).size >= 4)
        assertTrue(InteractionCatalog.findForEntity(tea.scientificName).any { it.id == "green-tea-nadolol-nccih" })
        assertTrue(hypericum.sourceScope.contains("nguồn y khoa riêng", ignoreCase = true))
        assertTrue(tea.sourceScope.contains("không dùng hồ sơ taxonomy", ignoreCase = true))
    }

    @Test fun sourceProvenanceAndUnknownBehaviorRemainExplicit() {
        assertTrue(SpeciesCatalog.search("rắn").isEmpty())
        assertTrue(SpeciesCatalog.records.all {
            it.sourceUrl.startsWith("https://") && it.sourceName.isNotBlank() && it.sourceScope.isNotBlank()
        })
        assertTrue(SpeciesCatalog.records.none {
            it.sourceScope.contains("ăn được", ignoreCase = true) && !it.sourceScope.contains("không", ignoreCase = true)
        })
    }
}
