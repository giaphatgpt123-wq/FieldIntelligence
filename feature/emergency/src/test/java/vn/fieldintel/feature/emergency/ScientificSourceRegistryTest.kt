package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificSourceRegistryTest {
    @Test fun onlyExplicitlyOpenSourcesAreBulkReady() {
        val bulk = ScientificSourceRegistry.bulkReady()
        assertEquals(listOf("wfo-taxonomic-backbone"), bulk.map { it.id })
        assertTrue(bulk.all { it.access == IngestionAccess.BULK_OPEN })
    }

    @Test fun vietnamAgricultureAndTraditionalMedicineSourcesAreRegistered() {
        val agriculture = ScientificSourceRegistry.forDomain(ScientificDomain.AGRICULTURE)
        val traditional = ScientificSourceRegistry.forDomain(ScientificDomain.TRADITIONAL_MEDICINE)
        assertTrue(agriculture.any { it.id == "prc-vietnam-genebank" })
        assertTrue(traditional.any { it.id == "moh-traditional-medicine" })
        assertTrue(traditional.any { it.id == "vietnam-pharmacopoeia-vi" })
        assertTrue(traditional.any { it.id == "national-institute-medicinal-materials" })
    }

    @Test fun everySourceRetainsProvenanceAndImportScope() {
        assertTrue(ScientificSourceRegistry.sources.isNotEmpty())
        assertTrue(ScientificSourceRegistry.sources.all {
            it.id.isNotBlank() &&
                it.authority.isNotBlank() &&
                it.url.startsWith("https://") &&
                it.licenseNote.isNotBlank() &&
                it.importScope.isNotBlank()
        })
    }
}
