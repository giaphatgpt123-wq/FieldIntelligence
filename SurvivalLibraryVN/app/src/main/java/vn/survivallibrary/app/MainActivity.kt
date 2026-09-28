package vn.survivallibrary.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

/**
 * Minimal native launcher.
 *
 * This screen deliberately avoids Compose, SQLite, network, updater and image decoding
 * so the application can always reach a visible first screen. The current Compose app
 * is opened only after the user taps the button below.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
            setBackgroundColor(Color.rgb(247, 243, 232))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Thư viện Sinh tồn Việt Nam"
            textSize = 28f
            setTextColor(Color.rgb(18, 63, 44))
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(Space(this), LinearLayout.LayoutParams(1, dp(18)))

        val status = TextView(this).apply {
            text = "Ứng dụng đã khởi động thành công.\nĐây là màn hình khởi động an toàn để tách lỗi hệ thống khỏi giao diện chính."
            textSize = 16f
            setTextColor(Color.rgb(72, 86, 76))
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(Space(this), LinearLayout.LayoutParams(1, dp(28)))

        val openMain = Button(this).apply {
            text = "Mở giao diện chính"
            textSize = 17f
            setOnClickListener {
                startActivity(Intent(this@MainActivity, MainAppActivity::class.java))
            }
        }
        root.addView(openMain, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(56)
        ))

        root.addView(Space(this), LinearLayout.LayoutParams(1, dp(18)))

        val note = TextView(this).apply {
            text = "Nếu màn hình này mở được nhưng giao diện chính dừng, lỗi nằm trong lớp Compose/dữ liệu và có thể sửa mà không thay bộ cài nền."
            textSize = 13f
            setTextColor(Color.rgb(102, 113, 104))
            gravity = Gravity.CENTER
        }
        root.addView(note, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
    }
}
