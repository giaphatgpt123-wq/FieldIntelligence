package vn.survivallibrary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DataEngineRemoteIndexParserTest {
    private fun index(entityCount: Int = 2, taskCount: Int = 5, shardEntityTotal: Int = 2, shardTaskTotal: Int = 5): String =
        """
        {
          "schemaVersion": 1,
          "snapshot": {
            "version": 10,
            "schemaVersion": 1,
            "generatedAt": 1234,
            "sha256": "${"a".repeat(64)}",
            "snapshotUrl": "https://raw.githubusercontent.com/example/progress-snapshot.json",
            "entityCount": $entityCount,
            "taskCount": $taskCount
          },
          "progressShards": {
            "schemaVersion": 1,
            "version": 10,
            "generatedAt": 1234,
            "shardCount": 2,
            "maxShardBytes": 2097152,
            "shards": [
              {
                "categoryId": "vegetables",
                "file": "vegetables.json",
                "url": "https://raw.githubusercontent.com/example/progress/vegetables.json",
                "sha256": "${"b".repeat(64)}",
                "bytes": 1200,
                "entityCount": 1,
                "aliasCount": 2,
                "fieldCount": 3,
                "taskCount": 4
              },
              {
                "categoryId": "freshwater-fish",
                "file": "freshwater-fish.json",
                "url": "https://raw.githubusercontent.com/example/progress/freshwater-fish.json",
                "sha256": "${"c".repeat(64)}",
                "bytes": 900,
                "entityCount": ${shardEntityTotal - 1},
                "aliasCount": 1,
                "fieldCount": 2,
                "taskCount": ${shardTaskTotal - 4}
              }
            ],
            "sourceHealth": {
              "file": "source-health.json",
              "url": "https://raw.githubusercontent.com/example/progress/source-health.json",
              "sha256": "${"d".repeat(64)}",
              "bytes": 300,
              "rowCount": 3
            }
          }
        }
        """.trimIndent()

    @Test
    fun parsesValidProgressShardManifest() {
        val parsed = DataEngineRemoteIndexParser.parse(index())
        assertEquals(10, parsed.snapshot.version)
        val progress = assertNotNull(parsed.progressShards).let { parsed.progressShards!! }
        assertEquals(2, progress.shardCount)
        assertEquals(2, progress.shards.sumOf { it.entityCount })
        assertEquals(5, progress.shards.sumOf { it.taskCount })
        assertEquals(3, progress.sourceHealth.rowCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEntityTotalMismatch() {
        DataEngineRemoteIndexParser.parse(index(entityCount = 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTaskTotalMismatch() {
        DataEngineRemoteIndexParser.parse(index(taskCount = 6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateCategory() {
        val bad = index().replace("freshwater-fish", "vegetables")
        DataEngineRemoteIndexParser.parse(bad)
    }
}
