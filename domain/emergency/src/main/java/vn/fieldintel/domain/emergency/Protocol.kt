package vn.fieldintel.domain.emergency

typealias ProtocolNodeId = String

enum class ResponderLevel { PUBLIC_FIRST_AID, TRAINED_FIRST_AIDER, PROFESSIONAL }
enum class NodeKind { OBSERVE, ACTION, DECISION, WARNING, COMMUNICATE, MONITOR, REASSESS, HANDOVER, TERMINAL }

data class ProtocolNode(val id: ProtocolNodeId, val kind: NodeKind, val critical: Boolean = false, val sourceClaimId: String? = null)
data class ProtocolEdge(val from: ProtocolNodeId, val to: ProtocolNodeId, val condition: String? = null, val priority: Int = 0)
data class ProtocolGraph(val id: String, val version: String, val responderLevel: ResponderLevel, val entryNode: ProtocolNodeId, val nodes: Map<ProtocolNodeId, ProtocolNode>, val edges: List<ProtocolEdge>, val revoked: Boolean = false)

enum class ViolationCode { REVOKED, NO_ENTRY_NODE, INVALID_EDGE, UNREACHABLE_TERMINAL, ORPHAN_CRITICAL_NODE, MISSING_CRITICAL_SOURCE }
data class ProtocolViolation(val code: ViolationCode, val nodeId: String? = null)
sealed interface ValidationResult { data object Valid: ValidationResult; data class Invalid(val violations: List<ProtocolViolation>): ValidationResult }

class ProtocolValidator {
 fun validate(g: ProtocolGraph): ValidationResult {
   val v=mutableListOf<ProtocolViolation>()
   if(g.revoked) v += ProtocolViolation(ViolationCode.REVOKED)
   if(g.entryNode !in g.nodes) v += ProtocolViolation(ViolationCode.NO_ENTRY_NODE, g.entryNode)
   g.edges.filter { it.from !in g.nodes || it.to !in g.nodes }.forEach { v += ProtocolViolation(ViolationCode.INVALID_EDGE, it.from) }
   g.nodes.values.filter { it.critical && it.sourceClaimId==null }.forEach { v += ProtocolViolation(ViolationCode.MISSING_CRITICAL_SOURCE,it.id) }
   if(g.entryNode in g.nodes) {
     val seen=mutableSetOf<String>(); val q=ArrayDeque<String>(); q.add(g.entryNode)
     while(q.isNotEmpty()){ val n=q.removeFirst(); if(seen.add(n)) g.edges.filter{it.from==n && it.to in g.nodes}.forEach{q.add(it.to)} }
     if(seen.none { g.nodes[it]?.kind==NodeKind.TERMINAL }) v += ProtocolViolation(ViolationCode.UNREACHABLE_TERMINAL)
     g.nodes.values.filter{it.critical && it.id !in seen}.forEach{v += ProtocolViolation(ViolationCode.ORPHAN_CRITICAL_NODE,it.id)}
   }
   return if(v.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(v)
 }
}
