package vn.duongodau.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataScopeControllerTest {
    private val controller = DataScopeController(
        listOf(
            AdminAdjacency("A", "B"),
            AdminAdjacency("A", "C"),
            AdminAdjacency("B", "D")
        )
    )

    @Test
    fun communeFocus_activatesOnlySelectedAndDirectNeighbors() {
        val plan = controller.selectCommune("80", "A")
        assertEquals(setOf("A", "B", "C"), plan.activeCommuneCodes)
        assertFalse(plan.isCommuneActive("D"))
    }

    @Test
    fun selectedCommune_getsFullStreams() {
        val plan = controller.selectCommune("80", "A")
        val primary = plan.partitions.first { it.adminCode == "A" }
        assertEquals(StreamState.ACTIVE_FULL, primary.state(DataStream.ROAD_GRAPH))
        assertEquals(StreamState.ACTIVE_FULL, primary.state(DataStream.ROAD_LIVE))
        assertEquals(StreamState.ACTIVE_FULL, primary.state(DataStream.ROAD_DISCOVERY))
    }

    @Test
    fun adjacentCommune_getsEdgeOnlyAndDiscoveryOff() {
        val plan = controller.selectCommune("80", "A")
        val adjacent = plan.partitions.first { it.adminCode == "B" }
        assertEquals(StreamState.ACTIVE_EDGE, adjacent.state(DataStream.ROAD_GRAPH))
        assertEquals(StreamState.ACTIVE_EDGE, adjacent.state(DataStream.ROAD_LIVE))
        assertEquals(StreamState.OFF, adjacent.state(DataStream.ROAD_DISCOVERY))
        assertEquals(StreamState.OFF, adjacent.state(DataStream.MAP_BASE))
    }

    @Test
    fun provinceFocus_doesNotWakeAllCommuneRoadStreams() {
        val plan = controller.selectProvince("80")
        assertEquals(ScopeMode.PROVINCE_FOCUS, plan.mode)
        assertTrue(plan.partitions.isEmpty())
        assertTrue(plan.activeCommuneCodes.isEmpty())
    }

    @Test
    fun routeCorridor_isExplicitAndLimitedToCorridor() {
        val plan = controller.routeCorridor("80", setOf("A", "B"), "D")
        assertEquals(ScopeMode.ROUTE_CORRIDOR, plan.mode)
        assertEquals(setOf("A", "B", "D"), plan.activeCommuneCodes)
        assertFalse(plan.isCommuneActive("C"))
    }
}
