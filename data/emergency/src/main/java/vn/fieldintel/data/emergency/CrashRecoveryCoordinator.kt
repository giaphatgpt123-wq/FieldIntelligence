package vn.fieldintel.data.emergency

import vn.fieldintel.domain.emergency.*

data class CrashRecoveryResult(val snapshot:StartupRecoverySnapshot,val reconciliation:ReconcileResult?)

class CrashRecoveryCoordinator(private val startup:StartupRecovery,private val checkpoints:FileRecoveryCheckpointStore){
 suspend fun recover():CrashRecoveryResult {
  val snapshot=startup.load()
  val incident=snapshot.incident ?: return CrashRecoveryResult(snapshot,null)
  if(snapshot.recovery is RecoveryState.Conflict){
   val r=snapshot.recovery as RecoveryState.Conflict
   return CrashRecoveryResult(snapshot,ReconcileResult.Conflict(incident.incidentId,r.reason))
  }
  return CrashRecoveryResult(snapshot,null)
 }
 fun reconcile(checkpoint:RecoveryCheckpoint?, entries:List<IncidentJournalEntry>):ReconcileResult = RecoveryReconciler().reconcile(checkpoint,entries)
 fun checkpoint():RecoveryCheckpoint? = checkpoints.read()
}