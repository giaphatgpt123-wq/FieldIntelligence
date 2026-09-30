package vn.survivallibrary.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bounded list screens for large libraries.
 *
 * The older paged screens appended every page to one LinearLayout. That avoided a single huge
 * query but still accumulated Views indefinitely during a long browsing session. These screens
 * keep only one PAGE_SIZE window resident at a time and probe one extra row to know whether a
 * next page exists. This bounds View count and thumbnail targets without changing DB schema.
 */
private const val PAGE_PROBE_SIZE = PagingPolicy.PAGE_SIZE + 1

data class WindowPage(
    val rows: List<PublishedRecord>,
    val hasNext: Boolean,
    val offset: Int
) {
    val pageNumber: Int get() = offset / PagingPolicy.PAGE_SIZE + 1
    val fromRecord: Int get() = if (rows.isEmpty()) 0 else offset + 1
    val toRecord: Int get() = offset + rows.size
}

private fun windowCategoryIcon(categoryId: String): NativeIcon = when (categoryId) {
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

abstract class WindowedPublishedBaseActivity : OptimizedPublishedBaseActivity() {
    protected fun pagerRow(previous: () -> Unit, next: () -> Unit): Triple<LinearLayout, View, View> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val previousView = secondaryButton("Trang trước", NativeIcon.BACK) { previous() }
        val nextView = secondaryButton("Trang sau", NativeIcon.CHEVRON_RIGHT) { next() }
        row.addView(previousView, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(5) })
        row.addView(nextView, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(5) })
        return Triple(row, previousView, nextView)
    }

    protected fun renderPage(holder: LinearLayout, page: WindowPage, emptyMessage: String) {
        holder.removeAllViews()
        if (page.rows.isEmpty()) {
            holder.addView(notice(emptyMessage))
            return
        }
        page.rows.forEachIndexed { index, record ->
            holder.addView(publishedRecordCard(record) { openPublishedRecord(record) })
            if (index != page.rows.lastIndex) holder.addView(space(8))
        }
    }

    protected fun pageStatus(page: WindowPage): String =
        if (page.rows.isEmpty()) "0 hồ sơ" else "Trang ${page.pageNumber} · hồ sơ ${page.fromRecord}–${page.toRecord}"
}

class WindowedPublishedSearchActivity : WindowedPublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var status: TextView
    private lateinit var previousPage: View
    private lateinit var nextPage: View
    private var queryText = ""
    private var offset = 0
    private var generation = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Tìm kiếm"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Tìm trong thư viện", "Mỗi trang giữ tối đa ${PagingPolicy.PAGE_SIZE} hồ sơ trong giao diện"))
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
        val pager = pagerRow(
            previous = { if (!loading && offset > 0) { offset = (offset - PagingPolicy.PAGE_SIZE).coerceAtLeast(0); loadPage() } },
            next = { if (!loading) { offset += PagingPolicy.PAGE_SIZE; loadPage() } }
        )
        previousPage = pager.second
        nextPage = pager.third
        content.addView(pager.first)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val requested = s?.toString().orEmpty()
                val debounceToken = ++generation
                input.postDelayed({ if (debounceToken == generation) resetAndLoad(requested) }, 250L)
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
        loadPage()
    }

    private fun loadPage() {
        if (loading) return
        loading = true
        val token = generation
        val requestedOffset = offset
        status.text = "Đang đọc trang ${requestedOffset / PagingPolicy.PAGE_SIZE + 1}…"
        previousPage.visibility = View.GONE
        nextPage.visibility = View.GONE
        Thread {
            val probe = PagedPublishedRepository.records(
                applicationContext,
                query = queryText,
                limit = PAGE_PROBE_SIZE,
                offset = requestedOffset
            )
            val page = WindowPage(probe.take(PagingPolicy.PAGE_SIZE), probe.size > PagingPolicy.PAGE_SIZE, requestedOffset)
            runOnUiThread {
                if (token != generation || requestedOffset != offset || isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                renderPage(holder, page, "Không có hồ sơ PUBLISHED phù hợp trên thiết bị.")
                status.text = pageStatus(page)
                previousPage.visibility = if (requestedOffset > 0) View.VISIBLE else View.INVISIBLE
                nextPage.visibility = if (page.hasNext) View.VISIBLE else View.INVISIBLE
            }
        }.start()
    }
}

class WindowedPublishedCategoryActivity : WindowedPublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private var status: TextView? = null
    private var previousPage: View? = null
    private var nextPage: View? = null
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

        content.addView(sectionTitle(title, "Chỉ giữ một trang trong RAM để danh mục lớn vẫn ổn định"))
        content.addView(space(8))
        status = text("", 10.5f, muted, false)
        content.addView(status)
        content.addView(space(9))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)
        content.addView(space(10))
        val pager = pagerRow(
            previous = { if (!loading && offset > 0) { offset = (offset - PagingPolicy.PAGE_SIZE).coerceAtLeast(0); loadPage() } },
            next = { if (!loading) { offset += PagingPolicy.PAGE_SIZE; loadPage() } }
        )
        previousPage = pager.second
        nextPage = pager.third
        content.addView(pager.first)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)

        if (categoryIds.isEmpty()) {
            status?.text = "0 hồ sơ"
            holder.addView(notice("Mục này chưa được ánh xạ tới một nhóm dữ liệu PUBLISHED cụ thể."))
            previousPage?.visibility = View.INVISIBLE
            nextPage?.visibility = View.INVISIBLE
        } else loadPage()
    }

    private fun loadCategoryIndex() {
        val token = ++generation
        Thread {
            val snapshot = PublishedLibraryRepository.snapshot(applicationContext, 1)
            runOnUiThread {
                if (token != generation || isFinishing || isDestroyed) return@runOnUiThread
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
                            startActivity(Intent(this@WindowedPublishedCategoryActivity, WindowedPublishedCategoryActivity::class.java).apply {
                                putExtra(EXTRA_TITLE, category.label)
                                putExtra(EXTRA_CATEGORY_IDS, arrayOf(category.id))
                            })
                        }
                        addView(NativeIconView(this@WindowedPublishedCategoryActivity, windowCategoryIcon(category.id), forest2), LinearLayout.LayoutParams(dp(27), dp(27)))
                        val words = LinearLayout(this@WindowedPublishedCategoryActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(11), 0, 0, 0)
                        }
                        words.addView(text(category.label, 14.5f, forest, true))
                        words.addView(text("${snapshot.categoryCounts[category.id] ?: 0} hồ sơ đã phát hành", 10.2f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
                        addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(NativeIconView(this@WindowedPublishedCategoryActivity, NativeIcon.CHEVRON_RIGHT, muted), LinearLayout.LayoutParams(dp(22), dp(22)))
                    }
                    holder.addView(row)
                    if (index != SurvivalLibraryCatalog.categories.lastIndex) holder.addView(space(8))
                }
                holder.addView(space(12))
                holder.addView(secondaryButton("Xem tiến độ thư viện", NativeIcon.REFRESH) {
                    startActivity(Intent(this, DataEngineProgressActivity::class.java))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
            }
        }.start()
    }

    private fun loadPage() {
        if (loading) return
        loading = true
        val token = ++generation
        val requestedOffset = offset
        status?.text = "Đang đọc trang ${requestedOffset / PagingPolicy.PAGE_SIZE + 1}…"
        previousPage?.visibility = View.GONE
        nextPage?.visibility = View.GONE
        Thread {
            val probe = PagedPublishedRepository.records(
                applicationContext,
                categoryIds = categoryIds,
                limit = PAGE_PROBE_SIZE,
                offset = requestedOffset
            )
            val page = WindowPage(probe.take(PagingPolicy.PAGE_SIZE), probe.size > PagingPolicy.PAGE_SIZE, requestedOffset)
            runOnUiThread {
                if (token != generation || requestedOffset != offset || isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                renderPage(holder, page, "Chưa có hồ sơ PUBLISHED cho mục này trên thiết bị.")
                status?.text = pageStatus(page)
                previousPage?.visibility = if (requestedOffset > 0) View.VISIBLE else View.INVISIBLE
                nextPage?.visibility = if (page.hasNext) View.VISIBLE else View.INVISIBLE
            }
        }.start()
    }

    companion object {
        const val EXTRA_TITLE = "windowed_published_title"
        const val EXTRA_CATEGORY_IDS = "windowed_published_category_ids"
    }
}

class WindowedPublishedSavedActivity : WindowedPublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var status: TextView
    private lateinit var previousPage: View
    private lateinit var nextPage: View
    private var offset = 0
    private var generation = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Đã lưu"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Hồ sơ đã lưu", "Chỉ giữ một trang để không tích lũy View và ảnh trong RAM"))
        content.addView(space(9))
        status = text("", 10.5f, muted, false)
        content.addView(status)
        content.addView(space(9))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)
        content.addView(space(10))
        val pager = pagerRow(
            previous = { if (!loading && offset > 0) { offset = (offset - PagingPolicy.PAGE_SIZE).coerceAtLeast(0); loadPage() } },
            next = { if (!loading) { offset += PagingPolicy.PAGE_SIZE; loadPage() } }
        )
        previousPage = pager.second
        nextPage = pager.third
        content.addView(pager.first)
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
        loadPage()
    }

    private fun loadPage() {
        if (loading) return
        loading = true
        val token = generation
        val requestedOffset = offset
        status.text = "Đang đọc trang ${requestedOffset / PagingPolicy.PAGE_SIZE + 1}…"
        previousPage.visibility = View.GONE
        nextPage.visibility = View.GONE
        Thread {
            val probe = PagedPublishedRepository.favorites(applicationContext, PAGE_PROBE_SIZE, requestedOffset)
            val page = WindowPage(probe.take(PagingPolicy.PAGE_SIZE), probe.size > PagingPolicy.PAGE_SIZE, requestedOffset)
            runOnUiThread {
                if (token != generation || requestedOffset != offset || isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                renderPage(holder, page, "Chưa có hồ sơ PUBLISHED nào được lưu.")
                status.text = pageStatus(page)
                previousPage.visibility = if (requestedOffset > 0) View.VISIBLE else View.INVISIBLE
                nextPage.visibility = if (page.hasNext) View.VISIBLE else View.INVISIBLE
            }
        }.start()
    }
}
