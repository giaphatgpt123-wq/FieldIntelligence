package vn.survivallibrary.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Production launcher for the PUBLISHED library.
 * The first frame is pure native Views; database reads happen only after setContentView().
 * List/media rendering is bounded by OptimizedPublishedBaseActivity.
 */
class PublishedHomeLauncherActivity : OptimizedPublishedBaseActivity() {
    private lateinit var statusText: TextView
    private lateinit var recentHolder: LinearLayout
    private lateinit var categoryHolder: LinearLayout
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        val (scroll, content) = scrollContent()

        content.addView(buildHeader())
        content.addView(space(10))
        content.addView(buildHero())
        content.addView(space(12))
        content.addView(buildSearchBar())
        content.addView(space(20))

        content.addView(sectionTitle("Tìm theo nhu cầu", "Chọn nhanh theo tình huống thực tế"))
        content.addView(space(10))
        content.addView(buildNeedGrid())
        content.addView(space(22))

        content.addView(sectionTitle("Dữ liệu đã phát hành", "Load được bao nhiêu, hiển thị bấy nhiêu"))
        content.addView(space(8))
        statusText = text("Đang đọc dữ liệu cục bộ…", 11f, muted, false)
        content.addView(statusText)
        content.addView(space(10))
        recentHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(recentHolder)
        content.addView(space(22))

        content.addView(sectionTitle("Khám phá theo danh mục", "Chỉ đếm hồ sơ PUBLISHED đang có trên thiết bị"))
        content.addView(space(10))
        categoryHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(categoryHolder)
        content.addView(space(18))

        content.addView(primaryButton("Nhận dạng nhanh bằng camera", NativeIcon.CAMERA) {
            startActivity(Intent(this, NativeCameraActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        content.addView(space(9))
        content.addView(secondaryButton("Xem tiến độ thư viện", NativeIcon.REFRESH) {
            startActivity(Intent(this, LibraryProgressActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        content.addView(space(14))
        content.addView(notice("Chỉ hồ sơ PUBLISHED trong database được hiển thị. Dữ liệu DEMO không được trộn vào thư viện thật."))

        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildBottomNav(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)))
        applyNativeRoot(root)

        renderCategories(emptyMap())
        loadSnapshot()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) loadSnapshot()
    }

    private fun loadSnapshot() {
        val generation = ++loadGeneration
        Thread {
            val snapshot = PublishedLibraryRepository.snapshot(applicationContext)
            runOnUiThread {
                if (generation != loadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                statusText.text = "${snapshot.publishedCount} hồ sơ đã phát hành · ${snapshot.verifiedCount} hồ sơ đã kiểm chứng"
                renderRecent(snapshot.recentRecords)
                renderCategories(snapshot.categoryCounts)
            }
        }.start()
    }

    private fun renderRecent(records: List<PublishedRecord>) {
        recentHolder.removeAllViews()
        if (records.isEmpty()) {
            recentHolder.addView(notice("Chưa có hồ sơ PUBLISHED trên thiết bị. Khi gói dữ liệu đầu tiên vượt kiểm tra và được đồng bộ, hồ sơ sẽ xuất hiện ngay tại đây."))
            return
        }
        records.take(6).forEachIndexed { index, record ->
            recentHolder.addView(publishedRecordCard(record) { openPublishedRecord(record) })
            if (index != minOf(records.size, 6) - 1) recentHolder.addView(space(8))
        }
    }

    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@PublishedHomeLauncherActivity).apply {
            setImageResource(R.drawable.ic_app_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = roundedSolid(paper, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        val titles = LinearLayout(this@PublishedHomeLauncherActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titles.addView(text("THƯ VIỆN SINH TỒN", 19f, forest, true))
        titles.addView(text("Dữ liệu Việt Nam · tiếng Việt trước · an toàn trước", 10.5f, muted, false).apply {
            setPadding(0, dp(2), 0, 0)
        })
        addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton(NativeIcon.SEARCH) {
            startActivity(Intent(this@PublishedHomeLauncherActivity, PagedPublishedSearchActivity::class.java))
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
    }

    private fun buildHero(): View = FrameLayout(this).apply {
        background = roundedSolid(forest, 23)
        clipToOutline = true
        addView(ImageView(this@PublishedHomeLauncherActivity).apply {
            setImageResource(R.drawable.hero_scene)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(View(this@PublishedHomeLauncherActivity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(40, 0, 0, 0), Color.argb(210, 8, 35, 23))
            )
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val overlay = LinearLayout(this@PublishedHomeLauncherActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(17), dp(17), dp(17), dp(17))
            addView(chip("THIÊN NHIÊN VIỆT NAM", Color.argb(238, 255, 254, 250), forest))
            addView(space(7))
            addView(text("Hiểu thiên nhiên\nSống an toàn hơn", 26f, Color.WHITE, true))
            addView(text("Nhận biết trực quan · dữ liệu kiểm chứng · cập nhật tăng dần", 11f, Color.WHITE, false).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }
        addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(205))
    }

    private fun buildSearchBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), 0, dp(10), 0)
        background = roundedSolid(paper, 18)
        isClickable = true
        isFocusable = true
        setOnClickListener { startActivity(Intent(this@PublishedHomeLauncherActivity, PagedPublishedSearchActivity::class.java)) }
        addView(NativeIconView(this@PublishedHomeLauncherActivity, NativeIcon.SEARCH, forest), LinearLayout.LayoutParams(dp(23), dp(23)))
        addView(text("Tìm tên tiếng Việt trong dữ liệu đã phát hành…", 12.2f, muted, false).apply {
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(chip("TÌM", sage, forest))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
    }

    private fun buildNeedGrid(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val row1 = LinearLayout(this@PublishedHomeLauncherActivity).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(needCard(NativeIcon.WATER, "Uống", emptySet()), LinearLayout.LayoutParams(0, dp(105), 1f).apply { marginEnd = dp(5) })
        row1.addView(needCard(NativeIcon.FOOD, "Ăn", setOf("vegetables", "roots", "fruit-crops", "mushrooms", "freshwater-fish", "marine-life")), LinearLayout.LayoutParams(0, dp(105), 1f).apply { marginStart = dp(5) })
        addView(row1)
        addView(space(9))
        val row2 = LinearLayout(this@PublishedHomeLauncherActivity).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(needCard(NativeIcon.SHELTER, "Ở", emptySet()), LinearLayout.LayoutParams(0, dp(105), 1f).apply { marginEnd = dp(5) })
        row2.addView(needCard(NativeIcon.SHIELD, "Tránh nguy hiểm", setOf("danger", "animals", "insects", "mushrooms")), LinearLayout.LayoutParams(0, dp(105), 1f).apply { marginStart = dp(5) })
        addView(row2)
    }

    private fun needCard(icon: NativeIcon, label: String, categoryIds: Set<String>): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = roundedSolid(sage, 18)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            startActivity(Intent(this@PublishedHomeLauncherActivity, PagedPublishedCategoryActivity::class.java).apply {
                putExtra(PagedPublishedCategoryActivity.EXTRA_TITLE, label)
                putExtra(PagedPublishedCategoryActivity.EXTRA_CATEGORY_IDS, categoryIds.toTypedArray())
            })
        }
        addView(NativeIconView(this@PublishedHomeLauncherActivity, icon, forest), LinearLayout.LayoutParams(dp(27), dp(27)))
        addView(space(6))
        addView(text(label, 16.5f, forest, true))
    }

    private fun renderCategories(counts: Map<String, Int>) {
        categoryHolder.removeAllViews()
        SurvivalLibraryCatalog.categories.forEachIndexed { index, category ->
            val icon = when (category.id) {
                "vegetables", "roots" -> NativeIcon.LEAF
                "fruit-crops" -> NativeIcon.FRUIT
                "flowers" -> NativeIcon.FLOWER
                "timber-trees" -> NativeIcon.TREE
                "mushrooms" -> NativeIcon.MUSHROOM
                "freshwater-fish", "marine-life" -> NativeIcon.FISH
                "insects" -> NativeIcon.BUG
                "medicinal-plants" -> NativeIcon.MEDICINE
                "animals", "danger" -> NativeIcon.SHIELD
                else -> NativeIcon.GRID
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(11), dp(12))
                background = roundedSolid(paper, 17)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(Intent(this@PublishedHomeLauncherActivity, PagedPublishedCategoryActivity::class.java).apply {
                        putExtra(PagedPublishedCategoryActivity.EXTRA_TITLE, category.label)
                        putExtra(PagedPublishedCategoryActivity.EXTRA_CATEGORY_IDS, arrayOf(category.id))
                    })
                }
                addView(NativeIconView(this@PublishedHomeLauncherActivity, icon, forest2), LinearLayout.LayoutParams(dp(27), dp(27)))
                val words = LinearLayout(this@PublishedHomeLauncherActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(11), 0, 0, 0)
                }
                words.addView(text(category.label, 14.5f, forest, true))
                words.addView(text("${counts[category.id] ?: 0} hồ sơ đã phát hành", 10.2f, muted, false).apply {
                    setPadding(0, dp(3), 0, 0)
                })
                addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(NativeIconView(this@PublishedHomeLauncherActivity, NativeIcon.CHEVRON_RIGHT, muted), LinearLayout.LayoutParams(dp(22), dp(22)))
            }
            categoryHolder.addView(row)
            if (index != SurvivalLibraryCatalog.categories.lastIndex) categoryHolder.addView(space(8))
        }
    }

    private fun buildBottomNav(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(paper)
        addView(navItem(NativeIcon.HOME, "Trang chủ") {}, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(navItem(NativeIcon.GRID, "Danh mục") {
            startActivity(Intent(this@PublishedHomeLauncherActivity, PagedPublishedCategoryActivity::class.java))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(navItem(NativeIcon.CAMERA, "Quét ảnh") {
            startActivity(Intent(this@PublishedHomeLauncherActivity, NativeCameraActivity::class.java))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(navItem(NativeIcon.BOOKMARK, "Đã lưu") {
            startActivity(Intent(this@PublishedHomeLauncherActivity, PagedPublishedSavedActivity::class.java))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(navItem(NativeIcon.REFRESH, "Cập nhật") {
            startActivity(Intent(this@PublishedHomeLauncherActivity, NativeUpdateActivity::class.java))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun navItem(icon: NativeIcon, label: String, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
        addView(NativeIconView(this@PublishedHomeLauncherActivity, icon, forest2), LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(text(label, 9f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
    }
}
