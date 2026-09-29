package vn.survivallibrary.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** Hard UI bounds so a large library never creates thousands of Views at once. */
object PagingPolicy {
    const val PAGE_SIZE = 40
    const val MAX_PAGE_SIZE = 80
}

/**
 * Bounded, sampled and asynchronous thumbnail loader.
 * Full-resolution images are never decoded directly into list rows.
 */
object PublishedThumbnailLoader {
    private const val CACHE_KB = 12 * 1024
    private val executor = Executors.newFixedThreadPool(2)
    private val cache = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            maxOf(1, value.allocationByteCount / 1024)
    }

    fun load(target: ImageView, path: String?, targetPx: Int): Boolean {
        if (path.isNullOrBlank() || targetPx <= 0) return false
        val file = File(path)
        if (!file.isFile) return false

        val key = "${file.absolutePath}@${targetPx}"
        target.tag = key
        cache.get(key)?.let {
            target.setImageBitmap(it)
            return true
        }

        val weakTarget = WeakReference(target)
        executor.execute {
            val bitmap = decodeSampled(file, targetPx) ?: return@execute
            cache.put(key, bitmap)
            weakTarget.get()?.post {
                val image = weakTarget.get() ?: return@post
                if (image.tag == key) image.setImageBitmap(bitmap)
            }
        }
        return true
    }

    private fun decodeSampled(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx &&
            bounds.outHeight / (sample * 2) >= targetPx
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
    }
}

/** Query adapter with SQL LIMIT/OFFSET paging. */
object PagedPublishedRepository {
    fun records(
        context: Context,
        categoryIds: Set<String> = emptySet(),
        query: String? = null,
        limit: Int = PagingPolicy.PAGE_SIZE,
        offset: Int = 0
    ): List<PublishedRecord> {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            val where = mutableListOf("r.published=1")
            val args = mutableListOf<String>()

            if (categoryIds.isNotEmpty()) {
                where += "r.category_id IN (${categoryIds.joinToString(",") { "?" }})"
                args.addAll(categoryIds)
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
                       (SELECT local_path FROM record_media m
                        WHERE m.record_id=r.id AND m.verified=1 AND m.local_path<>'' LIMIT 1)
                FROM library_records r
                WHERE ${where.joinToString(" AND ")}
                ORDER BY CASE r.usage_level
                    WHEN 'THUONG_DUNG' THEN 0
                    WHEN 'HAY_DUNG' THEN 1
                    WHEN 'IT_DUNG' THEN 2
                    WHEN 'HIEM_DUNG' THEN 3
                    WHEN 'KHONG_CO_KHA_NANG_DUNG' THEN 4
                    ELSE 5 END,
                    r.vietnamese_name COLLATE NOCASE ASC,
                    r.id ASC
                LIMIT ? OFFSET ?
            """.trimIndent()
            args += limit.coerceIn(1, PagingPolicy.MAX_PAGE_SIZE).toString()
            args += offset.coerceAtLeast(0).toString()

            db.readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
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

    fun favorites(
        context: Context,
        limit: Int = PagingPolicy.PAGE_SIZE,
        offset: Int = 0
    ): List<PublishedRecord> {
        val db = OfflineLibraryDb(context.applicationContext)
        return try {
            val sql = """
                SELECT r.id,r.package_id,r.vietnamese_name,r.category_id,r.usage_level,r.verification_state,
                       r.summary,r.high_risk,r.source_count,r.updated_at,
                       (SELECT local_path FROM record_media m
                        WHERE m.record_id=r.id AND m.verified=1 AND m.local_path<>'' LIMIT 1)
                FROM library_records r
                INNER JOIN favorites f ON f.record_id=r.id
                WHERE r.published=1
                ORDER BY f.saved_at DESC, r.id ASC
                LIMIT ? OFFSET ?
            """.trimIndent()
            db.readableDatabase.rawQuery(
                sql,
                arrayOf(
                    limit.coerceIn(1, PagingPolicy.MAX_PAGE_SIZE).toString(),
                    offset.coerceAtLeast(0).toString()
                )
            ).use { cursor ->
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

    private fun cursorToRecord(cursor: android.database.Cursor): PublishedRecord {
        val usage = runCatching { UsageLevel.valueOf(cursor.getString(4)) }
            .getOrDefault(UsageLevel.CHUA_PHAN_LOAI)
        val verification = runCatching { VerificationState.valueOf(cursor.getString(5)) }
            .getOrDefault(VerificationState.CHUA_CO)
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

private fun optimizedCategoryIcon(categoryId: String): NativeIcon = when (categoryId) {
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

private fun optimizedCategoryLabel(categoryId: String): String =
    SurvivalLibraryCatalog.category(categoryId)?.label ?: categoryId

/** Shared optimized card rendering for large-library screens. */
abstract class OptimizedPublishedBaseActivity : NativeBaseActivity() {
    protected fun publishedRecordCard(record: PublishedRecord, click: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(11), dp(10))
            background = roundedSolid(paper, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }

            addView(safeMedia(record, dp(84)), LinearLayout.LayoutParams(dp(84), dp(84)))

            val words = LinearLayout(this@OptimizedPublishedBaseActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(5), 0)
            }
            words.addView(text(record.vietnameseName, 15.5f, forest, true))
            words.addView(text(optimizedCategoryLabel(record.categoryId), 10.5f, muted, false).apply {
                setPadding(0, dp(4), 0, 0)
            })
            words.addView(text(record.usageLevel.label, 10.5f, forest2, true).apply {
                setPadding(0, dp(4), 0, 0)
            })
            if (record.summary.isNotBlank()) {
                words.addView(text(record.summary, 10.2f, muted, false).apply {
                    setPadding(0, dp(4), 0, 0)
                    maxLines = 2
                })
            }
            addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                NativeIconView(this@OptimizedPublishedBaseActivity, NativeIcon.CHEVRON_RIGHT, forest2),
                LinearLayout.LayoutParams(dp(22), dp(22))
            )
        }

    protected fun safeMedia(record: PublishedRecord, sizePx: Int): View = FrameLayout(this).apply {
        background = roundedSolid(sage, 16)
        addView(
            NativeIconView(this@OptimizedPublishedBaseActivity, optimizedCategoryIcon(record.categoryId), forest2),
            FrameLayout.LayoutParams((sizePx * 0.5).toInt(), (sizePx * 0.5).toInt(), Gravity.CENTER)
        )
        val image = ImageView(this@OptimizedPublishedBaseActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        PublishedThumbnailLoader.load(image, record.localMediaPath, maxOf(sizePx, dp(96)))
    }

    protected fun openPublishedRecord(record: PublishedRecord) {
        startActivity(
            Intent(this, OptimizedRecordDetailActivity::class.java)
                .putExtra(OptimizedRecordDetailActivity.EXTRA_RECORD_ID, record.id)
        )
    }
}

class PagedPublishedSearchActivity : OptimizedPublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var status: TextView
    private lateinit var loadMore: View
    private var queryText = ""
    private var offset = 0
    private var generation = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Tìm kiếm"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Tìm trong thư viện", "Tải theo trang để thư viện lớn vẫn mượt"))
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
        content.addView(space(9))
        status = text("", 10.5f, muted, false)
        content.addView(status)
        content.addView(space(9))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)
        content.addView(space(10))
        loadMore = secondaryButton("Xem thêm ${PagingPolicy.PAGE_SIZE} hồ sơ", NativeIcon.CHEVRON_RIGHT) {
            loadNext()
        }
        content.addView(loadMore, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val requested = s?.toString().orEmpty()
                val debounceToken = ++generation
                input.postDelayed({
                    if (debounceToken == generation) resetAndLoad(requested)
                }, 250L)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        resetAndLoad("")
    }

    private fun resetAndLoad(query: String) {
        generation++
        queryText = query
        offset = 0
        loading = false
        holder.removeAllViews()
        loadNext()
    }

    private fun loadNext() {
        if (loading) return
        loading = true
        val token = generation
        status.text = if (offset == 0) "Đang đọc dữ liệu…" else "Đang tải thêm…"
        loadMore.visibility = View.GONE

        Thread {
            val rows = PagedPublishedRepository.records(
                applicationContext,
                query = queryText,
                limit = PagingPolicy.PAGE_SIZE,
                offset = offset
            )
            runOnUiThread {
                if (token != generation || isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                if (rows.isEmpty() && offset == 0) {
                    holder.addView(notice("Không có hồ sơ PUBLISHED phù hợp trên thiết bị."))
                } else {
                    rows.forEachIndexed { index, record ->
                        holder.addView(publishedRecordCard(record) { openPublishedRecord(record) })
                        if (index != rows.lastIndex) holder.addView(space(8))
                    }
                    offset += rows.size
                }
                status.text = "Đang hiển thị $offset hồ sơ"
                loadMore.visibility = if (rows.size == PagingPolicy.PAGE_SIZE) View.VISIBLE else View.GONE
            }
        }.start()
    }
}

class PagedPublishedCategoryActivity : OptimizedPublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var status: TextView
    private lateinit var loadMore: View
    private var categoryIds: Set<String> = emptySet()
    private var offset = 0
    private var loading = false
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Danh mục"
        categoryIds = intent.getStringArrayExtra(EXTRA_CATEGORY_IDS)?.toSet().orEmpty()
        val root = baseRoot()
        root.addView(topBar(title))
        val (scroll, content) = scrollContent()

        if (categoryIds.isEmpty() && title == "Danh mục") {
            content.addView(sectionTitle("Danh mục thư viện", "Số lượng là hồ sơ PUBLISHED thực tế"))
            content.addView(space(10))
            holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(holder)
            root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            applyNativeRoot(root)
            loadCategoryIndex()
            return
        }

        content.addView(sectionTitle(title, "Mỗi lần chỉ dựng tối đa ${PagingPolicy.PAGE_SIZE} hồ sơ"))
        content.addView(space(8))
        status = text("", 10.5f, muted, false)
        content.addView(status)
        content.addView(space(9))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)
        content.addView(space(10))
        loadMore = secondaryButton("Xem thêm ${PagingPolicy.PAGE_SIZE} hồ sơ", NativeIcon.CHEVRON_RIGHT) {
            loadNext()
        }
        content.addView(loadMore, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)

        if (categoryIds.isEmpty()) {
            status.text = "0 hồ sơ"
            holder.addView(notice("Mục này chưa được ánh xạ tới một nhóm dữ liệu PUBLISHED cụ thể."))
            loadMore.visibility = View.GONE
        } else {
            loadNext()
        }
    }

    private fun loadCategoryIndex() {
        Thread {
            val snapshot = PublishedLibraryRepository.snapshot(applicationContext, 1)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                holder.removeAllViews()
                SurvivalLibraryCatalog.categories.forEachIndexed { index, category ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(13), dp(12), dp(11), dp(12))
                        background = roundedSolid(paper, 17)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            startActivity(Intent(this@PagedPublishedCategoryActivity, PagedPublishedCategoryActivity::class.java).apply {
                                putExtra(EXTRA_TITLE, category.label)
                                putExtra(EXTRA_CATEGORY_IDS, arrayOf(category.id))
                            })
                        }
                        addView(
                            NativeIconView(this@PagedPublishedCategoryActivity, optimizedCategoryIcon(category.id), forest2),
                            LinearLayout.LayoutParams(dp(27), dp(27))
                        )
                        val words = LinearLayout(this@PagedPublishedCategoryActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(11), 0, 0, 0)
                        }
                        words.addView(text(category.label, 14.5f, forest, true))
                        words.addView(text("${snapshot.categoryCounts[category.id] ?: 0} hồ sơ đã phát hành", 10.2f, muted, false).apply {
                            setPadding(0, dp(3), 0, 0)
                        })
                        addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(
                            NativeIconView(this@PagedPublishedCategoryActivity, NativeIcon.CHEVRON_RIGHT, muted),
                            LinearLayout.LayoutParams(dp(22), dp(22))
                        )
                    }
                    holder.addView(row)
                    if (index != SurvivalLibraryCatalog.categories.lastIndex) holder.addView(space(8))
                }
                holder.addView(space(12))
                holder.addView(secondaryButton("Xem tiến độ thư viện", NativeIcon.REFRESH) {
                    startActivity(Intent(this, LibraryProgressActivity::class.java))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
            }
        }.start()
    }

    private fun loadNext() {
        if (loading) return
        loading = true
        val token = ++generation
        status.text = if (offset == 0) "Đang đọc dữ liệu…" else "Đang tải thêm…"
        loadMore.visibility = View.GONE
        Thread {
            val rows = PagedPublishedRepository.records(
                applicationContext,
                categoryIds = categoryIds,
                limit = PagingPolicy.PAGE_SIZE,
                offset = offset
            )
            runOnUiThread {
                if (token != generation || isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                if (rows.isEmpty() && offset == 0) {
                    holder.addView(notice("Chưa có hồ sơ PUBLISHED cho mục này trên thiết bị."))
                } else {
                    rows.forEachIndexed { index, record ->
                        holder.addView(publishedRecordCard(record) { openPublishedRecord(record) })
                        if (index != rows.lastIndex) holder.addView(space(8))
                    }
                    offset += rows.size
                }
                status.text = "Đang hiển thị $offset hồ sơ"
                loadMore.visibility = if (rows.size == PagingPolicy.PAGE_SIZE) View.VISIBLE else View.GONE
            }
        }.start()
    }

    companion object {
        const val EXTRA_TITLE = "paged_published_title"
        const val EXTRA_CATEGORY_IDS = "paged_published_category_ids"
    }
}

class PagedPublishedSavedActivity : OptimizedPublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var status: TextView
    private lateinit var loadMore: View
    private var offset = 0
    private var generation = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Đã lưu"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Hồ sơ đã lưu", "Danh sách phân trang để không giữ quá nhiều ảnh trong RAM"))
        content.addView(space(9))
        status = text("", 10.5f, muted, false)
        content.addView(status)
        content.addView(space(9))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)
        content.addView(space(10))
        loadMore = secondaryButton("Xem thêm ${PagingPolicy.PAGE_SIZE} hồ sơ", NativeIcon.CHEVRON_RIGHT) { loadNext() }
        content.addView(loadMore, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
        resetAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (::holder.isInitialized) resetAndLoad()
    }

    private fun resetAndLoad() {
        generation++
        offset = 0
        loading = false
        holder.removeAllViews()
        loadNext()
    }

    private fun loadNext() {
        if (loading) return
        loading = true
        val token = generation
        status.text = if (offset == 0) "Đang đọc dữ liệu…" else "Đang tải thêm…"
        loadMore.visibility = View.GONE
        Thread {
            val rows = PagedPublishedRepository.favorites(applicationContext, PagingPolicy.PAGE_SIZE, offset)
            runOnUiThread {
                if (token != generation || isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                if (rows.isEmpty() && offset == 0) holder.addView(notice("Chưa có hồ sơ PUBLISHED nào được lưu."))
                else {
                    rows.forEachIndexed { index, record ->
                        holder.addView(publishedRecordCard(record) { openPublishedRecord(record) })
                        if (index != rows.lastIndex) holder.addView(space(8))
                    }
                    offset += rows.size
                }
                status.text = "Đang hiển thị $offset hồ sơ"
                loadMore.visibility = if (rows.size == PagingPolicy.PAGE_SIZE) View.VISIBLE else View.GONE
            }
        }.start()
    }
}

class OptimizedRecordDetailActivity : OptimizedPublishedBaseActivity() {
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
            val favorite = record?.let { PublishedLibraryRepository.isFavorite(applicationContext, it.id) } ?: false
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                holder.removeAllViews()
                if (record == null) {
                    holder.addView(notice("Không tìm thấy hồ sơ PUBLISHED này trong database cục bộ."))
                    return@runOnUiThread
                }
                holder.addView(safeMedia(record, dp(220)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
                holder.addView(space(14))
                holder.addView(text(record.vietnameseName, 25f, forest, true))
                holder.addView(text(optimizedCategoryLabel(record.categoryId), 11f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
                holder.addView(space(8))
                val tags = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                tags.addView(chip(record.usageLevel.label, sage, forest))
                if (record.highRisk) {
                    tags.addView(chip("NGUY CƠ CAO", warning, Color.rgb(112, 72, 12)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(8)
                    })
                }
                holder.addView(tags)
                holder.addView(space(16))
                holder.addView(sectionTitle("Thông tin đã phát hành", "Chỉ hiển thị dữ liệu đã có trong gói cục bộ"))
                holder.addView(space(8))
                holder.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(13), dp(13), dp(13))
                    background = roundedSolid(paper, 17)
                    addView(text(if (record.summary.isBlank()) "Hồ sơ hiện chưa có phần mô tả chi tiết trong schema dữ liệu hiện tại." else record.summary, 12.3f, ink, false))
                    addView(text("Nguồn kiểm chứng: ${record.sourceCount}", 10.5f, muted, false).apply { setPadding(0, dp(9), 0, 0) })
                    addView(text("Trạng thái: ${record.verificationState.label}", 10.5f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
                    addView(text("Gói: ${record.packageId}", 10.5f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
                })
                if (record.highRisk) {
                    holder.addView(space(12))
                    holder.addView(notice("Đây là hồ sơ nguy cơ cao. Không suy ra độ an toàn ăn/dùng/điều trị chỉ từ nhận dạng hình ảnh."))
                }
                holder.addView(space(12))
                holder.addView(primaryButton(if (favorite) "Bỏ lưu hồ sơ" else "Lưu hồ sơ", NativeIcon.BOOKMARK) {
                    Thread {
                        val now = !PublishedLibraryRepository.isFavorite(applicationContext, record.id)
                        PublishedLibraryRepository.setFavorite(applicationContext, record.id, now)
                        runOnUiThread { if (!isFinishing && !isDestroyed) load() }
                    }.start()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                holder.addView(space(10))
                holder.addView(secondaryButton("Quét ảnh để so sánh", NativeIcon.CAMERA) {
                    startActivity(Intent(this, NativeCameraActivity::class.java))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
            }
        }.start()
    }

    companion object { const val EXTRA_RECORD_ID = "optimized_published_record_id" }
}
