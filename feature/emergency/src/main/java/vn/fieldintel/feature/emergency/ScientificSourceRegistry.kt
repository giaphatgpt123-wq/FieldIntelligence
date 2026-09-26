package vn.fieldintel.feature.emergency

enum class ScientificDomain {
    TAXONOMY,
    BIODIVERSITY_OCCURRENCE,
    VIETNAM_PLANT_GENETIC_RESOURCES,
    AGRICULTURE,
    MEDICINAL_PLANTS,
    TRADITIONAL_MEDICINE,
    PHARMACOPOEIA
}

enum class IngestionAccess {
    BULK_OPEN,
    EXPORT_AVAILABLE_TERMS_PENDING,
    DATASET_LICENSE_REQUIRED,
    REFERENCE_ONLY_TERMS_PENDING
}

data class ScientificSource(
    val id: String,
    val name: String,
    val authority: String,
    val domains: Set<ScientificDomain>,
    val url: String,
    val access: IngestionAccess,
    val licenseNote: String,
    val importScope: String,
    val priority: Int
) {
    val canBulkImportNow: Boolean
        get() = access == IngestionAccess.BULK_OPEN
}

/**
 * Registry for scientific-library ingestion.
 *
 * A source being scientifically useful does not automatically grant redistribution rights.
 * Only sources explicitly marked BULK_OPEN may be bulk-ingested without an additional
 * source/dataset-level licence check. High-risk medicinal or traditional-use statements must
 * retain document-level provenance and must not be inferred from taxonomy records.
 */
object ScientificSourceRegistry {
    val sources: List<ScientificSource> = listOf(
        ScientificSource(
            id = "wfo-taxonomic-backbone",
            name = "World Flora Online Taxonomic Backbone",
            authority = "World Flora Online Consortium",
            domains = setOf(ScientificDomain.TAXONOMY),
            url = "https://www.worldfloraonline.org/downloadData",
            access = IngestionAccess.BULK_OPEN,
            licenseNote = "Static Taxonomic Backbone is published as CC0 1.0; retain version/DOI and access date.",
            importScope = "Accepted names, synonyms and taxonomic classification only.",
            priority = 1
        ),
        ScientificSource(
            id = "prc-vietnam-genebank",
            name = "Cơ sở dữ liệu nguồn gen thực vật nông nghiệp Việt Nam",
            authority = "Trung tâm Tài nguyên thực vật",
            domains = setOf(ScientificDomain.VIETNAM_PLANT_GENETIC_RESOURCES, ScientificDomain.AGRICULTURE),
            url = "https://csdl.prc.org.vn/Home/Data",
            access = IngestionAccess.EXPORT_AVAILABLE_TERMS_PENDING,
            licenseNote = "Trang có chức năng xuất dữ liệu; phải xác minh điều khoản tái sử dụng trước khi đóng gói/phân phối dữ liệu hàng loạt trong ứng dụng.",
            importScope = "Nhóm cây trồng, tên cây, GBVN, tên nguồn gen, tên khoa học, dân tộc, nơi thu thập, năm, cơ quan lưu giữ và metadata nguồn gen.",
            priority = 1
        ),
        ScientificSource(
            id = "moh-traditional-medicine",
            name = "Cục Quản lý Y, Dược cổ truyền",
            authority = "Bộ Y tế Việt Nam",
            domains = setOf(ScientificDomain.TRADITIONAL_MEDICINE, ScientificDomain.MEDICINAL_PLANTS),
            url = "https://ydct.moh.gov.vn/",
            access = IngestionAccess.REFERENCE_ONLY_TERMS_PENDING,
            licenseNote = "Dùng làm nguồn pháp quy/chuyên môn; kiểm tra quyền sử dụng theo từng văn bản/tệp trước khi nhập nguyên văn hoặc phân phối lại.",
            importScope = "Danh mục dược liệu, vị thuốc cổ truyền, tiêu chuẩn/chất lượng, cảnh báo và văn bản quản lý. Không suy ra chỉ định điều trị từ tên dược liệu.",
            priority = 1
        ),
        ScientificSource(
            id = "vietnam-pharmacopoeia-vi",
            name = "Dược điển Việt Nam VI",
            authority = "Bộ Y tế Việt Nam / Hội đồng Dược điển Việt Nam",
            domains = setOf(ScientificDomain.PHARMACOPOEIA, ScientificDomain.MEDICINAL_PLANTS, ScientificDomain.TRADITIONAL_MEDICINE),
            url = "https://moh.gov.vn/",
            access = IngestionAccess.REFERENCE_ONLY_TERMS_PENDING,
            licenseNote = "Nguồn tiêu chuẩn quốc gia; chỉ nhập metadata/trích dẫn được phép cho tới khi quyền tái sử dụng nội dung chuyên luận được xác minh.",
            importScope = "Tên chuyên luận, loại tiêu chuẩn, định danh dược liệu và tham chiếu tiêu chuẩn; không sao chép toàn văn chuyên luận khi chưa có quyền.",
            priority = 1
        ),
        ScientificSource(
            id = "national-institute-medicinal-materials",
            name = "Viện Dược liệu",
            authority = "Viện Dược liệu, Việt Nam",
            domains = setOf(ScientificDomain.MEDICINAL_PLANTS, ScientificDomain.TRADITIONAL_MEDICINE),
            url = "https://vienduoclieu.org.vn/",
            access = IngestionAccess.REFERENCE_ONLY_TERMS_PENDING,
            licenseNote = "Nguồn nghiên cứu chuyên ngành; xác minh điều khoản và quyền của từng tài liệu trước khi nhập nội dung chi tiết.",
            importScope = "Định danh cây thuốc, bảo tồn nguồn gen dược liệu, nghiên cứu và tài liệu chuyên ngành có provenance.",
            priority = 1
        ),
        ScientificSource(
            id = "kew-powo",
            name = "Plants of the World Online",
            authority = "Royal Botanic Gardens, Kew",
            domains = setOf(ScientificDomain.TAXONOMY),
            url = "https://powo.science.kew.org/",
            access = IngestionAccess.REFERENCE_ONLY_TERMS_PENDING,
            licenseNote = "Dùng để đối chiếu taxonomy/phân bố theo trang nguồn; không mặc định coi toàn bộ nội dung/hình ảnh là dữ liệu bulk có thể phân phối lại.",
            importScope = "Accepted name, synonyms, classification and distribution references.",
            priority = 1
        ),
        ScientificSource(
            id = "gbif",
            name = "Global Biodiversity Information Facility",
            authority = "GBIF Secretariat and dataset publishers",
            domains = setOf(ScientificDomain.TAXONOMY, ScientificDomain.BIODIVERSITY_OCCURRENCE),
            url = "https://www.gbif.org/",
            access = IngestionAccess.DATASET_LICENSE_REQUIRED,
            licenseNote = "Occurrence datasets use dataset-level CC0, CC BY or CC BY-NC licences; preserve dataset DOI, publisher and licence for every imported dataset.",
            importScope = "Taxonomic cross-checks and occurrence/distribution records with dataset-level provenance.",
            priority = 2
        )
    )

    fun byPriority(): List<ScientificSource> = sources.sortedWith(compareBy<ScientificSource> { it.priority }.thenBy { it.name })

    fun forDomain(domain: ScientificDomain): List<ScientificSource> =
        byPriority().filter { domain in it.domains }

    fun bulkReady(): List<ScientificSource> = byPriority().filter { it.canBulkImportNow }
}
