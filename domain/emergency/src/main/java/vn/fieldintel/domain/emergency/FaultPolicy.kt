package vn.fieldintel.domain.emergency

enum class FaultKind {
 PROCESS_KILLED, REBOOTED, JOURNAL_CORRUPT, DB_UNAVAILABLE, STORAGE_FULL,
 PACK_CORRUPT, PACK_REVOKED, GNSS_JUMP, GNSS_LOST, GNSS_STALE,
 AI_EXCEPTION, OUT_OF_MEMORY, THERMAL_LIMIT, UPDATE_INTERRUPTED
}
enum class SafeDisposition { RECOVER, RECONFIRM, UNKNOWN, BLOCK, FALLBACK_MINIMAL_UI, USE_LAST_RELIABLE_POSITION }

data class FaultDecision(val disposition:SafeDisposition, val reason:String)

class EmergencyFaultPolicy {
 fun decide(fault:FaultKind):FaultDecision = when(fault){
  FaultKind.PROCESS_KILLED, FaultKind.REBOOTED -> FaultDecision(SafeDisposition.RECONFIRM,"RUNTIME_INTERRUPTED")
  FaultKind.JOURNAL_CORRUPT, FaultKind.DB_UNAVAILABLE -> FaultDecision(SafeDisposition.BLOCK,"STATE_NOT_TRUSTWORTHY")
  FaultKind.STORAGE_FULL -> FaultDecision(SafeDisposition.BLOCK,"CRITICAL_WRITE_NOT_GUARANTEED")
  FaultKind.PACK_CORRUPT, FaultKind.PACK_REVOKED, FaultKind.UPDATE_INTERRUPTED -> FaultDecision(SafeDisposition.FALLBACK_MINIMAL_UI,"NO_TRUSTED_ACTIVE_PACK")
  FaultKind.GNSS_JUMP, FaultKind.GNSS_LOST, FaultKind.GNSS_STALE -> FaultDecision(SafeDisposition.USE_LAST_RELIABLE_POSITION,"CURRENT_POSITION_NOT_TRUSTWORTHY")
  FaultKind.AI_EXCEPTION -> FaultDecision(SafeDisposition.UNKNOWN,"AI_OUTPUT_UNAVAILABLE")
  FaultKind.OUT_OF_MEMORY, FaultKind.THERMAL_LIMIT -> FaultDecision(SafeDisposition.FALLBACK_MINIMAL_UI,"RESOURCE_DEGRADED")
 }
}