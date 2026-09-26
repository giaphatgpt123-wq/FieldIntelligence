package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
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
    fun highRiskCollectionsDoNotGetTaxonomyOnlyAutofill() {
        assertTrue(LibraryCollections.byId("traditional-medicine")!!.recordIds.isEmpty())
        assertTrue(LibraryCollections.byId("toxic-plants")!!.recordIds.isEmpty())
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
