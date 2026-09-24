package vn.fieldintel.domain.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EmergencyFaultPolicyTest {
 private val p=EmergencyFaultPolicy()
 @Test fun everyFaultHasFailClosedDisposition(){
  FaultKind.entries.forEach{ f ->
   val d=p.decide(f).disposition
   assertNotEquals("Fault must never imply safe",null,d)
  }
 }
 @Test fun corruptOrRevokedPackFallsBackMinimal(){
  assertEquals(SafeDisposition.FALLBACK_MINIMAL_UI,p.decide(FaultKind.PACK_CORRUPT).disposition)
  assertEquals(SafeDisposition.FALLBACK_MINIMAL_UI,p.decide(FaultKind.PACK_REVOKED).disposition)
 }
 @Test fun gnssFailuresUseLastReliablePosition(){
  listOf(FaultKind.GNSS_JUMP,FaultKind.GNSS_LOST,FaultKind.GNSS_STALE).forEach{
   assertEquals(SafeDisposition.USE_LAST_RELIABLE_POSITION,p.decide(it).disposition)
  }
 }
 @Test fun corruptStateBlocksInsteadOfGuessing(){
  assertEquals(SafeDisposition.BLOCK,p.decide(FaultKind.JOURNAL_CORRUPT).disposition)
  assertEquals(SafeDisposition.BLOCK,p.decide(FaultKind.DB_UNAVAILABLE).disposition)
 }
 @Test fun aiFailureBecomesUnknown(){
  assertEquals(SafeDisposition.UNKNOWN,p.decide(FaultKind.AI_EXCEPTION).disposition)
 }
}