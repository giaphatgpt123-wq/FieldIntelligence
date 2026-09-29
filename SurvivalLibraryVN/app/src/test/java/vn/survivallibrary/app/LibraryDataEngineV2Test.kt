package vn.survivallibrary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDataEngineV2Test {
    @Test
    fun plannerSchedulesOnlyMissingFields() {
        val entity = StagedLibraryEntity(
            canonicalId = "taxon:anabas-testudineus",
            categoryId = "freshwater-fish",
            scientificName = "Anabas testudineus",
            vietnameseName = "Cá rô đồng",
            fields = mapOf(
                DataFieldKey.CANONICAL_IDENTITY to verified(DataFieldKey.CANONICAL_IDENTITY),
                DataFieldKey.VIETNAMESE_PRIMARY_NAME to verified(DataFieldKey.VIETNAMESE_PRIMARY_NAME),
                DataFieldKey.VIETNAM_DISTRIBUTION to verified(DataFieldKey.VIETNAM_DISTRIBUTION),
                DataFieldKey.MEDIA_PRIMARY to verified(DataFieldKey.MEDIA_PRIMARY)
            )
        )

        val tasks = AiLibraryManager.plan(entity)

        assertFalse(tasks.any { it.field == DataFieldKey.CANONICAL_IDENTITY })
        assertFalse(tasks.any { it.field == DataFieldKey.VIETNAMESE_PRIMARY_NAME })
        assertFalse(tasks.any { it.field == DataFieldKey.VIETNAM_DISTRIBUTION })
        assertFalse(tasks.any { it.field == DataFieldKey.MEDIA_PRIMARY })
        assertTrue(tasks.any { it.field == DataFieldKey.VIETNAMESE_ALIASES })
        assertTrue(tasks.any { it.field == DataFieldKey.MEDIA_DIAGNOSTIC_SET })
        assertTrue(tasks.any { it.field == DataFieldKey.CONFUSABLE_SPECIES })
    }

    @Test
    fun highRiskSafetyTaskGetsTopPriorityAfterIdentity() {
        val entity = StagedLibraryEntity(
            canonicalId = "taxon:aedes-aegypti",
            categoryId = "danger",
            scientificName = "Aedes aegypti",
            highRisk = true,
            fields = mapOf(
                DataFieldKey.CANONICAL_IDENTITY to verified(DataFieldKey.CANONICAL_IDENTITY)
            )
        )

        val tasks = AiLibraryManager.plan(entity)

        assertEquals(DataFieldKey.SAFETY, tasks.first().field)
        assertEquals(LibraryTaskType.COLLECT_SAFETY, tasks.first().taskType)
    }

    @Test
    fun publicationCanOnlyBeQueuedAfterRuleEngineApproves() {
        val blocked = StagedLibraryEntity(
            canonicalId = "taxon:mangifera-indica",
            categoryId = "fruit-crops",
            scientificName = "Mangifera indica",
            quality = RecordQuality(vietnamRelevant = true)
        )
        assertFalse(AiLibraryManager.plan(blocked).any { it.taskType == LibraryTaskType.PUBLISH_RECORD })

        val allowed = blocked.copy(
            quality = RecordQuality(
                vietnamRelevant = true,
                verificationState = VerificationState.DA_KIEM_CHUNG,
                verifiedVietnameseName = true,
                verifiedIdentitySource = true,
                verifiedMedia = true
            )
        )
        assertTrue(AiLibraryManager.plan(allowed).any { it.taskType == LibraryTaskType.PUBLISH_RECORD })
    }

    @Test
    fun vietnameseAliasNormalizationDeduplicatesRegionalNames() {
        val merged = AiLibraryManager.mergeAliases(
            current = listOf(
                VietnameseAlias("Cá rô đồng", AliasType.PRIMARY, verified = true),
                VietnameseAlias("Cá rô", AliasType.OTHER_NAME, region = "Nam Bộ", verified = true)
            ),
            incoming = listOf(
                VietnameseAlias("ca ro dong", AliasType.OTHER_NAME),
                VietnameseAlias("CÁ RÔ", AliasType.REGIONAL, region = "Nam Bo")
            )
        )

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.displayName == "Cá rô đồng" && it.verified })
        assertTrue(merged.any { it.displayName == "Cá rô" && it.region == "Nam Bộ" })
    }

    @Test
    fun vietnameseNamesPreferVietnamSources() {
        val tiers = AiSourcePolicy.preferredTiers(DataFieldKey.VIETNAMESE_ALIASES)
        assertEquals(SourceTier.OFFICIAL_VIETNAM, tiers[0])
        assertEquals(SourceTier.SPECIALIST_VIETNAM, tiers[1])
        assertTrue(tiers.indexOf(SourceTier.GLOBAL_AUTHORITY) > tiers.indexOf(SourceTier.SPECIALIST_VIETNAM))
    }

    @Test
    fun retryTaskDoesNotBlockOtherReadyTasks() {
        val now = 1000L
        val blockedByCooldown = LibraryLoadTask(
            taskId = "a",
            canonicalId = "x",
            taskType = LibraryTaskType.COLLECT_MEDIA,
            field = DataFieldKey.MEDIA_PRIMARY,
            priority = 100,
            preferredSourceTiers = listOf(SourceTier.OPEN_SCIENCE),
            status = LibraryTaskStatus.RETRY,
            retryAfter = 2000L
        )
        val ready = LibraryLoadTask(
            taskId = "b",
            canonicalId = "x",
            taskType = LibraryTaskType.COLLECT_VIETNAMESE_NAMES,
            field = DataFieldKey.VIETNAMESE_ALIASES,
            priority = 80,
            preferredSourceTiers = listOf(SourceTier.OFFICIAL_VIETNAM)
        )

        assertEquals("b", AiLibraryManager.nextRunnable(listOf(blockedByCooldown, ready), now)?.taskId)
    }

    private fun verified(field: DataFieldKey) = StagedFieldState(
        field = field,
        valuePresent = true,
        evidenceCount = 2,
        verified = true,
        lastSourceKey = "verified-source"
    )
}
