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

data class LibraryRecordSource(
    val sourceKey: String,
    val title: String,
    val publisher: String,
    val uri: String,
    val checkedAt: Long = 0L
)

data class LibraryRecordMedia(
    val mediaId: String,
    val sourceUri: String,
    val downloadUri: String,
    val verified: Boolean,
    val angleLabel: String,
    val checksum: String,
    val license: String,
    val creator: String = "",
    val rightsHolder: String = "",
    val mimeType: String = "image/webp",
    val viewRole: String = "REFERENCE",
    val lifeStage: String = "",
    val isPrimary: Boolean = false,
    val diagnostic: Boolean = true
)

data class LibraryPackageRecord(
    val id: String,
    val vietnameseName: String,
    val categoryId: String,
    val usageLevel: UsageLevel,
    val verificationState: VerificationState,
    val summary: String,
    val highRisk: Boolean,
    val sourceCount: Int,
    val published: Boolean,
    val vietnamRelevant: Boolean,
    val verifiedVietnameseName: Boolean,
    val verifiedIdentitySource: Boolean,
    val verifiedMedia: Boolean,
    val hasUsageClaim: Boolean,
    val verifiedUsageSource: Boolean,
    val verifiedSafetySource: Boolean,
    val sources: List<LibraryRecordSource> = emptyList(),
    val media: List<LibraryRecordMedia> = emptyList(),
    val scientificName: String = "",
    val identificationSummary: String = "",
    val keyFeatures: List<String> = emptyList(),
    val confusableWith: List<String> = emptyList(),
    val requiredViewRoles: List<String> = emptyList(),
    val primaryViewRole: String = "",
    val qualityProfile: String = ""
) {
    fun quality(): RecordQuality = RecordQuality(
        vietnamRelevant = vietnamRelevant,
        usageLevel = usageLevel,
        verificationState = verificationState,
        verifiedVietnameseName = verifiedVietnameseName,
        verifiedIdentitySource = verifiedIdentitySource,
        verifiedMedia = verifiedMedia,
        hasUsageClaim = hasUsageClaim,
        verifiedUsageSource = verifiedUsageSource,
        highRisk = highRisk,
        verifiedSafetySource = verifiedSafetySource
    )
}

data class PackageValidationResult(
    val valid: Boolean,
    val blockers: List<String>
)

object LibraryDataPackages {
    val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2, 3)
    const val LATEST_SCHEMA_VERSION = 3
    const val MAX_RECORDS_PER_SHARD = 2_000
    const val MAX_SOURCES_PER_RECORD = 32
    const val MAX_MEDIA_PER_RECORD = 32
    private val SHARD_SUFFIX = Regex("^\\d{3,6}$")
    private val SHA256 = Regex("^[a-fA-F0-9]{64}$")

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

    fun basePackageId(packageId: String): String? {
        catalog.firstOrNull { it.packageId == packageId }?.let { return it.packageId }
        return catalog.firstOrNull { descriptor ->
            val prefix = "${descriptor.packageId}-s"
            packageId.startsWith(prefix) && packageId.removePrefix(prefix).matches(SHARD_SUFFIX)
        }?.packageId
    }

    fun descriptor(packageId: String): LibraryPackageDescriptor? {
        val base = basePackageId(packageId) ?: return null
        return catalog.firstOrNull { it.packageId == base }
    }

    fun isShard(packageId: String): Boolean {
        val base = basePackageId(packageId) ?: return false
        return base != packageId
    }

    fun merge(installed: List<InstalledPackageState>): List<LibraryPackageUiState> {
        val grouped = installed.groupBy { basePackageId(it.packageId) ?: it.packageId }
        return catalog.map { descriptor ->
            val states = grouped[descriptor.packageId].orEmpty()
            val aggregate = if (states.isEmpty()) null else InstalledPackageState(
                packageId = descriptor.packageId,
                version = states.maxOf { it.version },
                status = when {
                    states.any { it.status == PackageInstallStatus.BLOCKED } -> PackageInstallStatus.BLOCKED
                    states.any { it.status == PackageInstallStatus.UPDATE_AVAILABLE } -> PackageInstallStatus.UPDATE_AVAILABLE
                    states.all { it.status == PackageInstallStatus.INSTALLED } -> PackageInstallStatus.INSTALLED
                    else -> PackageInstallStatus.NOT_INSTALLED
                },
                recordCount = states.sumOf { it.recordCount },
                verifiedCount = states.sumOf { it.verifiedCount },
                installedAt = states.maxOf { it.installedAt },
                checksum = if (states.size == 1) states.first().checksum else "",
                sourceUri = if (states.size == 1) states.first().sourceUri else ""
            )
            LibraryPackageUiState(descriptor, aggregate)
        }
    }

    fun validate(manifest: LibraryPackageManifest): PackageValidationResult {
        val blockers = buildList {
            if (descriptor(manifest.packageId) == null) add("Gói dữ liệu không thuộc danh mục hoặc shard được phép")
            if (manifest.version <= 0) add("Phiên bản gói không hợp lệ")
            if (manifest.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) add("Phiên bản cấu trúc dữ liệu không được hỗ trợ")
            if (manifest.recordCount < 0) add("Số hồ sơ không hợp lệ")
            if (manifest.recordCount > MAX_RECORDS_PER_SHARD) add("Gói vượt $MAX_RECORDS_PER_SHARD hồ sơ; phải chia shard nhỏ hơn")
            if (manifest.verifiedCount < 0 || manifest.verifiedCount > manifest.recordCount) add("Số hồ sơ kiểm chứng không hợp lệ")
            if (!manifest.sha256.matches(SHA256)) add("Thiếu hoặc sai SHA-256")
            if (!isHttps(manifest.sourceUri)) add("Nguồn cập nhật phải dùng HTTPS")
        }
        return PackageValidationResult(valid = blockers.isEmpty(), blockers = blockers)
    }

    fun validateRecords(manifest: LibraryPackageManifest, records: List<LibraryPackageRecord>): PackageValidationResult {
        val descriptor = descriptor(manifest.packageId)
        val blockers = buildList {
            if (descriptor == null) {
                add("Không tìm thấy cấu hình cho gói ${manifest.packageId}")
                return@buildList
            }
            if (records.size != manifest.recordCount) add("Số hồ sơ thực tế không khớp manifest")
            if (records.map { it.id }.distinct().size != records.size) add("Gói có ID hồ sơ bị trùng")
            if (records.any { it.id.isBlank() || it.id.startsWith("demo-") }) add("Gói chứa ID hồ sơ không hợp lệ")
            if (records.any { it.vietnameseName.isBlank() }) add("Gói có hồ sơ thiếu tên tiếng Việt")
            if (records.any { it.sourceCount <= 0 }) add("Gói có hồ sơ thiếu nguồn kiểm chứng")
            if (descriptor.categoryIds.isNotEmpty() && records.any { it.categoryId !in descriptor.categoryIds }) {
                add("Gói chứa hồ sơ ngoài phạm vi danh mục được phép")
            }
            val verified = records.count { it.verificationState.rank >= VerificationState.DA_KIEM_CHUNG.rank }
            if (verified != manifest.verifiedCount) add("Số hồ sơ kiểm chứng không khớp manifest")

            if (manifest.schemaVersion >= 2) {
                val mediaIds = records.flatMap { record -> record.media.map { it.mediaId } }
                if (mediaIds.distinct().size != mediaIds.size) add("Gói schema v2+ có mediaId bị trùng")
            }

            records.forEach { record ->
                if (!record.published) add("${record.id}: hồ sơ chưa ở trạng thái phát hành")
                val decision = LibraryRules.publicationDecision(record.quality())
                if (!decision.publishable) add("${record.id}: ${decision.blockers.joinToString("; ")}")
                if (manifest.schemaVersion >= 2) validateProvenance(record).forEach { add("${record.id}: $it") }
                if (manifest.schemaVersion >= 3) IdentificationMediaPolicy.validate(record).forEach { add("${record.id}: $it") }
            }
        }
        return PackageValidationResult(valid = blockers.isEmpty(), blockers = blockers)
    }

    private fun validateProvenance(record: LibraryPackageRecord): List<String> = buildList {
        if (record.sources.isEmpty()) add("schema v2+ phải có nguồn chi tiết")
        if (record.sources.size != record.sourceCount) add("sourceCount không khớp số nguồn chi tiết")
        if (record.sources.size > MAX_SOURCES_PER_RECORD) add("quá nhiều nguồn trong một hồ sơ")
        if (record.sources.map { it.sourceKey }.distinct().size != record.sources.size) add("sourceKey bị trùng")
        record.sources.forEach { source ->
            if (source.sourceKey.isBlank()) add("sourceKey trống")
            if (source.title.isBlank()) add("nguồn thiếu tiêu đề")
            if (source.publisher.isBlank()) add("nguồn thiếu đơn vị xuất bản")
            if (!isHttps(source.uri)) add("URI nguồn phải dùng HTTPS")
        }

        if (record.media.size > MAX_MEDIA_PER_RECORD) add("quá nhiều media trong một hồ sơ")
        if (record.verifiedMedia && record.media.none { it.verified }) add("verifiedMedia=true nhưng không có media đã kiểm chứng")
        record.media.forEach { media ->
            if (media.mediaId.isBlank()) add("mediaId trống")
            if (!isHttps(media.sourceUri)) add("sourceUri media phải dùng HTTPS")
            if (!isHttps(media.downloadUri)) add("downloadUri media phải dùng HTTPS")
            if (media.angleLabel.isBlank()) add("media thiếu nhãn góc/đặc điểm")
            if (!media.checksum.matches(SHA256)) add("media thiếu hoặc sai SHA-256")
            if (media.license.isBlank()) add("media thiếu thông tin giấy phép")
            if (media.creator.isBlank() && media.rightsHolder.isBlank()) add("media thiếu creator/rightsHolder")
            if (!media.mimeType.startsWith("image/") && !media.mimeType.startsWith("video/")) add("mimeType media không hợp lệ")
            if (media.viewRole.isBlank()) add("media thiếu viewRole")
        }
    }

    private fun isHttps(value: String): Boolean = value.startsWith("https://", ignoreCase = true)
}