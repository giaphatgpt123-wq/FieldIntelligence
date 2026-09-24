package vn.fieldintel.domain.emergency

import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentJournalValidatorTest {
    private val validator = IncidentJournalValidator()

    @Test fun contiguousJournalIsResumable() {
        val entries = listOf(
            IncidentJournalEntry("i1",1,1,"INCIDENT_STARTED","a",100,10),
            IncidentJournalEntry("i1",2,2,"OBSERVATION_RECORDED","b",110,20)
        )
        assertTrue(validator.validate(entries) is RecoveryState.Resumable)
    }

    @Test fun sequenceGapIsConflict() {
        val entries = listOf(
            IncidentJournalEntry("i1",1,1,"INCIDENT_STARTED","a",100,10),
            IncidentJournalEntry("i1",3,2,"OBSERVATION_RECORDED","b",110,20)
        )
        assertTrue(validator.validate(entries) is RecoveryState.Conflict)
    }

    @Test fun activePhysicalActionRequiresReconfirmation() {
        val entries = listOf(
            IncidentJournalEntry("i1",1,1,"INCIDENT_STARTED","a",100,10),
            IncidentJournalEntry("i1",2,2,"INTERVENTION_STARTED","b",110,20)
        )
        assertTrue(validator.validate(entries) is RecoveryState.ReconfirmRequired)
    }
}
