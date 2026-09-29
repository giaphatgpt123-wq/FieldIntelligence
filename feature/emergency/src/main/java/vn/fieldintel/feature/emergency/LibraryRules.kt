package vn.fieldintel.feature.emergency

/**
 * Quy tắc thư viện dùng chung cho UI, nhập liệu, AI và phát hành dữ liệu.
 *
 * Nguyên tắc quan trọng: không suy ra mức độ sử dụng, độc tính, tính ăn được hay công dụng từ
 * taxonomy. Các giá trị đó phải được gắn bởi dữ liệu đã tuyển chọn/đối chiếu.
 */
enum class UsageLevel(val label: String, val rank: Int) {
    THUONG_DUNG("Thường dùng", 0),
    HAY_DUNG("Hay dùng", 1),
    IT_DUNG("Ít dùng", 2),
    HIEM_DUNG("Hiếm dùng", 3),
    KHONG_CO_KHA_NANG_DUNG("Không có khả năng dùng", 4),
    CHUA_PHAN_LOAI("Chưa phân loại", 5)
}

enum class VerificationState(val label: String, val rank: Int) {
    CHUA_CO("Chưa có", 0),
    DA_THU_THAP("Đã thu thập", 1),
    DA_DOI_CHIEU("Đã đối chiếu", 2),
    DA_KIEM_CHUNG("Đã kiểm chứng", 3),
    DA_PHAT_HANH("Đã phát hành", 4)
}

data class LibraryQualitySnapshot(
    val vietnamRelevant: Boolean,
    val usageLevel: UsageLevel = UsageLevel.CHUA_PHAN_LOAI,
    val verificationState: VerificationState = VerificationState.CHUA_CO,
    val verifiedVietnameseName: Boolean = false,
    val verifiedIdentitySource: Boolean = false,
    val verifiedMedia: Boolean = false,
    val usageClaimPresent: Boolean = false,
    val verifiedUsageSource: Boolean = false,
    val highRisk: Boolean = false,
    val verifiedSafetySource: Boolean = false
)

data class PublicationDecision(
    val publishable: Boolean,
    val blockers: List<String>
)

object LibraryRules {
    /**
     * Thứ tự điều hướng ưu tiên nhu cầu thực tế ở Việt Nam. Nhóm kỹ thuật/toàn cầu nằm sau các
     * nhóm người dùng thường cần xem trực tiếp.
     */
    val collectionOrder: List<String> = listOf(
        "vegetables",
        "roots-rhizomes",
        "fruit-crops",
        "staple-crops",
        "flowers",
        "timber-trees",
        "freshwater-fish",
        "mushrooms",
        "insects",
        "animals",
        "toxic-plants",
        "traditional-medicine",
        "herbal-monographs",
        "wfo-plants"
    )

    /** Nội dung người dùng nhìn thấy trước; dữ liệu khoa học chi tiết để sau phần Xem thêm. */
    val displayFieldOrder: List<String> = listOf(
        "Tên tiếng Việt",
        "Ảnh nhận biết",
        "Mức độ sử dụng",
        "Công dụng / cách dùng",
        "Nhận biết",
        "Dễ nhầm",
        "Cảnh báo an toàn",
        "Tên khoa học / nguồn"
    )

    fun publicationDecision(snapshot: LibraryQualitySnapshot): PublicationDecision {
        val blockers = buildList {
            if (!snapshot.vietnamRelevant) add("Chưa xác nhận mức liên quan tại Việt Nam")
            if (snapshot.usageLevel == UsageLevel.CHUA_PHAN_LOAI) {
                add("Mức độ sử dụng chưa được phân loại")
            } else if (!snapshot.verifiedUsageSource) {
                add("Mức độ sử dụng chưa có nguồn đối chiếu")
            }
            if (!snapshot.verifiedVietnameseName) add("Tên tiếng Việt chưa được đối chiếu")
            if (!snapshot.verifiedIdentitySource) add("Chưa có nguồn xác minh định danh/taxonomy")
            if (!snapshot.verifiedMedia) add("Chưa có ảnh đúng đối tượng đã kiểm tra nguồn")
            if (snapshot.usageClaimPresent && !snapshot.verifiedUsageSource) {
                add("Công dụng/cách dùng chưa có nguồn chuyên ngành")
            }
            if (snapshot.highRisk && !snapshot.verifiedSafetySource) {
                add("Đối tượng nguy cơ cao chưa có nguồn an toàn chuyên ngành")
            }
        }
        return PublicationDecision(
            publishable = blockers.isEmpty() && snapshot.verificationState.rank >= VerificationState.DA_KIEM_CHUNG.rank,
            blockers = blockers
        )
    }

    /**
     * Danh sách "Thường dùng ở Việt Nam" chỉ nhận hồ sơ đã được gắn mức sử dụng và kiểm chứng.
     * Không tự suy đoán mức phổ biến từ tên loài hoặc taxonomy.
     */
    fun mayAppearInVietnamPriorityFeed(snapshot: LibraryQualitySnapshot): Boolean {
        if (!snapshot.vietnamRelevant) return false
        if (snapshot.usageLevel == UsageLevel.CHUA_PHAN_LOAI) return false
        if (!snapshot.verifiedUsageSource) return false
        if (!snapshot.verifiedVietnameseName || !snapshot.verifiedMedia) return false
        return snapshot.verificationState.rank >= VerificationState.DA_KIEM_CHUNG.rank
    }

    fun orderedCollections(items: List<LibraryCollection>): List<LibraryCollection> {
        val rank = collectionOrder.withIndex().associate { it.value to it.index }
        return items.sortedWith(compareBy<LibraryCollection> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.label })
    }
}
