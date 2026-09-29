package vn.survivallibrary.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScalingSmokeTest {
    @Test
    fun windowedSearchStarts() {
        ActivityScenario.launch(WindowedPublishedSearchActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "giữ tối đa 40 hồ sơ"))
                assertTrue(containsText(activity.window.decorView, "Trang trước"))
                assertTrue(containsText(activity.window.decorView, "Trang sau"))
            }
        }
    }

    @Test
    fun windowedCategoryIndexStarts() {
        ActivityScenario.launch(WindowedPublishedCategoryActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Danh mục thư viện"))
            }
        }
    }

    @Test
    fun windowedSavedStarts() {
        ActivityScenario.launch(WindowedPublishedSavedActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Chỉ giữ một trang"))
            }
        }
    }

    @Test
    fun legacyPagedComponentResolvesToWindowedTarget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent().setClassName(context.packageName, "vn.survivallibrary.app.PagedPublishedSearchActivity")
        val info = context.packageManager.resolveActivity(intent, 0)?.activityInfo
        assertEquals("vn.survivallibrary.app.WindowedPublishedSearchActivity", info?.targetActivity)
    }

    @Test
    fun repositoryReturnsBoundedPagesFromLargeShard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("survival_library_vn.db")
        val db = OfflineLibraryDb(context)
        try {
            val records = (0 until 500).map { index ->
                LibraryPackageRecord(
                    id = "scale-$index",
                    vietnameseName = "Mẫu thử $index",
                    categoryId = "vegetables",
                    usageLevel = UsageLevel.THUONG_DUNG,
                    verificationState = VerificationState.DA_KIEM_CHUNG,
                    summary = "Dữ liệu kiểm thử phân trang",
                    highRisk = false,
                    sourceCount = 1,
                    published = true,
                    vietnamRelevant = true,
                    verifiedVietnameseName = true,
                    verifiedIdentitySource = true,
                    verifiedMedia = true,
                    hasUsageClaim = false,
                    verifiedUsageSource = false,
                    verifiedSafetySource = false
                )
            }
            val manifest = LibraryPackageManifest(
                packageId = "plants-core-s999",
                version = 1,
                schemaVersion = 1,
                recordCount = records.size,
                verifiedCount = records.size,
                sha256 = "c".repeat(64),
                sourceUri = "https://raw.githubusercontent.com/example/scale.json"
            )
            assertEquals(500, db.installVerifiedPackage(manifest, records))
        } finally {
            db.close()
        }

        try {
            val first = PagedPublishedRepository.records(
                context,
                categoryIds = setOf("vegetables"),
                limit = PagingPolicy.PAGE_SIZE,
                offset = 0
            )
            val second = PagedPublishedRepository.records(
                context,
                categoryIds = setOf("vegetables"),
                limit = PagingPolicy.PAGE_SIZE,
                offset = PagingPolicy.PAGE_SIZE
            )
            val probe = PagedPublishedRepository.records(
                context,
                categoryIds = setOf("vegetables"),
                limit = PagingPolicy.PAGE_SIZE + 1,
                offset = 0
            )
            assertEquals(PagingPolicy.PAGE_SIZE, first.size)
            assertEquals(PagingPolicy.PAGE_SIZE, second.size)
            assertEquals(PagingPolicy.PAGE_SIZE + 1, probe.size)
            assertTrue(first.map { it.id }.toSet().intersect(second.map { it.id }.toSet()).isEmpty())
        } finally {
            context.deleteDatabase("survival_library_vn.db")
        }
    }

    private fun containsText(view: View, expected: String): Boolean {
        if (view is TextView && view.text?.toString()?.contains(expected) == true) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsText(view.getChildAt(index), expected)) return true
            }
        }
        return false
    }
}
