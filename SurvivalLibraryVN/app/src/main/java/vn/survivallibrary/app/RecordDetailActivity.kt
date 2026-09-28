package vn.survivallibrary.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Native detail screen that mirrors the approved demo while keeping all sample copy explicitly non-authoritative. */
class RecordDetailActivity : Activity() {

    private val forest = Color.rgb(18, 63, 44)
    private val forest2 = Color.rgb(29, 90, 61)
    private val cream = Color.rgb(247, 243, 232)
    private val paper = Color.rgb(255, 254, 250)
    private val sage = Color.rgb(233, 241, 228)
    private val muted = Color.rgb(99, 111, 102)
    private val ink = Color.rgb(25, 35, 29)
    private val warning = Color.rgb(255, 236, 185)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cream
        window.navigationBarColor = paper

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Hồ sơ mẫu"
        val imageRes = intent.getIntExtra(EXTRA_IMAGE_RES, R.drawable.leaf_demo)
        val usage = intent.getStringExtra(EXTRA_USAGE) ?: "Chưa phân loại"

        val root = buildScreen(title, imageRes, usage)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun buildScreen(title: String, imageRes: Int, usage: String): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cream)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(28))
        }

        content.addView(topBar())
        content.addView(space(12))
        content.addView(hero(imageRes))
        content.addView(space(14))

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        words.addView(text(title, 27f, forest, true))
        words.addView(text("Hồ sơ giao diện mẫu", 11f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        titleRow.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(chip(usage, sage, forest))
        content.addView(titleRow)
        content.addView(space(10))
        content.addView(chip("DEMO · CHƯA DÙNG LÀM DỮ LIỆU SINH TỒN", warning, Color.rgb(113, 82, 13)))
        content.addView(space(18))

        content.addView(section("Nhận biết", "Nội dung nhận biết chỉ được hiển thị chính thức sau khi hồ sơ vượt cổng kiểm chứng và có ảnh/nguồn phù hợp."))
        content.addView(space(10))
        content.addView(section("Dễ nhầm", "Danh sách đối tượng dễ nhầm sẽ chỉ xuất hiện khi có nguồn đối chiếu đủ rõ. Không suy đoán từ hình dạng đơn lẻ."))
        content.addView(space(10))
        content.addView(section("Cách dùng", "Phần này chỉ công bố cách dùng đã được kiểm chứng. Bản DEMO hiện không đưa ra hướng dẫn ăn, uống hoặc điều trị."))
        content.addView(space(10))
        content.addView(section("Lưu ý an toàn", "Không sử dụng nội dung DEMO để quyết định ăn, uống, sơ cứu hay dùng làm thuốc. Hồ sơ thật phải qua bộ quy tắc kiểm chứng."))
        content.addView(space(18))
        content.addView(actionRow())

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun topBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = iconButton(NativeIcon.BACK) { finish() }
        row.addView(back, LinearLayout.LayoutParams(dp(46), dp(46)))
        row.addView(text("Hồ sơ", 17f, forest, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val favorite = iconButton(NativeIcon.HEART) { toast("Đã ghi nhận thao tác lưu") }
        row.addView(favorite, LinearLayout.LayoutParams(dp(46), dp(46)))
        return row
    }

    private fun hero(imageRes: Int): View {
        val frame = FrameLayout(this).apply {
            background = roundedSolid(sage, 24)
            clipToOutline = true
        }
        frame.addView(ImageView(this).apply {
            setImageResource(imageRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val badge = chip("ẢNH MẪU", Color.argb(235, 255, 254, 250), forest)
        frame.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(14); bottomMargin = dp(14)
        })
        return frame.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(245)) }
    }

    private fun section(title: String, body: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = roundedSolid(paper, 18)
            val heading = LinearLayout(this@RecordDetailActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(text(title, 16f, forest, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            heading.addView(NativeIconView(this@RecordDetailActivity, NativeIcon.CHEVRON_RIGHT, forest2), LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(heading)
            addView(text(body, 12.5f, muted, false).apply { setPadding(0, dp(8), 0, 0); setLineSpacing(0f, 1.08f) })
        }
    }

    private fun actionRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val compare = actionButton("Quét để so sánh", forest2, Color.WHITE, NativeIcon.CAMERA) { toast("Chức năng camera sẽ được nối ở bước kế tiếp") }
        val save = actionButton("Lưu hồ sơ", sage, forest, NativeIcon.BOOKMARK) { toast("Đã ghi nhận thao tác lưu") }
        row.addView(compare, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
        row.addView(save, LinearLayout.LayoutParams(0, dp(56), 1f))
        return row
    }

    private fun actionButton(label: String, bg: Int, fg: Int, icon: NativeIcon, click: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedSolid(bg, 17)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
            addView(NativeIconView(this@RecordDetailActivity, icon, fg), LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(text(label, 12f, fg, true).apply { setPadding(dp(7), 0, 0, 0) })
        }
    }

    private fun iconButton(icon: NativeIcon, click: () -> Unit): View {
        return FrameLayout(this).apply {
            background = roundedSolid(paper, 15)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
            addView(NativeIconView(this@RecordDetailActivity, icon, forest), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun chip(label: String, bg: Int, fg: Int): TextView = text(label, 9.5f, fg, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(10), dp(6), dp(10), dp(6)); background = roundedSolid(bg, 999)
    }

    private fun roundedSolid(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun space(height: Int): Space = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_IMAGE_RES = "imageRes"
        const val EXTRA_USAGE = "usage"
    }
}
