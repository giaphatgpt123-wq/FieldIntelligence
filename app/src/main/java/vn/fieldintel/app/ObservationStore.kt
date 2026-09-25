package vn.fieldintel.app

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import vn.fieldintel.feature.emergency.ObservationUi

/** Local-only pilot observations; photos are bounded previews, not original-resolution evidence. */
class ObservationStore(private val context: Context) {
    private val folder = File(context.filesDir, "observations")
    private val index = File(folder, "index.json")

    fun load(): List<ObservationUi> = runCatching {
        if (!index.exists()) return emptyList()
        val array = JSONArray(index.readText())
        (0 until array.length()).mapNotNull { i ->
            val item = array.getJSONObject(i)
            val id = item.getString("id")
            if (!id.matches(Regex("[a-f0-9-]{36}"))) return@mapNotNull null
            val photo = File(folder, "$id.jpg")
            if (!photo.isFile) return@mapNotNull null
            ObservationUi(id, item.getString("note"), item.getLong("createdAt"), photo.absolutePath)
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    fun save(preview: Bitmap, note: String): ObservationUi {
        val clean = note.trim().take(500)
        require(clean.isNotEmpty()) { "Cần ghi chú mẫu quan sát" }
        folder.mkdirs()
        val id = UUID.randomUUID().toString()
        val photo = File(folder, "$id.jpg")
        val photoTemp = File(folder, "$id.jpg.tmp")
        try {
            photoTemp.outputStream().use { out ->
                require(preview.compress(Bitmap.CompressFormat.JPEG, 82, out)) { "Không thể lưu ảnh" }
            }
            require(photoTemp.renameTo(photo)) { "Không thể hoàn tất lưu ảnh" }
            val entry = ObservationUi(id, clean, System.currentTimeMillis(), photo.absolutePath)
            val existing = if (index.exists()) JSONArray(index.readText()) else JSONArray()
            val updated = JSONArray().put(JSONObject().put("id", id).put("note", clean).put("createdAt", entry.createdAt))
            for (i in 0 until existing.length()) updated.put(existing.getJSONObject(i))
            val tempIndex = File(folder, "index.json.tmp")
            tempIndex.writeText(updated.toString())
            require(tempIndex.renameTo(index)) { "Không thể lưu danh mục ghi nhận" }
            return entry
        } catch (error: Exception) {
            photoTemp.delete()
            photo.delete()
            throw error
        }
    }
}
