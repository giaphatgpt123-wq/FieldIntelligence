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
            assertFalse(record.scopeNote.contains("tự điều trị an toàn", ignoreCase = true))
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
    fun gingerHasBothVietnameseAndEmaEvidenceWithoutDoseClaims() {
        val records = SpecialistEvidenceCatalog.forSpecies("zingiber-officinale")
        assertTrue(records.count { it.domain == EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE } >= 2)
        assertTrue(records.any { it.domain == EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH })
        assertTrue(records.all { !it.statement.contains("liều", ignoreCase = true) })
        assertTrue(records.any { it.title.contains("Sinh khương") })
        assertTrue(records.any { it.title.contains("Can khương") })
    }

    @Test
    fun hypericumMonographRemainsSeparateFromInteractionEvidence() {
        val records = SpecialistEvidenceCatalog.forSpecies("hypericum-perforatum")
        assertTrue(records.any { it.domain == EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH })
        assertTrue(InteractionCatalog.findForEntity("Hypericum perforatum").isNotEmpty())
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

    @Test
    fun rosaryPeaToxicologyEvidenceIsBoundedToSeedAbrin() {
        val record = SpecialistEvidenceCatalog.forSpecies("abrus-precatorius")
            .single { it.domain == EvidenceDomain.TOXICOLOGY }
        assertEquals("Hạt", record.plantPart)
        assertTrue(record.statement.contains("abrin", ignoreCase = true))
        assertTrue(record.sourceName.contains("CDC"))
        assertFalse(record.statement.contains("liều", ignoreCase = true))
        assertFalse(record.statement.contains("điều trị", ignoreCase = true))
    }
}
