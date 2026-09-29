package vn.survivallibrary.app

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTest {
    @Test
    fun launcherMainScreenReachesResumedStateAndRendersHome() {
        ActivityScenario.launch(MainAppActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse("Main activity must stay alive", activity.isFinishing)
                assertTrue("Home title must be visible", containsText(activity.window.decorView, "THƯ VIỆN SINH TỒN"))
                assertTrue("Need section must render", containsText(activity.window.decorView, "Tìm theo nhu cầu"))
                assertTrue("Camera action must render", containsText(activity.window.decorView, "Nhận dạng nhanh bằng camera"))
            }
        }
    }

    @Test
    fun recordDetailScreenReachesResumedStateAndRendersSafetySections() {
        ActivityScenario.launch(RecordDetailActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse("Record detail activity must stay alive", activity.isFinishing)
                assertTrue("Detail title must render", containsText(activity.window.decorView, "Hồ sơ"))
                assertTrue("Recognition section must render", containsText(activity.window.decorView, "Nhận biết"))
                assertTrue("Safety section must render", containsText(activity.window.decorView, "Lưu ý an toàn"))
            }
        }
    }

    @Test
    fun searchScreenStartsAndRendersSearchUi() {
        ActivityScenario.launch(NativeSearchActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Tìm trong thư viện"))
            }
        }
    }

    @Test
    fun categoryScreenStartsAndRendersCategoryList() {
        ActivityScenario.launch(NativeCategoryActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(containsText(activity.window.decorView, "Danh mục thư viện"))
                assertTrue(containsText(activity.window.decorView, "Cây cỏ, rau"))
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
    fun categoryNeedIntentRendersNoFakeDataMessageWhenEmpty() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, NativeCategoryActivity::class.java).apply {
            putExtra(NativeCategoryActivity.EXTRA_CATEGORY, "Uống")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<NativeCategoryActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertTrue(containsText(activity.window.decorView, "không tạo nội dung giả"))
            }
        }
    }

    @Test
    fun recoveryActivityStillStarts() {
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
