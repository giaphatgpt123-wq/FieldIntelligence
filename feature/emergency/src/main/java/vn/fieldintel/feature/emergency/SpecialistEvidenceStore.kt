package vn.fieldintel.feature.emergency

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

data class SpecialistEvidenceStatus(
    val installed: Boolean,
    val recordCount: Long = 0,
    val scope: String = "specialist-evidence-only"
)

/**
 * Read-only access to the separately distributed specialist-evidence database.
 *
 * This store never infers claims. It only returns bounded records that already exist in the
 * validated SQLite pack. If the pack is absent or invalid the caller can fall back to the small
 * audited catalog bundled in the APK.
 */
class SpecialistEvidenceStore(context: Context) {
    private val databaseFile = File(File(context.filesDir, DIRECTORY_NAME), DATABASE_NAME)

    fun databasePath(): File = databaseFile

    fun status(): SpecialistEvidenceStatus {
        if (!databaseFile.isFile || databaseFile.length() <= 0L) return SpecialistEvidenceStatus(false)
        return runCatching {
            openReadOnly().use { db ->
                requireSchema(db)
                val meta = readMeta(db)
                SpecialistEvidenceStatus(
                    installed = true,
                    recordCount = meta["recordCount"]?.trimJsonString()?.toLongOrNull() ?: 0L,
                    scope = meta["scope"]?.trimJsonString().orEmpty().ifBlank { "specialist-evidence-only" }
                )
            }
        }.getOrElse { SpecialistEvidenceStatus(false) }
    }

    fun forSpecies(speciesId: String, limit: Int = 100): List<SpecialistEvidenceRecord> {
        if (!databaseFile.isFile || speciesId.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)
        return runCatching {
            openReadOnly().use { db ->
                requireSchema(db)
                db.rawQuery(
                    """
                    SELECT evidence_id, species_id, domain, evidence_class, title, statement,
                           plant_part, source_name, source_url, source_record, scope_note
                    FROM evidence
                    WHERE species_id = ?
                    ORDER BY domain, evidence_id
                    LIMIT $safeLimit
                    """.trimIndent(),
                    arrayOf(speciesId)
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val domain = runCatching { EvidenceDomain.valueOf(cursor.getString(2)) }.getOrNull() ?: continue
                            val evidenceClass = runCatching { EvidenceClass.valueOf(cursor.getString(3)) }.getOrNull() ?: continue
                            add(
                                SpecialistEvidenceRecord(
                                    id = cursor.getString(0),
                                    speciesId = cursor.getString(1),
                                    domain = domain,
                                    evidenceClass = evidenceClass,
                                    title = cursor.getString(4),
                                    statement = cursor.getString(5),
                                    plantPart = cursor.getString(6),
                                    sourceName = cursor.getString(7),
                                    sourceUrl = cursor.getString(8),
                                    sourceRecord = cursor.getString(9),
                                    scopeNote = cursor.getString(10)
                                )
                            )
                        }
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun speciesIdsFor(domain: EvidenceDomain, limit: Int = 5000): Set<String> {
        if (!databaseFile.isFile) return emptySet()
        val safeLimit = limit.coerceIn(1, 20_000)
        return runCatching {
            openReadOnly().use { db ->
                requireSchema(db)
                db.rawQuery(
                    """
                    SELECT DISTINCT species_id
                    FROM evidence
                    WHERE domain = ?
                    ORDER BY species_id
                    LIMIT $safeLimit
                    """.trimIndent(),
                    arrayOf(domain.name)
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            cursor.getString(0)?.takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
            }
        }.getOrElse { emptySet() }
    }

    private fun openReadOnly(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    private fun requireSchema(db: SQLiteDatabase) {
        val tables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('meta','evidence') ORDER BY name",
            null
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        require(tables == setOf("evidence", "meta")) { "Specialist evidence schema is incomplete" }
        val meta = readMeta(db)
        val schemaVersion = meta["schemaVersion"]?.trimJsonString()?.toIntOrNull()
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) { "Unsupported specialist evidence schema: $schemaVersion" }
        require(meta["scope"]?.trimJsonString() == "specialist-evidence-only") { "Unexpected specialist evidence scope" }
        require(meta["doseRecommendationsIncluded"]?.trimJsonString() != "true") { "Dose recommendations are not allowed" }
        require(meta["treatmentRecommendationsIncluded"]?.trimJsonString() != "true") { "Treatment recommendations are not allowed" }
        require(meta["imageIdentificationIncluded"]?.trimJsonString() != "true") { "Image identification claims are not allowed" }
    }

    private fun readMeta(db: SQLiteDatabase): Map<String, String> = db.rawQuery(
        "SELECT key, value FROM meta",
        null
    ).use { cursor -> buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) } }

    private fun String.trimJsonString(): String {
        val value = trim()
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value.substring(1, value.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return value
    }

    companion object {
        const val DIRECTORY_NAME = "scientific-library"
        const val DATABASE_NAME = "specialist-evidence.sqlite"
        private const val SUPPORTED_SCHEMA_VERSION = 1
    }
}
