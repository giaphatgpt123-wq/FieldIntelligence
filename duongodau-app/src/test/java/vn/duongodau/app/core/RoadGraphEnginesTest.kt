package vn.duongodau.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadGraphEnginesTest {
    private fun connection(from: String, to: String, node: String, state: Passability) = RoadConnection(
        fromSegmentId = from,
        toSegmentId = to,
        viaNodeId = node,
        vehicleRules = mapOf(VehicleClass.BUS_29 to state),
        confidence = 1.0
    )

    @Test
    fun escapeRoute_excludesUnknownConnections() {
        val engine = EscapeRouteEngine()
        val result = engine.findEscapeRoute(
            startSegmentId = "A",
            safeSegmentIds = setOf("D"),
            connections = listOf(
                connection("A", "B", "N1", Passability.UNKNOWN),
                connection("A", "C", "N2", Passability.PASS),
                connection("C", "D", "N3", Passability.CONDITIONAL)
            ),
            vehicle = VehicleClass.BUS_29
        )
        assertTrue(result.found)
        assertEquals(listOf("A", "C", "D"), result.segmentPath)
    }

    @Test
    fun escapeRoute_reportsNoVerifiedPathWhenOnlyUnknownExists() {
        val result = EscapeRouteEngine().findEscapeRoute(
            startSegmentId = "A",
            safeSegmentIds = setOf("B"),
            connections = listOf(connection("A", "B", "N1", Passability.UNKNOWN)),
            vehicle = VehicleClass.BUS_29
        )
        assertFalse(result.found)
    }

    @Test
    fun lastSafeDiversion_returnsAlternativeNodeBeforeBlockedFuture() {
        val connections = listOf(
            connection("A", "B", "N1", Passability.PASS),
            connection("A", "X", "NALT", Passability.PASS),
            connection("B", "C", "N2", Passability.PASS)
        )
        val node = LastSafeDiversionEngine().findLastSafeDiversionNode(
            plannedSegmentIds = listOf("A", "B", "C"),
            connections = connections,
            vehicle = VehicleClass.BUS_29
        )
        assertEquals("NALT", node)
    }
}
