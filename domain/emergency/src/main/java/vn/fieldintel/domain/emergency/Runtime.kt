package vn.fieldintel.domain.emergency

data class ProtocolSession(val sessionId:String, val protocolId:String, val protocolVersion:String, val currentNodeId:String, val revision:Long)
data class RuntimeEvent(val key:String, val value:String?=null)
data class Transition(val from:String, val to:String, val nextRevision:Long)

class ProtocolRunner {
 fun next(graph:ProtocolGraph, session:ProtocolSession, event:RuntimeEvent):Transition? {
   require(!graph.revoked)
   val eligible=graph.edges.filter { it.from==session.currentNodeId && (it.condition==null || it.condition==event.key) }
   val edge=eligible.maxByOrNull { it.priority } ?: return null
   return Transition(edge.from, edge.to, session.revision+1)
 }
}
