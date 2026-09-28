package vn.survivallibrary.app

data class LibraryCategory(
    val id: String,
    val label: String,
    val icon: String,
    val subtitle: String
)

data class LibraryProgress(
    val categoryId: String,
    val targetCount: Int? = null,
    val collected: Int = 0,
    val withVerifiedMedia: Int = 0,
    val vietnameseNameReviewed: Int = 0,
    val verified: Int = 0,
    val published: Int = 0
)

data class LibraryRecordUi(
    val id: String,
    val vietnameseName: String,
    val categoryId: String,
    val usageLevel: UsageLevel,
    val verificationState: VerificationState,
    val visual: String,
    val summary: String,
    val isDemo: Boolean = true
)

object SurvivalLibraryCatalog {
    val categories: List<LibraryCategory> = listOf(
        LibraryCategory("vegetables", "Rau", "🥬", "Rau và cây rau thường gặp tại Việt Nam"),
        LibraryCategory("roots", "Củ / thân rễ", "🫚", "Tách rõ từng loài, không gộp sai biến thể"),
        LibraryCategory("fruit-crops", "Cây ăn quả", "🥭", "Cây cho quả thường gặp, ưu tiên tên Việt"),
        LibraryCategory("flowers", "Hoa", "🌸", "Hoa thường gặp và ảnh nhận biết đúng loài"),
        LibraryCategory("timber-trees", "Cây gỗ", "🌳", "Cây gỗ và cây thân gỗ thường gặp"),
        LibraryCategory("mushrooms", "Nấm", "🍄", "Ưu tiên phân biệt và cảnh báo an toàn"),
        LibraryCategory("freshwater-fish", "Cá nước ngọt", "🐟", "Cá nước ngọt thường gặp tại Việt Nam"),
        LibraryCategory("marine-life", "Cá / thủy sản biển", "🐠", "Sinh vật biển và thủy sản thường gặp"),
        LibraryCategory("insects", "Côn trùng", "🐝", "Côn trùng thường gặp, nhận biết nhanh"),
        LibraryCategory("animals", "Động vật", "🐾", "Động vật thường gặp và nhóm cần cảnh giác"),
        LibraryCategory("medicinal-plants", "Cây thuốc", "🌿", "Chỉ phát hành công dụng khi có nguồn chuyên ngành"),
        LibraryCategory("danger", "Nguy hiểm", "⚠️", "Nhóm có rủi ro cao, yêu cầu bằng chứng an toàn")
    )

    /**
     * Đây chỉ là các thẻ mẫu để kiểm tra UI. Không được tính vào tiến độ thư viện thật.
     */
    val demoRecords: List<LibraryRecordUi> = listOf(
        LibraryRecordUi(
            id = "demo-rau-muong",
            vietnameseName = "Rau muống",
            categoryId = "vegetables",
            usageLevel = UsageLevel.CHUA_PHAN_LOAI,
            verificationState = VerificationState.CHUA_CO,
            visual = "🥬",
            summary = "Dữ liệu mẫu giao diện — chưa phát hành hồ sơ thật"
        ),
        LibraryRecordUi(
            id = "demo-nghe-vang",
            vietnameseName = "Nghệ vàng",
            categoryId = "roots",
            usageLevel = UsageLevel.CHUA_PHAN_LOAI,
            verificationState = VerificationState.CHUA_CO,
            visual = "🫚",
            summary = "Dữ liệu mẫu giao diện — chưa phát hành hồ sơ thật"
        ),
        LibraryRecordUi(
            id = "demo-xoai",
            vietnameseName = "Xoài",
            categoryId = "fruit-crops",
            usageLevel = UsageLevel.CHUA_PHAN_LOAI,
            verificationState = VerificationState.CHUA_CO,
            visual = "🥭",
            summary = "Dữ liệu mẫu giao diện — chưa phát hành hồ sơ thật"
        ),
        LibraryRecordUi(
            id = "demo-nam",
            vietnameseName = "Nấm — mẫu bố cục",
            categoryId = "mushrooms",
            usageLevel = UsageLevel.CHUA_PHAN_LOAI,
            verificationState = VerificationState.CHUA_CO,
            visual = "🍄",
            summary = "Không dùng thẻ mẫu để quyết định ăn hoặc sử dụng"
        )
    )

    /**
     * Bảng tiến độ khởi tạo bằng 0. Mục tiêu để null cho tới khi được đặt và kiểm chứng,
     * tránh tạo số liệu mục tiêu giả.
     */
    val progress: List<LibraryProgress> = categories.map { category ->
        LibraryProgress(categoryId = category.id)
    }

    fun category(id: String?): LibraryCategory? = categories.firstOrNull { it.id == id }

    fun demoRecordsFor(categoryId: String?, query: String = ""): List<LibraryRecordUi> {
        val needle = query.trim()
        return demoRecords.filter { record ->
            (categoryId == null || record.categoryId == categoryId) &&
                (needle.isBlank() || record.vietnameseName.contains(needle, ignoreCase = true))
        }
    }
}
