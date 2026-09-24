package vn.fieldintel.domain.emergency

import org.junit.Assert.assertEquals
import org.junit.Test

class RescueHandoverBuilderTest {
 private val builder=RescueHandoverBuilder()
 @Test fun messageExistenceDoesNotMeanDelivered(){
  val m=RescueMessage("m1","i1","help",100,10)
  assertEquals(DeliveryState.QUEUED,builder.deliveryState(m,emptyList()))
 }
 @Test fun latestAttemptDeterminesObservedDeliveryState(){
  val m=RescueMessage("m1","i1","help",100,10)
  val a=listOf(
   CommunicationAttempt("a1","m1",CommunicationChannel.SMS,DeliveryState.ATTEMPTED,110,20),
   CommunicationAttempt("a2","m1",CommunicationChannel.SMS,DeliveryState.FAILED,120,30)
  )
  assertEquals(DeliveryState.FAILED,builder.deliveryState(m,a))
 }
 @Test fun handoverIsDeterministicTimeline(){
  val entries=listOf(
   HandoverEntry(2,HandoverFactType.CONTEXT,"location","trail",120,30),
   HandoverEntry(1,HandoverFactType.FACT,"observed","bleeding",110,20)
  )
  val h=builder.build("i1",entries)
  assertEquals(listOf(1L,2L),h.entries.map{it.sequence})
 }
}