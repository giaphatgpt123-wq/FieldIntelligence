package vn.fieldintel.domain.emergency
import org.junit.Assert.*
import org.junit.Test
class ProtocolValidatorTest {
 @Test fun validGraphPasses(){
  val nodes=listOf(ProtocolNode("start",NodeKind.OBSERVE),ProtocolNode("end",NodeKind.TERMINAL)).associateBy{it.id}
  assertEquals(ValidationResult.Valid, ProtocolValidator().validate(ProtocolGraph("p","1",ResponderLevel.PUBLIC_FIRST_AID,"start",nodes,listOf(ProtocolEdge("start","end")))))
 }
 @Test fun revokedNeverValid(){
  val nodes=mapOf("start" to ProtocolNode("start",NodeKind.TERMINAL))
  assertTrue(ProtocolValidator().validate(ProtocolGraph("p","1",ResponderLevel.PUBLIC_FIRST_AID,"start",nodes,emptyList(),true)) is ValidationResult.Invalid)
 }
}
