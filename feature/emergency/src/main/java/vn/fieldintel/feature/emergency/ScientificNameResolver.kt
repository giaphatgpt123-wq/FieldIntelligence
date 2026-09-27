package vn.fieldintel.feature.emergency

import android.database.sqlite.SQLiteDatabase
import java.util.Locale

/**
 * Bridges visual-model species labels to the authored scientific names stored by the taxonomy pack.
 *
 * A model may emit a canonical binomial such as `Channa striata`, while the scientific library may
 * store `Channa striata (Bloch, 1793)`. Resolution is deliberately narrow: exact name or the same
 * canonical binomial followed by authorship. It never falls back to another species/subspecies or
 * a genus neighbour.
 */
object ScientificNameResolver {
    fun matchesCanonical(query: String, candidate: String): Boolean {
        val q = collapse(query)
        val c = collapse(candidate)
        if (q.isBlank() || c.isBlank()) return false
        if (c.equals(q, ignoreCase = true)) return true

        // Only expand a canonical binomial. A longer model label must match exactly.
        if (q.split(' ').size != 2) return false
        val prefix = "$q "
        if (!c.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) return false

        val suffix = c.substring(prefix.length).trimStart()
        if (suffix.isBlank()) return false
        // Authorship commonly starts with '(' / '[' or an uppercase author surname. A lowercase
        // third epithet is treated as an infraspecific name and is deliberately rejected.
        return suffix.startsWith("(") || suffix.startsWith("[") || suffix.first().isUpperCase()
    }

    fun resolve(store: ScientificLibraryStore, scientificName: String): SpeciesRecord? {
        val queryText = collapse(scientificName)
        val query = searchNormalize(queryText)
        if (query.isBlank()) return null

        SpeciesCatalog.records.firstOrNull { matchesCanonical(queryText, it.scientificName) }?.let { return it }

        val database = store.databasePath()
        if (!database.isFile || database.length() <= 0L) return null

        val storedName = runCatching {
            SQLiteDatabase.openDatabase(
                database.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { db ->
                db.rawQuery(
                    """
                    SELECT scientific_name
                    FROM taxon
                    WHERE scientific_name_search = ?
                       OR scientific_name_search LIKE ? ESCAPE '\\'
                    ORDER BY CASE WHEN scientific_name_search = ? THEN 0 ELSE 1 END,
                             length(scientific_name_search), scientific_name_search
                    LIMIT 12
                    """.trimIndent(),
                    arrayOf(query, "${escapeLike(query)} %", query)
                ).use { cursor ->
                    var match: String? = null
                    while (cursor.moveToNext() && match == null) {
                        val candidate = cursor.getString(0)
                        if (matchesCanonical(queryText, candidate)) match = candidate
                    }
                    match
                }
            }
        }.getOrNull() ?: return null

        return store.findByScientificNames(listOf(storedName), limit = 8)
            .firstOrNull { matchesCanonical(queryText, it.scientificName) }
    }

    private fun collapse(value: String): String = value.trim().replace(Regex("\\s+"), " ")
    private fun searchNormalize(value: String): String = collapse(value).lowercase(Locale.ROOT)

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
