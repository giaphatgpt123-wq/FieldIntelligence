package vn.fieldintel.feature.emergency

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ScientificLibraryStatusSnapshot(
    val installed: Boolean,
    val recordCount: Long = 0,
    val acceptedRecordCount: Long = 0,
    val sourceVersion: String = "",
    val sourceDoi: String = "",
    val sourceLicense: String = "",
    val scope: String = "taxonomy-only"
)

/** Observable status holder updated by the IO loader. */
class ScientificLibraryStatus internal constructor() {
    var installed by mutableStateOf(false)
        internal set
    var recordCount by mutableStateOf(0L)
        internal set
    var acceptedRecordCount by mutableStateOf(0L)
        internal set
    var sourceVersion by mutableStateOf("")
        internal set
    var sourceDoi by mutableStateOf("")
        internal set
    var sourceLicense by mutableStateOf("")
        internal set
    var scope by mutableStateOf("taxonomy-only")
        internal set

    internal fun publish(value: ScientificLibraryStatusSnapshot) {
        installed = value.installed
        recordCount = value.recordCount
        acceptedRecordCount = value.acceptedRecordCount
        sourceVersion = value.sourceVersion
        sourceDoi = value.sourceDoi
        sourceLicense = value.sourceLicense
        scope = value.scope
    }
}

private class ScientificSearchState {
    var loading by mutableStateOf(false)
    var completed by mutableStateOf(false)
}

/**
 * Read-only access to the separately distributed scientific taxonomy database.
 *
 * UI-facing status/search/id access never performs SQLite reads on the Compose thread. Taxonomy
 * matches remain taxonomy/provenance only and must not be treated as image identification,
 * edibility/toxicity evidence, or medical guidance.
 */
class ScientificLibraryStore(context: Context) {
    private val appContext = context.applicationContext
    private val databaseFile = File(File(appContext.filesDir, DIRECTORY_NAME), DATABASE_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val liveStatus = ScientificLibraryStatus()
    private val searchResults = ConcurrentHashMap<SearchKey, SnapshotStateList<SpeciesRecord>>()
    private val searchStates = ConcurrentHashMap<SearchKey, ScientificSearchState>()
    private val recordCache = ConcurrentHashMap<String, SpeciesRecord>()
    private val pendingIdLoads = ConcurrentHashMap.newKeySet<String>()
    private val searchLock = Any()
    private var activeSearchJob: Job? = null
    private var activeSearchKey: SearchKey? = null

    init {
        refreshStatusAsync()
        LibraryCollectionRuntime.bind(appContext, this)
    }

    fun databasePath(): File = databaseFile

    /** Returns immediately. The same observable object is updated after the IO status read. */
    fun status(): ScientificLibraryStatus {
        refreshStatusAsync()
        return liveStatus
    }

    /** Call after an atomic pack replacement so stale searches and collection snapshots are cleared. */
    fun refreshAfterImport() {
        synchronized(searchLock) {
            activeSearchJob?.cancel()
            activeSearchJob = null
            activeSearchKey = null
            searchResults.clear()
            searchStates.clear()
        }
        recordCache.clear()
        pendingIdLoads.clear()
        refreshStatusAsync()
        LibraryCollectionRuntime.bind(appContext, this)
    }

    internal fun isInstalledBlocking(): Boolean = readStatusBlocking().installed

    /**
     * Returns an observable result list immediately. The first request for a key is debounced and
     * executed on Dispatchers.IO; a completed zero-result search stays completed and is not silently
     * reissued on every recomposition.
     */
    fun search(query: String, group: String = "Tất cả", limit: Int = 80): List<SpeciesRecord> {
        val key = searchKey(query, group, limit) ?: return emptyList()
        val observable = searchResults.getOrPut(key) { mutableStateListOf() }
        val state = searchStates.getOrPut(key) { ScientificSearchState() }

        synchronized(searchLock) {
            if (!state.loading && !state.completed) {
                activeSearchJob?.cancel()
                activeSearchKey = key
                state.loading = true
                activeSearchJob = scope.launch {
                    try {
                        delay(SEARCH_DEBOUNCE_MS)
                        val loaded = searchBlocking(key.query, key.group, key.limit)
                        loaded.forEach { recordCache[it.id] = it }
                        Snapshot.withMutableSnapshot {
                            observable.clear()
                            observable.addAll(loaded)
                            state.completed = true
                        }
                    } finally {
                        Snapshot.withMutableSnapshot { state.loading = false }
                    }
                }
            }
        }
        return observable
    }

    fun isSearching(query: String, group: String = "Tất cả", limit: Int = 80): Boolean {
        val key = searchKey(query, group, limit) ?: return false
        return searchStates[key]?.loading == true
    }

    fun isSearchCompleted(query: String, group: String = "Tất cả", limit: Int = 80): Boolean {
        val key = searchKey(query, group, limit) ?: return false
        return searchStates[key]?.completed == true
    }

    /**
     * UI path is cache-first and never opens SQLite synchronously. Search/collection resolution puts
     * displayed external records into this cache. A cache miss schedules a background lookup.
     */
    fun findById(id: String): SpeciesRecord? {
        recordCache[id]?.let { return it }
        if (!databaseFile.isFile || !id.startsWith(ID_PREFIX)) return null
        if (pendingIdLoads.add(id)) {
            scope.launch {
                try {
                    findByIdBlocking(id)?.let { recordCache[id] = it }
                } finally {
                    pendingIdLoads.remove(id)
                }
            }
        }
        return null
    }

    /** Background-only bulk lookup used by LibraryCollectionRuntime. */
    fun findByScientificNames(scientificNames: Collection<String>, limit: Int = 500): List<SpeciesRecord> {
        if (!databaseFile.isFile || scientificNames.isEmpty()) return emptyList()
        val normalized = scientificNames.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit.coerceIn(1, 1000))
            .toList()
        if (normalized.isEmpty()) return emptyList()
        val loaded = runCatching {
            openReadOnly().use { db ->
                requireSchema(db)
                val placeholders = normalized.joinToString(",") { "?" }
                val sql = """
                    SELECT source_id, source_record_id, scientific_name, library_group,
                           authority, license, source_scope, source_version, source_doi
                    FROM taxon
                    WHERE scientific_name_search IN ($placeholders)
                    ORDER BY scientific_name_search
                """.trimIndent()
                db.rawQuery(sql, normalized.toTypedArray()).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.toSpeciesRecord())
                    }
                }
            }
        }.getOrElse { emptyList() }
        loaded.forEach { recordCache[it.id] = it }
        return loaded
    }

    private fun refreshStatusAsync() {
        scope.launch {
            val loaded = readStatusBlocking()
            Snapshot.withMutableSnapshot { liveStatus.publish(loaded) }
        }
    }

    private fun readStatusBlocking(): ScientificLibraryStatusSnapshot {
        if (!databaseFile.isFile || databaseFile.length() <= 0L) return ScientificLibraryStatusSnapshot(false)
        return runCatching {
            openReadOnly().use { db ->
                requireSchema(db)
                val meta = readMeta(db)
                ScientificLibraryStatusSnapshot(
                    installed = true,
                    recordCount = meta["recordCount"]?.trimJsonString()?.toLongOrNull() ?: 0L,
                    acceptedRecordCount = meta["acceptedRecordCount"]?.trimJsonString()?.toLongOrNull() ?: 0L,
                    sourceVersion = meta["sourceVersion"]?.trimJsonString().orEmpty(),
                    sourceDoi = meta["sourceDoi"]?.trimJsonString().orEmpty(),
                    sourceLicense = meta["sourceLicense"]?.trimJsonString().orEmpty(),
                    scope = meta["scope"]?.trimJsonString().orEmpty().ifBlank { "taxonomy-only" }
                )
            }
        }.getOrElse { ScientificLibraryStatusSnapshot(false) }
    }

    private fun searchBlocking(needle: String, group: String, safeLimit: Int): List<SpeciesRecord> {
        if (!databaseFile.isFile) return emptyList()
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

    private fun findByIdBlocking(id: String): SpeciesRecord? {
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
        val meta = readMeta(db)
        val schemaVersion = meta["schemaVersion"]?.trimJsonString()?.toIntOrNull()
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) { "Unsupported scientific library schema: $schemaVersion" }
        require(meta["scope"]?.trimJsonString() == "taxonomy-only") { "Unexpected scientific library scope" }
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
        val sourceScope = getString(6).ifBlank { "taxonomy-only" }
        val version = getString(7)
        val doi = getString(8)
        val provenance = buildString {
            append("Phạm vi nguồn: ").append(sourceScope)
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

    private fun searchKey(query: String, group: String, limit: Int): SearchKey? {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return null
        return SearchKey(needle, group, limit.coerceIn(1, 200))
    }

    private data class SearchKey(val query: String, val group: String, val limit: Int)

    companion object {
        const val DIRECTORY_NAME = "scientific-library"
        const val DATABASE_NAME = "wfo-taxonomy.sqlite"
        private const val SUPPORTED_SCHEMA_VERSION = 1
        private const val ID_PREFIX = "scientific-db:"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
