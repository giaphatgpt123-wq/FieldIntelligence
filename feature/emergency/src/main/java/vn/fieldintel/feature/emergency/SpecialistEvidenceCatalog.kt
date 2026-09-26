package vn.fieldintel.feature.emergency

enum class EvidenceDomain {
    VIETNAM_TRADITIONAL_MEDICINE,
    HERBAL_MEDICINE_MONOGRAPH,
    TOXICOLOGY
}

enum class EvidenceClass {
    OFFICIAL_LISTING,
    REGULATORY_MONOGRAPH,
    PUBLIC_HEALTH_TOXICOLOGY,
    BOTANICAL_HAZARD_PROFILE
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
    val scopeNote: String,
    val scientificName: String = ""
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
            scopeNote = "Xác nhận loài/dược liệu xuất hiện trong danh mục chính thức; không tự suy ra chỉ định, liều dùng, hiệu quả hay chống chỉ định.",
            scientificName = "Curcuma longa L."
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
            scopeNote = "Chỉ ghi nhận tồn tại chuyên khảo quản lý và bộ phận dược liệu; không biến thành khuyến nghị tự điều trị.",
            scientificName = "Curcuma longa L."
        ),
        SpecialistEvidenceRecord(
            id = "moh-zingiber-officinale-sinh-khuong",
            speciesId = "zingiber-officinale",
            domain = EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE,
            evidenceClass = EvidenceClass.OFFICIAL_LISTING,
            title = "Sinh khương — danh mục vị thuốc YHCT",
            statement = "Danh mục của Bộ Y tế ghi Sinh khương, Rhizoma Zingiberis recens, từ Zingiber officinale Rosc.",
            plantPart = "Thân rễ tươi theo tên dược liệu trong nguồn",
            sourceName = "Cục Quản lý Y, Dược cổ truyền — Bộ Y tế Việt Nam",
            sourceUrl = "https://emohbackup.moh.gov.vn/publish/attach/getfile/412855",
            sourceRecord = "Danh mục vị thuốc YHCT — Sinh khương — Zingiber officinale Rosc.",
            scopeNote = "Xác nhận tên vị thuốc, dược liệu và loài trong danh mục chính thức; không tự suy ra công dụng, liều dùng hoặc tính an toàn.",
            scientificName = "Zingiber officinale Roscoe"
        ),
        SpecialistEvidenceRecord(
            id = "moh-zingiber-officinale-can-khuong",
            speciesId = "zingiber-officinale",
            domain = EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE,
            evidenceClass = EvidenceClass.OFFICIAL_LISTING,
            title = "Can khương — danh mục vị thuốc YHCT",
            statement = "Danh mục của Bộ Y tế ghi Can khương, Rhizoma Zingiberis, từ Zingiber officinale Rosc.",
            plantPart = "Thân rễ theo tên dược liệu trong nguồn",
            sourceName = "Cục Quản lý Y, Dược cổ truyền — Bộ Y tế Việt Nam",
            sourceUrl = "https://emohbackup.moh.gov.vn/publish/attach/getfile/412855",
            sourceRecord = "Nhóm thuốc trừ hàn — Can khương — Zingiber officinale Rosc.",
            scopeNote = "Xác nhận tên vị thuốc, dược liệu và loài trong danh mục chính thức; không tự suy ra công dụng, liều dùng hoặc tính an toàn.",
            scientificName = "Zingiber officinale Roscoe"
        ),
        SpecialistEvidenceRecord(
            id = "ema-zingiber-officinale-rhizoma-2025",
            speciesId = "zingiber-officinale",
            domain = EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH,
            evidenceClass = EvidenceClass.REGULATORY_MONOGRAPH,
            title = "Zingiberis rhizoma — EU herbal monograph",
            statement = "EMA/HMPC có chuyên khảo Liên minh châu Âu cho Zingiber officinale Roscoe, rhizoma; bản Revision 1 được cập nhật năm 2025.",
            plantPart = "Rhizoma",
            sourceName = "European Medicines Agency — HMPC",
            sourceUrl = "https://www.ema.europa.eu/en/medicines/herbal/zingiberis-rhizoma",
            sourceRecord = "EMA/HMPC/885789/2022 — Revision 1",
            scopeNote = "Chỉ ghi nhận chuyên khảo quản lý và bộ phận dược liệu. Không chuyển nội dung chuyên khảo thành hướng dẫn tự điều trị hoặc liều dùng trong ứng dụng.",
            scientificName = "Zingiber officinale Roscoe"
        ),
        SpecialistEvidenceRecord(
            id = "ema-hypericum-perforatum-herba-2023",
            speciesId = "hypericum-perforatum",
            domain = EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH,
            evidenceClass = EvidenceClass.REGULATORY_MONOGRAPH,
            title = "Hyperici herba — EU herbal monograph",
            statement = "EMA/HMPC có chuyên khảo Liên minh châu Âu cho Hypericum perforatum L., herba; đánh giá được ghi trạng thái hoàn tất.",
            plantPart = "Herba",
            sourceName = "European Medicines Agency — HMPC",
            sourceUrl = "https://www.ema.europa.eu/en/medicines/herbal/hyperici-herba-0",
            sourceRecord = "EMA/HMPC/7695/2021 — Revision 1",
            scopeNote = "Chỉ ghi nhận chuyên khảo quản lý. Các tương tác thuốc của Hypericum phải lấy từ lớp InteractionCatalog có nguồn riêng; không suy ra liều dùng hay hiệu quả cá nhân.",
            scientificName = "Hypericum perforatum L."
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
            scopeNote = "Cảnh báo độc chất ở hạt; không dùng bản ghi này để nhận dạng cây ngoài thực địa hoặc suy ra mức phơi nhiễm của một ca cụ thể.",
            scientificName = "Ricinus communis L."
        ),
        SpecialistEvidenceRecord(
            id = "cdc-abrus-precatorius-abrin",
            speciesId = "abrus-precatorius",
            domain = EvidenceDomain.TOXICOLOGY,
            evidenceClass = EvidenceClass.PUBLIC_HEALTH_TOXICOLOGY,
            title = "Abrin trong hạt Abrus precatorius",
            statement = "CDC xác nhận abrin là độc chất tự nhiên có nguồn từ hạt rosary pea/jequirity pea; NIOSH liên kết độc chất này trực tiếp với hạt Abrus precatorius.",
            plantPart = "Hạt",
            sourceName = "U.S. Centers for Disease Control and Prevention / NIOSH",
            sourceUrl = "https://www.cdc.gov/chemical-emergencies/chemical-fact-sheets/abrin.html",
            sourceRecord = "Abrin Chemical Fact Sheet; NIOSH Emergency Response Safety and Health Database",
            scopeNote = "Chỉ xác nhận nguy cơ độc chất gắn với hạt và abrin; không dùng bản ghi này để ước lượng liều phơi nhiễm, tiên lượng ca bệnh hoặc nhận dạng cây từ ảnh.",
            scientificName = "Abrus precatorius L."
        ),
        SpecialistEvidenceRecord(
            id = "kew-nerium-oleander-hazard-profile",
            speciesId = "nerium-oleander",
            domain = EvidenceDomain.TOXICOLOGY,
            evidenceClass = EvidenceClass.BOTANICAL_HAZARD_PROFILE,
            title = "Nerium oleander — hồ sơ nguy cơ thực vật",
            statement = "Kew Species Profiles cảnh báo mọi bộ phận của Nerium oleander cực độc nếu ăn; nhựa cây có thể gây viêm da và cần tránh hít khói khi đốt cây.",
            plantPart = "Toàn cây; nhựa; khói khi đốt",
            sourceName = "Royal Botanic Gardens, Kew — Plants of the World Online / Kew Species Profiles",
            sourceUrl = "https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:80460-1/general-information",
            sourceRecord = "Kew Species Profile — Hazards",
            scopeNote = "Cảnh báo nguy cơ theo hồ sơ thực vật của Kew; không dùng để suy ra mức phơi nhiễm, tiên lượng, xử trí cá nhân hay xác minh cây từ ảnh.",
            scientificName = "Nerium oleander L."
        )
    )

    fun forSpecies(speciesId: String): List<SpecialistEvidenceRecord> =
        records.filter { it.speciesId == speciesId }

    fun forScientificName(scientificName: String): List<SpecialistEvidenceRecord> =
        records.filter { it.scientificName.equals(scientificName, ignoreCase = true) }

    fun speciesIdsFor(domain: EvidenceDomain): Set<String> =
        records.asSequence().filter { it.domain == domain }.map { it.speciesId }.toSet()
}
