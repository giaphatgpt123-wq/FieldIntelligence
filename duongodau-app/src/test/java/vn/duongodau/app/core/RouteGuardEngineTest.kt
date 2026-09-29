package vn.duongodau.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGuardEngineTest {
    private fun segment(maxWeightT: Double? = null): RoadSegment = RoadSegment(
        id = "S1",
        roadId = "R1",
        startNodeId = "A",
        endNodeId = "B",
        geometry = listOf(GeoPoint(10.0, 106.0), GeoPoint(10.01, 106.01)),
        adminCodes = listOf("TEST"),
        roadClass = AttributeValue(RoadClass.COMMUNE, 1.0),
        surface = AttributeValue(SurfaceType.CONCRETE, 1.0),
        widthM = AttributeValue(5.0, 1.0),
        maxWeightT = AttributeValue(maxWeightT, if (maxWeightT == null) 0.0 else 1.0),
        condition = AttributeValue("GOOD", 1.0),
        existence = RoadExistence.CONFIRMED_ROAD,
        existenceConfidence = 1.0,
        geometryConfidence = 1.0,
        verification = VerificationState.CONFIRMED
    )

    @Test
    fun unknownBridgeWeight_neverPassesForKnownGrossWeight() {
        val bridge = RoadStructure(
            id = "B1",
            segmentId = "S1",
            name = "Cầu thử nghiệm",
            type = StructureType.BRIDGE,
            maxWeightT = AttributeValue(null, 0.0),
            maxHeightM = AttributeValue(null, 0.0),
            maxWidthM = AttributeValue(null, 0.0),
            operationState = AttributeValue("OPEN", 1.0),
            vehicleRules = mapOf(VehicleClass.BUS_29 to Passability.PASS)
        )
        val result = RouteGuardEngine().assess(
            segments = listOf(segment(20.0)),
            structures = listOf(bridge),
            liveEvents = emptyList(),
            vehicle = VehicleProfile(VehicleClass.BUS_29, grossWeightT = 10.0)
        )
        assertEquals(Passability.UNKNOWN, result.status)
        assertTrue(result.unknowns.any { it.contains("tải trọng") })
    }

    @Test
    fun ferryBlockedForVehicle_blocksWholeRoute() {
        val ferry = RoadStructure(
            id = "F1",
            segmentId = "S1",
            name = "Phà thử nghiệm",
            type = StructureType.FERRY,
            maxWeightT = AttributeValue(20.0, 1.0),
            maxHeightM = AttributeValue(null, 0.0),
            maxWidthM = AttributeValue(null, 0.0),
            operationState = AttributeValue("OPEN", 1.0),
            vehicleRules = mapOf(VehicleClass.BUS_29 to Passability.BLOCKED)
        )
        val result = RouteGuardEngine().assess(
            listOf(segment(20.0)), listOf(ferry), emptyList(), VehicleProfile(VehicleClass.BUS_29, grossWeightT = 9.0)
        )
        assertEquals(Passability.BLOCKED, result.status)
    }

    @Test
    fun weakestWeightLimit_blocksOverweightVehicle() {
        val result = RouteGuardEngine().assess(
            segments = listOf(segment(8.0)),
            structures = emptyList(),
            liveEvents = emptyList(),
            vehicle = VehicleProfile(VehicleClass.CAR_7, grossWeightT = 9.0)
        )
        assertEquals(Passability.BLOCKED, result.status)
        assertTrue(result.reasons.any { it.contains("vượt giới hạn") })
    }
}
