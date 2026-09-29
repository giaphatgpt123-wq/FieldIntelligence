package vn.survivallibrary.app

import java.text.Normalizer
import java.util.Locale

enum class DataFieldKey {
    CANONICAL_IDENTITY,
    VIETNAMESE_PRIMARY_NAME,
    VIETNAMESE_ALIASES,
    VIETNAM_DISTRIBUTION,
    MEDIA_PRIMARY,
    MEDIA_DIAGNOSTIC_SET,
    IDENTIFICATION_TRAITS,
    CONFUSABLE_SPECIES,
    USAGE_LEVEL,
    USAGE_CONTENT,
    SAFETY
}

enum class LibraryTaskType {
    RESOLVE_CANONICAL,
    COLLECT_VIETNAMESE_NAMES,
    COLLECT_DISTRIBUTION,
    COLLECT_MEDIA,
    COLLECT_IDENTIFICATION,
    COLLECT_CONFUSABLE,
    COLLECT_USAGE,
    COLLECT_SAFETY,
    VERIFY_FIELD,
    PUBLISH_RECORD
}

enum class LibraryTaskStatus {
    PENDING,
    RUNNING,
    RETRY,
    COMPLETED,
    BLOCKED
}

enum class AliasType {
    PRIMARY,
    OTHER_NAME,
    REGIONAL,
    FOLK,
    COMMERCIAL,
    OLD_NAME,
    SCIENTIFIC_SYNONYM,
    CONFUSABLE_NAME
}

enum class SourceTier {
    OFFICIAL_VIETNAM,
    SPECIALIST_VIETNAM,
    GLOBAL_AUTHORITY,
    OPEN_SCIENCE,
    COMMUNITY_REFERENCE
}

data class VietnameseAlias(
    val displayName: String,
    val aliasType: AliasType,
    val region: String = "",
    val sourceKey: String = "",
    val verified: Boolean = false
) {
    val normalizedName: String = VietnameseNameKey.normalize(displayName)
}

data class StagedFieldState(
    val field: DataFieldKey,
    val valuePresent: Boolean = false,
    val evidenceCount: Int = 0,
    val verified: Boolean = false,
    val lastSourceKey: String = "",
    val lastError: String = ""
)

data class StagedLibraryEntity(
    val canonicalId: String,
    val categoryId: String,
    val scientificName: String,
    val vietnameseName: String = "",
    val aliases: List<VietnameseAlias> = emptyList(),
    val fields: Map<DataFieldKey, StagedFieldState> = emptyMap(),
    val highRisk: Boolean = false,
    val published: Boolean = false,
    val quality: RecordQuality = RecordQuality(vietnamRelevant = false)
) {
    init {
        require(canonicalId.isNotBlank()) { "canonicalId không được để trống" }
        require(categoryId.isNotBlank()) { "categoryId không được để trống" }
        require(scientificName.isNotBlank()) { "scientificName không được để trống" }
    }

    fun field(key: DataFieldKey): StagedFieldState =
        fields[key] ?: StagedFieldState(field = key)
}

data class LibraryLoadTask(
    val taskId: String,
    val canonicalId: String,
    val taskType: LibraryTaskType,
    val field: DataFieldKey?,
    val priority: Int,
    val preferredSourceTiers: List<SourceTier>,
    val status: LibraryTaskStatus = LibraryTaskStatus.PENDING,
    val attempts: Int = 0,
    val retryAfter: Long = 0L,
    val lastError: String = ""
)

data class DataEngineProgress(
    val canonicalId: String,
    val totalTrackedFields: Int,
    val verifiedFields: Int,
    val fieldsWithData: Int,
    val completionPercent: Int,
    val published: Boolean
)

object VietnameseNameKey {
    private val combiningMarks = Regex("\\p{M}+")
    private val nonWord = Regex("[^a-z0-9]+")

    fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val decomposed = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val withoutMarks = combiningMarks.replace(decomposed, "")
            .replace('đ', 'd')
        return nonWord.replace(withoutMarks, " ").trim().replace(Regex("\\s+"), " ")
    }

    fun deduplicate(values: List<VietnameseAlias>): List<VietnameseAlias> =
        values
            .filter { it.normalizedName.isNotBlank() }
            .groupBy { it.normalizedName to normalize(it.region) }
            .map { (_, rows) ->
                rows.sortedWith(
                    compareByDescending<VietnameseAlias> { it.verified }
                        .thenBy { it.aliasType.ordinal }
                ).first()
            }
            .sortedWith(compareBy<VietnameseAlias> { it.aliasType.ordinal }.thenBy { it.displayName })
}

object AiSourcePolicy {
    fun preferredTiers(field: DataFieldKey): List<SourceTier> = when (field) {
        DataFieldKey.CANONICAL_IDENTITY -> listOf(
            SourceTier.GLOBAL_AUTHORITY,
            SourceTier.OFFICIAL_VIETNAM,
            SourceTier.SPECIALIST_VIETNAM,
            SourceTier.OPEN_SCIENCE
        )

        DataFieldKey.VIETNAMESE_PRIMARY_NAME,
        DataFieldKey.VIETNAMESE_ALIASES,
        DataFieldKey.VIETNAM_DISTRIBUTION -> listOf(
            SourceTier.OFFICIAL_VIETNAM,
            SourceTier.SPECIALIST_VIETNAM,
            SourceTier.GLOBAL_AUTHORITY,
            SourceTier.OPEN_SCIENCE,
            SourceTier.COMMUNITY_REFERENCE
        )

        DataFieldKey.MEDIA_PRIMARY,
        DataFieldKey.MEDIA_DIAGNOSTIC_SET -> listOf(
            SourceTier.OFFICIAL_VIETNAM,
            SourceTier.SPECIALIST_VIETNAM,
            SourceTier.OPEN_SCIENCE,
            SourceTier.GLOBAL_AUTHORITY,
            SourceTier.COMMUNITY_REFERENCE
        )

        DataFieldKey.IDENTIFICATION_TRAITS,
        DataFieldKey.CONFUSABLE_SPECIES -> listOf(
            SourceTier.SPECIALIST_VIETNAM,
            SourceTier.OFFICIAL_VIETNAM,
            SourceTier.GLOBAL_AUTHORITY,
            SourceTier.OPEN_SCIENCE
        )

        DataFieldKey.USAGE_LEVEL,
        DataFieldKey.USAGE_CONTENT -> listOf(
            SourceTier.OFFICIAL_VIETNAM,
            SourceTier.SPECIALIST_VIETNAM,
            SourceTier.GLOBAL_AUTHORITY
        )

        DataFieldKey.SAFETY -> listOf(
            SourceTier.OFFICIAL_VIETNAM,
            SourceTier.GLOBAL_AUTHORITY,
            SourceTier.SPECIALIST_VIETNAM
        )
    }
}

/**
 * AI Library Manager is an orchestrator, not the publication authority.
 *
 * It may decide what to collect next and which source classes to prefer. It cannot
 * bypass LibraryRules: publication is queued only when the deterministic Rule Engine
 * returns publishable=true.
 */
object AiLibraryManager {
    private data class WorkRule(
        val field: DataFieldKey,
        val taskType: LibraryTaskType,
        val priority: Int
    )

    private val workRules = listOf(
        WorkRule(DataFieldKey.CANONICAL_IDENTITY, LibraryTaskType.RESOLVE_CANONICAL, 100),
        WorkRule(DataFieldKey.SAFETY, LibraryTaskType.COLLECT_SAFETY, 98),
        WorkRule(DataFieldKey.VIETNAMESE_PRIMARY_NAME, LibraryTaskType.COLLECT_VIETNAMESE_NAMES, 96),
        WorkRule(DataFieldKey.VIETNAM_DISTRIBUTION, LibraryTaskType.COLLECT_DISTRIBUTION, 93),
        WorkRule(DataFieldKey.MEDIA_PRIMARY, LibraryTaskType.COLLECT_MEDIA, 92),
        WorkRule(DataFieldKey.VIETNAMESE_ALIASES, LibraryTaskType.COLLECT_VIETNAMESE_NAMES, 88),
        WorkRule(DataFieldKey.MEDIA_DIAGNOSTIC_SET, LibraryTaskType.COLLECT_MEDIA, 84),
        WorkRule(DataFieldKey.IDENTIFICATION_TRAITS, LibraryTaskType.COLLECT_IDENTIFICATION, 80),
        WorkRule(DataFieldKey.CONFUSABLE_SPECIES, LibraryTaskType.COLLECT_CONFUSABLE, 76),
        WorkRule(DataFieldKey.USAGE_LEVEL, LibraryTaskType.COLLECT_USAGE, 66),
        WorkRule(DataFieldKey.USAGE_CONTENT, LibraryTaskType.COLLECT_USAGE, 60)
    )

    fun plan(entity: StagedLibraryEntity): List<LibraryLoadTask> {
        val tasks = mutableListOf<LibraryLoadTask>()
        workRules.forEach { rule ->
            if (rule.field == DataFieldKey.SAFETY && !entity.highRisk) return@forEach
            val fieldState = entity.field(rule.field)
            if (fieldState.verified) return@forEach
            tasks += LibraryLoadTask(
                taskId = taskId(entity.canonicalId, rule.taskType, rule.field),
                canonicalId = entity.canonicalId,
                taskType = rule.taskType,
                field = rule.field,
                priority = rule.priority,
                preferredSourceTiers = AiSourcePolicy.preferredTiers(rule.field)
            )
        }

        val publication = LibraryRules.publicationDecision(entity.quality)
        if (!entity.published && publication.publishable) {
            tasks += LibraryLoadTask(
                taskId = taskId(entity.canonicalId, LibraryTaskType.PUBLISH_RECORD, null),
                canonicalId = entity.canonicalId,
                taskType = LibraryTaskType.PUBLISH_RECORD,
                field = null,
                priority = 90,
                preferredSourceTiers = emptyList()
            )
        }

        return tasks
            .distinctBy { it.taskId }
            .sortedWith(compareByDescending<LibraryLoadTask> { it.priority }.thenBy { it.taskId })
    }

    fun nextRunnable(tasks: List<LibraryLoadTask>, now: Long = System.currentTimeMillis()): LibraryLoadTask? =
        tasks.asSequence()
            .filter { it.status == LibraryTaskStatus.PENDING || it.status == LibraryTaskStatus.RETRY }
            .filter { it.retryAfter <= now }
            .sortedWith(compareByDescending<LibraryLoadTask> { it.priority }.thenBy { it.attempts }.thenBy { it.taskId })
            .firstOrNull()

    fun progress(entity: StagedLibraryEntity): DataEngineProgress {
        val states = DataFieldKey.entries
            .filterNot { it == DataFieldKey.SAFETY && !entity.highRisk }
            .map { entity.field(it) }
        val verified = states.count { it.verified }
        val withData = states.count { it.valuePresent || it.evidenceCount > 0 }
        val percent = if (states.isEmpty()) 0 else ((verified * 100.0) / states.size).toInt().coerceIn(0, 100)
        return DataEngineProgress(
            canonicalId = entity.canonicalId,
            totalTrackedFields = states.size,
            verifiedFields = verified,
            fieldsWithData = withData,
            completionPercent = percent,
            published = entity.published
        )
    }

    fun mergeAliases(current: List<VietnameseAlias>, incoming: List<VietnameseAlias>): List<VietnameseAlias> =
        VietnameseNameKey.deduplicate(current + incoming)

    private fun taskId(canonicalId: String, type: LibraryTaskType, field: DataFieldKey?): String =
        buildString {
            append(canonicalId)
            append(':')
            append(type.name)
            if (field != null) {
                append(':')
                append(field.name)
            }
        }
}
