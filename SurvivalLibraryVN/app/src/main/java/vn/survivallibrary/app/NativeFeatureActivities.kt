package vn.survivallibrary.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class NativeDemoRecord(
    val id: String,
    val title: String,
    val category: String,
    val usage: String,
    val imageRes: Int,
    val tags: Set<String>
)

object NativeDemoCatalog {
    val records = listOf(
        NativeDemoRecord("rau-muong", "Rau muống", "Cây cỏ, rau", "Thường dùng", R.drawable.rau_muong_demo, setOf("Ăn")),
        NativeDemoRecord("ca-rot", "Cà rốt", "Cây cỏ, rau", "Thường dùng", R.drawable.carrot_demo, setOf("Ăn")),
        NativeDemoRecord("la-thuc-vat", "Lá thực vật", "Cây cỏ, rau", "Nhận biết mẫu", R.drawable.leaf_demo, emptySet())
    )

    val categories = listOf(
        "Cây cỏ, rau", "Nấm", "Cá & thủy sản", "Côn trùng",
        "Hoa", "Cây gỗ", "Cây ăn quả", "Cây thuốc"
    )

    fun byId(id: String): NativeDemoRecord? = records.firstOrNull { it.id == id }
}

object NativeSavedStore {
    private const val PREF = "native_saved_records"
    private const val KEY = "saved_ids"

    fun ids(activity: Activity): Set<String> = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
        .getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun isSaved(activity: Activity, id: String): Boolean = ids(activity).contains(id)

    fun toggle(activity: Activity, id: String): Boolean {
        val mutable = ids(activity).toMutableSet()
        val nowSaved = if (mutable.contains(id)) {
            mutable.remove(id)
            false
        } else {
            mutable.add(id)
            true
        }
        activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE).edit().putStringSet(KEY, mutable).apply()
        return nowSaved
    }
}

abstract class NativeBaseActivity : Activity() {
    protected val forest = Color.rgb(18, 63, 44)
    protected val forest2 = Color.rgb(29, 90, 61)
    protected val cream = Color.rgb(247, 243, 232)
    protected val paper = Color.rgb(255, 254, 250)
    protected val sage = Color.rgb(233, 241, 228)
    protected val muted = Color.rgb(99, 111, 102)
    protected val ink = Color.rgb(25, 35, 29)
    protected val warning = Color.rgb(255, 236, 185)

    protected fun applyNativeRoot(root: View) {
        window.statusBarColor = cream
        window.navigationBarColor = paper
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    protected fun baseRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(cream)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    protected fun topBar(title: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(9), dp(14), dp(9))
        setBackgroundColor(cream)
        addView(iconButton(NativeIcon.BACK) { finish() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(text(title, 18f, forest, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Space(this@NativeBaseActivity), LinearLayout.LayoutParams(dp(44), 1))
    }

    protected fun scrollContent(): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(8), dp(15), dp(28))
        }
        scroll.addView(content)
        return scroll to content
    }

    protected fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    protected fun chip(label: String, bg: Int, fg: Int): TextView = text(label, 9.3f, fg, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(5), dp(9), dp(5))
        background = roundedSolid(bg, 999)
    }

    protected fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 20f, forest, true))
        addView(text(subtitle, 10.7f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
    }

    protected fun notice(message: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = roundedSolid(warning, 16)
        addView(text("LƯU Ý", 9.5f, Color.rgb(108, 80, 20), true))
        addView(text(message, 11.3f, Color.rgb(92, 76, 43), false).apply { setPadding(0, dp(5), 0, 0) })
    }

    protected fun recordCard(record: NativeDemoRecord, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(9), dp(9), dp(11), dp(9))
        background = roundedSolid(paper, 18)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }

        addView(ImageView(this@NativeBaseActivity).apply {
            setImageResource(record.imageRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSolid(sage, 14)
            clipToOutline = true
        }, LinearLayout.LayoutParams(dp(84), dp(84)))

        val words = LinearLayout(this@NativeBaseActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(5), 0)
        }
        val top = LinearLayout(this@NativeBaseActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(record.title, 15.5f, forest, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(chip("DEMO", warning, Color.rgb(112, 82, 16)))
        words.addView(top)
        words.addView(text(record.category, 10.5f, muted, false).apply { setPadding(0, dp(5), 0, 0) })
        words.addView(text(record.usage, 10.5f, forest2, true).apply { setPadding(0, dp(4), 0, 0) })
        addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(NativeIconView(this@NativeBaseActivity, NativeIcon.CHEVRON_RIGHT, forest2), LinearLayout.LayoutParams(dp(22), dp(22)))
    }

    protected fun openRecord(record: NativeDemoRecord) {
        startActivity(Intent(this, RecordDetailActivity::class.java).apply {
            putExtra("recordId", record.id)
            putExtra(RecordDetailActivity.EXTRA_TITLE, record.title)
            putExtra(RecordDetailActivity.EXTRA_IMAGE_RES, record.imageRes)
            putExtra(RecordDetailActivity.EXTRA_USAGE, record.usage)
        })
    }

    protected fun iconButton(icon: NativeIcon, click: () -> Unit): View = FrameLayout(this).apply {
        background = roundedSolid(paper, 15)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
        addView(NativeIconView(this@NativeBaseActivity, icon, forest), FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
    }

    protected fun primaryButton(label: String, icon: NativeIcon, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        background = roundedSolid(forest, 17)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
        addView(NativeIconView(this@NativeBaseActivity, icon, Color.WHITE), LinearLayout.LayoutParams(dp(22), dp(22)))
        addView(text(label, 12.5f, Color.WHITE, true).apply { setPadding(dp(8), 0, 0, 0) })
    }

    protected fun secondaryButton(label: String, icon: NativeIcon, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        background = roundedSolid(sage, 17)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
        addView(NativeIconView(this@NativeBaseActivity, icon, forest), LinearLayout.LayoutParams(dp(22), dp(22)))
        addView(text(label, 12.5f, forest, true).apply { setPadding(dp(8), 0, 0, 0) })
    }

    protected fun roundedSolid(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    protected fun space(height: Int): Space = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    protected fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

class NativeSearchActivity : NativeBaseActivity() {
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Tìm kiếm"))
        val (scroll, content) = scrollContent()

        content.addView(sectionTitle("Tìm trong thư viện", "Ưu tiên tên tiếng Việt và nội dung đã có trong thiết bị"))
        content.addView(space(12))

        val input = EditText(this).apply {
            hint = "Ví dụ: rau muống, cà rốt..."
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedSolid(paper, 18)
        }
        content.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        content.addView(space(12))
        content.addView(notice("Kết quả có nhãn DEMO chỉ để kiểm tra luồng giao diện; không được dùng làm căn cứ ăn, uống, sơ cứu hoặc dùng thuốc."))
        content.addView(space(16))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        render(intent.getStringExtra(EXTRA_QUERY).orEmpty())
        val initial = intent.getStringExtra(EXTRA_QUERY).orEmpty()
        if (initial.isNotBlank()) input.setText(initial)
    }

    private fun render(query: String) {
        results.removeAllViews()
        val q = normalize(query)
        val matches = NativeDemoCatalog.records.filter { record ->
            q.isBlank() || normalize(record.title).contains(q) || normalize(record.category).contains(q) || record.tags.any { normalize(it).contains(q) }
        }
        if (matches.isEmpty()) {
            results.addView(text("Không tìm thấy hồ sơ phù hợp trong dữ liệu hiện có.", 13f, muted, false).apply { setPadding(dp(5), dp(14), dp(5), dp(14)) })
            return
        }
        matches.forEachIndexed { index, record ->
            results.addView(recordCard(record) { openRecord(record) })
            if (index != matches.lastIndex) results.addView(space(9))
        }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(Locale("vi", "VN")), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace('đ', 'd')
            .trim()
    }

    companion object { const val EXTRA_QUERY = "query" }
}

class NativeCategoryActivity : NativeBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Tất cả danh mục"
        val root = baseRoot()
        root.addView(topBar(if (category == "Tất cả danh mục") "Danh mục" else category))
        val (scroll, content) = scrollContent()

        if (category == "Tất cả danh mục") {
            content.addView(sectionTitle("Danh mục thư viện", "Chọn nhóm để xem dữ liệu hiện có"))
            content.addView(space(12))
            NativeDemoCatalog.categories.forEachIndexed { index, name ->
                val icon = when (name) {
                    "Cây cỏ, rau" -> NativeIcon.LEAF
                    "Nấm" -> NativeIcon.MUSHROOM
                    "Cá & thủy sản" -> NativeIcon.FISH
                    "Côn trùng" -> NativeIcon.BUG
                    "Hoa" -> NativeIcon.FLOWER
                    "Cây gỗ" -> NativeIcon.TREE
                    "Cây ăn quả" -> NativeIcon.FRUIT
                    else -> NativeIcon.MEDICINE
                }
                content.addView(categoryRow(icon, name))
                if (index != NativeDemoCatalog.categories.lastIndex) content.addView(space(8))
            }
        } else {
            content.addView(sectionTitle(category, "Dữ liệu hiển thị ngay khi đạt điều kiện công bố"))
            content.addView(space(12))
            val matches = NativeDemoCatalog.records.filter { it.category == category || it.tags.contains(category) }
            if (matches.isEmpty()) {
                content.addView(notice("Chưa có hồ sơ đã kiểm chứng để hiển thị cho mục này. Ứng dụng không tạo nội dung giả để lấp chỗ trống."))
            } else {
                matches.forEachIndexed { index, record ->
                    content.addView(recordCard(record) { openRecord(record) })
                    if (index != matches.lastIndex) content.addView(space(9))
                }
                content.addView(space(14))
                content.addView(notice("Các hồ sơ đang thấy là DEMO giao diện. Dữ liệu thật chỉ được công bố sau kiểm chứng."))
            }
        }

        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
    }

    private fun categoryRow(icon: NativeIcon, label: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(12), dp(12))
        background = roundedSolid(paper, 17)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            startActivity(Intent(this@NativeCategoryActivity, NativeCategoryActivity::class.java).putExtra(EXTRA_CATEGORY, label))
        }
        addView(NativeIconView(this@NativeCategoryActivity, icon, forest2), LinearLayout.LayoutParams(dp(28), dp(28)))
        addView(text(label, 14.5f, forest, true).apply { setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(NativeIconView(this@NativeCategoryActivity, NativeIcon.CHEVRON_RIGHT, muted), LinearLayout.LayoutParams(dp(22), dp(22)))
    }

    companion object { const val EXTRA_CATEGORY = "category" }
}

class NativeCameraActivity : NativeBaseActivity() {
    private lateinit var preview: ImageView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Quét ảnh"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Nhận dạng từ hình ảnh", "Chụp bằng camera hoặc chọn ảnh có sẵn"))
        content.addView(space(12))

        preview = ImageView(this).apply {
            setImageResource(R.drawable.leaf_demo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSolid(sage, 22)
            clipToOutline = true
        }
        content.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(235)))
        content.addView(space(12))

        status = text("Chưa chọn ảnh.", 12f, muted, false)
        content.addView(status)
        content.addView(space(14))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(primaryButton("Chụp ảnh", NativeIcon.CAMERA) { openCamera() }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
        actions.addView(secondaryButton("Chọn ảnh", NativeIcon.GRID) { pickImage() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        content.addView(actions)
        content.addView(space(16))
        content.addView(notice("Bản hiện tại đã nhận ảnh từ camera/thư viện. Model nhận dạng offline chưa được đóng gói nên ứng dụng không trả kết quả nhận dạng giả."))

        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) startActivityForResult(intent, REQ_CAMERA)
        else toast("Thiết bị không có ứng dụng camera phù hợp")
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQ_PICK)
    }

    @Deprecated("Deprecated in Android API but retained for a dependency-free native beta flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_PICK -> {
                val uri = data?.data ?: return
                preview.setImageURI(uri)
                preview.scaleType = ImageView.ScaleType.CENTER_CROP
                status.text = "Đã nhận ảnh từ thư viện. Sẵn sàng cho bước nối model nhận dạng offline."
            }
            REQ_CAMERA -> {
                val bitmap = data?.extras?.get("data") as? Bitmap ?: return
                preview.setImageBitmap(bitmap)
                preview.scaleType = ImageView.ScaleType.CENTER_CROP
                status.text = "Đã nhận ảnh từ camera. Sẵn sàng cho bước nối model nhận dạng offline."
            }
        }
    }

    companion object {
        private const val REQ_CAMERA = 501
        private const val REQ_PICK = 502
    }
}

class NativeSavedActivity : NativeBaseActivity() {
    override fun onResume() {
        super.onResume()
        if (::rootHolder.isInitialized) render()
    }

    private lateinit var rootHolder: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootHolder = baseRoot()
        applyNativeRoot(rootHolder)
        render()
    }

    private fun render() {
        rootHolder.removeAllViews()
        rootHolder.addView(topBar("Đã lưu"))
        val (scroll, content) = scrollContent()
        content.addView(sectionTitle("Hồ sơ đã lưu", "Lưu cục bộ trên thiết bị để mở lại nhanh"))
        content.addView(space(12))
        val saved = NativeSavedStore.ids(this).mapNotNull { NativeDemoCatalog.byId(it) }
        if (saved.isEmpty()) {
            content.addView(notice("Chưa có hồ sơ nào được lưu. Mở một hồ sơ DEMO và bấm Lưu để kiểm tra luồng này."))
        } else {
            saved.forEachIndexed { index, record ->
                content.addView(recordCard(record) { openRecord(record) })
                if (index != saved.lastIndex) content.addView(space(9))
            }
        }
        rootHolder.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }
}

class NativeUpdateActivity : NativeBaseActivity() {
    private lateinit var appStatus: TextView
    private lateinit var downloadButton: Button
    private lateinit var libraryStatus: TextView
    private var downloadUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = baseRoot()
        root.addView(topBar("Cập nhật"))
        val (scroll, content) = scrollContent()

        content.addView(sectionTitle("Cập nhật ứng dụng", "Kiểm tra GitHub Release chính thức của ứng dụng"))
        content.addView(space(10))
        val appCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedSolid(paper, 18)
        }
        appStatus = text("Phiên bản đang cài: ${currentVersion()}", 12f, muted, false)
        appCard.addView(appStatus)
        appCard.addView(space(12))
        appCard.addView(primaryButton("Kiểm tra phiên bản mới", NativeIcon.REFRESH) { checkAppUpdate() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        appCard.addView(space(8))
        downloadButton = Button(this).apply {
            text = "Tải phiên bản mới"
            isEnabled = false
            setOnClickListener {
                val url = downloadUrl ?: return@setOnClickListener
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        appCard.addView(downloadButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        content.addView(appCard)
        content.addView(space(20))

        content.addView(sectionTitle("Cập nhật thư viện", "Dữ liệu được kiểm tra và cài riêng, không cần thay APK"))
        content.addView(space(10))
        val dataCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedSolid(paper, 18)
        }
        libraryStatus = text("Chưa kiểm tra gói dữ liệu.", 12f, muted, false)
        dataCard.addView(libraryStatus)
        dataCard.addView(space(12))
        dataCard.addView(secondaryButton("Kiểm tra & cập nhật thư viện", NativeIcon.REFRESH) { updateLibrary() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        content.addView(dataCard)
        content.addView(space(16))
        content.addView(notice("APK chỉ được tải từ GitHub Release của dự án. Gói thư viện vẫn phải qua kiểm tra cấu trúc và SHA-256 trước khi cài."))

        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        applyNativeRoot(root)
    }

    private fun currentVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private fun checkAppUpdate() {
        appStatus.text = "Đang kiểm tra GitHub Release..."
        downloadButton.isEnabled = false
        Thread {
            val result = runCatching { fetchLatestAppRelease() }
            runOnUiThread {
                result.onSuccess { release ->
                    if (release == null) {
                        appStatus.text = "Không tìm thấy bản phát hành hợp lệ."
                        return@onSuccess
                    }
                    val current = currentVersion()
                    if (compareVersion(release.first, current) > 0) {
                        downloadUrl = release.second
                        appStatus.text = "Có phiên bản mới: ${release.first}. Bấm Tải phiên bản mới để mở đường tải GitHub Release."
                        downloadButton.isEnabled = true
                    } else {
                        downloadUrl = null
                        appStatus.text = "Ứng dụng đang ở phiên bản mới nhất: $current"
                        downloadButton.isEnabled = false
                    }
                }.onFailure { error ->
                    appStatus.text = "Không kiểm tra được phiên bản: ${error.message ?: "lỗi kết nối"}"
                }
            }
        }.start()
    }

    private fun fetchLatestAppRelease(): Pair<String, String>? {
        val connection = URL(RELEASES_API).openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "SurvivalLibraryVN-Android")
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(json)
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                val tag = release.optString("tag_name")
                if (!tag.startsWith(TAG_PREFIX)) continue
                val version = tag.removePrefix(TAG_PREFIX)
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.optString("name") == APK_NAME) {
                        val url = asset.optString("browser_download_url")
                        if (url.startsWith("https://github.com/")) return version to url
                    }
                }
            }
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersion(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(left.size, right.size)
        repeat(size) { index ->
            val lv = left.getOrElse(index) { 0 }
            val rv = right.getOrElse(index) { 0 }
            if (lv != rv) return lv.compareTo(rv)
        }
        return 0
    }

    private fun updateLibrary() {
        libraryStatus.text = "Đang kiểm tra gói dữ liệu..."
        Thread {
            val result = runCatching { LibraryUpdateEngine.checkAndUpdate(this) }
            runOnUiThread {
                result.onSuccess { update ->
                    libraryStatus.text = buildString {
                        append(update.message)
                        if (update.installedRecords > 0) append(" Đã cài ${update.installedRecords} hồ sơ.")
                        if (update.errors.isNotEmpty()) append(" Có ${update.errors.size} lỗi bị chặn.")
                    }
                }.onFailure { error ->
                    libraryStatus.text = "Cập nhật thư viện thất bại an toàn: ${error.message ?: "không xác định"}"
                }
            }
        }.start()
    }

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/giaphatgpt123-wq/FieldIntelligence/releases?per_page=30"
        private const val TAG_PREFIX = "survival-library-vn-v"
        private const val APK_NAME = "SurvivalLibraryVN-Full.apk"
    }
}
