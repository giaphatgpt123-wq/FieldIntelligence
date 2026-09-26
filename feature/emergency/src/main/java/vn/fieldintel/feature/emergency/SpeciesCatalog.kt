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
 * Offline starter catalog with explicit provenance.
 * Sources verify taxonomic names/classification only. They do not verify a photographed specimen,
 * edibility, toxicity, treatment, or field-safety decisions.
 */
object SpeciesCatalog {
    val groups: List<String> = listOf("Thực vật", "Động vật", "Côn trùng", "Nấm")

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
            id = "bambusa-vulgaris",
            vietnameseName = "Tre Bambusa vulgaris",
            scientificName = "Bambusa vulgaris Schrad. ex J.C.Wendl.",
            group = "Thực vật",
            sourceName = "Royal Botanic Gardens, Kew — Plants of the World Online",
            sourceUrl = "https://powo.science.kew.org/taxon/392574-1",
            sourceScope = "Tên được chấp nhận, phân loại và phạm vi phân bố; không suy ra công dụng hoặc độ an toàn."
        ),
        SpeciesRecord(
            id = "centella-asiatica",
            vietnameseName = "Rau má",
            scientificName = "Centella asiatica (L.) Urb.",
            group = "Thực vật",
            sourceName = "Royal Botanic Gardens, Kew — Plants of the World Online",
            sourceUrl = "https://powo.science.kew.org/taxon/1197718-2",
            sourceScope = "Tên được chấp nhận, phân loại và phạm vi phân bố; không dùng hồ sơ taxonomy để hướng dẫn dùng làm thuốc hoặc thực phẩm."
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
            id = "vespa-tropica",
            vietnameseName = "Ong bắp cày Vespa tropica",
            scientificName = "Vespa tropica (Linnaeus, 1758)",
            group = "Côn trùng",
            sourceName = "GBIF — Catalogue of Life",
            sourceUrl = "https://www.gbif.org/taxon/7G3C3",
            sourceScope = "Tên được chấp nhận và phân loại; không suy ra mức nguy hiểm, độc tính hoặc xử trí khi bị đốt."
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
        ),
        SpeciesRecord(
            id = "ganoderma-lucidum",
            vietnameseName = "Nấm Ganoderma lucidum",
            scientificName = "Ganoderma lucidum (Curtis) P. Karst.",
            group = "Nấm",
            sourceName = "GBIF — Catalogue of Life / Species Fungorum",
            sourceUrl = "https://www.gbif.org/taxon/6JWYZ",
            sourceScope = "Tên được chấp nhận và phân loại nấm; không dùng hồ sơ này để quyết định ăn, dùng thuốc hoặc tự nhận dạng mẫu ngoài thực địa."
        ),
        SpeciesRecord(
            id = "termitomyces-clypeatus",
            vietnameseName = "Nấm Termitomyces clypeatus",
            scientificName = "Termitomyces clypeatus R.Heim",
            group = "Nấm",
            sourceName = "GBIF Backbone Taxonomy",
            sourceUrl = "https://www.gbif.org/species/2530598",
            sourceScope = "Tên khoa học và phân loại; không dùng hồ sơ taxonomy để kết luận ăn được hoặc xác minh mẫu vật trong ảnh."
        )
    )

    fun countByGroup(group: String): Int = records.count { it.group == group }

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
