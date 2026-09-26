package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCatalogTest {
    @Test fun offlineSearchHandlesVietnameseAccentAndScientificName() {
        assertEquals("mangifera-indica", SpeciesCatalog.search("xoai").single().id)
        assertEquals("musa-acuminata", SpeciesCatalog.search("MUSA ACUMINATA").single().id)
        assertTrue(SpeciesCatalog.search("dua").map { it.id }.containsAll(listOf("cocos-nucifera", "ananas-comosus")))
        assertEquals("centella-asiatica", SpeciesCatalog.search("rau ma").single().id)
        assertEquals("oryza-sativa", SpeciesCatalog.search("lua").single().id)
        assertEquals("zingiber-officinale", SpeciesCatalog.search("gung").single().id)
        assertEquals("curcuma-longa", SpeciesCatalog.search("nghe").single().id)
        assertEquals("cymbopogon-citratus", SpeciesCatalog.search("sa").single().id)
        assertEquals("carica-papaya", SpeciesCatalog.search("du du").single().id)
        assertEquals("psidium-guajava", SpeciesCatalog.search("psidium guajava").single().id)
    }

    @Test fun freshwaterFishCollectionIsCompleteAndSearchableOffline() {
        val fish = LibraryCollections.recordsFor("freshwater-fish")
        assertEquals(20, fish.size)
        assertEquals(fish.size, fish.map { it.id }.toSet().size)
        assertTrue(fish.all { it.group == "Cá nước ngọt" && it.sourceUrl.startsWith("https://www.gbif.org/taxon/") })
        assertEquals("anabas-testudineus", SpeciesCatalog.search("ca ro dong", "Cá nước ngọt").single().id)
        assertEquals("pangasianodon-hypophthalmus", SpeciesCatalog.search("pangasianodon hypophthalmus", "Cá nước ngọt").single().id)
        assertEquals("chitala-ornata", SpeciesCatalog.search("ca that lat cuom", "Cá nước ngọt").single().id)
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

    @Test fun agricultureAndMedicinalExpansionKeepsTaxonomySeparateFromUseClaims() {
        val ids = SpeciesCatalog.records.map { it.id }.toSet()
        assertTrue(setOf(
            "oryza-sativa", "zingiber-officinale", "curcuma-longa",
            "cymbopogon-citratus", "carica-papaya", "psidium-guajava"
        ).all { it in ids })
        assertTrue(SpeciesCatalog.records.filter { it.id in ids }.all { it.sourceUrl.startsWith("https://") })
        assertTrue(SpeciesCatalog.search("Curcuma longa").single().sourceScope.contains("không suy ra", ignoreCase = true))
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
