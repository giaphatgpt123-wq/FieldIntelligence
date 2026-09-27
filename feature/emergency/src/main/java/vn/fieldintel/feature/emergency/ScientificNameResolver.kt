package vn.fieldintel.feature.emergency

import android.database.sqlite.SQLiteDatabase
import java.util.Locale

/**
 * Bridges visual-model species labels to the authored scientific names stored by the taxonomy pack.
 *
 * A model may emit a canonical binomial such as `Channa striata`, while the scientific library may
 * store `Channa striata (Bloch, 1793)`. Resolution is deliberately narrow: exact name or the same
 * canonical name followed by authorship. It never falls back to another species or genus neighbour.
 */
object ScientificNameResolver {
    fun matchesCanonical(query: String, candidate: String): Boolean {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isBlank() || c.isBlank()) return false
        return c == q || c.startsWith("$q (") || c.startsWith("$q ") && q.split(' ').size >= 2
    }

    fun resolve(store: ScientificLibraryStore, scientificName: String): SpeciesRecord? {
        val query = normalize(scientificName)
        if (query.isBlank()) return null

        SpeciesCatalog.records.firstOrNull { matchesCanonical(query, it.scientificName) }?.let { return it }

        val database = store.databasePath()
        if (!database.isFile || database.length() <= 0L) return null

        val exactStoredName = runCatching {
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
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(query, "${escapeLike(query)} %", query)
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }
        }.getOrNull() ?: return null

        if (!matchesCanonical(query, exactStoredName)) return null
        return store.findByScientificNames(listOf(exactStoredName), limit = 8)
            .firstOrNull { matchesCanonical(query, it.scientificName) }
    }

    private fun normalize(value: String): String = value.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
