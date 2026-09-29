package vn.duongodau.app.core

enum class ScopeMode { NATIONWIDE_INDEX, PROVINCE_FOCUS, COMMUNE_FOCUS, ROUTE_CORRIDOR }
enum class StreamState { ACTIVE_FULL, ACTIVE_EDGE, METADATA_ONLY, OFF }
enum class DataStream {
    ADMIN_INDEX,
    BOUNDARY,
    ROAD_GRAPH,
    ROAD_ATTRIBUTES,
    STRUCTURES,
    ROAD_LIVE,
    ROAD_DISCOVERY,
    SEARCH_INDEX,
    MAP_BASE
}

data class AdminAdjacency(
    val communeCode: String,
    val adjacentCommuneCode: String,
    val confidence: Double = 1.0,
    val sourceId: String? = null
)

data class ScopePartition(
    val adminCode: String,
    val streamStates: Map<DataStream, StreamState>
) {
    fun state(stream: DataStream): StreamState = streamStates[stream] ?: StreamState.OFF
}

data class DataScopePlan(
    val mode: ScopeMode,
    val selectedProvinceCode: String? = null,
    val selectedCommuneCode: String? = null,
    val primaryCommuneCodes: Set<String> = emptySet(),
    val adjacentCommuneCodes: Set<String> = emptySet(),
    val partitions: List<ScopePartition> = emptyList()
) {
    val activeCommuneCodes: Set<String>
        get() = primaryCommuneCodes + adjacentCommuneCodes

    fun isCommuneActive(code: String): Boolean = code in activeCommuneCodes
}

/**
 * Controls which geographical partitions are allowed to consume CPU/RAM/I/O/network.
 *
 * Nationwide data remains indexed on disk, but expensive road/live/discovery streams are
 * activated only for the selected commune plus its directly adjacent communes.
 */
class DataScopeController(adjacencies: List<AdminAdjacency>) {
    private val adjacencyIndex: Map<String, Set<String>> = buildMap {
        adjacencies.filter { it.confidence > 0.0 }.forEach { edge ->
            put(edge.communeCode, (get(edge.communeCode).orEmpty() + edge.adjacentCommuneCode))
            put(edge.adjacentCommuneCode, (get(edge.adjacentCommuneCode).orEmpty() + edge.communeCode))
        }
    }

    fun nationwideIndex(): DataScopePlan = DataScopePlan(mode = ScopeMode.NATIONWIDE_INDEX)

    /**
     * Province selection wakes only province-level metadata/boundary/search manifests.
     * It deliberately does NOT activate every commune road graph in that province.
     */
    fun selectProvince(provinceCode: String): DataScopePlan = DataScopePlan(
        mode = ScopeMode.PROVINCE_FOCUS,
        selectedProvinceCode = provinceCode
    )

    /**
     * Full streams: selected commune.
     * Edge streams: directly adjacent communes only.
     * All other communes must remain OFF at the repository/scheduler layer.
     */
    fun selectCommune(provinceCode: String, communeCode: String): DataScopePlan {
        val adjacent = adjacencyIndex[communeCode].orEmpty() - communeCode
        val full = fullPartition(communeCode)
        val edge = adjacent.map(::edgePartition)
        return DataScopePlan(
            mode = ScopeMode.COMMUNE_FOCUS,
            selectedProvinceCode = provinceCode,
            selectedCommuneCode = communeCode,
            primaryCommuneCodes = setOf(communeCode),
            adjacentCommuneCodes = adjacent,
            partitions = listOf(full) + edge
        )
    }

    /**
     * Explicit navigation may temporarily wake only communes intersecting a route corridor.
     * This is a special mode and must not become background nationwide loading.
     */
    fun routeCorridor(
        provinceCode: String?,
        corridorCommuneCodes: Set<String>,
        destinationCommuneCode: String
    ): DataScopePlan {
        val corridor = corridorCommuneCodes + destinationCommuneCode
        return DataScopePlan(
            mode = ScopeMode.ROUTE_CORRIDOR,
            selectedProvinceCode = provinceCode,
            selectedCommuneCode = destinationCommuneCode,
            primaryCommuneCodes = corridor,
            adjacentCommuneCodes = emptySet(),
            partitions = corridor.map(::fullPartition)
        )
    }

    private fun fullPartition(code: String) = ScopePartition(
        adminCode = code,
        streamStates = DataStream.entries.associateWith { stream ->
            when (stream) {
                DataStream.MAP_BASE -> StreamState.METADATA_ONLY
                else -> StreamState.ACTIVE_FULL
            }
        }
    )

    private fun edgePartition(code: String) = ScopePartition(
        adminCode = code,
        streamStates = mapOf(
            DataStream.ADMIN_INDEX to StreamState.METADATA_ONLY,
            DataStream.BOUNDARY to StreamState.ACTIVE_EDGE,
            DataStream.ROAD_GRAPH to StreamState.ACTIVE_EDGE,
            DataStream.ROAD_ATTRIBUTES to StreamState.ACTIVE_EDGE,
            DataStream.STRUCTURES to StreamState.ACTIVE_EDGE,
            DataStream.ROAD_LIVE to StreamState.ACTIVE_EDGE,
            DataStream.ROAD_DISCOVERY to StreamState.OFF,
            DataStream.SEARCH_INDEX to StreamState.ACTIVE_EDGE,
            DataStream.MAP_BASE to StreamState.OFF
        )
    )
}
