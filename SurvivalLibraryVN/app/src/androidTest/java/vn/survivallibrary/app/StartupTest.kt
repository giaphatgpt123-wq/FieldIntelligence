package vn.survivallibrary.app

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
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
                assertTrue(
                    "Home title must be visible in the rendered native hierarchy",
                    containsText(activity.window.decorView, "THƯ VIỆN SINH TỒN")
                )
                assertTrue(
                    "Need section must render",
                    containsText(activity.window.decorView, "Tìm theo nhu cầu")
                )
                assertTrue(
                    "Camera action must render",
                    containsText(activity.window.decorView, "Nhận dạng nhanh bằng camera")
                )
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
