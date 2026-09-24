package vn.fieldintel.domain.emergency

data class RecoveryCheckpoint(val incidentId:String,val lastSequence:Long,val lastRevision:Long,val monotonicTimeMs:Long)

sealed interface ReconcileResult {
 data class Consistent(val incidentId:String,val sequence:Long,val revision:Long):ReconcileResult
 data class RecoverFromJournal(val incidentId:String,val sequence:Long,val revision:Long):ReconcileResult
 data class Conflict(val incidentId:String,val reason:String):ReconcileResult
}

class RecoveryReconciler {
 fun reconcile(checkpoint:RecoveryCheckpoint?, entries:List<IncidentJournalEntry>):ReconcileResult {
  if(entries.isEmpty()) return ReconcileResult.Conflict(checkpoint?.incidentId ?: "UNKNOWN","NO_JOURNAL")
  val state=IncidentJournalValidator().validate(entries)
  if(state is RecoveryState.Conflict) return ReconcileResult.Conflict(state.incidentId,state.reason)
  val last=entries.maxByOrNull{it.sequence} ?: return ReconcileResult.Conflict("UNKNOWN","NO_JOURNAL")
  if(checkpoint==null) return ReconcileResult.RecoverFromJournal(last.incidentId,last.sequence,last.revision)
  if(checkpoint.incidentId!=last.incidentId) return ReconcileResult.Conflict(last.incidentId,"CHECKPOINT_INCIDENT_MISMATCH")
  if(checkpoint.lastSequence>last.sequence || checkpoint.lastRevision>last.revision) return ReconcileResult.Conflict(last.incidentId,"CHECKPOINT_AHEAD_OF_JOURNAL")
  return if(checkpoint.lastSequence==last.sequence && checkpoint.lastRevision==last.revision)
   ReconcileResult.Consistent(last.incidentId,last.sequence,last.revision)
  else ReconcileResult.RecoverFromJournal(last.incidentId,last.sequence,last.revision)
 }
}