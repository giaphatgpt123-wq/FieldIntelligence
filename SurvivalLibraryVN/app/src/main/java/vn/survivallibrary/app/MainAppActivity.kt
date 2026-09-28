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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast

/**
 * Native, demo-style home screen.
 *
 * Runtime stability remains the priority: this screen uses only Android Views and bundled
 * drawables. It does not open SQLite, network, updater or Compose during startup.
 */
class MainAppActivity : Activity() {

    private val forest = Color.rgb(18, 63, 44)
    private val forest2 = Color.rgb(29, 90, 61)
    private val forestSoft = Color.rgb(56, 111, 78)
    private val cream = Color.rgb(247, 243, 232)
    private val paper = Color.rgb(255, 254, 250)
    private val sage = Color.rgb(233, 241, 228)
    private val sage2 = Color.rgb(218, 232, 214)
    private val muted = Color.rgb(99, 111, 102)
    private val ink = Color.rgb(25, 35, 29)
    private val gold = Color.rgb(225, 198, 126)
    private val warning = Color.rgb(255, 236, 185)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cream
        window.navigationBarColor = paper
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
            setBackgroundColor(cream)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(26))
        }

        content.addView(buildHeader())
        content.addView(space(12))
        content.addView(buildHero())
        content.addView(space(14))
        content.addView(buildSearchBar())
        content.addView(space(24))

        content.addView(sectionTitle("Tìm theo nhu cầu", "Chọn nhanh theo tình huống thực tế"))
        content.addView(space(12))
        content.addView(buildNeedGrid())
        content.addView(space(26))

        content.addView(sectionTitle("Thường dùng ở Việt Nam", "Ưu tiên nội dung quen thuộc, dễ nhận biết"))
        content.addView(space(12))
        content.addView(buildPopularRow())
        content.addView(space(12))
        content.addView(buildUsageFilters())
        content.addView(space(26))

        content.addView(sectionTitle("Khám phá theo danh mục", "Tiếng Việt trước · ảnh trước · chi tiết khi cần"))
        content.addView(space(12))
        content.addView(buildCategoryGrid())
        content.addView(space(22))
        content.addView(buildCameraCard())
        content.addView(space(18))
        content.addView(buildDataNotice())

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        root.addView(buildBottomNav(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(72)
        ))

        return root
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_icon_demo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSolid(paper, 14)
            clipToOutline = true
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titles.addView(text("THƯ VIỆN SINH TỒN", 20f, forest, true))
        titles.addView(text("Hiểu thiên nhiên · sống an toàn hơn", 11.5f, muted, false))
        row.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val search = TextView(this).apply {
            text = "⌕"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(forest)
            background = roundedSolid(paper, 16)
            setOnClickListener { toast("Mở tìm kiếm") }
        }
        row.addView(search, LinearLayout.LayoutParams(dp(48), dp(48)))
        return row
    }

    private fun buildHero(): View {
        val frame = FrameLayout(this).apply {
            background = roundedSolid(forest, 24)
            clipToOutline = true
        }

        val image = ImageView(this).apply {
            setImageResource(R.drawable.hero_scene)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        frame.addView(image, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val shade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(55, 0, 0, 0), Color.argb(205, 8, 35, 23))
            )
        }
        frame.addView(shade, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        overlay.addView(chip("THIÊN NHIÊN VIỆT NAM", paper, forest))
        overlay.addView(space(8))
        overlay.addView(text("Hiểu thiên nhiên\nSống an toàn hơn", 29f, Color.WHITE, true).apply {
            setLineSpacing(0f, 0.95f)
        })
        overlay.addView(text("Nhận biết trực quan · tiếng Việt trước · an toàn trước", 12.5f, Color.WHITE, false).apply {
            setPadding(0, dp(7), 0, 0)
        })
        frame.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        return frame.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(222))
        }
    }

    private fun buildSearchBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            background = roundedStroke(paper, 18, Color.rgb(116, 167, 117), 1)
            setOnClickListener { toast("Tìm trong thư viện") }
        }
        row.addView(text("⌕", 24f, forest, true), LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(text("Tìm cây, rau, nấm, cá, kỹ năng...", 13.5f, muted, false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(chip("TÌM", sage, forest))
        return row.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        }
    }

    private fun buildNeedGrid(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val first = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        first.addView(needCard("💧", "Uống", "Nước và đồ uống an toàn", Color.rgb(221, 239, 234)), weightedCardParams(6))
        first.addView(needCard("🥬", "Ăn", "Thực phẩm và cách dùng", Color.rgb(235, 240, 215)), weightedCardParams(0))
        container.addView(first)
        container.addView(space(10))

        val second = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        second.addView(needCard("⛺", "Ở", "Trú ẩn và kỹ năng", Color.rgb(242, 230, 205)), weightedCardParams(6))
        second.addView(needCard("⚠", "Tránh nguy hiểm", "Cây độc, động vật, sự cố", Color.rgb(247, 225, 205)), weightedCardParams(0))
        container.addView(second)
        return container
    }

    private fun needCard(icon: String, title: String, subtitle: String, color: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedSolid(color, 20)
            isClickable = true
            isFocusable = true
            setOnClickListener { toast(title) }
            addView(text(icon, 27f, forest, false))
            addView(space(8))
            addView(text(title, 19f, forest, true))
            addView(text(subtitle, 10.5f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
        }
    }

    private fun buildPopularRow(): View {
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, dp(4), 0)
        }
        row.addView(photoCard(R.drawable.rau_muong_demo, "Rau muống", "Thường dùng", "DEMO", forest2), fixedCardParams(158, 10))
        row.addView(photoCard(R.drawable.carrot_demo, "Cà rốt", "Thường dùng", "DEMO", Color.rgb(179, 109, 38)), fixedCardParams(158, 10))
        row.addView(photoCard(R.drawable.leaf_demo, "Lá thực vật", "Nhận biết mẫu", "DEMO", Color.rgb(87, 129, 77)), fixedCardParams(158, 0))
        scroller.addView(row)
        return scroller
    }

    private fun photoCard(imageRes: Int, title: String, usage: String, badge: String, accent: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedSolid(paper, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { toast(title) }
        }
        val image = ImageView(this).apply {
            setImageResource(imageRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSolid(sage, 18)
            clipToOutline = true
        }
        card.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(118)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(11))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(text(title, 15f, ink, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(chip(badge, warning, Color.rgb(116, 86, 17)))
        body.addView(top)
        body.addView(text(usage, 11f, accent, true).apply { setPadding(0, dp(5), 0, 0) })
        body.addView(text("Ảnh · nguồn · mức sử dụng", 9.5f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
        card.addView(body)
        return card
    }

    private fun buildUsageFilters(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = listOf("Thường dùng", "Hay dùng", "Ít dùng", "Hiếm dùng", "Xem thêm")
        labels.forEachIndexed { index, label ->
            row.addView(
                chip(label, if (index == 0) forest else paper, if (index == 0) Color.WHITE else forest),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                    if (index != labels.lastIndex) marginEnd = dp(8)
                }
            )
        }
        scroll.addView(row)
        return scroll
    }

    private fun buildCategoryGrid(): View {
        val categories = listOf(
            Triple("🌿", "Cây cỏ, rau", "Thường gặp và cách dùng"),
            Triple("🍄", "Nấm", "Nhận biết và cảnh báo"),
            Triple("🐟", "Cá & thủy sản", "Nước ngọt và vùng biển"),
            Triple("🐝", "Côn trùng", "Có ích và nguy hiểm"),
            Triple("🌸", "Hoa", "Tên Việt và hình ảnh"),
            Triple("🌳", "Cây gỗ", "Đặc điểm và phân biệt"),
            Triple("🍊", "Cây ăn quả", "Quả, mùa vụ, cách dùng"),
            Triple("🌱", "Cây thuốc", "Nhận biết và lưu ý an toàn")
        )

        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        categories.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { columnIndex, item ->
                val margin = if (columnIndex == 0) 6 else 0
                row.addView(categoryCard(item.first, item.second, item.third), weightedCardParams(margin))
            }
            if (pair.size == 1) row.addView(Space(this), weightedCardParams(0))
            container.addView(row)
            if (rowIndex != categories.chunked(2).lastIndex) container.addView(space(10))
        }
        return container
    }

    private fun categoryCard(icon: String, title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = roundedSolid(paper, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { toast(title) }
            addView(text(icon, 25f, forest, false))
            addView(space(7))
            addView(text(title, 15f, forest, true))
            addView(text(subtitle, 10f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        }
    }

    private fun buildCameraCard(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedGradient(forest2, forest, 20)
            isClickable = true
            isFocusable = true
            setOnClickListener { toast("Quét ảnh / chụp ảnh") }
        }
        row.addView(text("◉", 34f, Color.WHITE, true), LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        words.addView(text("Nhận dạng nhanh bằng camera", 16f, Color.WHITE, true))
        words.addView(text("Quét ảnh / Chụp ảnh · AI offline sẽ được nối sau", 10.5f, Color.argb(220, 255, 255, 255), false))
        row.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("›", 30f, Color.WHITE, false))
        return row
    }

    private fun buildDataNotice(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedSolid(Color.rgb(243, 236, 211), 16)
            addView(text("DỮ LIỆU HIỆN TẠI", 10f, Color.rgb(103, 78, 17), true))
            addView(text("Các thẻ có nhãn DEMO chỉ dùng để kiểm tra giao diện. Hồ sơ thật chỉ xuất hiện sau khi qua bộ quy tắc kiểm chứng.", 11.5f, Color.rgb(100, 87, 52), false).apply {
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = paper
        }
        val items = listOf(
            Triple("⌂", "Trang chủ", true),
            Triple("▦", "Danh mục", false),
            Triple("◎", "Quét ảnh", false),
            Triple("♡", "Đã lưu", false),
            Triple("↻", "Cập nhật", false)
        )
        items.forEach { item ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(2), dp(2), dp(2))
                if (item.third) background = roundedSolid(sage, 16)
                isClickable = true
                isFocusable = true
                setOnClickListener { toast(item.second) }
                addView(text(item.first, if (item.second == "Quét ảnh") 25f else 20f, if (item.third) forest else muted, true).apply {
                    gravity = Gravity.CENTER
                })
                addView(text(item.second, 9.5f, if (item.third) forest else muted, item.third).apply {
                    gravity = Gravity.CENTER
                })
            }
            nav.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        return nav
    }

    private fun sectionTitle(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 22f, forest, true))
            addView(text(subtitle, 11f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun chip(label: String, backgroundColor: Int, textColor: Int): TextView {
        return text(label, 9.5f, textColor, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundedSolid(backgroundColor, 999)
        }
    }

    private fun space(heightDp: Int): Space = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun weightedCardParams(endMarginDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(126), 1f).apply {
            marginEnd = dp(endMarginDp)
        }
    }

    private fun fixedCardParams(widthDp: Int, endMarginDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(endMarginDp)
        }
    }

    private fun roundedSolid(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun roundedStroke(fill: Int, radiusDp: Int, stroke: Int, strokeDp: Int): GradientDrawable {
        return roundedSolid(fill, radiusDp).apply {
            setStroke(dp(strokeDp), stroke)
        }
    }

    private fun roundedGradient(start: Int, end: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
