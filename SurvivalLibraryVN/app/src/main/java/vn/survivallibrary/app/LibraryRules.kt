package vn.survivallibrary.app

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

data class RecordQuality(
    val vietnamRelevant: Boolean,
    val usageLevel: UsageLevel = UsageLevel.CHUA_PHAN_LOAI,
    val verificationState: VerificationState = VerificationState.CHUA_CO,
    val verifiedVietnameseName: Boolean = false,
    val verifiedIdentitySource: Boolean = false,
    val verifiedMedia: Boolean = false,
    val hasUsageClaim: Boolean = false,
    val verifiedUsageSource: Boolean = false,
    val highRisk: Boolean = false,
    val verifiedSafetySource: Boolean = false
)

data class PublicationDecision(
    val publishable: Boolean,
    val blockers: List<String>
)

object LibraryRules {
    val displayOrder = listOf(
        "Tên tiếng Việt",
        "Ảnh nhận biết",
        "Mức độ sử dụng",
        "Công dụng / cách dùng",
        "Nhận biết",
        "Dễ nhầm",
        "Cảnh báo an toàn",
        "Tên khoa học / nguồn"
    )

    fun publicationDecision(quality: RecordQuality): PublicationDecision {
        val blockers = buildList {
            if (!quality.vietnamRelevant) add("Chưa xác nhận mức liên quan tại Việt Nam")
            if (!quality.verifiedVietnameseName) add("Tên tiếng Việt chưa được đối chiếu")
            if (!quality.verifiedIdentitySource) add("Chưa có nguồn định danh")
            if (!quality.verifiedMedia) add("Chưa có ảnh đúng đối tượng đã kiểm tra nguồn")
            if (quality.hasUsageClaim && !quality.verifiedUsageSource) {
                add("Công dụng/cách dùng chưa có nguồn chuyên ngành")
            }
            if (quality.highRisk && !quality.verifiedSafetySource) {
                add("Đối tượng nguy cơ cao chưa có nguồn an toàn chuyên ngành")
            }
        }
        return PublicationDecision(
            publishable = blockers.isEmpty() &&
                quality.verificationState.rank >= VerificationState.DA_KIEM_CHUNG.rank,
            blockers = blockers
        )
    }

    fun canEnterVietnamPriorityFeed(quality: RecordQuality): Boolean {
        return quality.vietnamRelevant &&
            quality.usageLevel != UsageLevel.CHUA_PHAN_LOAI &&
            quality.verifiedVietnameseName &&
            quality.verifiedMedia &&
            quality.verificationState.rank >= VerificationState.DA_KIEM_CHUNG.rank
    }
}
