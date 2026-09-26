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
        SpeciesRecord("mangifera-indica", "Xoài", "Mangifera indica L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/69913-1", "Tên khoa học và phân loại; không xác minh mẫu vật trong ảnh."),
        SpeciesRecord("musa-acuminata", "Chuối (Musa acuminata)", "Musa acuminata Colla", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:797527-1", "Tên khoa học và phân loại; tên thông dụng không đủ để nhận dạng."),
        SpeciesRecord("cocos-nucifera", "Dừa", "Cocos nucifera L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/666160-1", "Tên khoa học, phân loại và phạm vi phân bố; không xác minh mẫu vật trong ảnh."),
        SpeciesRecord("bambusa-vulgaris", "Tre Bambusa vulgaris", "Bambusa vulgaris Schrad. ex J.C.Wendl.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/392574-1", "Tên được chấp nhận, phân loại và phạm vi phân bố; không suy ra công dụng hoặc độ an toàn."),
        SpeciesRecord("centella-asiatica", "Rau má", "Centella asiatica (L.) Urb.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/1197718-2", "Tên được chấp nhận, phân loại và phạm vi phân bố; không dùng hồ sơ taxonomy để hướng dẫn dùng làm thuốc hoặc thực phẩm."),
        SpeciesRecord("hypericum-perforatum", "St. John's wort (Hypericum perforatum)", "Hypericum perforatum L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/433719-1", "Tên được chấp nhận và phân loại. Cảnh báo tương tác thuốc trong app lấy từ nguồn y khoa riêng, không suy ra từ taxonomy Kew."),
        SpeciesRecord("camellia-sinensis", "Chè / trà (Camellia sinensis)", "Camellia sinensis (L.) Kuntze", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/828548-1", "Tên được chấp nhận, phân loại và phạm vi phân bố; không dùng hồ sơ taxonomy để kết luận liều dùng, công dụng hoặc tương tác thuốc."),
        SpeciesRecord("oryza-sativa", "Lúa", "Oryza sativa L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:316812-2", "Tên được chấp nhận và phân loại cho cây lúa; hồ sơ taxonomy không thay thế dữ liệu giống, canh tác, sâu bệnh hoặc an toàn thực phẩm."),
        SpeciesRecord("zingiber-officinale", "Gừng", "Zingiber officinale Roscoe", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:798372-1", "Tên được chấp nhận và phân loại; thông tin dùng làm thực phẩm hoặc dược liệu phải lấy từ nguồn chuyên ngành riêng."),
        SpeciesRecord("curcuma-longa", "Nghệ", "Curcuma longa L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/796451-1", "Tên được chấp nhận và phân loại; không suy ra hiệu quả điều trị, liều dùng hoặc chống chỉ định từ taxonomy."),
        SpeciesRecord("cymbopogon-citratus", "Sả", "Cymbopogon citratus (DC.) Stapf", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/396896-1", "Tên được chấp nhận, phân loại và phân bố; không suy ra tính an toàn thực phẩm hay dược liệu từ taxonomy."),
        SpeciesRecord("carica-papaya", "Đu đủ", "Carica papaya L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:30011248-2", "Tên được chấp nhận, phân loại và phân bố; không dùng taxonomy để kết luận bộ phận nào ăn được, liều dùng hoặc công dụng chữa bệnh."),
        SpeciesRecord("psidium-guajava", "Ổi", "Psidium guajava L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/600841-1", "Tên được chấp nhận và phân loại; dữ liệu công dụng, thực phẩm hoặc dược liệu phải có nguồn riêng."),
        SpeciesRecord("apis-cerana", "Ong mật châu Á", "Apis cerana Fabricius, 1793", "Côn trùng", "GBIF — Catalogue of Life backbone", "https://www.gbif.org/species/1341979", "Tên khoa học và phân loại; không suy ra mức nguy hiểm hay xử trí vết đốt."),
        SpeciesRecord("aedes-aegypti", "Muỗi vằn (Aedes aegypti)", "Aedes (Stegomyia) aegypti (Linnaeus, 1762)", "Côn trùng", "GBIF — Catalogue of Life backbone", "https://www.gbif.org/taxon/89W72", "Tên khoa học và phân loại; không dùng hồ sơ này để chẩn đoán bệnh hoặc xác minh cá thể trong ảnh."),
        SpeciesRecord("vespa-tropica", "Ong bắp cày Vespa tropica", "Vespa tropica (Linnaeus, 1758)", "Côn trùng", "GBIF — Catalogue of Life", "https://www.gbif.org/taxon/7G3C3", "Tên được chấp nhận và phân loại; không suy ra mức nguy hiểm, độc tính hoặc xử trí khi bị đốt."),
        SpeciesRecord("macaca-fascicularis", "Khỉ đuôi dài", "Macaca fascicularis (Raffles, 1821)", "Động vật", "GBIF — Integrated Taxonomic Information System", "https://www.gbif.org/species/102119311", "Tên khoa học và phân loại; không xác minh cá thể trong ảnh hoặc hành vi an toàn."),
        SpeciesRecord("varanus-salvator", "Kỳ đà nước", "Varanus salvator (Laurenti, 1768)", "Động vật", "GBIF — Catalogue of Life backbone", "https://www.gbif.org/taxon/7FG74", "Tên khoa học và phân loại; không xác minh cá thể trong ảnh hoặc hướng dẫn tiếp cận."),
        SpeciesRecord("ganoderma-lucidum", "Nấm Ganoderma lucidum", "Ganoderma lucidum (Curtis) P. Karst.", "Nấm", "GBIF — Catalogue of Life / Species Fungorum", "https://www.gbif.org/taxon/6JWYZ", "Tên được chấp nhận và phân loại nấm; không dùng hồ sơ này để quyết định ăn, dùng thuốc hoặc tự nhận dạng mẫu ngoài thực địa."),
        SpeciesRecord("termitomyces-clypeatus", "Nấm Termitomyces clypeatus", "Termitomyces clypeatus R.Heim", "Nấm", "GBIF Backbone Taxonomy", "https://www.gbif.org/species/2530598", "Tên khoa học và phân loại; không dùng hồ sơ taxonomy để kết luận ăn được hoặc xác minh mẫu vật trong ảnh.")
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
        return tokens.any { token -> token == needle || (needle.length >= 4 && token.startsWith(needle)) }
    }

    fun search(query: String, group: String = "Tất cả"): List<SpeciesRecord> {
        val needle = normalized(query.trim())
        return records.filter { record ->
            (group == "Tất cả" || record.group == group) &&
                (matchesQuery(record.vietnameseName, needle) || matchesQuery(record.scientificName, needle))
        }
    }
}
