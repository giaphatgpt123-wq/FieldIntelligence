package vn.fieldintel.feature.emergency

/**
 * Curated navigation collections for the small starter catalog.
 *
 * These labels are UI organization only. They are not evidence that a plant is edible,
 * medicinal, toxic, or safe to use. High-risk claims remain in separately sourced evidence
 * layers such as InteractionCatalog and future medicinal/toxicology datasets.
 */
data class LibraryCollection(
    val id: String,
    val label: String,
    val icon: String,
    val subtitle: String,
    val recordIds: Set<String>
)

object LibraryCollections {
    val items: List<LibraryCollection> = listOf(
        LibraryCollection(
            id = "traditional-medicine",
            label = "Cây thuốc Đông y",
            icon = "⚕",
            subtitle = "Chỉ hiện hồ sơ có lớp bằng chứng dược liệu riêng",
            recordIds = emptySet()
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
            recordIds = emptySet()
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
            id = "animals",
            label = "Động vật",
            icon = "🐾",
            subtitle = "Taxonomy động vật",
            recordIds = setOf("macaca-fascicularis", "varanus-salvator")
        )
    )

    fun byId(id: String?): LibraryCollection? = items.firstOrNull { it.id == id }

    fun recordsFor(id: String): List<SpeciesRecord> {
        val ids = byId(id)?.recordIds.orEmpty()
        return SpeciesCatalog.records.filter { it.id in ids }
    }
}
