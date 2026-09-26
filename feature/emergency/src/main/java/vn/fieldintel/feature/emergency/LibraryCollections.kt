package vn.fieldintel.feature.emergency

/**
 * Navigation collections for the scientific library.
 *
 * Taxonomy-only groups may be curated directly. High-risk labels such as medicinal or toxic are
 * evidence-gated. When validated offline SQLite packs are available, those collections are
 * replaced by runtime records resolved from specialist evidence -> canonical scientific name ->
 * taxonomy. The starter catalog remains the explicit fallback when the external packs are absent.
 */
data class LibraryCollection(
    val id: String,
    val label: String,
    val icon: String,
    val subtitle: String,
    val recordIds: Set<String>,
    val evidenceDomain: EvidenceDomain? = null
)

object LibraryCollections {
    private val baseItems: List<LibraryCollection> = listOf(
        LibraryCollection(
            id = "wfo-plants",
            label = "Kho thực vật WFO",
            icon = "🌿",
            subtitle = "Tra cứu tên phân loại offline theo chi • chưa xác định mẫu vật",
            recordIds = emptySet()
        ),
        LibraryCollection(
            id = "traditional-medicine",
            label = "Cây thuốc Đông y",
            icon = "⚕",
            subtitle = "Chỉ hiện hồ sơ có bằng chứng dược liệu chính thức Việt Nam",
            recordIds = SpecialistEvidenceCatalog.speciesIdsFor(EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE),
            evidenceDomain = EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE
        ),
        LibraryCollection(
            id = "herbal-monographs",
            label = "Chuyên khảo dược liệu",
            icon = "▣",
            subtitle = "Chuyên khảo quản lý quốc tế • không phải hướng dẫn tự điều trị",
            recordIds = SpecialistEvidenceCatalog.speciesIdsFor(EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH),
            evidenceDomain = EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH
        ),
        LibraryCollection(
            id = "vegetables",
            label = "Rau",
            icon = "🥬",
            subtitle = "Phân loại điều hướng • không suy ra ăn được",
            recordIds = setOf("centella-asiatica")
        ),
        LibraryCollection(
            id = "roots-rhizomes",
            label = "Củ / thân rễ",
            icon = "◉",
            subtitle = "Nhóm hình thái nông nghiệp lõi",
            recordIds = setOf("zingiber-officinale", "curcuma-longa")
        ),
        LibraryCollection(
            id = "fruit-crops",
            label = "Quả / cây ăn quả",
            icon = "●",
            subtitle = "Nhóm cây trồng lõi • không phải hướng dẫn sử dụng",
            recordIds = setOf("mangifera-indica", "musa-acuminata", "cocos-nucifera", "carica-papaya", "psidium-guajava")
        ),
        LibraryCollection(
            id = "staple-crops",
            label = "Cây lương thực",
            icon = "🌾",
            subtitle = "Nhóm cây trồng lõi",
            recordIds = setOf("oryza-sativa")
        ),
        LibraryCollection(
            id = "toxic-plants",
            label = "Cây độc",
            icon = "⚠",
            subtitle = "Chỉ hiện khi có bằng chứng độc tính chuyên ngành",
            recordIds = SpecialistEvidenceCatalog.speciesIdsFor(EvidenceDomain.TOXICOLOGY),
            evidenceDomain = EvidenceDomain.TOXICOLOGY
        ),
        LibraryCollection(
            id = "mushrooms",
            label = "Nấm",
            icon = "🍄",
            subtitle = "Taxonomy nấm • không suy ra ăn được",
            recordIds = setOf("ganoderma-lucidum", "termitomyces-clypeatus")
        ),
        LibraryCollection(
            id = "insects",
            label = "Côn trùng",
            icon = "🐝",
            subtitle = "Taxonomy côn trùng",
            recordIds = setOf("apis-cerana", "aedes-aegypti", "vespa-tropica")
        ),
        LibraryCollection(
            id = "freshwater-fish",
            label = "Cá nước ngọt",
            icon = "🐟",
            subtitle = "20 hồ sơ tên loài • một số loài chịu nước lợ",
            recordIds = FreshwaterFishCatalog.records.mapTo(linkedSetOf()) { it.id }
        ),
        LibraryCollection(
            id = "animals",
            label = "Động vật",
            icon = "🐾",
            subtitle = "Taxonomy động vật",
            recordIds = setOf("macaca-fascicularis", "varanus-salvator")
        )
    )

    /** Reading this property from Compose observes the runtime evidence snapshot and state. */
    val items: List<LibraryCollection>
        get() {
            val runtimeState = LibraryCollectionRuntime.state
            return baseItems.map { item ->
                if (item.evidenceDomain == null) return@map item

                val runtime = LibraryCollectionRuntime.recordsFor(item.id)
                val statusLabel = when (runtimeState) {
                    LibraryCollectionRuntimeState.IDLE -> "CHỜ KHỞI TẠO"
                    LibraryCollectionRuntimeState.LOADING -> "ĐANG NẠP SQLITE"
                    LibraryCollectionRuntimeState.SQLITE_READY -> "SQLITE READY"
                    LibraryCollectionRuntimeState.FALLBACK -> "FALLBACK BỘ LÕI"
                }
                val withStatus = item.copy(subtitle = "${item.subtitle} • $statusLabel")
                if (runtime == null) withStatus else withStatus.copy(recordIds = runtime.mapTo(linkedSetOf()) { it.id })
            }
        }

    fun byId(id: String?): LibraryCollection? = items.firstOrNull { it.id == id }

    fun recordsFor(id: String): List<SpeciesRecord> {
        val base = baseItems.firstOrNull { it.id == id } ?: return emptyList()
        if (base.evidenceDomain != null) {
            LibraryCollectionRuntime.recordsFor(id)?.let { return it }
        }
        return starterRecordsFor(id)
    }

    internal fun starterRecordsFor(id: String): List<SpeciesRecord> {
        val ids = baseItems.firstOrNull { it.id == id }?.recordIds.orEmpty()
        return SpeciesCatalog.records.filter { it.id in ids }
    }

    internal fun evidenceCollections(): List<LibraryCollection> = baseItems.filter { it.evidenceDomain != null }
}
