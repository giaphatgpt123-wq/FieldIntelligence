package vn.fieldintel.feature.emergency

/**
 * Lớp hồ sơ nội dung đã tuyển chọn, đặt tách khỏi SpeciesRecord.
 *
 * SpeciesRecord chỉ chứa định danh/taxonomy và nguồn gốc. Các nội dung có thể ảnh hưởng quyết định
 * thực địa như mức độ sử dụng, nhận biết, dễ nhầm, công dụng hay cảnh báo chỉ được đưa vào đây khi
 * có bằng chứng tương ứng. Cách tách này ngăn dữ liệu taxonomy bị hiểu nhầm thành bằng chứng sử dụng
 * hoặc an toàn.
 */
data class LibraryProfile(
    val recordId: String,
    val usageLevel: UsageLevel = UsageLevel.CHUA_PHAN_LOAI,
    val verificationState: VerificationState = VerificationState.CHUA_CO,
    val vietnamRelevant: Boolean = false,
    val verifiedVietnameseName: Boolean = false,
    val verifiedIdentitySource: Boolean = false,
    val verifiedMedia: Boolean = false,
    val recognitionTraits: List<String> = emptyList(),
    val lookAlikes: List<String> = emptyList(),
    val distribution: String? = null,
    val usageStatement: String? = null,
    val usageInstructions: String? = null,
    val safetyWarning: String? = null,
    val highRisk: Boolean = false,
    val verifiedSafetySource: Boolean = false,
    val usageSourceName: String? = null,
    val usageSourceUrl: String? = null,
    val usageSourceScope: String? = null,
    val usageEvidenceNote: String? = null
) {
    init {
        require(recordId.isNotBlank()) { "recordId không được để trống" }
    }

    val hasUsageClaim: Boolean
        get() = !usageStatement.isNullOrBlank() || !usageInstructions.isNullOrBlank()

    val hasVerifiedUsageSource: Boolean
        get() = !usageSourceName.isNullOrBlank() &&
            !usageSourceUrl.isNullOrBlank() &&
            !usageSourceScope.isNullOrBlank()

    fun qualitySnapshot(): LibraryQualitySnapshot = LibraryQualitySnapshot(
        vietnamRelevant = vietnamRelevant,
        usageLevel = usageLevel,
        verificationState = verificationState,
        verifiedVietnameseName = verifiedVietnameseName,
        verifiedIdentitySource = verifiedIdentitySource,
        verifiedMedia = verifiedMedia,
        usageClaimPresent = hasUsageClaim,
        verifiedUsageSource = hasVerifiedUsageSource,
        highRisk = highRisk,
        verifiedSafetySource = verifiedSafetySource
    )

    fun publicationDecision(): PublicationDecision = LibraryRules.publicationDecision(qualitySnapshot())
}

/**
 * Registry nhỏ cho lớp hồ sơ tuyển chọn nằm trong APK.
 *
 * Dữ liệu pilot/collector có thể bổ sung bằng bundle ngoài APK; khi chưa có hồ sơ đã đối chiếu,
 * UI phải nhận trạng thái mặc định thay vì suy đoán từ taxonomy.
 */
object LibraryProfiles {
    private val curated: Map<String, LibraryProfile> = emptyMap()

    fun find(recordId: String): LibraryProfile? = curated[recordId]

    fun forRecord(recordId: String): LibraryProfile = find(recordId) ?: LibraryProfile(recordId = recordId)
}
