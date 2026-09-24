package vn.fieldintel.domain.emergency

enum class CommunicationChannel { VOICE, SMS, DATA, LOCAL_SHARE, OFFLINE_QUEUE }
enum class DeliveryState { QUEUED, ATTEMPTED, SENT, DELIVERED, FAILED, UNKNOWN }
enum class HandoverFactType { FACT, CONTEXT }

data class RescueMessage(
 val messageId:String, val incidentId:String, val body:String,
 val createdWallTimeMs:Long, val createdMonotonicTimeMs:Long
)

data class CommunicationAttempt(
 val attemptId:String, val messageId:String, val channel:CommunicationChannel,
 val state:DeliveryState, val wallTimeMs:Long, val monotonicTimeMs:Long,
 val detail:String?=null
)

data class HandoverEntry(
 val sequence:Long, val type:HandoverFactType, val label:String, val value:String,
 val wallTimeMs:Long, val monotonicTimeMs:Long
)

data class HandoverRecord(val incidentId:String, val entries:List<HandoverEntry>)

class RescueHandoverBuilder {
 fun deliveryState(message:RescueMessage, attempts:List<CommunicationAttempt>):DeliveryState {
  val related=attempts.filter{it.messageId==message.messageId}.sortedBy{it.monotonicTimeMs}
  return related.lastOrNull()?.state ?: DeliveryState.QUEUED
 }
 fun build(incidentId:String, entries:List<HandoverEntry>):HandoverRecord {
  val ordered=entries.sortedWith(compareBy<HandoverEntry>{it.sequence}.thenBy{it.monotonicTimeMs})
  return HandoverRecord(incidentId,ordered)
 }
}