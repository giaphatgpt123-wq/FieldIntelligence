package vn.fieldintel.data.emergency

import androidx.room.withTransaction
import vn.fieldintel.domain.emergency.IncidentJournalEntry

sealed interface AppendResult {
 data class Appended(val sequence:Long,val revision:Long):AppendResult
 data class Rejected(val reason:String):AppendResult
}

class EmergencyJournalStore(private val db:EmergencyDatabase){
 suspend fun append(entry:IncidentJournalEntry):AppendResult = db.withTransaction {
  val dao=db.emergencyDao()
  val incident=dao.incident(entry.incidentId) ?: return@withTransaction AppendResult.Rejected("INCIDENT_NOT_FOUND")
  val existing=dao.events(entry.incidentId)
  val last=existing.lastOrNull()
  val expected=(last?.sequence ?: 0L)+1L
  if(entry.sequence!=expected) return@withTransaction AppendResult.Rejected("SEQUENCE_MISMATCH")
  if(last!=null && entry.revision<last.revision) return@withTransaction AppendResult.Rejected("REVISION_REGRESSION")
  if(last!=null && entry.monotonicTimeMs<last.monotonicTime) return@withTransaction AppendResult.Rejected("MONOTONIC_TIME_REGRESSION")
  if(entry.revision<incident.revision) return@withTransaction AppendResult.Rejected("INCIDENT_REVISION_REGRESSION")
  val advanced=dao.advanceRevision(entry.incidentId,entry.revision)
  if(advanced!=1) return@withTransaction AppendResult.Rejected("INCIDENT_REVISION_UPDATE_FAILED")
  dao.insertEvent(IncidentEventEntity(
   eventId=entry.incidentId+":"+entry.sequence, incidentId=entry.incidentId,
   sequence=entry.sequence, revision=entry.revision, eventType=entry.eventType,
   payloadHash=entry.payloadHash, wallTime=entry.wallTimeMs, monotonicTime=entry.monotonicTimeMs
  ))
  AppendResult.Appended(entry.sequence,entry.revision)
 }
}