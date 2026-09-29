package vn.duongodau.app.core

import kotlin.math.abs

/** Core rule engines for P0. No external AI SDK is required for these deterministic safety rules. */
class AdminConversionEngine(private val transitions: List<AdminTransition>) {
    fun toCurrent(oldCode: String): List<AdminTransition> =
        transitions.filter { it.oldCode == oldCode }

    fun toHistorical(currentCode: String): List<AdminTransition> =
        transitions.filter { it.newCode == currentCode }
}

data class RoadCandidate(
    val candidateId: String,
    val geometry: List<GeoPoint>,
    val existence: RoadExistence,
    val existenceConfidence: Double,
    val sources: List<SourceEvidence>,
    val proposedName: String? = null
)

class RoadDiscoveryEngine {
    fun classifyCandidate(
        candidateId: String,
        geometry: List<GeoPoint>,
        evidences: List<SourceEvidence>,
        proposedName: String? = null
    ): RoadCandidate {
        val weightedConfidence = if (evidences.isEmpty()) 0.0 else
            evidences.map { it.confidence.coerceIn(0.0, 1.0) }.average()
        val existence = when {
            geometry.size < 2 && evidences.isEmpty() -> RoadExistence.NO_EVIDENCE
            weightedConfidence >= 0.90 && geometry.size >= 2 -> RoadExistence.CONFIRMED_ROAD
            weightedConfidence >= 0.65 && geometry.size >= 2 -> RoadExistence.PROBABLE_ROAD
            geometry.size >= 2 -> RoadExistence.POSSIBLE_PATH
            else -> RoadExistence.UNKNOWN_CONTINUATION
        }
        return RoadCandidate(candidateId, geometry, existence, weightedConfidence, evidences, proposedName)
    }
}

class RoadIdentificationEngine {
    fun normalizeName(value: String?): String = value
        ?.lowercase()
        ?.replace(Regex("\\b(đường|duong|đ\\.|d\\.)\\b"), " ")
        ?.replace(Regex("[^a-z0-9àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ ]"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()

    fun likelySameRoad(a: Road, b: Road): Boolean {
        if (!a.code.isNullOrBlank() && a.code.equals(b.code, ignoreCase = true)) return true
        val namesA = (listOfNotNull(a.officialName, a.displayName) + a.aliases).map(::normalizeName).filter { it.isNotBlank() }.toSet()
        val namesB = (listOfNotNull(b.officialName, b.displayName) + b.aliases).map(::normalizeName).filter { it.isNotBlank() }.toSet()
        return namesA.intersect(namesB).isNotEmpty()
    }

    fun approximateGeometryMatch(a: RoadSegment, b: RoadSegment, toleranceDegrees: Double = 0.0002): Boolean {
        val aStart = a.geometry.firstOrNull() ?: return false
        val bStart = b.geometry.firstOrNull() ?: return false
        val aEnd = a.geometry.lastOrNull() ?: return false
        val bEnd = b.geometry.lastOrNull() ?: return false
        fun close(x: GeoPoint, y: GeoPoint) = abs(x.lat - y.lat) <= toleranceDegrees && abs(x.lon - y.lon) <= toleranceDegrees
        return (close(aStart, bStart) && close(aEnd, bEnd)) || (close(aStart, bEnd) && close(aEnd, bStart))
    }
}

object FreshnessPolicy {
    /** Conservative P0 TTLs; production values can be policy-configured by event/source type. */
    val liveEventMinutes = mapOf(
        LiveSeverity.INFO to 240L,
        LiveSeverity.ATTENTION to 120L,
        LiveSeverity.RISK to 60L,
        LiveSeverity.BLOCKED to 30L
    )
}

class RouteGuardEngine {
    fun assess(
        segments: List<RoadSegment>,
        structures: List<RoadStructure>,
        liveEvents: List<LiveRoadEvent>,
        vehicle: VehicleProfile,
        lastSafeDiversionNodeId: String? = null
    ): RouteGuardResult {
        val reasons = mutableListOf<String>()
        val unknowns = mutableListOf<String>()
        var status = Passability.PASS

        fun worsen(newState: Passability) {
            status = when {
                status == Passability.BLOCKED || newState == Passability.BLOCKED -> Passability.BLOCKED
                status == Passability.UNKNOWN || newState == Passability.UNKNOWN -> Passability.UNKNOWN
                status == Passability.CONDITIONAL || newState == Passability.CONDITIONAL -> Passability.CONDITIONAL
                else -> Passability.PASS
            }
        }

        segments.forEach { segment ->
            if (segment.existence in setOf(RoadExistence.NO_EVIDENCE, RoadExistence.POSSIBLE_DATA_GAP, RoadExistence.UNKNOWN_CONTINUATION)) {
                unknowns += "Đoạn ${segment.id}: liên thông/chứng cứ tồn tại chưa đủ"
                worsen(Passability.UNKNOWN)
            }
            segment.maxWeightT.value?.let { limit ->
                vehicle.grossWeightT?.let { actual ->
                    if (actual > limit) {
                        reasons += "Đoạn ${segment.id}: tải trọng xe $actual tấn vượt giới hạn $limit tấn"
                        worsen(Passability.BLOCKED)
                    }
                }
            }
            if (!segment.maxWeightT.isKnown && vehicle.grossWeightT != null) {
                unknowns += "Đoạn ${segment.id}: chưa xác minh tải trọng"
                worsen(Passability.UNKNOWN)
            }
        }

        structures.forEach { structure ->
            val rule = structure.vehicleRules[vehicle.vehicleClass] ?: Passability.UNKNOWN
            when (rule) {
                Passability.BLOCKED -> {
                    reasons += "${structure.name ?: structure.type.name}: không phù hợp ${vehicle.vehicleClass.name}"
                    worsen(Passability.BLOCKED)
                }
                Passability.CONDITIONAL -> {
                    reasons += "${structure.name ?: structure.type.name}: chỉ đi có điều kiện"
                    worsen(Passability.CONDITIONAL)
                }
                Passability.UNKNOWN -> {
                    unknowns += "${structure.name ?: structure.type.name}: chưa xác minh cho ${vehicle.vehicleClass.name}"
                    worsen(Passability.UNKNOWN)
                }
                Passability.PASS -> Unit
            }
            structure.maxWeightT.value?.let { limit ->
                vehicle.grossWeightT?.let { actual ->
                    if (actual > limit) {
                        reasons += "${structure.name ?: structure.type.name}: tải trọng xe $actual tấn vượt $limit tấn"
                        worsen(Passability.BLOCKED)
                    }
                }
            }
            if (!structure.maxWeightT.isKnown && vehicle.grossWeightT != null && structure.type in setOf(StructureType.BRIDGE, StructureType.FERRY, StructureType.BOAT)) {
                unknowns += "${structure.name ?: structure.type.name}: chưa xác minh tải trọng"
                worsen(Passability.UNKNOWN)
            }
        }

        liveEvents.filter { vehicle.vehicleClass in it.affectedVehicles }.forEach { event ->
            when (event.severity) {
                LiveSeverity.BLOCKED -> {
                    reasons += "Sự kiện trực tiếp: ${event.title}"
                    worsen(Passability.BLOCKED)
                }
                LiveSeverity.RISK -> {
                    reasons += "Nguy cơ phía trước: ${event.title}"
                    worsen(Passability.CONDITIONAL)
                }
                LiveSeverity.ATTENTION -> {
                    reasons += "Chú ý: ${event.title}"
                    worsen(Passability.CONDITIONAL)
                }
                LiveSeverity.INFO -> Unit
            }
        }

        // Safety invariant: a route with critical unknowns must never leave as PASS.
        if (status == Passability.PASS && unknowns.isNotEmpty()) status = Passability.UNKNOWN
        return RouteGuardResult(status, reasons.distinct(), unknowns.distinct(), lastSafeDiversionNodeId)
    }
}
