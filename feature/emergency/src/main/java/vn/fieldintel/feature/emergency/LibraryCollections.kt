package vn.fieldintel.feature.emergency

/**
 * Navigation collections for the scientific library.
 *
 * Taxonomy-only groups may be curated directly. High-risk labels such as medicinal or toxic are
 * evidence-gated. When validated offline SQLite packs are available, those collections are
 * replaced by runtime records resolved from specialist evidence -> canonical scientific name ->
 * taxonomy. The starter catalog remains the explicit fallback when the external packs are absent.
 *
 * User-facing ordering is governed by [LibraryRules]: Vietnamese, practical collections appear
 * before global/taxonomy-oriented collections. This must not be used to infer edibility, toxicity
 * or medicinal value.
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
            label = "Thực vật",
            icon = "🌿",
            subtitle = "Tên Việt ưu tiên • ảnh đúng loài • xem dữ liệu khoa học khi cần",
            recordIds = emptySet()
        ),
        LibraryCollection(
            id = "traditional-medicine",
            label = "Cây thuốc Đông y",
            icon = "⚕",
            subtitle = "Chỉ hiện hồ sơ có nguồn dược liệu chính thức Việt Nam",
            recordIds = SpecialistEvidenceCatalog.speciesIdsFor(EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE),
            evidenceDomain = EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE
        ),
        LibraryCollection(
            id = "herbal-monographs",
            label = "Chuyên khảo dược liệu",
            icon = "▣",
            subtitle = "Nguồn chuyên ngành • nội dung sâu đặt trong phần Xem thêm",
            recordIds = SpecialistEvidenceCatalog.speciesIdsFor(EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH),
            evidenceDomain = EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH
        ),
        LibraryCollection(
            id = "vegetables",
            label = "Rau",
            icon = "🥬",
            subtitle = "Ưu tiên loại thường gặp ở Việt Nam • tên Việt và ảnh đặt trước",
            recordIds = setOf("centella-asiatica")
        ),
        LibraryCollection(
            id = "roots-rhizomes",
            label = "Củ / thân rễ",
            icon = "◉",
            subtitle = "Tách rõ từng loài • không gộp các biến thể có đặc điểm khác nhau",
            recordIds = setOf("zingiber-officinale", "curcuma-longa")
        ),
        LibraryCollection(
            id = "flowers",
            label = "Hoa",
            icon = "🌸",
            subtitle = "Hoa thường gặp • tên Việt và ảnh nhận biết đặt trước",
            recordIds = setOf("nerium-oleander")
        ),
        LibraryCollection(
            id = "timber-trees",
            label = "Cây gỗ",
            icon = "🌳",
            subtitle = "Bổ sung dần theo hồ sơ đã đối chiếu • không hiển thị dữ liệu giả",
            recordIds = emptySet()
        ),
        LibraryCollection(
            id = "fruit-crops",
            label = "Cây ăn quả",
            icon = "🍊",
            subtitle = "Ưu tiên cây thường gặp ở Việt Nam • ảnh và tên Việt đặt trước",
            recordIds = setOf("mangifera-indica", "musa-acuminata", "cocos-nucifera", "carica-papaya", "psidium-guajava", "ananas-comosus", "artocarpus-heterophyllus", "citrus-maxima")
        ),
        LibraryCollection(
            id = "staple-crops",
            label = "Cây lương thực",
            icon = "🌾",
            subtitle = "Nhóm cây lương thực thường gặp • hiển thị ngắn gọn, dễ nhận biết",
            recordIds = setOf("oryza-sativa")
        ),
        LibraryCollection(
            id = "toxic-plants",
            label = "Cây độc",
            icon = "⚠",
            subtitle = "Nhận biết và cảnh báo rõ • chỉ hiện khi có bằng chứng chuyên ngành",
            recordIds = SpecialistEvidenceCatalog.speciesIdsFor(EvidenceDomain.TOXICOLOGY),
            evidenceDomain = EvidenceDomain.TOXICOLOGY
        ),
        LibraryCollection(
            id = "mushrooms",
            label = "Nấm",
            icon = "🍄",
            subtitle = "Ảnh nhiều góc • ưu tiên phần dễ nhầm và cảnh báo an toàn",
            recordIds = setOf("ganoderma-lucidum", "termitomyces-clypeatus")
        ),
        LibraryCollection(
            id = "insects",
            label = "Côn trùng",
            icon = "🐝",
            subtitle = "Ưu tiên loài thường gặp • nhận biết nhanh bằng tên Việt và ảnh",
            recordIds = setOf("apis-cerana", "aedes-aegypti", "vespa-tropica")
        ),
        LibraryCollection(
            id = "freshwater-fish",
            label = "Cá nước ngọt",
            icon = "🐟",
            subtitle = "Cá nước ngọt Việt Nam • tên Việt, ảnh và đặc điểm nhận biết",
            recordIds = FreshwaterFishCatalog.records.mapTo(linkedSetOf()) { it.id }
        ),
        LibraryCollection(
            id = "animals",
            label = "Động vật",
            icon = "🐾",
            subtitle = "Ưu tiên loài thường gặp • nhận biết và cảnh báo khi cần",
            recordIds = setOf("macaca-fascicularis", "varanus-salvator")
        )
    )

    /** Reading this property from Compose observes the runtime evidence snapshot and state. */
    val items: List<LibraryCollection>
        get() {
            val runtimeState = LibraryCollectionRuntime.state
            val resolved = baseItems.map { item ->
                if (item.evidenceDomain == null && item.id !in REVIEWED_IDS) return@map item

                val runtime = LibraryCollectionRuntime.recordsFor(item.id)
                val statusLabel = when (runtimeState) {
                    LibraryCollectionRuntimeState.IDLE -> "CHỜ KHỞI TẠO"
                    LibraryCollectionRuntimeState.LOADING -> "ĐANG NẠP DỮ LIỆU"
                    LibraryCollectionRuntimeState.SQLITE_READY -> "DỮ LIỆU OFFLINE SẴN SÀNG"
                    LibraryCollectionRuntimeState.FALLBACK -> "ĐANG DÙNG BỘ LÕI"
                }
                val withStatus = item.copy(subtitle = "${item.subtitle} • $statusLabel")
                if (runtime == null) withStatus else withStatus.copy(recordIds = runtime.mapTo(linkedSetOf()) { it.id })
            }
            return LibraryRules.orderedCollections(resolved)
        }

    fun byId(id: String?): LibraryCollection? = items.firstOrNull { it.id == id }

    fun recordsFor(id: String): List<SpeciesRecord> {
        val base = baseItems.firstOrNull { it.id == id } ?: return emptyList()
        if (base.evidenceDomain != null || id in REVIEWED_IDS) {
            LibraryCollectionRuntime.recordsFor(id)?.let { return it }
        }
        return starterRecordsFor(id)
    }

    internal fun starterRecordsFor(id: String): List<SpeciesRecord> {
        val ids = baseItems.firstOrNull { it.id == id }?.recordIds.orEmpty()
        return SpeciesCatalog.records.filter { it.id in ids }
    }

    internal fun evidenceCollections(): List<LibraryCollection> = baseItems.filter { it.evidenceDomain != null }
    internal fun reviewedCollections(): List<LibraryCollection> = baseItems.filter { it.id in REVIEWED_IDS }
    private val REVIEWED_IDS = setOf("vegetables", "flowers", "timber-trees", "fruit-crops")
}
