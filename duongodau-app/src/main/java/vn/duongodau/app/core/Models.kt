package vn.duongodau.app.core

enum class AdminLevel { PROVINCE, COMMUNE, HISTORICAL_DISTRICT }
enum class VerificationState { CONFIRMED, HIGH_CONFIDENCE, PROBABLE, UNVERIFIED, CONFLICTING }
enum class Passability { PASS, CONDITIONAL, BLOCKED, UNKNOWN }
enum class RoadExistence { CONFIRMED_ROAD, PROBABLE_ROAD, POSSIBLE_PATH, POSSIBLE_DATA_GAP, TRUE_DEAD_END, UNKNOWN_CONTINUATION, NO_EVIDENCE }
enum class RoadClass { EXPRESSWAY, NATIONAL, PROVINCIAL, DISTRICT, COMMUNE, HAMLET, RESIDENTIAL, AGRICULTURAL, FOREST, SERVICE, PRIVATE, UNCLASSIFIED }
enum class SurfaceType { ASPHALT, CONCRETE, GRAVEL, STONE, EARTH, MIXED, UNKNOWN }
enum class StructureType { BRIDGE, FERRY, BOAT, TUNNEL, CULVERT, FORD, CAUSEWAY }
enum class VehicleClass { CAR_7, BUS_16, BUS_29, BUS_30_PLUS, TRUCK_LIGHT, TRUCK_MEDIUM, TRUCK_HEAVY, MOTORBIKE }
enum class LiveSeverity { INFO, ATTENTION, RISK, BLOCKED }

data class GeoPoint(val lat: Double, val lon: Double)

data class AdminUnit(
    val code: String,
    val name: String,
    val level: AdminLevel,
    val parentCode: String? = null,
    val effectiveFrom: String? = null,
    val isCurrent: Boolean = true
)

data class AdminTransition(
    val oldCode: String,
    val newCode: String,
    val effectiveFrom: String,
    val legalSource: String,
    val verification: VerificationState
)

data class SourceEvidence(
    val sourceId: String,
    val sourceType: String,
    val reference: String,
    val observedAt: String?,
    val receivedAt: String?,
    val expiresAt: String?,
    val confidence: Double,
    val verification: VerificationState
)

data class AttributeValue<T>(
    val value: T?,
    val confidence: Double,
    val evidence: List<SourceEvidence> = emptyList()
) {
    val isKnown: Boolean get() = value != null
}

data class RoadNode(
    val id: String,
    val point: GeoPoint,
    val type: String,
    val adminCode: String?
)

data class RoadSegment(
    val id: String,
    val roadId: String,
    val startNodeId: String,
    val endNodeId: String,
    val geometry: List<GeoPoint>,
    val adminCodes: List<String>,
    val roadClass: AttributeValue<RoadClass>,
    val surface: AttributeValue<SurfaceType>,
    val widthM: AttributeValue<Double>,
    val maxWeightT: AttributeValue<Double>,
    val condition: AttributeValue<String>,
    val existence: RoadExistence,
    val existenceConfidence: Double,
    val geometryConfidence: Double,
    val verification: VerificationState
)

data class Road(
    val id: String,
    val officialName: String?,
    val displayName: String,
    val code: String?,
    val aliases: List<String>,
    val segmentIds: List<String>
)

data class RoadStructure(
    val id: String,
    val segmentId: String,
    val name: String?,
    val type: StructureType,
    val maxWeightT: AttributeValue<Double>,
    val maxHeightM: AttributeValue<Double>,
    val maxWidthM: AttributeValue<Double>,
    val operationState: AttributeValue<String>,
    val vehicleRules: Map<VehicleClass, Passability>,
    val feesVnd: Map<VehicleClass, AttributeValue<Long>> = emptyMap()
)

data class RoadConnection(
    val fromSegmentId: String,
    val toSegmentId: String,
    val viaNodeId: String,
    val vehicleRules: Map<VehicleClass, Passability>,
    val confidence: Double
)

data class LiveRoadEvent(
    val id: String,
    val segmentId: String?,
    val structureId: String?,
    val title: String,
    val severity: LiveSeverity,
    val affectedVehicles: Set<VehicleClass>,
    val observedAt: String,
    val expiresAt: String?,
    val evidence: List<SourceEvidence>
)

data class VehicleProfile(
    val vehicleClass: VehicleClass,
    val grossWeightT: Double? = null,
    val heightM: Double? = null,
    val widthM: Double? = null,
    val lengthM: Double? = null
)

data class RouteGuardResult(
    val status: Passability,
    val reasons: List<String>,
    val unknowns: List<String>,
    val lastSafeDiversionNodeId: String? = null
)
