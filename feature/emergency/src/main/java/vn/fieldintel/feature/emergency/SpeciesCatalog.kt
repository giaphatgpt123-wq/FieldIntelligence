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

/** Small offline starter catalog. Source verifies taxonomic names only, not image identity or safety. */
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
        )
    )
    private fun normalized(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd')

    fun search(query: String, group: String = "Tất cả"): List<SpeciesRecord> {
        val needle = normalized(query.trim())
        return records.filter { record ->
            (group == "Tất cả" || record.group == group) &&
                (needle.isEmpty() || normalized(record.vietnameseName).contains(needle) ||
                    normalized(record.scientificName).contains(needle))
        }
    }
}
