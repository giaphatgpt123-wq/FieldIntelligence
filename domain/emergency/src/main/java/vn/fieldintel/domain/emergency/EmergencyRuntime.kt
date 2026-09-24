package vn.fieldintel.domain.emergency

data class EmergencyRuntimeState(
 val pack:PackSelection, val position:PositionAssessment, val recovery:RecoveryState
)

sealed interface EmergencyCommand {
 data class StartIncident(val incidentId:String):EmergencyCommand
 data class RecordEvent(val entry:IncidentJournalEntry):EmergencyCommand
 data object Resume:EmergencyCommand
}

sealed interface RuntimeGate {
 data object Ready:RuntimeGate
 data class Blocked(val reason:String):RuntimeGate
 data class Reconfirm(val reason:String):RuntimeGate
}

class EmergencyRuntimeCoordinator {
 fun gate(state:EmergencyRuntimeState):RuntimeGate {
  return when(val r=state.recovery){
   is RecoveryState.Conflict -> RuntimeGate.Blocked("RECOVERY_CONFLICT:"+r.reason)
   is RecoveryState.ReconfirmRequired -> RuntimeGate.Reconfirm(r.reason)
   else -> if(state.pack.slot==PackSlot.MINIMAL_UI) RuntimeGate.Blocked("NO_SAFE_PROTOCOL_PACK") else RuntimeGate.Ready
  }
 }
 fun usablePosition(state:EmergencyRuntimeState):PositionFix? {
  return when(state.position.health){
   PositionHealth.GOOD,PositionHealth.DEGRADED -> state.position.current
   PositionHealth.POOR,PositionHealth.LOST,PositionHealth.SUSPECT -> state.position.lastReliable
  }
 }
}