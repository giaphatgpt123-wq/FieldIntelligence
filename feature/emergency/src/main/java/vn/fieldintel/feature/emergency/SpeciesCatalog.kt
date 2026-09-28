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
    val groups: List<String> = listOf("Thực vật", "Động vật", "Cá nước ngọt", "Côn trùng", "Nấm")

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
        SpeciesRecord("curcuma-longa", "Nghệ vàng", "Curcuma longa L.", "Thực vật", "Sở Khoa học và Công nghệ TP Cần Thơ; Kew POWO", "https://sokhcn.cantho.gov.vn/default.aspx?nid=17522&pid=57", "Tên Việt gắn với Curcuma longa; không gộp nghệ đen hoặc các loài Curcuma khác; công dụng và cách dùng cần chứng cứ riêng theo bộ phận."),
        SpeciesRecord("curcuma-zedoaria", "Nghệ đen (nga truật)", "Curcuma zedoaria (Christm.) Roscoe", "Thực vật", "Tạp chí Khoa học và Công nghệ (VISTA); Kew POWO", "https://vjol.vista.gov.vn/KHH/article/view/17631", "Nguồn nghiên cứu Việt Nam gọi Curcuma zedoaria là nghệ đen; Kew xác nhận tên khoa học được chấp nhận. Chưa xác minh công dụng, cách dùng hay ảnh offline cho hồ sơ này."),
        SpeciesRecord("cymbopogon-citratus", "Sả", "Cymbopogon citratus (DC.) Stapf", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/396896-1", "Tên được chấp nhận, phân loại và phân bố; không suy ra tính an toàn thực phẩm hay dược liệu từ taxonomy."),
        SpeciesRecord("carica-papaya", "Đu đủ", "Carica papaya L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:30011248-2", "Tên được chấp nhận, phân loại và phân bố; không dùng taxonomy để kết luận bộ phận nào ăn được, liều dùng hoặc công dụng chữa bệnh."),
        SpeciesRecord("psidium-guajava", "Ổi", "Psidium guajava L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/600841-1", "Tên được chấp nhận và phân loại; dữ liệu công dụng, thực phẩm hoặc dược liệu phải có nguồn riêng."),
        SpeciesRecord("ipomoea-batatas", "Khoai lang", "Ipomoea batatas (L.) Lam.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/1101088-2", "Tên khoa học và phân loại; không dùng để nhận dạng mẫu vật trong ảnh hoặc kết luận tính ăn được, độc tính, công dụng."),
        SpeciesRecord("manihot-esculenta", "Sắn (khoai mì)", "Manihot esculenta Crantz", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/351790-1", "Tên khoa học và phân loại; không dùng để nhận dạng mẫu vật trong ảnh hoặc kết luận tính ăn được, độc tính, công dụng."),
        SpeciesRecord("colocasia-esculenta", "Khoai môn (Colocasia esculenta)", "Colocasia esculenta (L.) Schott", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/1170772-2", "Tên khoa học và phân loại; không dùng để nhận dạng mẫu vật trong ảnh hoặc kết luận tính ăn được, độc tính, công dụng."),
        SpeciesRecord("piper-nigrum", "Hồ tiêu", "Piper nigrum L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/682369-1", "Tên khoa học và phân loại; không dùng để nhận dạng mẫu vật trong ảnh hoặc kết luận tính ăn được, độc tính, công dụng."),
        SpeciesRecord("ananas-comosus", "Dứa (thơm)", "Ananas comosus (L.) Merr.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/12322-2", "Tên khoa học và phân loại; không dùng để nhận dạng mẫu vật trong ảnh hoặc kết luận tính ăn được, độc tính, công dụng."),
        SpeciesRecord("artocarpus-heterophyllus", "Mít", "Artocarpus heterophyllus Lam.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/850389-1", "Tên khoa học và phân loại; không dùng để nhận dạng mẫu vật trong ảnh hoặc kết luận tính ăn được, độc tính, công dụng."),
        SpeciesRecord("capsicum-annuum", "Ớt (Capsicum annuum)", "Capsicum annuum L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/316944-2", "Tên khoa học và phân loại; không dùng để nhận dạng mẫu vật trong ảnh hoặc kết luận tính ăn được, độc tính, công dụng."),
        SpeciesRecord("citrus-maxima", "Bưởi", "Citrus maxima (Burm.) Merr.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/30075266-2", "Tên khoa học và phân loại; không dùng để nhận dạng mẫu vật trong ảnh hoặc kết luận tính ăn được, độc tính, công dụng."),
        SpeciesRecord("ricinus-communis", "Thầu dầu", "Ricinus communis L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/355498-1", "Tên được chấp nhận và phân loại. Cảnh báo ricin/hạt được lấy từ lớp độc chất CDC riêng, không suy ra từ taxonomy Kew."),
        SpeciesRecord("abrus-precatorius", "Cam thảo dây / rosary pea", "Abrus precatorius L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/469605-1", "Tên được chấp nhận và phân loại. Cảnh báo abrin trong hạt được lấy từ lớp độc chất CDC/NIOSH riêng, không suy ra từ taxonomy Kew."),
        SpeciesRecord("nerium-oleander", "Trúc đào / oleander", "Nerium oleander L.", "Thực vật", "Royal Botanic Gardens, Kew — Plants of the World Online", "https://powo.science.kew.org/taxon/80460-1", "Tên được chấp nhận và phân loại. Cảnh báo độc tính được lấy từ lớp bằng chứng chuyên ngành riêng, không suy ra từ taxonomy."),
        SpeciesRecord("apis-cerana", "Ong mật châu Á", "Apis cerana Fabricius, 1793", "Côn trùng", "GBIF — Catalogue of Life backbone", "https://www.gbif.org/species/1341979", "Tên khoa học và phân loại; không suy ra mức nguy hiểm hay xử trí vết đốt."),
        SpeciesRecord("aedes-aegypti", "Muỗi vằn (Aedes aegypti)", "Aedes (Stegomyia) aegypti (Linnaeus, 1762)", "Côn trùng", "GBIF — Catalogue of Life backbone", "https://www.gbif.org/taxon/89W72", "Tên khoa học và phân loại; không dùng hồ sơ này để chẩn đoán bệnh hoặc xác minh cá thể trong ảnh."),
        SpeciesRecord("vespa-tropica", "Ong bắp cày Vespa tropica", "Vespa tropica (Linnaeus, 1758)", "Côn trùng", "GBIF — Catalogue of Life", "https://www.gbif.org/taxon/7G3C3", "Tên được chấp nhận và phân loại; không suy ra mức nguy hiểm, độc tính hoặc xử trí khi bị đốt."),
        SpeciesRecord("macaca-fascicularis", "Khỉ đuôi dài", "Macaca fascicularis (Raffles, 1821)", "Động vật", "GBIF — Integrated Taxonomic Information System", "https://www.gbif.org/species/102119311", "Tên khoa học và phân loại; không xác minh cá thể trong ảnh hoặc hành vi an toàn."),
        SpeciesRecord("varanus-salvator", "Kỳ đà nước", "Varanus salvator (Laurenti, 1768)", "Động vật", "GBIF — Catalogue of Life backbone", "https://www.gbif.org/taxon/7FG74", "Tên khoa học và phân loại; không xác minh cá thể trong ảnh hoặc hướng dẫn tiếp cận."),
        SpeciesRecord("ganoderma-lucidum", "Nấm linh chi (Ganoderma lucidum)", "Ganoderma lucidum (Curtis) P. Karst.", "Nấm", "Viện Khoa học Lâm nghiệp Việt Nam; GBIF", "https://vafs.gov.vn/vn/gia-tri-duoc-lieu-va-cai-thien-chat-luong-trong-nuoi-trong-nhan-tao-nam-linh-chi-viet-nam/", "Nguồn Việt Nam ghép nấm linh chi với Ganoderma lucidum; tên linh chi cũng dùng cho nhiều loài khác. Không dùng tên thông dụng để xác định ăn được hay làm thuốc."),
        SpeciesRecord("termitomyces-clypeatus", "Nấm Termitomyces clypeatus", "Termitomyces clypeatus R.Heim", "Nấm", "GBIF Backbone Taxonomy", "https://www.gbif.org/species/2530598", "Tên khoa học và phân loại; không dùng hồ sơ taxonomy để kết luận ăn được hoặc xác minh mẫu vật trong ảnh.")
    ) + FreshwaterFishCatalog.records

    fun countByGroup(group: String): Int = records.count { it.group == group }

    private fun normalized(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('đ', 'd')

    private fun matchesQuery(value: String, needle: String, accentSensitive: Boolean): Boolean {
        if (needle.isEmpty()) return true
        val candidate = if (accentSensitive) value.lowercase(Locale.ROOT) else normalized(value)
        if (' ' in needle) return candidate.contains(needle)
        val tokens = candidate.split(Regex("[^\\p{L}0-9]+"))
            .filter { it.isNotEmpty() }
        return tokens.any { token -> token == needle || (needle.length >= 4 && token.startsWith(needle)) }
    }

    fun search(query: String, group: String = "Tất cả"): List<SpeciesRecord> {
        val raw = query.trim().lowercase(Locale.ROOT)
        val accentSensitive = normalized(raw) != raw
        val needle = if (accentSensitive) raw else normalized(raw)
        return records.filter { record ->
            (group == "Tất cả" || record.group == group) &&
                (matchesQuery(record.vietnameseName, needle, accentSensitive) ||
                    matchesQuery(record.scientificName, needle, accentSensitive))
        }
    }
}
