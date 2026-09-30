package vn.survivallibrary.app

import org.json.JSONObject

data class DataEngineRemoteProgressShard(
    val categoryId: String,
    val url: String,
    val sha256: String,
    val bytes: Int,
    val entityCount: Int,
    val aliasCount: Int,
    val fieldCount: Int,
    val taskCount: Int
)

data class DataEngineRemoteSourceHealthShard(
    val url: String,
    val sha256: String,
    val bytes: Int,
    val rowCount: Int
)

data class DataEngineRemoteProgressManifest(
    val schemaVersion: Int,
    val version: Int,
    val shardCount: Int,
    val maxShardBytes: Int,
    val shards: List<DataEngineRemoteProgressShard>,
    val sourceHealth: DataEngineRemoteSourceHealthShard
)

data class DataEngineRemoteIndex(
    val snapshot: DataEngineRemoteSnapshot,
    val progressShards: DataEngineRemoteProgressManifest?
)

object DataEngineRemoteIndexParser {
    private const val MAX_PROGRESS_SHARDS = 2_000
    private const val MAX_PROGRESS_SHARD_BYTES = 2 * 1024 * 1024
    private const val MAX_SOURCE_HEALTH_BYTES = 512 * 1024

    fun parse(json: String): DataEngineRemoteIndex {
        val legacy = DataEngineStagingIndexParser.parse(json)
        val root = JSONObject(json)
        val progress = root.optJSONObject("progressShards") ?: return DataEngineRemoteIndex(legacy, null)
        require(progress.optInt("schemaVersion", -1) == 1) { "Progress shard schema không được hỗ trợ" }
        require(progress.getInt("version") == legacy.version) { "Progress shard version không khớp snapshot" }
        val declaredShardCount = progress.getInt("shardCount")
        require(declaredShardCount in 1..MAX_PROGRESS_SHARDS) { "Số progress shard không hợp lệ" }
        val maxShardBytes = progress.getInt("maxShardBytes")
        require(maxShardBytes in 1..MAX_PROGRESS_SHARD_BYTES) { "Giới hạn progress shard không hợp lệ" }
        val array = progress.getJSONArray("shards")
        require(array.length() == declaredShardCount) { "Progress shardCount không khớp manifest" }

        val categories = linkedSetOf<String>()
        val shards = buildList {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                val categoryId = row.getString("categoryId").trim()
                require(categoryId.isNotBlank() && categories.add(categoryId)) { "Progress category trống hoặc trùng" }
                val bytes = row.getInt("bytes")
                val entityCount = row.getInt("entityCount")
                val aliasCount = row.getInt("aliasCount")
                val fieldCount = row.getInt("fieldCount")
                val taskCount = row.getInt("taskCount")
                require(bytes in 1..maxShardBytes) { "Progress shard vượt giới hạn" }
                require(entityCount in 0..DataEngineStagingIndexParser.MAX_ENTITIES) { "Progress entityCount không hợp lệ" }
                require(aliasCount in 0..DataEngineStagingIndexParser.MAX_ALIASES) { "Progress aliasCount không hợp lệ" }
                require(fieldCount in 0..DataEngineStagingIndexParser.MAX_FIELDS) { "Progress fieldCount không hợp lệ" }
                require(taskCount in 0..DataEngineStagingIndexParser.MAX_TASKS) { "Progress taskCount không hợp lệ" }
                val sha = row.getString("sha256").lowercase()
                require(sha.matches(Regex("^[a-f0-9]{64}$"))) { "SHA-256 progress shard không hợp lệ" }
                add(
                    DataEngineRemoteProgressShard(
                        categoryId = categoryId,
                        url = row.getString("url"),
                        sha256 = sha,
                        bytes = bytes,
                        entityCount = entityCount,
                        aliasCount = aliasCount,
                        fieldCount = fieldCount,
                        taskCount = taskCount
                    )
                )
            }
        }
        require(shards.sumOf { it.entityCount } == legacy.entityCount) { "Tổng entity progress shard không khớp index" }
        require(shards.sumOf { it.taskCount } == legacy.taskCount) { "Tổng task progress shard không khớp index" }
        require(shards.sumOf { it.aliasCount } <= DataEngineStagingIndexParser.MAX_ALIASES) { "Tổng alias progress vượt giới hạn" }
        require(shards.sumOf { it.fieldCount } <= DataEngineStagingIndexParser.MAX_FIELDS) { "Tổng field progress vượt giới hạn" }

        val source = progress.getJSONObject("sourceHealth")
        val sourceBytes = source.getInt("bytes")
        val sourceCount = source.getInt("rowCount")
        val sourceSha = source.getString("sha256").lowercase()
        require(sourceBytes in 1..MAX_SOURCE_HEALTH_BYTES) { "Source-health shard vượt giới hạn" }
        require(sourceCount in 0..DataEngineStagingIndexParser.MAX_SOURCES) { "Source-health count vượt giới hạn" }
        require(sourceSha.matches(Regex("^[a-f0-9]{64}$"))) { "SHA-256 source-health không hợp lệ" }

        return DataEngineRemoteIndex(
            snapshot = legacy,
            progressShards = DataEngineRemoteProgressManifest(
                schemaVersion = 1,
                version = legacy.version,
                shardCount = declaredShardCount,
                maxShardBytes = maxShardBytes,
                shards = shards,
                sourceHealth = DataEngineRemoteSourceHealthShard(
                    url = source.getString("url"),
                    sha256 = sourceSha,
                    bytes = sourceBytes,
                    rowCount = sourceCount
                )
            )
        )
    }
}
