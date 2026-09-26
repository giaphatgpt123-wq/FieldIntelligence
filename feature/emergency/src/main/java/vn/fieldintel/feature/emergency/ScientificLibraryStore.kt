package vn.fieldintel.feature.emergency

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Read-only access to the separately distributed scientific taxonomy database.
 *
 * The database contains taxonomy/provenance only. A database match must never be treated as
 * image identification, edibility/toxicity evidence, or medical guidance.
 */
data class ScientificLibraryStatus(
    val installed: Boolean,
    val recordCount: Long = 0,
    val acceptedRecordCount: Long = 0,
    val sourceVersion: String = "",
    val sourceDoi: String = "",
    val sourceLicense: String = "",
    val scope: String = "taxonomy-only"
)

class ScientificLibraryStore(context: Context) {
    private val databaseFile = File(File(context.filesDir, DIRECTORY_NAME), DATABASE_NAME)

    fun databasePath(): File = databaseFile

    fun status(): ScientificLibraryStatus {
        if (!databaseFile.isFile || databaseFile.length() <= 0L) return ScientificLibraryStatus(installed = false)
        return runCatching {
            openReadOnly().use { db ->
                requireSchema(db)
                val meta = readMeta(db)
                ScientificLibraryStatus(
                    installed = true,
                    recordCount = meta["recordCount"]?.trimJsonString()?.toLongOrNull() ?: 0L,
                    acceptedRecordCount = meta["acceptedRecordCount"]?.trimJsonString()?.toLongOrNull() ?: 0L,
                    sourceVersion = meta["sourceVersion"]?.trimJsonString().orEmpty(),
                    sourceDoi = meta["sourceDoi"]?.trimJsonString().orEmpty(),
                    sourceLicense = meta["sourceLicense"]?.trimJsonString().orEmpty(),
                    scope = meta["scope"]?.trimJsonString().orEmpty().ifBlank { "taxonomy-only" }
                )
            }
        }.getOrElse { ScientificLibraryStatus(installed = false) }
    }

    fun search(query: String, group: String = "Tất cả", limit: Int = 80): List<SpeciesRecord> {
        if (!databaseFile.isFile || query.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)
        val needle = query.trim().lowercase()
        return runCatching {
            openReadOnly().use { db ->
                requireSchema(db)
                val where = StringBuilder("scientific_name_search LIKE ? ESCAPE '\\\\'")
                val args = mutableListOf("${escapeLike(needle)}%")
                if (group != "Tất cả") {
                    where.append(" AND library_group = ?")
                    args += group
                }
                val sql = """
                    SELECT source_id, source_record_id, scientific_name, library_group,
                           authority, license, source_scope, source_version, source_doi
                    FROM taxon
                    WHERE $where
                    ORDER BY scientific_name_search
                    LIMIT $safeLimit
                """.trimIndent()
                db.rawQuery(sql, args.toTypedArray()).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.toSpeciesRecord())
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun findById(id: String): SpeciesRecord? {
        if (!databaseFile.isFile || !id.startsWith(ID_PREFIX)) return null
        val body = id.removePrefix(ID_PREFIX)
        val separator = body.indexOf('|')
        if (separator <= 0 || separator >= body.lastIndex) return null
        val sourceId = body.substring(0, separator)
        val sourceRecordId = body.substring(separator + 1)
        return runCatching {
            openReadOnly().use { db ->
                requireSchema(db)
                db.rawQuery(
                    """
                    SELECT source_id, source_record_id, scientific_name, library_group,
                           authority, license, source_scope, source_version, source_doi
                    FROM taxon
                    WHERE source_id = ? AND source_record_id = ?
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(sourceId, sourceRecordId)
                ).use { cursor -> if (cursor.moveToFirst()) cursor.toSpeciesRecord() else null }
            }
        }.getOrNull()
    }

    private fun openReadOnly(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    private fun requireSchema(db: SQLiteDatabase) {
        val table = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('meta','taxon') ORDER BY name",
            null
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        require(table == setOf("meta", "taxon")) { "Scientific library schema is incomplete" }
        val schemaVersion = readMeta(db)["schemaVersion"]?.trimJsonString()?.toIntOrNull()
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) { "Unsupported scientific library schema: $schemaVersion" }
    }

    private fun readMeta(db: SQLiteDatabase): Map<String, String> = db.rawQuery(
        "SELECT key, value FROM meta",
        null
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
        }
    }

    private fun android.database.Cursor.toSpeciesRecord(): SpeciesRecord {
        val sourceId = getString(0)
        val sourceRecordId = getString(1)
        val scientificName = getString(2)
        val group = getString(3).ifBlank { "Thực vật" }
        val authority = getString(4).ifBlank { sourceId }
        val license = getString(5)
        val scope = getString(6).ifBlank { "taxonomy-only" }
        val version = getString(7)
        val doi = getString(8)
        val provenance = buildString {
            append("Phạm vi nguồn: ").append(scope)
            if (license.isNotBlank()) append(" • license ").append(license)
            if (version.isNotBlank()) append(" • phiên bản ").append(version)
            append(". Không xác minh mẫu vật trong ảnh, tính ăn được, độc tính hoặc hướng dẫn điều trị.")
        }
        return SpeciesRecord(
            id = "$ID_PREFIX$sourceId|$sourceRecordId",
            vietnameseName = scientificName,
            scientificName = scientificName,
            group = group,
            sourceName = authority,
            sourceUrl = if (doi.isBlank()) "" else "https://doi.org/$doi",
            sourceScope = provenance
        )
    }

    private fun String.trimJsonString(): String {
        val value = trim()
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value.substring(1, value.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return value
    }

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    companion object {
        const val DIRECTORY_NAME = "scientific-library"
        const val DATABASE_NAME = "wfo-taxonomy.sqlite"
        private const val SUPPORTED_SCHEMA_VERSION = 1
        private const val ID_PREFIX = "scientific-db:"
    }
}
