package vn.fieldintel.domain.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EmergencyRuntimeCoordinatorTest {
 private val c=EmergencyRuntimeCoordinator()
 private val active=PackSelection(EmergencyPack("p","1",true,false,PackSlot.ACTIVE),PackSlot.ACTIVE,"ok")
 private val pos=PositionAssessment(null,null,PositionHealth.LOST,null,"none")
 @Test fun recoveryConflictBlocks(){
  val s=EmergencyRuntimeState(active,pos,RecoveryState.Conflict("i","gap"))
  assert(c.gate(s) is RuntimeGate.Blocked)
 }
 @Test fun physicalActionRecoveryRequiresReconfirm(){
  val s=EmergencyRuntimeState(active,pos,RecoveryState.ReconfirmRequired("i","active"))
  assert(c.gate(s) is RuntimeGate.Reconfirm)
 }
 @Test fun minimalUiCannotExecuteProtocol(){
  val m=PackSelection(null,PackSlot.MINIMAL_UI,"none")
  assert(c.gate(EmergencyRuntimeState(m,pos,RecoveryState.NoActiveIncident)) is RuntimeGate.Blocked)
 }
 @Test fun suspectCurrentUsesLastReliable(){
  val current=PositionFix(11.0,106.0,5.0,2,2)
  val last=PositionFix(10.0,105.0,5.0,1,1)
  val p=PositionAssessment(current,last,PositionHealth.SUSPECT,0,"jump")
  assertSame(last,c.usablePosition(EmergencyRuntimeState(active,p,RecoveryState.NoActiveIncident)))
 }
}