package vn.duongodau.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

class DuongODauApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        if (root.findViewWithTag<TextView>(OVERLAY_TAG) != null) return

        val button = TextView(activity).apply {
            tag = OVERLAY_TAG
            text = "⚙ Cập nhật"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(28, 16, 28, 16)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f
                setColor(Color.rgb(14, 111, 115))
                setStroke(2, Color.argb(70, 255, 255, 255))
            }
            elevation = 12f
            setOnClickListener {
                activity.startActivity(Intent(activity, SystemActivity::class.java))
            }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = 18
            marginEnd = 18
        }
        root.addView(button, params)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val OVERLAY_TAG = "duongodau.system.overlay"
    }
}
