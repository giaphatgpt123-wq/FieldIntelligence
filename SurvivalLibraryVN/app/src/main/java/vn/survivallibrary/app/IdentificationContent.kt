package vn.survivallibrary.app

import android.content.Context
import org.json.JSONArray

/** Media/view quality gate derived from the original library rules. */
object IdentificationMediaPolicy {
    const val PROFILE = "IDENTIFICATION_V1"

    private val plantCategories = setOf(
        "vegetables", "roots", "fruit-crops", "flowers", "timber-trees", "medicinal-plants"
    )
    private val sixImageCategories = setOf(
        "mushrooms", "freshwater-fish", "marine-life", "insects", "animals", "danger"
    )

    fun minimumImageCount(categoryId: String): Int = when (categoryId) {
        in plantCategories -> 5
        in sixImageCategories -> 6
        else -> 5
    }

    fun validate(record: LibraryPackageRecord): List<String> = buildList {
        if (record.qualityProfile != PROFILE) {
            add("qualityProfile phải là $PROFILE")
            return@buildList
        }
        if (record.scientificName.isBlank()) add("thiếu tên khoa học cho hồ sơ nhận dạng")
        if (record.identificationSummary.isBlank()) add("thiếu mô tả nhận biết có nguồn")
        if (record.requiredViewRoles.isEmpty()) add("thiếu danh sách góc/bộ phận ảnh bắt buộc")
        if (record.primaryViewRole.isBlank()) add("thiếu vai trò ảnh đại diện")

        val images = record.media.filter {
            it.verified && it.diagnostic && it.mimeType.startsWith("image/")
        }
        val minimum = minimumImageCount(record.categoryId)
        if (images.size < minimum) add("bộ ảnh nhận dạng chỉ có ${images.size}/$minimum ảnh đạt chuẩn")

        val duplicateChecksums = images.groupBy { it.checksum.lowercase() }.filterValues { it.size > 1 }
        if (duplicateChecksums.isNotEmpty()) add("bộ ảnh có tệp trùng checksum, không được tính là đa góc")

        val availableRoles = images.map { it.viewRole.trim() }.filter { it.isNotBlank() }.toSet()
        val required = record.requiredViewRoles.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val missing = required - availableRoles
        if (missing.isNotEmpty()) add("thiếu góc/bộ phận bắt buộc: ${missing.joinToString()}")
        if (availableRoles.size < minOf(4, minimum)) add("bộ ảnh chưa đủ đa dạng vai trò nhận diện")

        val primary = images.filter { it.isPrimary }
        if (primary.size != 1) add("phải có đúng 1 ảnh đại diện nhận dạng")
        if (primary.size == 1 && primary.first().viewRole != record.primaryViewRole) {
            add("ảnh đại diện không đúng primaryViewRole=${record.primaryViewRole}")
        }
        if (record.primaryViewRole.isNotBlank() && record.primaryViewRole !in required) {
            add("primaryViewRole phải nằm trong requiredViewRoles")
        }
    }

    fun roleLabel(role: String): String = when (role) {
        "WHOLE" -> "Toàn thể"
        "ADULT_WHOLE" -> "Trưởng thành · toàn thân"
        "JUVENILE" -> "Con non"
        "LEAF" -> "Lá"
        "STEM" -> "Thân/cuống"
        "FLOWER" -> "Hoa"
        "FRUIT" -> "Quả"
        "UNDERGROUND_PART" -> "Củ/thân rễ/rễ"
        "CROSS_SECTION" -> "Mặt cắt"
        "BARK" -> "Vỏ/thân cây"
        "UNDERSIDE" -> "Mặt dưới"
        "STIPE" -> "Cuống"
        "HEAD" -> "Đầu/mõm"
        "SIDE" -> "Dáng nghiêng"
        "DORSAL" -> "Mặt lưng"
        "FINS" -> "Vây"
        "MOUTH" -> "Miệng"
        "WINGS" -> "Cánh"
        "ANTENNAE" -> "Râu"
        "HABITAT" -> "Trong môi trường sống"
        "REFERENCE" -> "Ảnh tham chiếu"
        else -> role.ifBlank { "Ảnh tham chiếu" }
    }
}

data class PublishedMediaItem(
    val mediaId: String,
    val localPath: String,
    val sourceUri: String,
    val angleLabel: String,
    val viewRole: String,
    val lifeStage: String,
    val isPrimary: Boolean,
    val diagnostic: Boolean,
    val license: String,
    val creator: String,
    val rightsHolder: String
)

data class PublishedIdentificationDetail(
    val scientificName: String,
    val identificationSummary: String,
    val keyFeatures: List<String>,
    val confusableWith: List<String>,
    val requiredViewRoles: List<String>,
    val primaryViewRole: String,
    val qualityProfile: String,
    val media: List<PublishedMediaItem>,
    val sources: List<LibraryRecordSource>
) {
    val diagnosticImages: List<PublishedMediaItem>
        get() = media.filter { it.diagnostic && it.localPath.isNotBlank() }

    val primaryMedia: PublishedMediaItem?
        get() = diagnosticImages.firstOrNull { it.isPrimary } ?: diagnosticImages.firstOrNull()

    val missingRequiredRoles: Set<String>
        get() = requiredViewRoles.toSet() - diagnosticImages.map { it.viewRole }.toSet()
}

/** Reads richer identification fields without changing the lightweight list model. */
object PublishedIdentificationRepository {
    fun detail(context: Context, recordId: String): PublishedIdentificationDetail? {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            val fields = db.readableDatabase.query(
                "library_records",
                arrayOf(
                    "scientific_name", "identification_summary", "key_features_json", "confusable_json",
                    "required_view_roles_json", "primary_view_role", "quality_profile"
                ),
                "id = ? AND published = 1",
                arrayOf(recordId),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                arrayOf(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.getString(4), cursor.getString(5), cursor.getString(6)
                )
            }

            val media = mutableListOf<PublishedMediaItem>()
            db.readableDatabase.query(
                "record_media",
                arrayOf(
                    "media_id", "local_path", "source_uri", "angle_label", "view_role", "life_stage",
                    "is_primary", "diagnostic", "license", "creator", "rights_holder"
                ),
                "record_id = ? AND verified = 1",
                arrayOf(recordId),
                null,
                null,
                "is_primary DESC, diagnostic DESC, media_id ASC"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    media += PublishedMediaItem(
                        mediaId = cursor.getString(0),
                        localPath = cursor.getString(1),
                        sourceUri = cursor.getString(2),
                        angleLabel = cursor.getString(3),
                        viewRole = cursor.getString(4),
                        lifeStage = cursor.getString(5),
                        isPrimary = cursor.getInt(6) == 1,
                        diagnostic = cursor.getInt(7) == 1,
                        license = cursor.getString(8),
                        creator = cursor.getString(9),
                        rightsHolder = cursor.getString(10)
                    )
                }
            }

            PublishedIdentificationDetail(
                scientificName = fields[0],
                identificationSummary = fields[1],
                keyFeatures = parseStringList(fields[2]),
                confusableWith = parseStringList(fields[3]),
                requiredViewRoles = parseStringList(fields[4]),
                primaryViewRole = fields[5],
                qualityProfile = fields[6],
                media = media,
                sources = db.recordSources(recordId)
            )
        } catch (_: Exception) {
            null
        } finally {
            db.close()
        }
    }

    private fun parseStringList(value: String): List<String> = runCatching {
        val array = JSONArray(value.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optString(index).trim()
                if (item.isNotBlank()) add(item)
            }
        }
    }.getOrDefault(emptyList())
}
