package vn.survivallibrary.app

import android.app.Activity
import android.content.Intent
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Native, demo-style home screen.
 * Runtime stability remains the priority: no Compose, SQLite or network is opened during startup.
 */
class MainAppActivity : Activity() {

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
        title = "Thư viện Sinh tồn Việt Nam"

        val root = buildScreen()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cream)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(cream)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(10), dp(15), dp(24))
        }

        content.addView(buildHeader())
        content.addView(space(10))
        content.addView(buildHero())
        content.addView(space(12))
        content.addView(buildSearchBar())
        content.addView(space(22))

        content.addView(sectionTitle("Tìm theo nhu cầu", "Chọn nhanh theo tình huống thực tế"))
        content.addView(space(10))
        content.addView(buildNeedGrid())
        content.addView(space(24))

        content.addView(sectionTitle("Thường dùng ở Việt Nam", "Ưu tiên nội dung quen thuộc, dễ nhận biết"))
        content.addView(space(10))
        content.addView(buildPopularRow())
        content.addView(space(10))
        content.addView(buildUsageFilters())
        content.addView(space(24))

        content.addView(sectionTitle("Khám phá theo danh mục", "Tiếng Việt trước · ảnh trước · chi tiết khi cần"))
        content.addView(space(10))
        content.addView(buildCategoryGrid())
        content.addView(space(20))
        content.addView(buildCameraCard())
        content.addView(space(16))
        content.addView(buildDataNotice())

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildBottomNav(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)))
        return root
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_app_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = roundedSolid(paper, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipToOutline = true
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titles.addView(text("THƯ VIỆN SINH TỒN", 19f, forest, true))
        titles.addView(text("Thiên nhiên Việt Nam · hiểu để an toàn", 10.8f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(iconButton(NativeIcon.SEARCH) { openSearch() }, LinearLayout.LayoutParams(dp(46), dp(46)))
        return row
    }

    private fun buildHero(): View {
        val frame = FrameLayout(this).apply {
            background = roundedSolid(forest, 24)
            clipToOutline = true
        }
        frame.addView(ImageView(this).apply {
            setImageResource(R.drawable.hero_scene)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        frame.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(48, 0, 0, 0), Color.argb(210, 8, 35, 23)))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(17), dp(17), dp(17), dp(17))
        }
        overlay.addView(chip("THIÊN NHIÊN VIỆT NAM", Color.argb(238, 255, 254, 250), forest))
        overlay.addView(space(7))
        overlay.addView(text("Hiểu thiên nhiên\nSống an toàn hơn", 27f, Color.WHITE, true).apply { setLineSpacing(0f, 0.96f) })
        overlay.addView(text("Nhận biết trực quan · tiếng Việt trước · an toàn trước", 11.5f, Color.WHITE, false).apply { setPadding(0, dp(6), 0, 0) })
        frame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        return frame.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(214)) }
    }

    private fun buildSearchBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), 0, dp(10), 0)
            background = roundedStroke(paper, 18, Color.rgb(116, 167, 117), 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { openSearch() }
        }
        row.addView(NativeIconView(this, NativeIcon.SEARCH, forest), LinearLayout.LayoutParams(dp(23), dp(23)))
        row.addView(text("Tìm tên, hình ảnh hoặc kỹ năng...", 12.8f, muted, false).apply { setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(chip("TÌM", sage, forest))
        return row.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)) }
    }

    private fun buildNeedGrid(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val first = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        first.addView(needCard(NativeIcon.WATER, "Uống", "Nguồn nước & an toàn", Color.rgb(221, 239, 234)), weightedCardParams(6, 112))
        first.addView(needCard(NativeIcon.FOOD, "Ăn", "Thực phẩm & cách dùng", Color.rgb(235, 240, 215)), weightedCardParams(0, 112))
        container.addView(first)
        container.addView(space(9))

        val second = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        second.addView(needCard(NativeIcon.SHELTER, "Ở", "Trú ẩn & kỹ năng", Color.rgb(242, 230, 205)), weightedCardParams(6, 112))
        second.addView(needCard(NativeIcon.SHIELD, "Tránh nguy hiểm", "Cây độc, động vật, sự cố", Color.rgb(247, 225, 205)), weightedCardParams(0, 112))
        container.addView(second)
        return container
    }

    private fun needCard(icon: NativeIcon, title: String, subtitle: String, color: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = roundedSolid(color, 19)
            isClickable = true
            isFocusable = true
            setOnClickListener { openCategory(title) }
            addView(NativeIconView(this@MainAppActivity, icon, forest), LinearLayout.LayoutParams(dp(27), dp(27)))
            addView(space(6))
            addView(text(title, 17f, forest, true))
            addView(text(subtitle, 10.2f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        }
    }

    private fun buildPopularRow(): View {
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, dp(4), 0) }
        row.addView(photoCard(R.drawable.rau_muong_demo, "Rau muống", "Thường dùng", "rau-muong", forest2), fixedCardParams(152, 9))
        row.addView(photoCard(R.drawable.carrot_demo, "Cà rốt", "Thường dùng", "ca-rot", Color.rgb(179, 109, 38)), fixedCardParams(152, 9))
        row.addView(photoCard(R.drawable.leaf_demo, "Lá thực vật", "Nhận biết mẫu", "la-thuc-vat", Color.rgb(87, 129, 77)), fixedCardParams(152, 0))
        scroller.addView(row)
        return scroller
    }

    private fun photoCard(imageRes: Int, title: String, usage: String, recordId: String, accent: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedSolid(paper, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { openDetail(recordId, title, imageRes, usage) }
        }
        card.addView(ImageView(this).apply {
            setImageResource(imageRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSolid(sage, 18)
            clipToOutline = true
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)))

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(10)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(title, 14.5f, ink, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(chip("DEMO", warning, Color.rgb(116, 86, 17)))
        body.addView(top)
        body.addView(text(usage, 10.8f, accent, true).apply { setPadding(0, dp(4), 0, 0) })
        body.addView(text("Ảnh · nguồn · mức sử dụng", 9f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
        card.addView(body)
        return card
    }

    private fun buildUsageFilters(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = listOf("Thường dùng", "Hay dùng", "Ít dùng", "Hiếm dùng", "Xem thêm")
        labels.forEachIndexed { index, label ->
            val filter = chip(label, if (index == 0) forest else paper, if (index == 0) Color.WHITE else forest).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener { openSearch(label) }
            }
            row.addView(filter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                if (index != labels.lastIndex) marginEnd = dp(7)
            })
        }
        scroll.addView(row)
        return scroll
    }

    private fun buildCategoryGrid(): View {
        val categories = listOf(
            CategoryUi(NativeIcon.LEAF, "Cây cỏ, rau", "Thường gặp và cách dùng"),
            CategoryUi(NativeIcon.MUSHROOM, "Nấm", "Nhận biết và cảnh báo"),
            CategoryUi(NativeIcon.FISH, "Cá & thủy sản", "Nước ngọt và vùng biển"),
            CategoryUi(NativeIcon.BUG, "Côn trùng", "Có ích và nguy hiểm"),
            CategoryUi(NativeIcon.FLOWER, "Hoa", "Tên Việt và hình ảnh"),
            CategoryUi(NativeIcon.TREE, "Cây gỗ", "Đặc điểm và phân biệt"),
            CategoryUi(NativeIcon.FRUIT, "Cây ăn quả", "Quả, mùa vụ, cách dùng"),
            CategoryUi(NativeIcon.MEDICINE, "Cây thuốc", "Nhận biết và lưu ý an toàn")
        )
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        categories.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { columnIndex, item -> row.addView(categoryCard(item), weightedCardParams(if (columnIndex == 0) 6 else 0, 104)) }
            if (pair.size == 1) row.addView(Space(this), weightedCardParams(0, 104))
            container.addView(row)
            if (rowIndex != categories.chunked(2).lastIndex) container.addView(space(9))
        }
        return container
    }

    private fun categoryCard(item: CategoryUi): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = roundedSolid(paper, 17)
            isClickable = true
            isFocusable = true
            setOnClickListener { openCategory(item.title) }
            addView(NativeIconView(this@MainAppActivity, item.icon, forest2), LinearLayout.LayoutParams(dp(25), dp(25)))
            addView(space(6))
            addView(text(item.title, 14f, forest, true))
            addView(text(item.subtitle, 9.6f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        }
    }

    private fun buildCameraCard(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = roundedGradient(forest2, forest, 20)
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainAppActivity, NativeCameraActivity::class.java)) }
        }
        row.addView(NativeIconView(this, NativeIcon.CAMERA, Color.WHITE), LinearLayout.LayoutParams(dp(36), dp(36)))
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0) }
        words.addView(text("Nhận dạng nhanh bằng camera", 15f, Color.WHITE, true))
        words.addView(text("Chụp ảnh / chọn ảnh · không trả kết quả giả khi chưa có model", 10.2f, Color.argb(220, 255, 255, 255), false).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(NativeIconView(this, NativeIcon.CHEVRON_RIGHT, Color.WHITE), LinearLayout.LayoutParams(dp(24), dp(24)))
        return row
    }

    private fun buildDataNotice(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(11), dp(13), dp(11))
        background = roundedSolid(Color.rgb(243, 236, 211), 15)
        addView(text("DỮ LIỆU HIỆN TẠI", 9.5f, Color.rgb(103, 78, 17), true))
        addView(text("Các thẻ DEMO chỉ dùng kiểm tra giao diện. Hồ sơ thật chỉ xuất hiện sau khi vượt bộ quy tắc kiểm chứng.", 10.8f, Color.rgb(100, 87, 52), false).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(5))
            setBackgroundColor(paper)
        }
        val items = listOf(
            NavUi(NativeIcon.HOME, "Trang chủ", true),
            NavUi(NativeIcon.GRID, "Danh mục", false),
            NavUi(NativeIcon.CAMERA, "Quét ảnh", false),
            NavUi(NativeIcon.BOOKMARK, "Đã lưu", false),
            NavUi(NativeIcon.REFRESH, "Cập nhật", false)
        )
        items.forEach { item ->
            val color = if (item.selected) forest else muted
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(2))
                if (item.selected) background = roundedSolid(sage, 15)
                isClickable = true
                isFocusable = true
                setOnClickListener { onNav(item.label) }
                addView(NativeIconView(this@MainAppActivity, item.icon, color), LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(text(item.label, 9.2f, color, item.selected).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
            }
            nav.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        return nav
    }

    private fun onNav(label: String) {
        when (label) {
            "Trang chủ" -> Unit
            "Danh mục" -> openCategory("Tất cả danh mục")
            "Quét ảnh" -> startActivity(Intent(this, NativeCameraActivity::class.java))
            "Đã lưu" -> startActivity(Intent(this, NativeSavedActivity::class.java))
            "Cập nhật" -> startActivity(Intent(this, NativeUpdateActivity::class.java))
        }
    }

    private fun openSearch(query: String = "") {
        startActivity(Intent(this, NativeSearchActivity::class.java).apply {
            if (query.isNotBlank()) putExtra(NativeSearchActivity.EXTRA_QUERY, query)
        })
    }

    private fun openCategory(category: String) {
        startActivity(Intent(this, NativeCategoryActivity::class.java).putExtra(NativeCategoryActivity.EXTRA_CATEGORY, category))
    }

    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 20f, forest, true))
        addView(text(subtitle, 10.5f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
    }

    private fun iconButton(icon: NativeIcon, click: () -> Unit): View = FrameLayout(this).apply {
        background = roundedSolid(paper, 15)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
        addView(NativeIconView(this@MainAppActivity, icon, forest), FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
    }

    private fun openDetail(recordId: String, title: String, imageRes: Int, usage: String) {
        startActivity(Intent(this, RecordDetailActivity::class.java).apply {
            putExtra("recordId", recordId)
            putExtra(RecordDetailActivity.EXTRA_TITLE, title)
            putExtra(RecordDetailActivity.EXTRA_IMAGE_RES, imageRes)
            putExtra(RecordDetailActivity.EXTRA_USAGE, usage)
        })
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun chip(label: String, backgroundColor: Int, textColor: Int): TextView = text(label, 9.2f, textColor, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(5), dp(9), dp(5))
        background = roundedSolid(backgroundColor, 999)
    }

    private fun space(heightDp: Int): Space = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(heightDp)) }

    private fun weightedCardParams(endMarginDp: Int, heightDp: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(heightDp), 1f).apply { marginEnd = dp(endMarginDp) }

    private fun fixedCardParams(widthDp: Int, endMarginDp: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(endMarginDp) }

    private fun roundedSolid(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun roundedStroke(fill: Int, radiusDp: Int, stroke: Int, strokeDp: Int): GradientDrawable = roundedSolid(fill, radiusDp).apply { setStroke(dp(strokeDp), stroke) }

    private fun roundedGradient(start: Int, end: Int, radiusDp: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply { cornerRadius = dp(radiusDp).toFloat() }

    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class CategoryUi(val icon: NativeIcon, val title: String, val subtitle: String)
    private data class NavUi(val icon: NativeIcon, val label: String, val selected: Boolean)
}
