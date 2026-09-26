package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialistEvidenceCatalogTest {
    @Test
    fun everyEvidenceRecordLinksToKnownSpeciesAndHttpsSource() {
        val ids = SpeciesCatalog.records.map { it.id }.toSet()
        SpecialistEvidenceCatalog.records.forEach { record ->
            assertTrue("Unknown species ${record.speciesId}", record.speciesId in ids)
            assertTrue(record.sourceUrl.startsWith("https://"))
            assertTrue(record.statement.isNotBlank())
            assertTrue(record.scopeNote.isNotBlank())
        }
    }

    @Test
    fun evidenceIdsAreUnique() {
        val ids = SpecialistEvidenceCatalog.records.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun curcumaHasVietnamTraditionalMedicineEvidence() {
        val records = SpecialistEvidenceCatalog.forSpecies("curcuma-longa")
        assertTrue(records.any { it.domain == EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE })
        assertTrue(records.any { it.domain == EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH })
    }

    @Test
    fun castorBeanToxicologyEvidenceIsBoundedToSeedRicin() {
        val record = SpecialistEvidenceCatalog.forSpecies("ricinus-communis")
            .single { it.domain == EvidenceDomain.TOXICOLOGY }
        assertEquals("Hạt", record.plantPart)
        assertTrue(record.statement.contains("ricin", ignoreCase = true))
        assertFalse(record.statement.contains("liều", ignoreCase = true))
        assertFalse(record.statement.contains("điều trị", ignoreCase = true))
    }
}
