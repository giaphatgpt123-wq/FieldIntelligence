package vn.duongodau.app.core

import java.util.ArrayDeque

enum class GraphIssueType {
    MISSING_NODE,
    ISOLATED_SEGMENT,
    POSSIBLE_DATA_GAP,
    DUPLICATE_CONNECTION,
    UNKNOWN_CONNECTION
}

data class GraphIssue(
    val type: GraphIssueType,
    val entityId: String,
    val message: String,
    val severity: LiveSeverity
)

class RoadGraphValidator {
    fun validate(
        nodes: List<RoadNode>,
        segments: List<RoadSegment>,
        connections: List<RoadConnection>
    ): List<GraphIssue> {
        val issues = mutableListOf<GraphIssue>()
        val nodeIds = nodes.map { it.id }.toSet()
        val segmentIds = segments.map { it.id }.toSet()

        segments.forEach { segment ->
            if (segment.startNodeId !in nodeIds) {
                issues += GraphIssue(GraphIssueType.MISSING_NODE, segment.id, "Thiếu node đầu ${segment.startNodeId}", LiveSeverity.RISK)
            }
            if (segment.endNodeId !in nodeIds) {
                issues += GraphIssue(GraphIssueType.MISSING_NODE, segment.id, "Thiếu node cuối ${segment.endNodeId}", LiveSeverity.RISK)
            }
            if (segment.existence in setOf(RoadExistence.POSSIBLE_DATA_GAP, RoadExistence.UNKNOWN_CONTINUATION)) {
                issues += GraphIssue(GraphIssueType.POSSIBLE_DATA_GAP, segment.id, "Đoạn có khả năng mất dữ liệu hoặc chưa rõ phần tiếp nối", LiveSeverity.RISK)
            }
            val touching = connections.count { it.fromSegmentId == segment.id || it.toSegmentId == segment.id }
            if (touching == 0 && segments.size > 1) {
                issues += GraphIssue(GraphIssueType.ISOLATED_SEGMENT, segment.id, "Đoạn đang tách khỏi road graph", LiveSeverity.ATTENTION)
            }
        }

        val seen = mutableSetOf<Triple<String, String, String>>()
        connections.forEach { connection ->
            if (connection.fromSegmentId !in segmentIds || connection.toSegmentId !in segmentIds || connection.viaNodeId !in nodeIds) {
                issues += GraphIssue(GraphIssueType.UNKNOWN_CONNECTION, "${connection.fromSegmentId}->${connection.toSegmentId}", "Kết nối tham chiếu đối tượng chưa tồn tại", LiveSeverity.RISK)
            }
            val key = Triple(connection.fromSegmentId, connection.toSegmentId, connection.viaNodeId)
            if (!seen.add(key)) {
                issues += GraphIssue(GraphIssueType.DUPLICATE_CONNECTION, "${connection.fromSegmentId}->${connection.toSegmentId}", "Kết nối bị trùng", LiveSeverity.ATTENTION)
            }
        }
        return issues.distinct()
    }
}

data class RemoteAreaAssessment(
    val enabled: Boolean,
    val score: Double,
    val reasons: List<String>
)

class RemoteAreaEngine {
    /**
     * A conservative heuristic. It is not a geographic truth classifier; it decides when
     * the app should switch to stricter checking and earlier warnings.
     */
    fun assess(segments: List<RoadSegment>, connections: List<RoadConnection>): RemoteAreaAssessment {
        if (segments.isEmpty()) return RemoteAreaAssessment(true, 1.0, listOf("Không có dữ liệu mạng đường"))
        val unknownRatio = segments.count {
            it.existence !in setOf(RoadExistence.CONFIRMED_ROAD) ||
                !it.surface.isKnown || !it.widthM.isKnown
        }.toDouble() / segments.size
        val degree = connections.size.toDouble() / segments.size
        val reasons = mutableListOf<String>()
        var score = 0.0
        if (unknownRatio >= 0.35) {
            score += 0.45
            reasons += "Tỷ lệ thuộc tính/đường chưa xác minh cao"
        }
        if (degree < 1.0) {
            score += 0.35
            reasons += "Ít kết nối và tuyến thay thế"
        }
        if (segments.any { it.existence in setOf(RoadExistence.POSSIBLE_DATA_GAP, RoadExistence.UNKNOWN_CONTINUATION, RoadExistence.POSSIBLE_PATH) }) {
            score += 0.20
            reasons += "Có đoạn mất dữ liệu hoặc chưa xác minh tiếp nối"
        }
        return RemoteAreaAssessment(enabled = score >= 0.50, score = score.coerceAtMost(1.0), reasons = reasons)
    }
}

data class EscapeRouteResult(
    val found: Boolean,
    val segmentPath: List<String>,
    val reason: String
)

class EscapeRouteEngine {
    /** Find a verified/conditional segment path to any requested safe segment. UNKNOWN is excluded. */
    fun findEscapeRoute(
        startSegmentId: String,
        safeSegmentIds: Set<String>,
        connections: List<RoadConnection>,
        vehicle: VehicleClass
    ): EscapeRouteResult {
        if (startSegmentId in safeSegmentIds) return EscapeRouteResult(true, listOf(startSegmentId), "Đang ở đoạn an toàn")

        val adjacency = mutableMapOf<String, MutableList<String>>()
        connections.forEach { c ->
            val state = c.vehicleRules[vehicle] ?: Passability.UNKNOWN
            if (state == Passability.PASS || state == Passability.CONDITIONAL) {
                adjacency.getOrPut(c.fromSegmentId) { mutableListOf() }.add(c.toSegmentId)
            }
        }

        val queue = ArrayDeque<String>()
        val previous = mutableMapOf<String, String?>()
        queue.add(startSegmentId)
        previous[startSegmentId] = null

        var target: String? = null
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current in safeSegmentIds) {
                target = current
                break
            }
            adjacency[current].orEmpty().forEach { next ->
                if (next !in previous) {
                    previous[next] = current
                    queue.add(next)
                }
            }
        }

        if (target == null) return EscapeRouteResult(false, emptyList(), "Không tìm thấy tuyến thoát đã xác minh cho phương tiện")
        val reversed = mutableListOf<String>()
        var cursor: String? = target
        while (cursor != null) {
            reversed += cursor
            cursor = previous[cursor]
        }
        return EscapeRouteResult(true, reversed.asReversed(), "Tìm thấy tuyến thoát")
    }
}

class LastSafeDiversionEngine {
    /**
     * Returns the last node along a planned segment path where another known PASS/CONDITIONAL
     * connection exists for the same vehicle. This is the decision point to warn before.
     */
    fun findLastSafeDiversionNode(
        plannedSegmentIds: List<String>,
        connections: List<RoadConnection>,
        vehicle: VehicleClass
    ): String? {
        if (plannedSegmentIds.size < 2) return null
        for (index in plannedSegmentIds.size - 2 downTo 0) {
            val current = plannedSegmentIds[index]
            val plannedNext = plannedSegmentIds[index + 1]
            val alternative = connections.firstOrNull { c ->
                c.fromSegmentId == current &&
                    c.toSegmentId != plannedNext &&
                    (c.vehicleRules[vehicle] == Passability.PASS || c.vehicleRules[vehicle] == Passability.CONDITIONAL)
            }
            if (alternative != null) return alternative.viaNodeId
        }
        return null
    }
}
