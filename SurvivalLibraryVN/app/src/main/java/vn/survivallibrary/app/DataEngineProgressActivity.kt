package vn.survivallibrary.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Operational dashboard for Data Engine V2.
 *
 * Counts come only from local published DB + DataEngineStore staging/task/source tables.
 * Pipeline progress is mirrored through a SHA-256 verified transport; V2-G prefers
 * category shards and keeps the legacy full snapshot as a rollback path.
 * The mirror is never treated as PUBLISHED library content.
 */
class DataEngineProgressActivity : PublishedBaseActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var status: TextView
    private var loadGeneration = 0
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Tiến độ AI & dữ liệu"))
        val (scroll, content) = scrollContent()

        content.addView(sectionTitle("Data Engine V2", "AI điều phối task · Rule Engine quyết định phát hành"))
        content.addView(space(8))
        status = text("Đang đọc staging và task queue…", 11f, muted, false)
        content.addView(status)
        content.addView(space(10))
        content.addView(primaryButton("Đồng bộ tiến độ AI", NativeIcon.REFRESH) { syncProgress() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        content.addView(space(12))
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(holder)

        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
        load()
    }

    override fun onResume() {
        super.onResume()
        if (::holder.isInitialized) load()
    }

    private fun syncProgress() {
        if (syncing) return
        syncing = true
        status.text = "Đang kiểm tra các shard tiến độ có SHA-256…"
        Thread {
            val result = DataEngineIncrementalStagingSync.checkAndSync(applicationContext)
            runOnUiThread {
                syncing = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = result.message
                if (result.errors.isNotEmpty()) toast(result.errors.first())
                load()
            }
        }.start()
    }

    private fun load() {
        val generation = ++loadGeneration
        Thread {
            val engine = DataEngineProgressSqlRepository.snapshot(applicationContext)
            val published = PublishedLibraryRepository.snapshot(applicationContext, 1)
            runOnUiThread {
                if (generation != loadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                render(engine, published)
            }
        }.start()
    }

    private fun render(engine: DataEngineDashboardSnapshot, published: PublishedLibrarySnapshot) {
        holder.removeAllViews()
        if (!syncing) {
            status.text = buildString {
                append("${engine.stagedEntities} hồ sơ staging · ${published.publishedCount} hồ sơ PUBLISHED")
                if (engine.totalTrackedFields > 0) append(" · ${engine.completionPercent}% trường đã kiểm chứng")
            }
        }

        holder.addView(summaryGrid(engine, published))
        holder.addView(space(18))

        holder.addView(sectionTitle("Task queue", "Một task lỗi không làm dừng các task khác"))
        holder.addView(space(9))
        holder.addView(taskCard(engine))
        holder.addView(space(18))

        holder.addView(sectionTitle("Theo danh mục", "Số liệu thật từ staging; không dùng mục tiêu ước lượng"))
        holder.addView(space(9))
        engine.categories.forEachIndexed { index, category ->
            holder.addView(categoryCard(category))
            if (index != engine.categories.lastIndex) holder.addView(space(8))
        }

        holder.addView(space(18))
        holder.addView(sectionTitle("Hồ sơ cần xử lý", "Ưu tiên BLOCKED/RETRY rồi đến trường còn thiếu"))
        holder.addView(space(9))
        if (engine.entities.isEmpty()) {
            holder.addView(notice("Staging trên thiết bị chưa có hồ sơ. Bấm Đồng bộ tiến độ AI để lấy dữ liệu đã kiểm SHA-256 từ pipeline; nếu shard mới chưa dùng được thì app vẫn có thể quay về snapshot cũ an toàn."))
        } else {
            engine.entities.take(30).forEachIndexed { index, entity ->
                holder.addView(entityCard(entity))
                if (index != minOf(engine.entities.size, 30) - 1) holder.addView(space(8))
            }
            if (engine.stagedEntities > 30) {
                holder.addView(space(7))
                holder.addView(text("Đang hiển thị 30/${engine.stagedEntities} hồ sơ ưu tiên xử lý.", 10f, muted, false))
            }
        }

        holder.addView(space(18))
        holder.addView(sectionTitle("Tình trạng nguồn", "Cooldown nguồn lỗi thay vì làm dừng toàn pipeline"))
        holder.addView(space(9))
        if (engine.sourceHealth.isEmpty()) {
            holder.addView(notice("Chưa có sự kiện source-health trong staging trên thiết bị."))
        } else {
            engine.sourceHealth.forEachIndexed { index, source ->
                holder.addView(sourceCard(source))
                if (index != engine.sourceHealth.lastIndex) holder.addView(space(7))
            }
        }

        holder.addView(space(16))
        holder.addView(notice("Staging chỉ dùng cho tiến độ và chẩn đoán. Hồ sơ chỉ xuất hiện trong thư viện chính sau khi vượt LibraryRules và được cài như gói PUBLISHED đã kiểm tra SHA-256."))
    }

    private fun summaryGrid(engine: DataEngineDashboardSnapshot, published: PublishedLibrarySnapshot): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val first = LinearLayout(this@DataEngineProgressActivity).apply { orientation = LinearLayout.HORIZONTAL }
            first.addView(summaryCard("Staging", engine.stagedEntities.toString(), "hồ sơ đang xử lý"), weightParams(true))
            first.addView(summaryCard("Đã phát hành", published.publishedCount.toString(), "trong thư viện cục bộ"), weightParams(false))
            addView(first)
            addView(space(8))
            val second = LinearLayout(this@DataEngineProgressActivity).apply { orientation = LinearLayout.HORIZONTAL }
            second.addView(summaryCard("Trường có dữ liệu", engine.fieldsWithData.toString(), "đã thu được evidence/value"), weightParams(true))
            second.addView(summaryCard("Đã kiểm chứng", engine.verifiedFields.toString(), "trường vượt verification"), weightParams(false))
            addView(second)
        }

    private fun weightParams(left: Boolean) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        if (left) marginEnd = dp(4) else marginStart = dp(4)
    }

    private fun summaryCard(title: String, value: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(13), dp(13), dp(13))
        background = roundedSolid(paper, 17)
        addView(text(title, 11f, muted, true))
        addView(text(value, 24f, forest, true).apply { setPadding(0, dp(5), 0, 0) })
        addView(text(subtitle, 9.6f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun taskCard(engine: DataEngineDashboardSnapshot): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(13), dp(13), dp(13))
        background = roundedSolid(paper, 17)
        addView(taskRow("Chờ", engine.pendingTasks, forest))
        addView(space(5))
        addView(taskRow("Đang chạy", engine.runningTasks, forest2))
        addView(space(5))
        addView(taskRow("Retry", engine.retryTasks, Color.rgb(170, 101, 24)))
        addView(space(5))
        addView(taskRow("Blocked", engine.blockedTasks, Color.rgb(171, 58, 50)))
        addView(space(5))
        addView(taskRow("Hoàn tất", engine.completedTasks, Color.rgb(60, 122, 72)))
    }

    private fun taskRow(label: String, count: Int, color: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(label, 11.5f, muted, false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(count.toString(), 14f, color, true))
    }

    private fun categoryCard(category: DataEngineCategoryProgress): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = roundedSolid(paper, 17)

        val top = LinearLayout(this@DataEngineProgressActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val words = LinearLayout(this@DataEngineProgressActivity).apply { orientation = LinearLayout.VERTICAL }
        words.addView(text(category.label, 13.8f, forest, true))
        words.addView(text("${category.stagedEntities} staging · ${category.publishedEntities} staging đã publish", 9.8f, muted, false).apply {
            setPadding(0, dp(3), 0, 0)
        })
        top.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("${category.completionPercent}%", 16f, forest2, true))
        addView(top)
        addView(space(8))
        addView(progressBar(category.completionPercent))
        addView(text(
            "${category.verifiedFields}/${category.totalTrackedFields} trường kiểm chứng · ${category.fieldsWithData} trường có dữ liệu · retry ${category.retryTasks} · blocked ${category.blockedTasks}",
            9.6f,
            muted,
            false
        ).apply { setPadding(0, dp(7), 0, 0) })
    }

    private fun entityCard(entity: DataEngineEntityProgress): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = roundedSolid(paper, 17)
        val title = entity.vietnameseName.ifBlank { entity.scientificName }
        val top = LinearLayout(this@DataEngineProgressActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val words = LinearLayout(this@DataEngineProgressActivity).apply { orientation = LinearLayout.VERTICAL }
        words.addView(text(title, 13.5f, forest, true))
        if (entity.vietnameseName.isNotBlank()) {
            words.addView(text(entity.scientificName, 9.6f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
        }
        top.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (entity.blockedTasks > 0) top.addView(chip("BLOCKED ${entity.blockedTasks}", Color.rgb(250, 221, 216), Color.rgb(145, 46, 40)))
        else if (entity.retryTasks > 0) top.addView(chip("RETRY ${entity.retryTasks}", warning, Color.rgb(116, 77, 14)))
        else top.addView(chip("${entity.completionPercent}%", sage, forest))
        addView(top)
        addView(space(8))
        addView(progressBar(entity.completionPercent))
        val missing = entity.missingFields.take(4).joinToString(" · ") { fieldLabel(it) }
        addView(text(
            if (missing.isBlank()) "Không còn trường verification bị thiếu trong schema hiện tại." else "Còn thiếu: $missing${if (entity.missingFields.size > 4) "…" else ""}",
            9.8f,
            muted,
            false
        ).apply { setPadding(0, dp(7), 0, 0) })
    }

    private fun sourceCard(source: DataEngineSourceHealth): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(11), dp(13), dp(11))
        background = roundedSolid(paper, 16)
        val words = LinearLayout(this@DataEngineProgressActivity).apply { orientation = LinearLayout.VERTICAL }
        words.addView(text(source.sourceKey, 12.5f, forest, true))
        words.addView(text(
            "${source.tier.ifBlank { "CHƯA PHÂN TIER" }} · ${source.consecutiveFailures} lỗi liên tiếp${if (source.coolingDown()) " · đang cooldown" else ""}",
            9.7f,
            muted,
            false
        ).apply { setPadding(0, dp(3), 0, 0) })
        if (source.lastError.isNotBlank()) {
            words.addView(text(source.lastError, 9.2f, muted, false).apply { setPadding(0, dp(3), 0, 0); maxLines = 2 })
        }
        addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(chip(if (source.coolingDown()) "COOLDOWN" else "OK", if (source.coolingDown()) warning else sage, if (source.coolingDown()) Color.rgb(116, 77, 14) else forest))
    }

    private fun progressBar(value: Int): ProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progress = value.coerceIn(0, 100)
        isIndeterminate = false
        minimumHeight = dp(8)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
    }

    private fun fieldLabel(field: DataFieldKey): String = when (field) {
        DataFieldKey.CANONICAL_IDENTITY -> "định danh"
        DataFieldKey.VIETNAMESE_PRIMARY_NAME -> "tên Việt chính"
        DataFieldKey.VIETNAMESE_ALIASES -> "tên gọi khác"
        DataFieldKey.VIETNAM_DISTRIBUTION -> "phân bố VN"
        DataFieldKey.MEDIA_PRIMARY -> "ảnh chính"
        DataFieldKey.MEDIA_DIAGNOSTIC_SET -> "ảnh đa góc"
        DataFieldKey.IDENTIFICATION_TRAITS -> "nhận biết"
        DataFieldKey.CONFUSABLE_SPECIES -> "dễ nhầm"
        DataFieldKey.USAGE_LEVEL -> "mức sử dụng"
        DataFieldKey.USAGE_CONTENT -> "công dụng/cách dùng"
        DataFieldKey.SAFETY -> "an toàn"
    }
}
