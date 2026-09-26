package vn.fieldintel.feature.emergency

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.security.MessageDigest

data class ScientificLocalMedia(
    val bytes: ByteArray,
    val mimeType: String,
    val license: String,
    val sourceIdentifier: String,
    val sha256: String
)

/** Reads optional content-addressed reference images embedded in scientific schema-v2 SQLite. */
class ScientificMediaStore(context: Context) {
    private val databaseFile = File(
        File(context.applicationContext.filesDir, ScientificLibraryStore.DIRECTORY_NAME),
        ScientificLibraryStore.DATABASE_NAME
    )

    fun fishWithLocalMediaCount(): Long {
        if (!databaseFile.isFile || databaseFile.length() <= 0L) return 0L
        return runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                if (!hasLocalMediaTables(db)) return@use 0L
                val metaValue = db.rawQuery("SELECT value FROM meta WHERE key='fishWithLocalMedia' LIMIT 1", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).trim().trim('"').toLongOrNull() else null
                }
                metaValue ?: db.rawQuery(
                    """SELECT COUNT(*) FROM taxon t
                       WHERE t.library_group='Cá nước ngọt' AND EXISTS (
                         SELECT 1 FROM species_media_local l
                         WHERE l.source_id=t.source_id AND l.source_record_id=t.source_record_id
                       )""".trimIndent(), null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
            }
        }.getOrElse { 0L }
    }

    fun loadForRecord(recordId: String, limit: Int = 2): List<ScientificLocalMedia> {
        val key = parseRecordId(recordId) ?: return emptyList()
        if (!databaseFile.isFile || databaseFile.length() <= 0L) return emptyList()
        return runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                if (!hasLocalMediaTables(db)) return@use emptyList()
                db.rawQuery(
                    """
                    SELECT b.media_blob, b.mime_type, l.media_license,
                           l.source_identifier, b.sha256, b.size_bytes
                    FROM species_media_local l
                    JOIN scientific_media_blob b ON b.sha256 = l.sha256
                    WHERE l.source_id = ? AND l.source_record_id = ?
                    ORDER BY l.source_identifier
                    LIMIT ${limit.coerceIn(1, MAX_MEDIA_PER_PROFILE)}
                    """.trimIndent(),
                    arrayOf(key.first, key.second)
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val bytes = cursor.getBlob(0) ?: continue
                            val mimeType = cursor.getString(1).orEmpty()
                            val license = cursor.getString(2).orEmpty()
                            val sourceIdentifier = cursor.getString(3).orEmpty()
                            val expectedSha = cursor.getString(4).orEmpty().lowercase()
                            val expectedSize = cursor.getLong(5)
                            if (bytes.isEmpty() || bytes.size.toLong() != expectedSize || expectedSize > MAX_MEDIA_BYTES) continue
                            if (mimeType !in ALLOWED_MIME_TYPES || license !in ALLOWED_LICENSES) continue
                            val actualSha = MessageDigest.getInstance("SHA-256")
                                .digest(bytes)
                                .joinToString("") { "%02x".format(it) }
                            if (actualSha != expectedSha) continue
                            add(ScientificLocalMedia(bytes, mimeType, license, sourceIdentifier, expectedSha))
                        }
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun hasLocalMediaTables(db: SQLiteDatabase): Boolean {
        val required = setOf("species_media_local", "scientific_media_blob")
        val actual = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('species_media_local','scientific_media_blob')",
            null
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return actual == required
    }

    private fun parseRecordId(id: String): Pair<String, String>? {
        if (!id.startsWith(ID_PREFIX)) return null
        val body = id.removePrefix(ID_PREFIX)
        val separator = body.indexOf('|')
        if (separator <= 0 || separator >= body.lastIndex) return null
        return body.substring(0, separator) to body.substring(separator + 1)
    }

    companion object {
        private const val ID_PREFIX = "scientific-db:"
        private const val MAX_MEDIA_PER_PROFILE = 3
        private const val MAX_MEDIA_BYTES = 5L * 1024L * 1024L
        private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val ALLOWED_LICENSES = setOf(
            "CC0-1.0", "CC-BY-4.0", "CC-BY-NC-4.0",
            "https://creativecommons.org/publicdomain/zero/1.0/",
            "https://creativecommons.org/licenses/by/4.0/",
            "https://creativecommons.org/licenses/by-nc/4.0/"
        )
    }
}
