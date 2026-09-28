package vn.survivallibrary.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Stable native main screen.
 *
 * This activity deliberately avoids Compose, SQLite, network calls and image decoding.
 * It is the recovery baseline for real-device runtime stability. Once this screen is
 * confirmed on the target phone, features can be re-enabled one layer at a time.
 */
class MainAppActivity : Activity() {

    private val forest = Color.rgb(18, 63, 44)
    private val forest2 = Color.rgb(29, 90, 61)
    private val cream = Color.rgb(247, 243, 232)
    private val paper = Color.rgb(255, 254, 250)
    private val muted = Color.rgb(102, 113, 104)
    private val sage = Color.rgb(233, 241, 228)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Thư viện Sinh tồn Việt Nam"
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cream)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }

        content.addView(text("THƯ VIỆN SINH TỒN", 25f, forest, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        content.addView(text("Tri thức Việt Nam • dễ hiểu • an toàn trước", 13f, muted, false).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(16))
        })

        content.addView(card().apply {
            setPadding(dp(18), dp(22), dp(18), dp(22))
            addView(text("Hiểu thiên nhiên\nSống an toàn hơn", 27f, Color.WHITE, true))
            addView(text("Ưu tiên tiếng Việt, nhận biết trực quan và cảnh báo rõ ràng.", 13f, Color.WHITE, false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }.also {
            it.background = roundedGradient(forest2, forest, 24)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(sectionTitle("Tìm theo nhu cầu"))

        val needs = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            useDefaultMargins = false
        }
        listOf(
            "Uống\nNguồn nước & an toàn",
            "Ăn\nThực phẩm & cách dùng",
            "Ở\nTrú ẩn & kỹ năng",
            "Tránh nguy hiểm\nCây độc, động vật, sự cố"
        ).forEach { label ->
            val item = TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(forest, 18)
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(100)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            needs.addView(item, lp)
        }
        content.addView(needs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(sectionTitle("Thường dùng ở Việt Nam"))
        content.addView(infoRow("Rau muống", "DEMO giao diện • hồ sơ thật sẽ chỉ hiện sau kiểm chứng"))
        content.addView(infoRow("Cà rốt", "DEMO giao diện • chưa dùng làm dữ liệu sinh tồn"))

        content.addView(sectionTitle("Danh mục chính"))
        listOf(
            "Rau, củ, quả",
            "Cây ăn quả",
            "Cây gỗ",
            "Hoa",
            "Nấm",
            "Cá & thủy sản",
            "Động vật",
            "Côn trùng",
            "Cây thuốc",
            "Kỹ năng sinh tồn"
        ).forEach { content.addView(simpleCategory(it)) }

        val updateButton = Button(this).apply {
            text = "CẬP NHẬT"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(forest2, 18)
            setOnClickListener {
                android.widget.Toast.makeText(
                    this@MainAppActivity,
                    "Khung cập nhật đã giữ chỗ. Sẽ nối lại sau khi xác nhận runtime ổn định.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        content.addView(updateButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(22)
        })

        content.addView(text("Bản ổn định nền native • không khởi tạo Compose/SQLite khi mở màn hình chính", 11f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun sectionTitle(value: String): TextView = text(value, 20f, forest, true).apply {
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun simpleCategory(label: String): TextView = TextView(this).apply {
        text = "$label   ›"
        textSize = 15f
        setTextColor(Color.rgb(29, 42, 34))
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(paper, 16)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(7)
        layoutParams = lp
    }

    private fun infoRow(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(13), dp(15), dp(13))
        background = rounded(sage, 16)
        addView(text(title, 16f, forest, true))
        addView(text(subtitle, 11f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun roundedGradient(start: Int, end: Int, radiusDp: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(start, end)
    ).apply {
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
