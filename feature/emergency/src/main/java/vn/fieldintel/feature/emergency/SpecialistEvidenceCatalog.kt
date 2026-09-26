package vn.fieldintel.feature.emergency

enum class EvidenceDomain {
    VIETNAM_TRADITIONAL_MEDICINE,
    HERBAL_MEDICINE_MONOGRAPH,
    TOXICOLOGY
}

enum class EvidenceClass {
    OFFICIAL_LISTING,
    REGULATORY_MONOGRAPH,
    PUBLIC_HEALTH_TOXICOLOGY
}

data class SpecialistEvidenceRecord(
    val id: String,
    val speciesId: String,
    val domain: EvidenceDomain,
    val evidenceClass: EvidenceClass,
    val title: String,
    val statement: String,
    val plantPart: String = "",
    val sourceName: String,
    val sourceUrl: String,
    val sourceRecord: String = "",
    val scopeNote: String
)

/**
 * High-risk specialist evidence kept separate from taxonomy.
 *
 * A record here proves only the bounded statement carried by that source. It must not be expanded
 * into a diagnosis, dose, treatment recommendation, edibility decision, or image identification.
 */
object SpecialistEvidenceCatalog {
    val records: List<SpecialistEvidenceRecord> = listOf(
        SpecialistEvidenceRecord(
            id = "moh-curcuma-longa-khuong-hoang-uat-kim",
            speciesId = "curcuma-longa",
            domain = EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE,
            evidenceClass = EvidenceClass.OFFICIAL_LISTING,
            title = "Khương hoàng/Uất kim — danh mục dược liệu YDCT",
            statement = "Nguồn Bộ Y tế liệt kê Khương hoàng/Uất kim với dược liệu Rhizoma et Radix Curcumae longae và loài Curcuma longa L.",
            plantPart = "Thân rễ / rễ theo tên dược liệu trong nguồn",
            sourceName = "Cục Quản lý Y, Dược cổ truyền — Bộ Y tế Việt Nam",
            sourceUrl = "https://emohbackup.moh.gov.vn/publish/attach/getfile/412855",
            sourceRecord = "Nhóm thuốc hoạt huyết, khứ ứ — Khương hoàng/Uất kim",
            scopeNote = "Xác nhận loài/dược liệu xuất hiện trong danh mục chính thức; không tự suy ra chỉ định, liều dùng, hiệu quả hay chống chỉ định."
        ),
        SpecialistEvidenceRecord(
            id = "ema-curcuma-longa-rhizoma",
            speciesId = "curcuma-longa",
            domain = EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH,
            evidenceClass = EvidenceClass.REGULATORY_MONOGRAPH,
            title = "Curcumae longae rhizoma — EU herbal monograph",
            statement = "EMA/HMPC có chuyên khảo dược liệu cho Curcuma longa L., rhizoma và trạng thái đánh giá đã hoàn tất.",
            plantPart = "Rhizoma",
            sourceName = "European Medicines Agency — HMPC",
            sourceUrl = "https://www.ema.europa.eu/en/medicines/herbal/curcumae-longae-rhizoma",
            sourceRecord = "EMA/HMPC/329755/2017; addendum EMA/HMPC/81467/2025",
            scopeNote = "Chỉ ghi nhận tồn tại chuyên khảo quản lý và bộ phận dược liệu; không biến thành khuyến nghị tự điều trị."
        ),
        SpecialistEvidenceRecord(
            id = "cdc-ricinus-communis-ricin",
            speciesId = "ricinus-communis",
            domain = EvidenceDomain.TOXICOLOGY,
            evidenceClass = EvidenceClass.PUBLIC_HEALTH_TOXICOLOGY,
            title = "Ricin trong hạt thầu dầu",
            statement = "CDC xác nhận ricin là độc chất tự nhiên có trong hạt thầu dầu; hạt bị nhai và nuốt có thể giải phóng ricin và gây tổn thương.",
            plantPart = "Hạt",
            sourceName = "U.S. Centers for Disease Control and Prevention",
            sourceUrl = "https://www.cdc.gov/chemical-emergencies/chemical-fact-sheets/ricin.html",
            sourceRecord = "Ricin Chemical Fact Sheet",
            scopeNote = "Cảnh báo độc chất ở hạt; không dùng bản ghi này để nhận dạng cây ngoài thực địa hoặc suy ra mức phơi nhiễm của một ca cụ thể."
        )
    )

    fun forSpecies(speciesId: String): List<SpecialistEvidenceRecord> =
        records.filter { it.speciesId == speciesId }

    fun speciesIdsFor(domain: EvidenceDomain): Set<String> =
        records.asSequence().filter { it.domain == domain }.map { it.speciesId }.toSet()
}
