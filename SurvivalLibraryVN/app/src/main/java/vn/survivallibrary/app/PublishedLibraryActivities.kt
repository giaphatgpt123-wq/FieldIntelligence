package vn.survivallibrary.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import android.content.Context
import android.content.Intent
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** A record that has actually been installed in the local published library. */
data class PublishedRecord(
    val id: String,
    val packageId: String,
    val vietnameseName: String,
    val categoryId: String,
    val usageLevel: UsageLevel,
    val verificationState: VerificationState,
    val summary: String,
    val highRisk: Boolean,
    val sourceCount: Int,
    val updatedAt: Long,
    val localMediaPath: String? = null
)

data class CategoryPublishedCount(
    val categoryId: String,
    val count: Int
)

data class PublishedLibrarySnapshot(
    val publishedCount: Int,
    val verifiedCount: Int,
    val categoryCounts: Map<String, Int>,
    val installedPackages: List<InstalledPackageState>,
    val recentRecords: List<PublishedRecord>
)

/**
 * Read-only adapter for the installed database. Every call opens the database lazily and closes it.
 * Nothing here is invoked before the first UI frame of the launcher.
 */
object PublishedLibraryRepository {
    fun snapshot(context: Context, recentLimit: Int = 8): PublishedLibrarySnapshot {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            val counts = linkedMapOf<String, Int>()
            db.readableDatabase.rawQuery(
                "SELECT category_id, COUNT(*) FROM library_records WHERE published = 1 GROUP BY category_id",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) counts[cursor.getString(0)] = cursor.getInt(1)
            }
            PublishedLibrarySnapshot(
                publishedCount = db.publishedCount(),
                verifiedCount = db.verifiedCount(),
                categoryCounts = counts,
                installedPackages = db.installedPackageStates(),
                recentRecords = queryRecords(db, null, null, null, recentLimit)
            )
        } catch (_: Exception) {
            PublishedLibrarySnapshot(0, 0, emptyMap(), emptyList(), emptyList())
        } finally {
            db.close()
        }
    }

    fun records(
        context: Context,
        categoryId: String? = null,
        query: String? = null,
        usageLevel: UsageLevel? = null,
        limit: Int = 300
    ): List<PublishedRecord> {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            queryRecords(db, categoryId, query, usageLevel, limit)
        } catch (_: Exception) {
            emptyList()
        } finally {
            db.close()
        }
    }

    fun byId(context: Context, id: String): PublishedRecord? {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            queryRecords(db, null, null, null, 1, id).firstOrNull()
        } catch (_: Exception) {
            null
        } finally {
            db.close()
        }
    }

    fun favoriteRecords(context: Context, limit: Int = 300): List<PublishedRecord> {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            val sql = """
                SELECT r.id,r.package_id,r.vietnamese_name,r.category_id,r.usage_level,r.verification_state,
                       r.summary,r.high_risk,r.source_count,r.updated_at,
                       (SELECT local_path FROM record_media m WHERE m.record_id=r.id AND m.verified=1 AND m.local_path<>'' LIMIT 1)
                FROM library_records r
                INNER JOIN favorites f ON f.record_id=r.id
                WHERE r.published=1
                ORDER BY f.saved_at DESC
                LIMIT ?
            """.trimIndent()
            db.readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursorToRecord(cursor))
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            db.close()
        }
    }

    fun isFavorite(context: Context, id: String): Boolean {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            id in db.favoriteIds()
        } catch (_: Exception) {
            false
        } finally {
            db.close()
        }
    }

    fun setFavorite(context: Context, id: String, favorite: Boolean): Boolean {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            db.setFavorite(id, favorite)
            favorite
        } catch (_: Exception) {
            false
        } finally {
            db.close()
        }
    }

    private fun queryRecords(
        db: OfflineLibraryDb,
        categoryId: String?,
        query: String?,
        usageLevel: UsageLevel?,
        limit: Int,
        exactId: String? = null
    ): List<PublishedRecord> {
        val where = mutableListOf("r.published=1")
        val args = mutableListOf<String>()
        if (!exactId.isNullOrBlank()) {
            where += "r.id=?"
            args += exactId
        }
        if (!categoryId.isNullOrBlank()) {
            where += "r.category_id=?"
            args += categoryId
        }
        if (usageLevel != null) {
            where += "r.usage_level=?"
            args += usageLevel.name
        }
        if (!query.isNullOrBlank()) {
            where += "(r.vietnamese_name LIKE ? COLLATE NOCASE OR r.summary LIKE ? COLLATE NOCASE)"
            val like = "%${query.trim()}%"
            args += like
            args += like
        }
        val sql = """
            SELECT r.id,r.package_id,r.vietnamese_name,r.category_id,r.usage_level,r.verification_state,
                   r.summary,r.high_risk,r.source_count,r.updated_at,
                   (SELECT local_path FROM record_media m WHERE m.record_id=r.id AND m.verified=1 AND m.local_path<>'' LIMIT 1)
            FROM library_records r
            WHERE ${where.joinToString(" AND ")}
            ORDER BY CASE r.usage_level
                WHEN 'THUONG_DUNG' THEN 0
                WHEN 'HAY_DUNG' THEN 1
                WHEN 'IT_DUNG' THEN 2
                WHEN 'HIEM_DUNG' THEN 3
                WHEN 'KHONG_CO_KHA_NANG_DUNG' THEN 4
                ELSE 5 END,
                r.vietnamese_name COLLATE NOCASE ASC
            LIMIT ?
        """.trimIndent()
        args += limit.coerceIn(1, 1000).toString()
        return db.readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursorToRecord(cursor))
            }
        }
    }

    private fun cursorToRecord(cursor: android.database.Cursor): PublishedRecord {
        val usage = runCatching { UsageLevel.valueOf(cursor.getString(4)) }.getOrDefault(UsageLevel.CHUA_PHAN_LOAI)
        val verification = runCatching { VerificationState.valueOf(cursor.getString(5)) }.getOrDefault(VerificationState.CHUA_CO)
        return PublishedRecord(
            id = cursor.getString(0),
            packageId = cursor.getString(1),
            vietnameseName = cursor.getString(2),
            categoryId = cursor.getString(3),
            usageLevel = usage,
            verificationState = verification,
            summary = cursor.getString(6),
            highRisk = cursor.getInt(7) == 1,
            sourceCount = cursor.getInt(8),
            updatedAt = cursor.getLong(9),
            localMediaPath = if (cursor.isNull(10)) null else cursor.getString(10)
        )
    }
}

private fun normalizedVi(value: String): String = Normalizer.normalize(
    value.lowercase(Locale("vi", "VN")), Normalizer.Form.NFD
).replace("\\p{M}+".toRegex(), "").replace('đ', 'd').trim()

private fun categoryIcon(categoryId: String): NativeIcon = when (categoryId) {
    "vegetables", "roots" -> NativeIcon.LEAF
    "fruit-crops" -> NativeIcon.FRUIT
    "flowers" -> NativeIcon.FLOWER
    "timber-trees" -> NativeIcon.TREE
    "mushrooms" -> NativeIcon.MUSHROOM
    "freshwater-fish", "marine-life" -> NativeIcon.FISH
    "insects" -> NativeIcon.BUG
    "animals", "danger" -> NativeIcon.SHIELD
    "medicinal-plants" -> NativeIcon.MEDICINE
    else -> NativeIcon.GRID
}

private fun categoryLabel(categoryId: String): String = SurvivalLibraryCatalog.category(categoryId)?.label ?: categoryId

abstract class PublishedBaseActivity : NativeBaseActivity() {
    protected fun publishedRecordCard(record: PublishedRecord, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(11), dp(10))
        background = roundedSolid(paper, 18)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }

        addView(mediaOrIcon(record, dp(84)), LinearLayout.LayoutParams(dp(84), dp(84)))

        val words = LinearLayout(this@PublishedBaseActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(5), 0)
        }
        words.addView(text(record.vietnameseName, 15.5f, forest, true))
        words.addView(text(categoryLabel(record.categoryId), 10.5f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
        words.addView(text(record.usageLevel.label, 10.5f, forest2, true).apply { setPadding(0, dp(4), 0, 0) })
        if (record.summary.isNotBlank()) {
            words.addView(text(record.summary, 10.2f, muted, false).apply { setPadding(0, dp(4), 0, 0); maxLines = 2 })
        }
        addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(NativeIconView(this@PublishedBaseActivity, NativeIcon.CHEVRON_RIGHT, forest2), LinearLayout.LayoutParams(dp(22), dp(22)))
    }

    protected fun mediaOrIcon(record: PublishedRecord, size: Int): View {
        val path = record.localMediaPath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.isFile) {
                val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                if (bitmap != null) {
                    return ImageView(this).apply {
                        setImageBitmap(bitmap)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = roundedSolid(sage, 16)
                        clipToOutline = true
                    }
                }
            }
        }
        return FrameLayout(this).apply {
            background = roundedSolid(sage, 16)
            addView(
                NativeIconView(this@PublishedBaseActivity, categoryIcon(record.categoryId), forest2),
                FrameLayout.LayoutParams((size * 0.52).toInt(), (size * 0.52).toInt(), Gravity.CENTER)
            )
        }
    }

    protected fun openPublishedRecord(record: PublishedRecord) {
        startActivity(Intent(this, PublishedRecordDetailActivity::class.java).putExtra(PublishedRecordDetailActivity.EXTRA_RECORD_ID, record.id))
    }
}

/** Launcher: renders immediately, then reads the local DB on a worker thread. */
class PublishedHomeActivity : PublishedBaseActivity() {
    private lateinit var statusText: TextView
    private lateinit var recentHolder: LinearLayout
    private lateinit var categoryHolder: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        val (scroll, content) = scrollContent()
        content.addView(homeHeader())
        content.addView(space(12))
        content.addView(homeSearch())
        content.addView(space(18))
        content.addView(sectionTitle("Tìm theo nhu cầu", "Chọn nhanh theo tình huống thực tế"))
        content.addView(space(10))
        content.addView(needGrid())
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
        content.addView(space(16))
        content.addView(primaryButton("Nhận dạng nhanh bằng camera", NativeIcon.CAMERA) {
            startActivity(Intent(this, NativeCameraActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        content.addView(space(10))
        content.addView(secondaryButton("Xem tiến độ thư viện", NativeIcon.REFRESH) {
            startActivity(Intent(this, LibraryProgressActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        content.addView(space(14))
        content.addView(notice("Dữ liệu PUBLISHED được đọc từ database cục bộ. Nếu chưa có gói dữ liệu thật, ứng dụng để trống thay vì đưa hồ sơ DEMO vào kết quả."))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)))
        applyNativeRoot(root)
        renderCategories(emptyMap())
        loadSnapshot()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) loadSnapshot()
    }

    private fun loadSnapshot() {
        Thread {
            val snapshot = PublishedLibraryRepository.snapshot(applicationContext)
            runOnUiThread {
                statusText.text = "${snapshot.publishedCount} hồ sơ đã phát hành · ${snapshot.verifiedCount} hồ sơ đã kiểm chứng trên thiết bị"
                recentHolder.removeAllViews()
                if (snapshot.recentRecords.isEmpty()) {
                    recentHolder.addView(notice("Chưa có hồ sơ PUBLISHED trên thiết bị. Khi pipeline phát hành gói hợp lệ và thiết bị đồng bộ, hồ sơ mới sẽ xuất hiện ngay tại đây."))
                } else {
                    snapshot.recentRecords.take(6).forEachIndexed { index, record ->
                        recentHolder.addView(publishedRecordCard(record) { openPublishedRecord(record) })
                        if (index != minOf(snapshot.recentRecords.size, 6) - 1) recentHolder.addView(space(8))
                    }
                }
                renderCategories(snapshot.categoryCounts)
            }
        }.start()
    }

    private fun homeHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@PublishedHomeActivity).apply {
            setImageResource(R.drawable.ic_app_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = roundedSolid(paper, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = LinearLayout(this@PublishedHomeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        titles.addView(text("THƯ VIỆN SINH TỒN", 19f, forest, true))
        titles.addView(text("Dữ liệu Việt Nam · tiếng Việt trước · an toàn trước", 10.5f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
        addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton(NativeIcon.SEARCH) { startActivity(Intent(this@PublishedHomeActivity, PublishedSearchActivity::class.java)) }, LinearLayout.LayoutParams(dp(46), dp(46)))
    }

    private fun homeSearch(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), 0, dp(10), 0)
        background = roundedSolid(paper, 18)
        isClickable = true
        setOnClickListener { startActivity(Intent(this@PublishedHomeActivity, PublishedSearchActivity::class.java)) }
        addView(NativeIconView(this@PublishedHomeActivity, NativeIcon.SEARCH, forest), LinearLayout.LayoutParams(dp(23), dp(23)))
        addView(text("Tìm tên tiếng Việt trong dữ liệu đã phát hành…", 12.2f, muted, false).apply { setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)) }

    private fun needGrid(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val r1 = LinearLayout(this@PublishedHomeActivity).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(needCard(NativeIcon.WATER, "Uống", emptySet()), LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginEnd = dp(5) })
        r1.addView(needCard(NativeIcon.FOOD, "Ăn", setOf("vegetables", "roots", "fruit-crops", "mushrooms", "freshwater-fish", "marine-life")), LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginStart = dp(5) })
        addView(r1)
        addView(space(9))
        val r2 = LinearLayout(this@PublishedHomeActivity).apply { orientation = LinearLayout.HORIZONTAL }
        r2.addView(needCard(NativeIcon.SHELTER, "Ở", emptySet()), LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginEnd = dp(5) })
        r2.addView(needCard(NativeIcon.SHIELD, "Tránh nguy hiểm", setOf("danger", "animals", "insects", "mushrooms")), LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginStart = dp(5) })
        addView(r2)
    }

    private fun needCard(icon: NativeIcon, label: String, categoryIds: Set<String>): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = roundedSolid(sage, 18)
        isClickable = true
        setOnClickListener {
            startActivity(Intent(this@PublishedHomeActivity, PublishedCategoryActivity::class.java).apply {
                putExtra(PublishedCategoryActivity.EXTRA_TITLE, label)
                putExtra(PublishedCategoryActivity.EXTRA_CATEGORY_IDS, categoryIds.toTypedArray())
            })
        }
        addView(NativeIconView(this@PublishedHomeActivity, icon, forest), LinearLayout.LayoutParams(dp(26), dp(26)))
        addView(space(6))
        addView(text(label, 16.5f, forest, true))
    }

    private fun renderCategories(counts: Map<String, Int>) {
        categoryHolder.removeAllViews()
        SurvivalLibraryCatalog.categories.forEachIndexed { index, category ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(11), dp(12))
                background = roundedSolid(paper, 17)
                isClickable = true
                setOnClickListener {
                    startActivity(Intent(this@PublishedHomeActivity, PublishedCategoryActivity::class.java).apply {
                        putExtra(PublishedCategoryActivity.EXTRA_TITLE, category.label)
                        putExtra(PublishedCategoryActivity.EXTRA_CATEGORY_IDS, arrayOf(category.id))
                    })
                }
                addView(NativeIconView(this@PublishedHomeActivity, categoryIcon(category.id), forest2), LinearLayout.LayoutParams(dp(27), dp(27)))
                val words = LinearLayout(this@PublishedHomeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0) }
                words.addView(text(category.label, 14.5f, forest, true))
                words.addView(text("${counts[category.id] ?: 0} hồ sơ đã phát hành", 10.2f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
                addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(NativeIconView(this@PublishedHomeActivity, NativeIcon.CHEVRON_RIGHT, muted), LinearLayout.LayoutParams(dp(22), dp(22)))
            }
            categoryHolder.addView(row)
            if (index != SurvivalLibraryCatalog.categories.lastIndex) categoryHolder.addView(space(8))
        }
    }

    private fun bottomNav(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(paper)
        addView(navItem(NativeIcon.HOME, "Trang chủ") {}, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(navItem(NativeIcon.GRID, "Danh mục") { startActivity(Intent(this@PublishedHomeActivity, PublishedCategoryActivity::class.java)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(navItem(NativeIcon.CAMERA, "Quét ảnh") { startActivity(Intent(this@PublishedHomeActivity, NativeCameraActivity::class.java)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(navItem(NativeIcon.BOOKMARK, "Đã lưu") { startActivity(Intent(this@PublishedHomeActivity, PublishedSavedActivity::class.java)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(navItem(NativeIcon.REFRESH, "Cập nhật") { startActivity(Intent(this@PublishedHomeActivity, NativeUpdateActivity::class.java)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun navItem(icon: NativeIcon, label: String, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        setOnClickListener { click() }
        addView(NativeIconView(this@PublishedHomeActivity, icon, forest2), LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(text(label, 9f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
    }
}

class PublishedSearchActivity : PublishedBaseActivity() {
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private var token = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Tìm kiếm"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Tìm trong thư viện", "Chỉ tìm hồ sơ PUBLISHED đã cài trên thiết bị"))
        content.addView(space(12))
        val input = EditText(this).apply {
            hint = "Ví dụ: rau muống, nghệ, cá rô…"
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedSolid(paper, 18)
        }
        content.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        content.addView(space(10))
        status = text("", 10.5f, muted, false)
        content.addView(status)
        content.addView(space(10))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = load(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        load("")
    }

    private fun load(query: String) {
        val current = ++token
        status.text = "Đang đọc dữ liệu…"
        Thread {
            val rows = PublishedLibraryRepository.records(applicationContext, query = query, limit = 300)
            runOnUiThread {
                if (current != token) return@runOnUiThread
                results.removeAllViews()
                status.text = "${rows.size} hồ sơ phù hợp"
                if (rows.isEmpty()) {
                    results.addView(notice("Không có hồ sơ PUBLISHED phù hợp trên thiết bị. Kết quả DEMO không được trộn vào tìm kiếm thật."))
                } else {
                    rows.forEachIndexed { index, record ->
                        results.addView(publishedRecordCard(record) { openPublishedRecord(record) })
                        if (index != rows.lastIndex) results.addView(space(8))
                    }
                }
            }
        }.start()
    }
}

class PublishedCategoryActivity : PublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Danh mục"
        val ids = intent.getStringArrayExtra(EXTRA_CATEGORY_IDS)?.toSet().orEmpty()
        val root = baseRoot()
        root.addView(topBar(title))
        val (scroll, content) = scrollContent()
        if (ids.isEmpty() && title == "Danh mục") {
            content.addView(sectionTitle("Danh mục thư viện", "Mỗi số lượng là hồ sơ PUBLISHED thực tế trên thiết bị"))
            content.addView(space(10))
            holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(holder)
            root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            applyNativeRoot(root)
            loadCategoryIndex()
            return
        }
        content.addView(sectionTitle(title, "Dữ liệu hiển thị tăng dần theo các gói đã phát hành"))
        content.addView(space(8))
        status = text("Đang đọc dữ liệu…", 10.5f, muted, false)
        content.addView(status)
        content.addView(space(10))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
        loadRecords(ids)
    }

    private fun loadCategoryIndex() {
        Thread {
            val snapshot = PublishedLibraryRepository.snapshot(applicationContext, 1)
            runOnUiThread {
                holder.removeAllViews()
                SurvivalLibraryCatalog.categories.forEachIndexed { index, category ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(13), dp(12), dp(11), dp(12))
                        background = roundedSolid(paper, 17)
                        isClickable = true
                        setOnClickListener {
                            startActivity(Intent(this@PublishedCategoryActivity, PublishedCategoryActivity::class.java).apply {
                                putExtra(EXTRA_TITLE, category.label)
                                putExtra(EXTRA_CATEGORY_IDS, arrayOf(category.id))
                            })
                        }
                        addView(NativeIconView(this@PublishedCategoryActivity, categoryIcon(category.id), forest2), LinearLayout.LayoutParams(dp(27), dp(27)))
                        val words = LinearLayout(this@PublishedCategoryActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0) }
                        words.addView(text(category.label, 14.5f, forest, true))
                        words.addView(text("${snapshot.categoryCounts[category.id] ?: 0} hồ sơ đã phát hành", 10.2f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
                        addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(NativeIconView(this@PublishedCategoryActivity, NativeIcon.CHEVRON_RIGHT, muted), LinearLayout.LayoutParams(dp(22), dp(22)))
                    }
                    holder.addView(row)
                    if (index != SurvivalLibraryCatalog.categories.lastIndex) holder.addView(space(8))
                }
                holder.addView(space(12))
                holder.addView(secondaryButton("Xem tiến độ thư viện", NativeIcon.REFRESH) { startActivity(Intent(this, LibraryProgressActivity::class.java)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
            }
        }.start()
    }

    private fun loadRecords(categoryIds: Set<String>) {
        Thread {
            val rows = if (categoryIds.isEmpty()) emptyList() else categoryIds.flatMap { PublishedLibraryRepository.records(applicationContext, categoryId = it, limit = 500) }
                .distinctBy { it.id }
                .sortedWith(compareBy<PublishedRecord> { it.usageLevel.rank }.thenBy { normalizedVi(it.vietnameseName) })
            runOnUiThread {
                status.text = "${rows.size} hồ sơ đã phát hành"
                holder.removeAllViews()
                if (rows.isEmpty()) {
                    holder.addView(notice("Chưa có hồ sơ PUBLISHED cho mục này trên thiết bị. Ứng dụng không dùng dữ liệu DEMO để lấp khoảng trống."))
                } else {
                    rows.forEachIndexed { index, record ->
                        holder.addView(publishedRecordCard(record) { openPublishedRecord(record) })
                        if (index != rows.lastIndex) holder.addView(space(8))
                    }
                }
            }
        }.start()
    }

    companion object {
        const val EXTRA_TITLE = "published_title"
        const val EXTRA_CATEGORY_IDS = "published_category_ids"
    }
}

class PublishedSavedActivity : PublishedBaseActivity() {
    private lateinit var holder: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Đã lưu"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Hồ sơ đã lưu", "Chỉ hồ sơ PUBLISHED được lưu trong database"))
        content.addView(space(12))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
    }

    override fun onResume() {
        super.onResume()
        if (!::holder.isInitialized) return
        Thread {
            val rows = PublishedLibraryRepository.favoriteRecords(applicationContext)
            runOnUiThread {
                holder.removeAllViews()
                if (rows.isEmpty()) holder.addView(notice("Chưa có hồ sơ PUBLISHED nào được lưu."))
                else rows.forEachIndexed { index, record ->
                    holder.addView(publishedRecordCard(record) { openPublishedRecord(record) })
                    if (index != rows.lastIndex) holder.addView(space(8))
                }
            }
        }.start()
    }
}

class PublishedRecordDetailActivity : PublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private var recordId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordId = intent.getStringExtra(EXTRA_RECORD_ID).orEmpty()
        val root = baseRoot()
        root.addView(topBar("Hồ sơ"))
        val (scroll, content) = scrollContent()
        holder = content
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
        load()
    }

    private fun load() {
        holder.removeAllViews()
        holder.addView(text("Đang đọc hồ sơ…", 12f, muted, false))
        Thread {
            val record = PublishedLibraryRepository.byId(applicationContext, recordId)
            val favorite = if (record != null) PublishedLibraryRepository.isFavorite(applicationContext, record.id) else false
            runOnUiThread {
                holder.removeAllViews()
                if (record == null) {
                    holder.addView(notice("Không tìm thấy hồ sơ PUBLISHED này trong database cục bộ."))
                    return@runOnUiThread
                }
                holder.addView(mediaOrIcon(record, dp(220)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
                holder.addView(space(14))
                holder.addView(text(record.vietnameseName, 25f, forest, true))
                holder.addView(text(categoryLabel(record.categoryId), 11f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
                holder.addView(space(8))
                val tags = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                tags.addView(chip(record.usageLevel.label, sage, forest))
                if (record.highRisk) tags.addView(chip("NGUY CƠ CAO", warning, Color.rgb(112, 72, 12)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
                holder.addView(tags)
                holder.addView(space(16))
                holder.addView(sectionTitle("Thông tin đã phát hành", "Không hiển thị trường chưa có trong gói dữ liệu"))
                holder.addView(space(8))
                holder.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(13), dp(13), dp(13))
                    background = roundedSolid(paper, 17)
                    addView(text(if (record.summary.isBlank()) "Hồ sơ hiện chưa có phần mô tả chi tiết trong schema dữ liệu v1." else record.summary, 12.3f, ink, false))
                    addView(text("Nguồn kiểm chứng: ${record.sourceCount}", 10.5f, muted, false).apply { setPadding(0, dp(9), 0, 0) })
                    addView(text("Trạng thái: ${record.verificationState.label}", 10.5f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
                    addView(text("Gói: ${record.packageId}", 10.5f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
                })
                holder.addView(space(14))
                if (record.highRisk) holder.addView(notice("Đây là hồ sơ nguy cơ cao. Nhận dạng hình ảnh và quyết định ăn/dùng/điều trị là các lớp riêng; không suy ra độ an toàn chỉ từ tên loài."))
                holder.addView(space(12))
                holder.addView(primaryButton(if (favorite) "Bỏ lưu hồ sơ" else "Lưu hồ sơ", NativeIcon.BOOKMARK) {
                    Thread {
                        val now = !PublishedLibraryRepository.isFavorite(applicationContext, record.id)
                        PublishedLibraryRepository.setFavorite(applicationContext, record.id, now)
                        runOnUiThread { load() }
                    }.start()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                holder.addView(space(10))
                holder.addView(secondaryButton("Quét ảnh để so sánh", NativeIcon.CAMERA) { startActivity(Intent(this, NativeCameraActivity::class.java)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
            }
        }.start()
    }

    companion object { const val EXTRA_RECORD_ID = "published_record_id" }
}

class LibraryProgressActivity : PublishedBaseActivity() {
    private lateinit var holder: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Tiến độ thư viện"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Bảng tiến độ trên thiết bị", "Phản ánh dữ liệu đã cài; không bịa số liệu collector chưa đồng bộ"))
        content.addView(space(12))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
        load()
    }

    private fun load() {
        Thread {
            val snapshot = PublishedLibraryRepository.snapshot(applicationContext, 1)
            runOnUiThread {
                holder.removeAllViews()
                holder.addView(metricCard("Đã phát hành", snapshot.publishedCount.toString(), "Hồ sơ PUBLISHED hiện có trong database"))
                holder.addView(space(8))
                holder.addView(metricCard("Đã kiểm chứng", snapshot.verifiedCount.toString(), "Hồ sơ có trạng thái kiểm chứng trở lên"))
                holder.addView(space(16))
                holder.addView(sectionTitle("Theo danh mục", "Số lượng tăng ngay sau khi gói dữ liệu hợp lệ được cài"))
                holder.addView(space(9))
                SurvivalLibraryCatalog.categories.forEach { category ->
                    holder.addView(metricCard(category.label, (snapshot.categoryCounts[category.id] ?: 0).toString(), category.subtitle))
                    holder.addView(space(7))
                }
                holder.addView(space(10))
                holder.addView(sectionTitle("Gói dữ liệu", "Phiên bản và số hồ sơ đã cài"))
                holder.addView(space(9))
                if (snapshot.installedPackages.isEmpty()) {
                    holder.addView(notice("Chưa có gói dữ liệu thật nào được cài. Update index hiện có thể vẫn chưa phát hành package."))
                } else {
                    snapshot.installedPackages.forEach { pkg ->
                        holder.addView(metricCard(pkg.packageId, "v${pkg.version}", "${pkg.recordCount} hồ sơ · ${pkg.verifiedCount} kiểm chứng · ${pkg.status.name}"))
                        holder.addView(space(7))
                    }
                }
                holder.addView(space(10))
                holder.addView(notice("Các cột DISCOVERED / NORMALIZED / MATCHED / NEEDS_REVIEW thuộc pipeline thu thập phía nguồn. Schema đồng bộ tiến độ từ pipeline sẽ được nối ở bước tiếp theo; màn hình này chỉ hiển thị số đã có bằng chứng cục bộ."))
                holder.addView(space(10))
                holder.addView(primaryButton("Kiểm tra cập nhật thư viện", NativeIcon.REFRESH) { startActivity(Intent(this, NativeUpdateActivity::class.java)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
            }
        }.start()
    }

    private fun metricCard(title: String, value: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = roundedSolid(paper, 16)
        val words = LinearLayout(this@LibraryProgressActivity).apply { orientation = LinearLayout.VERTICAL }
        words.addView(text(title, 13.5f, forest, true))
        words.addView(text(subtitle, 10f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(value, 18f, forest2, true).apply { gravity = Gravity.END })
    }
}
