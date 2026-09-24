package vn.fieldintel.data.emergency

import vn.fieldintel.domain.emergency.*

data class StartupRecoverySnapshot(
 val incident:IncidentEntity?,
 val recovery:RecoveryState,
 val lastLocation:LocationSnapshotEntity?
)

class StartupRecovery(private val dao:EmergencyDao){
 suspend fun load():StartupRecoverySnapshot {
  val incident=dao.activeIncident() ?: return StartupRecoverySnapshot(null,RecoveryState.NoActiveIncident,null)
  val journal=dao.events(incident.incidentId).map { e ->
   IncidentJournalEntry(e.incidentId,e.sequence,e.revision,e.eventType,e.payloadHash,e.wallTime,e.monotonicTime)
  }
  val recovery=if(journal.isEmpty()) RecoveryState.Conflict(incident.incidentId,"ACTIVE_INCIDENT_WITHOUT_EVENTS") else IncidentJournalValidator().validate(journal)
  return StartupRecoverySnapshot(incident,recovery,dao.latestLocation(incident.incidentId))
 }
}