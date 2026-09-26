package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCollectionsTest {
    @Test
    fun collectionIdsAreUniqueAndResolve() {
        val ids = LibraryCollections.items.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id -> assertEquals(id, LibraryCollections.byId(id)?.id) }
    }

    @Test
    fun everyCuratedRecordExistsInStarterCatalog() {
        val catalogIds = SpeciesCatalog.records.map { it.id }.toSet()
        LibraryCollections.items.forEach { collection ->
            assertTrue(
                "Unknown record in ${collection.id}",
                collection.recordIds.all { it in catalogIds }
            )
        }
    }

    @Test
    fun highRiskCollectionsRequireSpecialistEvidence() {
        val medicinal = LibraryCollections.byId("traditional-medicine")!!.recordIds
        val monographs = LibraryCollections.byId("herbal-monographs")!!.recordIds
        val toxic = LibraryCollections.byId("toxic-plants")!!.recordIds
        assertFalse(medicinal.isEmpty())
        assertFalse(monographs.isEmpty())
        assertFalse(toxic.isEmpty())
        assertTrue(medicinal.all { id ->
            SpecialistEvidenceCatalog.forSpecies(id).any { it.domain == EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE }
        })
        assertTrue(monographs.all { id ->
            SpecialistEvidenceCatalog.forSpecies(id).any { it.domain == EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH }
        })
        assertTrue(toxic.all { id ->
            SpecialistEvidenceCatalog.forSpecies(id).any { it.domain == EvidenceDomain.TOXICOLOGY }
        })
    }

    @Test
    fun evidenceBackedCollectionsExposeKnownRecords() {
        val traditional = LibraryCollections.recordsFor("traditional-medicine")
        assertTrue(traditional.any { it.id == "curcuma-longa" })
        assertTrue(traditional.any { it.id == "zingiber-officinale" })

        val monographs = LibraryCollections.recordsFor("herbal-monographs")
        assertTrue(monographs.any { it.id == "curcuma-longa" })
        assertTrue(monographs.any { it.id == "zingiber-officinale" })
        assertTrue(monographs.any { it.id == "hypericum-perforatum" })

        val toxic = LibraryCollections.recordsFor("toxic-plants")
        assertTrue(toxic.any { it.id == "ricinus-communis" })
        assertTrue(toxic.any { it.id == "abrus-precatorius" })
        assertTrue(toxic.any { it.id == "nerium-oleander" })
    }

    @Test
    fun curatedCollectionsExposeExpectedCoreGroups() {
        assertTrue(LibraryCollections.recordsFor("fruit-crops").any { it.id == "mangifera-indica" })
        assertTrue(LibraryCollections.recordsFor("staple-crops").any { it.id == "oryza-sativa" })
        assertTrue(LibraryCollections.recordsFor("mushrooms").all { it.group == "Nấm" })
        assertTrue(LibraryCollections.recordsFor("insects").all { it.group == "Côn trùng" })
        assertTrue(LibraryCollections.recordsFor("animals").all { it.group == "Động vật" })
    }
}
