package vn.survivallibrary.app

enum class PackageInstallStatus {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
    BLOCKED
}

data class InstalledPackageState(
    val packageId: String,
    val version: Int,
    val status: PackageInstallStatus,
    val recordCount: Int,
    val verifiedCount: Int,
    val installedAt: Long,
    val checksum: String,
    val sourceUri: String
)

data class LibraryPackageDescriptor(
    val packageId: String,
    val displayName: String,
    val description: String,
    val categoryIds: Set<String>
)

data class LibraryPackageUiState(
    val descriptor: LibraryPackageDescriptor,
    val installed: InstalledPackageState?
) {
    val status: PackageInstallStatus = installed?.status ?: PackageInstallStatus.NOT_INSTALLED
    val recordCount: Int = installed?.recordCount ?: 0
    val verifiedCount: Int = installed?.verifiedCount ?: 0
}

data class LibraryPackageManifest(
    val packageId: String,
    val version: Int,
    val schemaVersion: Int,
    val recordCount: Int,
    val verifiedCount: Int,
    val sha256: String,
    val sourceUri: String
)

data class PackageValidationResult(
    val valid: Boolean,
    val blockers: List<String>
)

object LibraryDataPackages {
    const val SUPPORTED_SCHEMA_VERSION = 1

    val catalog: List<LibraryPackageDescriptor> = listOf(
        LibraryPackageDescriptor(
            packageId = "plants-core",
            displayName = "Thực vật Việt Nam",
            description = "Rau, củ, quả, hoa, cây gỗ và cây thuốc đã kiểm chứng.",
            categoryIds = setOf("vegetables", "roots", "fruit-crops", "flowers", "timber-trees", "medicinal-plants")
        ),
        LibraryPackageDescriptor(
            packageId = "mushrooms-core",
            displayName = "Nấm Việt Nam",
            description = "Nấm thường gặp; ưu tiên phân biệt và cảnh báo an toàn.",
            categoryIds = setOf("mushrooms")
        ),
        LibraryPackageDescriptor(
            packageId = "aquatic-core",
            displayName = "Cá & thủy sản",
            description = "Cá nước ngọt, cá biển và thủy sản thường gặp tại Việt Nam.",
            categoryIds = setOf("freshwater-fish", "marine-life")
        ),
        LibraryPackageDescriptor(
            packageId = "fauna-core",
            displayName = "Động vật & côn trùng",
            description = "Động vật, côn trùng và nhóm cần cảnh giác.",
            categoryIds = setOf("insects", "animals", "danger")
        ),
        LibraryPackageDescriptor(
            packageId = "skills-core",
            displayName = "Kỹ năng sinh tồn",
            description = "Kiến thức và quy trình sinh tồn dùng offline.",
            categoryIds = emptySet()
        )
    )

    fun merge(installed: List<InstalledPackageState>): List<LibraryPackageUiState> {
        val byId = installed.associateBy { it.packageId }
        return catalog.map { descriptor -> LibraryPackageUiState(descriptor, byId[descriptor.packageId]) }
    }

    fun validate(manifest: LibraryPackageManifest): PackageValidationResult {
        val blockers = buildList {
            if (catalog.none { it.packageId == manifest.packageId }) add("Gói dữ liệu không thuộc danh mục được phép")
            if (manifest.version <= 0) add("Phiên bản gói không hợp lệ")
            if (manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION) add("Phiên bản cấu trúc dữ liệu không được hỗ trợ")
            if (manifest.recordCount < 0) add("Số hồ sơ không hợp lệ")
            if (manifest.verifiedCount < 0 || manifest.verifiedCount > manifest.recordCount) add("Số hồ sơ kiểm chứng không hợp lệ")
            if (!manifest.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) add("Thiếu hoặc sai SHA-256")
            if (!manifest.sourceUri.startsWith("https://")) add("Nguồn cập nhật phải dùng HTTPS")
        }
        return PackageValidationResult(valid = blockers.isEmpty(), blockers = blockers)
    }
}
