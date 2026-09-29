package vn.survivallibrary.app

import android.content.Context
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
    fun pagedSearchStarts() {
        ActivityScenario.launch(PagedPublishedSearchActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Tải theo trang"))
            }
        }
    }

    @Test
    fun pagedCategoryIndexStarts() {
        ActivityScenario.launch(PagedPublishedCategoryActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Danh mục thư viện"))
            }
        }
    }

    @Test
    fun pagedSavedStarts() {
        ActivityScenario.launch(PagedPublishedSavedActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Danh sách phân trang"))
            }
        }
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
            assertEquals(PagingPolicy.PAGE_SIZE, first.size)
            assertEquals(PagingPolicy.PAGE_SIZE, second.size)
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
