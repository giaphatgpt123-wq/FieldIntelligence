package vn.survivallibrary.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DataEngineStagingIndexParserTest {
    @Test
    fun parsesValidSnapshotIndex() {
        val parsed = DataEngineStagingIndexParser.parse(
            """
            {
              "schemaVersion": 1,
              "snapshot": {
                "version": 4,
                "schemaVersion": 1,
                "generatedAt": 1234,
                "sha256": "${"a".repeat(64)}",
                "snapshotUrl": "https://raw.githubusercontent.com/example/progress-snapshot.json",
                "entityCount": 12,
                "taskCount": 48
              }
            }
            """.trimIndent()
        )

        assertEquals(4, parsed.version)
        assertEquals(12, parsed.entityCount)
        assertEquals(48, parsed.taskCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSha() {
        DataEngineStagingIndexParser.parse(
            """
            {
              "schemaVersion": 1,
              "snapshot": {
                "version": 1,
                "schemaVersion": 1,
                "generatedAt": 0,
                "sha256": "bad",
                "snapshotUrl": "https://raw.githubusercontent.com/example/progress-snapshot.json",
                "entityCount": 12,
                "taskCount": 48
              }
            }
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSnapshotSchema() {
        DataEngineStagingIndexParser.parse(
            """
            {
              "schemaVersion": 1,
              "snapshot": {
                "version": 1,
                "schemaVersion": 99,
                "generatedAt": 0,
                "sha256": "${"b".repeat(64)}",
                "snapshotUrl": "https://raw.githubusercontent.com/example/progress-snapshot.json",
                "entityCount": 12,
                "taskCount": 48
              }
            }
            """.trimIndent()
        )
    }
}
