package vn.duongodau.app.data

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Bounded offline OSM discovery only. No geometry-to-admin assignment or route permission. */
class RoadCatalogRepository(private val context: Context) {
    data class Entry(
        val id: String,
        val name: String,
        val roadClass: String,
        val surface: String?,
        val maxWeight: String?,
        val sourceUrl: String,
        val sourceTimestamp: String?
    )

    data class Catalog(
        val entries: List<Entry>,
        val retrievedAt: String,
        val sourceSha256: String,
        val scope: String
    ) {
        fun search(query: String, limit: Int = 30): List<Entry> {
            val needle = normalize(query.trim())
            if (needle.isEmpty()) return emptyList()
            return entries.asSequence()
                .filter { normalize(it.name).contains(needle) || normalize(it.id).contains(needle) }
                .take(limit)
                .toList()
        }
    }

    fun load(): Catalog {
        val json = JSONObject(context.assets.open("road_pilot_bbox.json").bufferedReader().use { it.readText() })
        require(json.getInt("schemaVersion") == 1)
        require(json.getString("scope") == "BBOX_ONLY_UNMAPPED_ADMIN")
        require(json.getString("verification") == "UNVERIFIED")
        val roads = json.getJSONArray("roads")
        val entries = (0 until roads.length()).map { index ->
            val row = roads.getJSONObject(index)
            val id = row.getString("id")
            val sourceUrl = row.getString("sourceUrl")
            require(id.matches(Regex("osm-way-[0-9]+")))
            require(sourceUrl == "https://www.openstreetmap.org/way/${id.removePrefix("osm-way-")}")
            Entry(
                id = id,
                name = row.getString("name"),
                roadClass = row.getString("roadClass"),
                surface = row.optString("surface").takeIf { it.isNotBlank() && it != "null" },
                maxWeight = row.optString("maxWeight").takeIf { it.isNotBlank() && it != "null" },
                sourceUrl = sourceUrl,
                sourceTimestamp = row.optString("sourceTimestamp").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        require(entries.map { it.id }.distinct().size == entries.size)
        return Catalog(entries, json.getString("retrievedAt"), json.getString("sourceSha256"), json.getString("scope"))
    }

    companion object {
        private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd').replace('Đ', 'D')
            .lowercase(Locale.ROOT)
    }
}
