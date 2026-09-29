package vn.duongodau.app.data

import vn.duongodau.app.core.*

/**
 * Synthetic P0 data only. It exists to exercise UI and safety logic and must never be
 * interpreted as a real road record.
 */
object PilotRoadRepository {
    private val demoEvidence = SourceEvidence(
        sourceId = "P0_DEMO",
        sourceType = "SYNTHETIC",
        reference = "Synthetic test record – not field data",
        observedAt = null,
        receivedAt = null,
        expiresAt = null,
        confidence = 0.0,
        verification = VerificationState.UNVERIFIED
    )

    val road = Road(
        id = "DEMO-ROAD-001",
        officialName = null,
        displayName = "Đường chưa xác định tên – dữ liệu mẫu P0",
        code = "DEMO-UNT-001",
        aliases = emptyList(),
        segmentIds = listOf("DEMO-SEG-001")
    )

    val segment = RoadSegment(
        id = "DEMO-SEG-001",
        roadId = road.id,
        startNodeId = "DEMO-NODE-A",
        endNodeId = "DEMO-NODE-B",
        geometry = listOf(GeoPoint(11.30, 106.10), GeoPoint(11.305, 106.108)),
        adminCodes = emptyList(),
        roadClass = AttributeValue(RoadClass.UNCLASSIFIED, 0.0, listOf(demoEvidence)),
        surface = AttributeValue(null, 0.0, listOf(demoEvidence)),
        widthM = AttributeValue(null, 0.0, listOf(demoEvidence)),
        maxWeightT = AttributeValue(null, 0.0, listOf(demoEvidence)),
        condition = AttributeValue(null, 0.0, listOf(demoEvidence)),
        existence = RoadExistence.POSSIBLE_PATH,
        existenceConfidence = 0.0,
        geometryConfidence = 0.0,
        verification = VerificationState.UNVERIFIED
    )

    fun assess(vehicleClass: VehicleClass): RouteGuardResult = RouteGuardEngine().assess(
        segments = listOf(segment),
        structures = emptyList(),
        liveEvents = emptyList(),
        vehicle = VehicleProfile(vehicleClass = vehicleClass)
    )
}
