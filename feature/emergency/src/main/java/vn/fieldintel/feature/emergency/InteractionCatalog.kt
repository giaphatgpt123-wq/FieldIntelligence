package vn.fieldintel.feature.emergency

enum class InteractionEntityType { TAXON, FOOD, DRUG, HERB, ANIMAL_PRODUCT, CONDITION, PROCESS }
enum class InteractionType {
    HERB_DRUG, FOOD_DRUG, HERB_HERB, FOOD_FOOD, PLANT_ANIMAL_PRODUCT,
    ALLERGY_CROSS_REACTION, TOXIN_PROCESSING, CONDITION_CONTRAINDICATION, OTHER
}
enum class InteractionSeverity { INFO, CAUTION, HIGH, CRITICAL, UNKNOWN }
enum class EvidenceLevel { CONFIRMED, PROBABLE, POSSIBLE, TRADITIONAL_CLAIM, INSUFFICIENT_EVIDENCE }

data class InteractionEntity(
    val id: String,
    val label: String,
    val type: InteractionEntityType,
    val scientificName: String? = null,
    val aliases: List<String> = emptyList()
)

data class InteractionSource(
    val authority: String,
    val title: String,
    val url: String,
    val sourceDate: String? = null
)

data class SafetyInteraction(
    val id: String,
    val a: InteractionEntity,
    val b: InteractionEntity,
    val type: InteractionType,
    val severity: InteractionSeverity,
    val evidence: EvidenceLevel,
    val summary: String,
    val mechanism: String? = null,
    val atRisk: List<String> = emptyList(),
    val action: String,
    val sources: List<InteractionSource>
)

/**
 * Evidence-backed interaction catalog.
 *
 * Safety rules:
 * - Taxonomy alone must never create an interaction claim.
 * - Every published interaction needs an HTTPS source and explicit evidence level.
 * - Traditional claims are retained as claims, never promoted to confirmed evidence.
 * - UNKNOWN/insufficient evidence must not be rendered as a prohibition.
 */
object InteractionCatalog {
    val records: List<SafetyInteraction> = listOf(
        SafetyInteraction(
            id = "grapefruit-simvastatin-fda",
            a = InteractionEntity(
                id = "food-grapefruit",
                label = "Bưởi chùm / grapefruit",
                type = InteractionEntityType.FOOD,
                scientificName = "Citrus × paradisi",
                aliases = listOf("grapefruit", "grapefruit juice")
            ),
            b = InteractionEntity(
                id = "drug-simvastatin",
                label = "Simvastatin",
                type = InteractionEntityType.DRUG,
                aliases = listOf("simvastatin")
            ),
            type = InteractionType.FOOD_DRUG,
            severity = InteractionSeverity.HIGH,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "Nước grapefruit có thể làm tăng phơi nhiễm simvastatin; mức tương tác phụ thuộc lượng dùng và thuốc cụ thể.",
            mechanism = "Tương tác liên quan chuyển hóa/vận chuyển thuốc; nhãn FDA ghi nhận thay đổi phơi nhiễm simvastatin khi dùng cùng grapefruit juice.",
            atRisk = listOf("Người đang dùng simvastatin"),
            action = "Không tự kết luận an toàn theo tên thực phẩm. Kiểm tra đúng thuốc và hướng dẫn kê đơn; hỏi bác sĩ hoặc dược sĩ nếu đang dùng thường xuyên.",
            sources = listOf(
                InteractionSource(
                    authority = "U.S. Food and Drug Administration (FDA)",
                    title = "FDA pharmacists help you use medicines safely — food/drug interaction guidance",
                    url = "https://www.fda.gov/consumers/consumer-updates/fda-pharmacists-help-you-use-medicines-safely"
                )
            )
        )
    )

    fun validate(record: SafetyInteraction): List<String> {
        val errors = mutableListOf<String>()
        if (record.id.isBlank()) errors += "missing id"
        if (record.a.id.isBlank() || record.b.id.isBlank()) errors += "missing entity id"
        if (record.summary.isBlank()) errors += "missing summary"
        if (record.action.isBlank()) errors += "missing action"
        if (record.sources.isEmpty()) errors += "missing source"
        if (record.sources.any { !it.url.startsWith("https://") || it.authority.isBlank() || it.title.isBlank() }) {
            errors += "invalid source provenance"
        }
        if (record.evidence == EvidenceLevel.CONFIRMED && record.sources.isEmpty()) errors += "confirmed without source"
        if (record.evidence == EvidenceLevel.TRADITIONAL_CLAIM && record.severity == InteractionSeverity.CRITICAL) {
            errors += "traditional claim cannot be promoted to critical"
        }
        return errors
    }

    fun allValidated(): Boolean = records.all { validate(it).isEmpty() }

    fun findForEntity(query: String): List<SafetyInteraction> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        fun matches(entity: InteractionEntity): Boolean {
            val values = buildList {
                add(entity.id)
                add(entity.label)
                entity.scientificName?.let(::add)
                addAll(entity.aliases)
            }
            return values.any { it.lowercase().contains(needle) || needle.contains(it.lowercase()) }
        }
        return records.filter { matches(it.a) || matches(it.b) }
    }

    fun pair(aQuery: String, bQuery: String): List<SafetyInteraction> {
        val aMatches = findForEntity(aQuery).toSet()
        if (aMatches.isEmpty()) return emptyList()
        return findForEntity(bQuery).filter { it in aMatches }
    }
}
