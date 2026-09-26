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
    private val fdaGrapefruit = InteractionSource(
        authority = "U.S. Food and Drug Administration (FDA)",
        title = "Grapefruit Juice and Some Drugs Don't Mix",
        url = "https://www.fda.gov/consumers/consumer-updates/grapefruit-juice-and-some-drugs-dont-mix"
    )
    private val nccihHerbDrug = InteractionSource(
        authority = "NIH National Center for Complementary and Integrative Health (NCCIH)",
        title = "Herb-Drug Interactions: What the Science Says",
        url = "https://www.nccih.nih.gov/health/providers/digest/herb-drug-interactions-science"
    )
    private val nccihStJohn = InteractionSource(
        authority = "NIH National Center for Complementary and Integrative Health (NCCIH)",
        title = "St. John's Wort: Usefulness and Safety",
        url = "https://www.nccih.nih.gov/health/st-johns-wort"
    )
    private val nihVitaminK = InteractionSource(
        authority = "NIH Office of Dietary Supplements",
        title = "Vitamin K — Health Professional Fact Sheet",
        url = "https://ods.od.nih.gov/factsheets/vitaminK-HealthProfessional/"
    )

    private val grapefruit = InteractionEntity(
        id = "food-grapefruit",
        label = "Bưởi chùm / grapefruit",
        type = InteractionEntityType.FOOD,
        scientificName = "Citrus × paradisi",
        aliases = listOf("grapefruit", "grapefruit juice", "nước bưởi chùm")
    )
    private val stJohnsWort = InteractionEntity(
        id = "herb-hypericum-perforatum",
        label = "St. John's wort / Hypericum perforatum",
        type = InteractionEntityType.HERB,
        scientificName = "Hypericum perforatum L.",
        aliases = listOf("St John's wort", "St. John's wort", "Hypericum perforatum")
    )
    private val greenTea = InteractionEntity(
        id = "herb-camellia-sinensis-green-tea",
        label = "Trà xanh / green tea",
        type = InteractionEntityType.HERB,
        scientificName = "Camellia sinensis (L.) Kuntze",
        aliases = listOf("green tea", "trà xanh", "Camellia sinensis")
    )

    val records: List<SafetyInteraction> = listOf(
        SafetyInteraction(
            id = "grapefruit-simvastatin-fda",
            a = grapefruit,
            b = InteractionEntity("drug-simvastatin", "Simvastatin", InteractionEntityType.DRUG, aliases = listOf("simvastatin", "Zocor")),
            type = InteractionType.FOOD_DRUG,
            severity = InteractionSeverity.HIGH,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "Grapefruit có thể làm tăng nồng độ/phơi nhiễm của một số statin, trong đó FDA nêu simvastatin là ví dụ cần cảnh giác.",
            mechanism = "Grapefruit có thể ảnh hưởng enzyme/vận chuyển tham gia chuyển hóa thuốc ở ruột, làm thay đổi lượng thuốc vào máu.",
            atRisk = listOf("Người đang dùng simvastatin"),
            action = "Kiểm tra đúng nhãn thuốc và hướng dẫn kê đơn. Không tự thay đổi liều; hỏi bác sĩ hoặc dược sĩ nếu đang dùng grapefruit thường xuyên.",
            sources = listOf(fdaGrapefruit)
        ),
        SafetyInteraction(
            id = "grapefruit-nifedipine-fda",
            a = grapefruit,
            b = InteractionEntity("drug-nifedipine", "Nifedipine", InteractionEntityType.DRUG, aliases = listOf("nifedipine", "Procardia", "Adalat CC")),
            type = InteractionType.FOOD_DRUG,
            severity = InteractionSeverity.HIGH,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "FDA liệt kê nifedipine trong nhóm thuốc huyết áp có thể tương tác với grapefruit.",
            mechanism = "Tương tác có thể làm thay đổi lượng nifedipine hấp thu/chuyển hóa; mức độ phụ thuộc sản phẩm và từng người.",
            atRisk = listOf("Người đang dùng nifedipine"),
            action = "Đọc cảnh báo trên nhãn thuốc và trao đổi với bác sĩ/dược sĩ trước khi dùng grapefruit hoặc nước grapefruit thường xuyên.",
            sources = listOf(fdaGrapefruit)
        ),
        SafetyInteraction(
            id = "grapefruit-cyclosporine-fda",
            a = grapefruit,
            b = InteractionEntity("drug-cyclosporine", "Cyclosporine", InteractionEntityType.DRUG, aliases = listOf("cyclosporine", "Neoral", "Sandimmune")),
            type = InteractionType.FOOD_DRUG,
            severity = InteractionSeverity.HIGH,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "FDA liệt kê cyclosporine trong nhóm thuốc chống thải ghép có thể tương tác với grapefruit.",
            mechanism = "Grapefruit có thể làm thay đổi hấp thu/chuyển hóa cyclosporine và do đó thay đổi phơi nhiễm thuốc.",
            atRisk = listOf("Người ghép tạng hoặc người đang dùng cyclosporine"),
            action = "Không tự điều chỉnh chế độ dùng thuốc. Kiểm tra hướng dẫn kê đơn và liên hệ bác sĩ/dược sĩ nếu có dùng grapefruit.",
            sources = listOf(fdaGrapefruit)
        ),
        SafetyInteraction(
            id = "hypericum-cyclosporine-nccih",
            a = stJohnsWort,
            b = InteractionEntity("drug-cyclosporine", "Cyclosporine", InteractionEntityType.DRUG, aliases = listOf("cyclosporine")),
            type = InteractionType.HERB_DRUG,
            severity = InteractionSeverity.HIGH,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "NCCIH ghi nhận tương tác có ý nghĩa lâm sàng giữa St. John's wort và cyclosporine; thảo dược này có thể làm giảm hiệu lực của thuốc.",
            mechanism = "St. John's wort cảm ứng enzyme cytochrome P450 và P-glycoprotein đường ruột, có thể làm giảm nồng độ một số thuốc.",
            atRisk = listOf("Người dùng cyclosporine", "Người ghép tạng"),
            action = "Không tự dùng St. John's wort cùng cyclosporine. Trao đổi với bác sĩ/dược sĩ trước khi bắt đầu hoặc ngừng thảo dược.",
            sources = listOf(nccihHerbDrug, nccihStJohn)
        ),
        SafetyInteraction(
            id = "hypericum-warfarin-nccih",
            a = stJohnsWort,
            b = InteractionEntity("drug-warfarin", "Warfarin", InteractionEntityType.DRUG, aliases = listOf("warfarin", "Coumadin", "Jantoven")),
            type = InteractionType.HERB_DRUG,
            severity = InteractionSeverity.HIGH,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "NCCIH ghi nhận St. John's wort có thể làm yếu tác dụng của warfarin và tương tác này đã được ghi nhận lâm sàng.",
            mechanism = "Cảm ứng chuyển hóa/vận chuyển thuốc có thể làm giảm phơi nhiễm warfarin và thay đổi hiệu quả chống đông.",
            atRisk = listOf("Người đang dùng warfarin", "Người cần kiểm soát INR"),
            action = "Không tự thêm hoặc ngừng St. John's wort. Báo cho bác sĩ/dược sĩ vì việc thay đổi có thể cần theo dõi chống đông.",
            sources = listOf(nccihHerbDrug, nccihStJohn)
        ),
        SafetyInteraction(
            id = "hypericum-oral-contraceptive-nccih",
            a = stJohnsWort,
            b = InteractionEntity("drug-oral-contraceptive", "Thuốc tránh thai đường uống", InteractionEntityType.DRUG, aliases = listOf("oral contraceptive", "birth control pill", "thuốc tránh thai")),
            type = InteractionType.HERB_DRUG,
            severity = InteractionSeverity.HIGH,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "NCCIH nêu St. John's wort có thể làm yếu tác dụng của thuốc tránh thai đường uống.",
            mechanism = "Cảm ứng enzyme/vận chuyển có thể làm giảm nồng độ một số hormone tránh thai.",
            atRisk = listOf("Người đang dùng thuốc tránh thai đường uống"),
            action = "Không tự phối hợp. Hỏi bác sĩ/dược sĩ về tương tác và biện pháp tránh thai phù hợp nếu đang dùng St. John's wort.",
            sources = listOf(nccihHerbDrug, nccihStJohn)
        ),
        SafetyInteraction(
            id = "hypericum-serotonergic-antidepressant-nccih",
            a = stJohnsWort,
            b = InteractionEntity("drug-serotonergic-antidepressant", "Một số thuốc chống trầm cảm tác động serotonin", InteractionEntityType.DRUG, aliases = listOf("antidepressant", "SSRI", "SNRI", "serotonergic drug")),
            type = InteractionType.HERB_DRUG,
            severity = InteractionSeverity.CRITICAL,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "NCCIH cảnh báo phối hợp St. John's wort với một số thuốc tác động serotonin có thể làm tăng tác dụng phụ serotonin và có thể nghiêm trọng.",
            mechanism = "Hiệu ứng serotonergic cộng gộp có thể làm tăng nguy cơ hội chứng serotonin.",
            atRisk = listOf("Người đang dùng thuốc chống trầm cảm hoặc thuốc khác tác động serotonin"),
            action = "Không tự phối hợp. Nếu đã dùng cùng và xuất hiện kích động, tim nhanh, sốt, lú lẫn hoặc cứng cơ, cần tìm trợ giúp y tế khẩn cấp.",
            sources = listOf(nccihHerbDrug, nccihStJohn)
        ),
        SafetyInteraction(
            id = "green-tea-nadolol-nccih",
            a = greenTea,
            b = InteractionEntity("drug-nadolol", "Nadolol", InteractionEntityType.DRUG, aliases = listOf("nadolol")),
            type = InteractionType.HERB_DRUG,
            severity = InteractionSeverity.CAUTION,
            evidence = EvidenceLevel.PROBABLE,
            summary = "NCCIH cho biết trà xanh liều cao đã được ghi nhận làm giảm nồng độ nadolol trong máu và có thể làm giảm hiệu quả thuốc.",
            mechanism = "Cơ chế có thể liên quan đến giảm hấp thu/vận chuyển thuốc; dữ liệu hiện không đủ để suy rộng cho mọi cách uống trà xanh.",
            atRisk = listOf("Người đang dùng nadolol"),
            action = "Không suy rộng cảnh báo này cho mọi lượng trà xanh. Nếu dùng nadolol và sử dụng trà xanh đậm đặc hoặc sản phẩm bổ sung, nên hỏi bác sĩ/dược sĩ.",
            sources = listOf(nccihHerbDrug)
        ),
        SafetyInteraction(
            id = "vitamin-k-warfarin-nih",
            a = InteractionEntity(
                id = "food-vitamin-k-rich",
                label = "Thực phẩm hoặc bổ sung giàu vitamin K",
                type = InteractionEntityType.FOOD,
                aliases = listOf("vitamin K", "vitamin K-rich food", "rau lá xanh", "leafy green vegetables")
            ),
            b = InteractionEntity("drug-warfarin", "Warfarin", InteractionEntityType.DRUG, aliases = listOf("warfarin", "Coumadin", "Jantoven")),
            type = InteractionType.FOOD_DRUG,
            severity = InteractionSeverity.HIGH,
            evidence = EvidenceLevel.CONFIRMED,
            summary = "NIH ODS cảnh báo vitamin K có tương tác quan trọng với warfarin; thay đổi đột ngột lượng vitamin K có thể làm tăng hoặc giảm hiệu quả chống đông.",
            mechanism = "Warfarin đối kháng hoạt động của vitamin K; thay đổi lượng vitamin K có thể làm thay đổi các yếu tố đông máu phụ thuộc vitamin K.",
            atRisk = listOf("Người đang dùng warfarin hoặc thuốc chống đông đối kháng vitamin K"),
            action = "Không cần tự loại bỏ hoàn toàn thực phẩm giàu vitamin K; mục tiêu là giữ lượng dùng tương đối ổn định và trao đổi với bác sĩ về thay đổi lớn trong chế độ ăn hoặc bổ sung.",
            sources = listOf(nihVitaminK)
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
