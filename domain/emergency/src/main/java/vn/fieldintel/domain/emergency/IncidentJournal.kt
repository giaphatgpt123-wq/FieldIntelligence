package vn.fieldintel.domain.emergency

/**
 * Append-only emergency journal contract.
 * The journal is authoritative for recovery sequencing; events are never updated in place.
 */
data class IncidentJournalEntry(
    val incidentId: String,
    val sequence: Long,
    val revision: Long,
    val eventType: String,
    val payloadHash: String,
    val wallTimeMs: Long,
    val monotonicTimeMs: Long
)

sealed interface RecoveryState {
    data object NoActiveIncident : RecoveryState
    data class Resumable(
        val incidentId: String,
        val lastSequence: Long,
        val lastRevision: Long
    ) : RecoveryState
    data class ReconfirmRequired(
        val incidentId: String,
        val reason: String
    ) : RecoveryState
    data class Conflict(
        val incidentId: String,
        val reason: String
    ) : RecoveryState
}

class IncidentJournalValidator {
    fun validate(entries: List<IncidentJournalEntry>): RecoveryState {
        if (entries.isEmpty()) return RecoveryState.NoActiveIncident
        val ordered = entries.sortedBy { it.sequence }
        val incidentId = ordered.first().incidentId
        if (ordered.any { it.incidentId != incidentId }) {
            return RecoveryState.Conflict(incidentId, "MIXED_INCIDENT_IDS")
        }
        ordered.forEachIndexed { index, entry ->
            if (index > 0) {
                val previous = ordered[index - 1]
                if (entry.sequence != previous.sequence + 1) {
                    return RecoveryState.Conflict(incidentId, "SEQUENCE_GAP")
                }
                if (entry.revision < previous.revision) {
                    return RecoveryState.Conflict(incidentId, "REVISION_REGRESSION")
                }
                if (entry.monotonicTimeMs < previous.monotonicTimeMs) {
                    return RecoveryState.Conflict(incidentId, "MONOTONIC_TIME_REGRESSION")
                }
            }
        }
        val last = ordered.last()
        val physicalActionActive = last.eventType in setOf(
            "INTERVENTION_STARTED",
            "PHYSICAL_ACTION_ACTIVE"
        )
        return if (physicalActionActive) {
            RecoveryState.ReconfirmRequired(incidentId, "ACTIVE_PHYSICAL_ACTION")
        } else {
            RecoveryState.Resumable(incidentId, last.sequence, last.revision)
        }
    }
}
