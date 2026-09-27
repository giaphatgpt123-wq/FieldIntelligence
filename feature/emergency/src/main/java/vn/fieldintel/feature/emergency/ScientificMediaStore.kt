package vn.fieldintel.feature.emergency

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.Snapshot
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScientificLocalMedia(
    val bytes: ByteArray,
    val mimeType: String,
    val license: String,
    val sourceIdentifier: String,
    val sha256: String
)

/** Reads optional content-addressed reference images embedded in scientific schema-v2 SQLite. */
class ScientificMediaStore(context: Context) {
    private val appContext = context.applicationContext
    private val databaseFile = File(
        File(appContext.filesDir, ScientificLibraryStore.DIRECTORY_NAME),
        ScientificLibraryStore.DATABASE_NAME
    )

    /**
     * Returns the shared Compose-observable set immediately and refreshes it on the IO scope.
     * This avoids opening SQLite on the composition thread while preserving the exact state-set
     * reference when a fish pack is installed/replaced after the screen has already opened.
     */
    fun localMediaRecordIds(): Set<String> {
        startRevisionWatcher()
        requestObservedMediaRefresh()
        return OBSERVED_MEDIA_IDS
    }

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
        if (!databaseFile.isFile || databaseFile.length() <= 0L) return emptyList()
        return runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                if (!hasLocalMediaTables(db)) return@use emptyList()
                val key = parseRecordId(recordId) ?: resolveStarterFishKey(db, recordId) ?: return@use emptyList()
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
                            val expectedSha = cursor.getString(4).orEmpty().lowercase(Locale.ROOT)
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

    /**
     * Fish starter cards use stable APK IDs while downloaded scientific records use source IDs.
     * Resolve only an exact canonical fish species, never a neighbouring species/subspecies.
     */
    private fun resolveStarterFishKey(db: SQLiteDatabase, recordId: String): Pair<String, String>? {
        val starter = SpeciesCatalog.records.firstOrNull { it.id == recordId && it.group == FISH_GROUP } ?: return null
        val canonical = starter.scientificName.trim().split(Regex("\\s+")).take(2).joinToString(" ")
        if (canonical.split(' ').size != 2) return null
        val needle = canonical.lowercase(Locale.ROOT)
        return db.rawQuery(
            """
            SELECT source_id, source_record_id, scientific_name
            FROM taxon
            WHERE library_group = ?
              AND (scientific_name_search = ? OR scientific_name_search LIKE ? ESCAPE '\\')
            ORDER BY CASE WHEN scientific_name_search = ? THEN 0 ELSE 1 END,
                     length(scientific_name_search), scientific_name_search
            LIMIT 12
            """.trimIndent(),
            arrayOf(FISH_GROUP, needle, "${escapeLike(needle)} %", needle)
        ).use { cursor ->
            var resolved: Pair<String, String>? = null
            while (cursor.moveToNext() && resolved == null) {
                val candidate = cursor.getString(2).orEmpty()
                if (ScientificNameResolver.matchesCanonical(canonical, candidate)) {
                    resolved = cursor.getString(0) to cursor.getString(1)
                }
            }
            resolved
        }
    }

    private fun requestObservedMediaRefresh() {
        if (databaseFingerprint() == observedFingerprint) return
        if (!REFRESH_IN_FLIGHT.compareAndSet(false, true)) return
        REVISION_WATCHER_SCOPE.launch {
            try {
                refreshObservedMediaIds(force = false)
            } finally {
                REFRESH_IN_FLIGHT.set(false)
            }
        }
    }

    private fun startRevisionWatcher() {
        if (!REVISION_WATCHER_STARTED.compareAndSet(false, true)) return
        REVISION_WATCHER_SCOPE.launch {
            while (isActive) {
                delay(REVISION_POLL_MS)
                val fingerprint = databaseFingerprint()
                if (fingerprint == observedFingerprint) continue
                delay(REVISION_SETTLE_MS)
                requestObservedMediaRefresh()
            }
        }
    }

    private fun refreshObservedMediaIds(force: Boolean) {
        val fingerprintBefore = databaseFingerprint()
        if (!force && fingerprintBefore == observedFingerprint) return

        val fresh = readLocalMediaRecordIds()
        observedFingerprint = databaseFingerprint()

        Snapshot.withMutableSnapshot {
            val desired = buildSet {
                add(OBSERVER_MARKER)
                addAll(fresh)
            }
            if (OBSERVED_MEDIA_IDS != desired) {
                OBSERVED_MEDIA_IDS.clear()
                OBSERVED_MEDIA_IDS.addAll(desired)
            }
        }
    }

    private fun readLocalMediaRecordIds(): Set<String> {
        if (!databaseFile.isFile || databaseFile.length() <= 0L) return emptySet()
        return runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                if (!hasLocalMediaTables(db)) return@use emptySet()
                db.rawQuery(
                    "SELECT DISTINCT source_id,source_record_id FROM species_media_local",
                    null
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add("$ID_PREFIX${cursor.getString(0)}|${cursor.getString(1)}")
                    }
                }
            }
        }.getOrElse { emptySet() }
    }

    private fun databaseFingerprint(): String {
        if (!databaseFile.isFile || databaseFile.length() <= 0L) return MISSING_FINGERPRINT
        return "${databaseFile.length()}:${databaseFile.lastModified()}"
    }

    private fun hasLocalMediaTables(db: SQLiteDatabase): Boolean {
        val required = setOf("species_media_local", "scientific_media_blob")
        val actual = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('species_media_local','scientific_media_blob')",
            null
        ).use { cursor -> buildSet { while(cursor.moveToNext()) add(cursor.getString(0)) } }
        return actual == required
    }

    private fun parseRecordId(id: String): Pair<String, String>? {
        if (!id.startsWith(ID_PREFIX)) return null
        val body = id.removePrefix(ID_PREFIX)
        val separator = body.indexOf('|')
        if (separator <= 0 || separator >= body.lastIndex) return null
        return body.substring(0, separator) to body.substring(separator + 1)
    }

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    companion object {
        private const val ID_PREFIX = "scientific-db:"
        private const val FISH_GROUP = "Cá nước ngọt"
        private const val OBSERVER_MARKER = "__fieldintel_scientific_media_observer__"
        private const val MAX_MEDIA_PER_PROFILE = 3
        private const val MAX_MEDIA_BYTES = 5L * 1024L * 1024L
        private const val REVISION_POLL_MS = 1_500L
        private const val REVISION_SETTLE_MS = 150L
        private const val MISSING_FINGERPRINT = "missing"

        private val OBSERVED_MEDIA_IDS = mutableStateSetOf(OBSERVER_MARKER)
        private val REVISION_WATCHER_STARTED = AtomicBoolean(false)
        private val REFRESH_IN_FLIGHT = AtomicBoolean(false)
        private val REVISION_WATCHER_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var observedFingerprint: String = "uninitialized"

        private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val ALLOWED_LICENSES = setOf(
            "CC0-1.0", "CC-BY-4.0", "CC-BY-NC-4.0",
            "https://creativecommons.org/publicdomain/zero/1.0/",
            "https://creativecommons.org/licenses/by/4.0/",
            "https://creativecommons.org/licenses/by-nc/4.0/"
        )
    }
}
