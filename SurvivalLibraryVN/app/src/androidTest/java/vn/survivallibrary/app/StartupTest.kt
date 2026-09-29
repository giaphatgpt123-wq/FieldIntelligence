package vn.survivallibrary.app

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTest {
    @Test
    fun launcherPublishedHomeReachesResumedStateAndRendersCoreActions() {
        ActivityScenario.launch(PublishedHomeLauncherActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse("Published home must stay alive", activity.isFinishing)
                assertTrue("Home title must be visible", containsText(activity.window.decorView, "THƯ VIỆN SINH TỒN"))
                assertTrue("Need section must render", containsText(activity.window.decorView, "Tìm theo nhu cầu"))
                assertTrue("Published section must render", containsText(activity.window.decorView, "Dữ liệu đã phát hành"))
                assertTrue("Camera action must render", containsText(activity.window.decorView, "Nhận dạng nhanh bằng camera"))
            }
        }
    }

    @Test
    fun defaultPolicySchedulesUnmeteredLibrarySyncWithoutOpeningNetworkOnUiThread() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RuntimePolicyStore.setSyncMode(context, LibrarySyncMode.WIFI_AUTO)
        LibrarySyncScheduler.reconcile(context)

        val scheduler = context.getSystemService(JobScheduler::class.java)
        val oneShot = scheduler.getPendingJob(LibrarySyncScheduler.ONE_SHOT_JOB_ID)
        val periodic = scheduler.getPendingJob(LibrarySyncScheduler.PERIODIC_JOB_ID)

        assertNotNull("One-shot Wi-Fi sync job must be scheduled", oneShot)
        assertNotNull("Periodic Wi-Fi sync job must be scheduled", periodic)
        assertTrue(oneShot!!.networkType == JobInfo.NETWORK_TYPE_UNMETERED)
        assertTrue(periodic!!.networkType == JobInfo.NETWORK_TYPE_UNMETERED)
        assertTrue(periodic.isPeriodic)
    }

    @Test
    fun publishedSearchStartsAndDoesNotMixDemoResults() {
        ActivityScenario.launch(PublishedSearchActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Tìm trong thư viện"))
                assertTrue(containsText(activity.window.decorView, "PUBLISHED"))
            }
        }
    }

    @Test
    fun publishedCategoryIndexStarts() {
        ActivityScenario.launch(PublishedCategoryActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Danh mục thư viện"))
            }
        }
    }

    @Test
    fun libraryProgressStartsWithoutInventingCollectorNumbers() {
        ActivityScenario.launch(LibraryProgressActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Bảng tiến độ trên thiết bị"))
            }
        }
    }

    @Test
    fun legacyRecordDetailStillStartsForRecoveryCompatibility() {
        ActivityScenario.launch(RecordDetailActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse("Record detail activity must stay alive", activity.isFinishing)
                assertTrue("Detail title must render", containsText(activity.window.decorView, "Hồ sơ"))
            }
        }
    }

    @Test
    fun cameraScreenStartsWithoutOpeningCameraAutomatically() {
        ActivityScenario.launch(NativeCameraActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Nhận dạng từ hình ảnh"))
                assertTrue(containsText(activity.window.decorView, "Chụp ảnh"))
            }
        }
    }

    @Test
    fun updateScreenStartsWithoutNetworkAtStartup() {
        ActivityScenario.launch(NativeUpdateActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Cập nhật ứng dụng"))
                assertTrue(containsText(activity.window.decorView, "Cập nhật thư viện"))
            }
        }
    }

    @Test
    fun legacyRecoveryActivityStillStarts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse("Recovery activity must stay alive", activity.isFinishing)
            }
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
