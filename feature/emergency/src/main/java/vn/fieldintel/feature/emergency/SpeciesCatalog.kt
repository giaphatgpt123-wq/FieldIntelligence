package vn.fieldintel.feature.emergency

import java.text.Normalizer
import java.util.Locale

data class SpeciesRecord(
    val id: String,
    val vietnameseName: String,
    val scientificName: String,
    val group: String,
    val sourceName: String,
    val sourceUrl: String,
    val sourceScope: String
)

/**
 * Small offline starter catalog.
 * Sources below verify taxonomic names/classification only. They do not verify a photographed
 * specimen, edibility, toxicity, treatment, or field-safety decisions.
 */
object SpeciesCatalog {
    val records: List<SpeciesRecord> = listOf(
        SpeciesRecord(
            id = "mangifera-indica",
            vietnameseName = "Xoài",
            scientificName = "Mangifera indica L.",
            group = "Thực vật",
            sourceName = "Royal Botanic Gardens, Kew — Plants of the World Online",
            sourceUrl = "https://powo.science.kew.org/taxon/69913-1",
            sourceScope = "Tên khoa học và phân loại; không xác minh mẫu vật trong ảnh."
        ),
        SpeciesRecord(
            id = "musa-acuminata",
            vietnameseName = "Chuối (Musa acuminata)",
            scientificName = "Musa acuminata Colla",
            group = "Thực vật",
            sourceName = "Royal Botanic Gardens, Kew — Plants of the World Online",
            sourceUrl = "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:797527-1",
            sourceScope = "Tên khoa học và phân loại; tên thông dụng không đủ để nhận dạng."
        ),
        SpeciesRecord(
            id = "cocos-nucifera",
            vietnameseName = "Dừa",
            scientificName = "Cocos nucifera L.",
            group = "Thực vật",
            sourceName = "Royal Botanic Gardens, Kew — Plants of the World Online",
            sourceUrl = "https://powo.science.kew.org/taxon/666160-1",
            sourceScope = "Tên khoa học, phân loại và phạm vi phân bố; không xác minh mẫu vật trong ảnh."
        ),
        SpeciesRecord(
            id = "apis-cerana",
            vietnameseName = "Ong mật châu Á",
            scientificName = "Apis cerana Fabricius, 1793",
            group = "Côn trùng",
            sourceName = "GBIF — Catalogue of Life backbone",
            sourceUrl = "https://www.gbif.org/species/1341979",
            sourceScope = "Tên khoa học và phân loại; không suy ra mức nguy hiểm hay xử trí vết đốt."
        ),
        SpeciesRecord(
            id = "aedes-aegypti",
            vietnameseName = "Muỗi vằn (Aedes aegypti)",
            scientificName = "Aedes (Stegomyia) aegypti (Linnaeus, 1762)",
            group = "Côn trùng",
            sourceName = "GBIF — Catalogue of Life backbone",
            sourceUrl = "https://www.gbif.org/taxon/89W72",
            sourceScope = "Tên khoa học và phân loại; không dùng hồ sơ này để chẩn đoán bệnh hoặc xác minh cá thể trong ảnh."
        ),
        SpeciesRecord(
            id = "macaca-fascicularis",
            vietnameseName = "Khỉ đuôi dài",
            scientificName = "Macaca fascicularis (Raffles, 1821)",
            group = "Động vật",
            sourceName = "GBIF — Integrated Taxonomic Information System",
            sourceUrl = "https://www.gbif.org/species/102119311",
            sourceScope = "Tên khoa học và phân loại; không xác minh cá thể trong ảnh hoặc hành vi an toàn."
        ),
        SpeciesRecord(
            id = "varanus-salvator",
            vietnameseName = "Kỳ đà nước",
            scientificName = "Varanus salvator (Laurenti, 1768)",
            group = "Động vật",
            sourceName = "GBIF — Catalogue of Life backbone",
            sourceUrl = "https://www.gbif.org/taxon/7FG74",
            sourceScope = "Tên khoa học và phân loại; không xác minh cá thể trong ảnh hoặc hướng dẫn tiếp cận."
        )
    )

    private fun normalized(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('đ', 'd')

    private fun matchesQuery(value: String, needle: String): Boolean {
        if (needle.isEmpty()) return true
        val candidate = normalized(value)
        if (' ' in needle) return candidate.contains(needle)

        val tokens = candidate.split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
        return tokens.any { token ->
            token == needle || (needle.length >= 4 && token.startsWith(needle))
        }
    }

    fun search(query: String, group: String = "Tất cả"): List<SpeciesRecord> {
        val needle = normalized(query.trim())
        return records.filter { record ->
            (group == "Tất cả" || record.group == group) &&
                (matchesQuery(record.vietnameseName, needle) || matchesQuery(record.scientificName, needle))
        }
    }
}
