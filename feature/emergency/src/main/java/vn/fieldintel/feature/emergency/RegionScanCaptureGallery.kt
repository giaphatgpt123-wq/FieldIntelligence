package vn.fieldintel.feature.emergency

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps captured evidence reachable without making private camera files public. */
@Composable
internal fun RegionScanCaptureGallery(latestCapture: String?) {
    val context = LocalContext.current
    val captures = remember(latestCapture) {
        listOf("classified", "pending").flatMap { folder ->
            File(context.filesDir, "region-scans/$folder")
                .listFiles()?.filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }.orEmpty()
        }.sortedByDescending { it.lastModified() }
    }
    var shareError by remember { mutableStateOf<String?>(null) }
    if (captures.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF245860))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ẢNH ĐÃ LƯU • ${captures.size}", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Ảnh và metadata được lưu offline trên máy. Mở hoặc chia sẻ khi cần đối chiếu.", color = Color(0xFFE0F3EE))
            captures.take(6).forEach { file ->
                val thumbnail by produceState<Bitmap?>(null, file.absolutePath) {
                    value = withContext(Dispatchers.IO) { decodeScanThumbnail(file) }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF326C70))) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        thumbnail?.let {
                            Image(it.asImageBitmap(), "Ảnh thực địa ${file.name}", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (file.parentFile?.name == "classified") "Có ứng viên model" else "Chờ phân loại",
                                modifier = Modifier.weight(1f),
                                color = Color.White
                            )
                            Button(
                                onClick = {
                                    shareError = runCatching {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/jpeg"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Chia sẻ ảnh thực địa"))
                                    }.exceptionOrNull()?.message
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text("CHIA SẺ") }
                        }
                    }
                }
            }
            if (captures.size > 6) Text("Đang hiển thị 6 ảnh gần nhất.", color = Color(0xFFE0F3EE))
            shareError?.let { Text("Không thể mở chia sẻ ảnh: $it", color = Color(0xFFFFD166)) }
        }
    }
}

private fun decodeScanThumbnail(file: File): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1000) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@runCatching null
    val orientation = ExifInterface(file.absolutePath)
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) bitmap else Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true
    ).also { if (it !== bitmap) bitmap.recycle() }
}.getOrNull()
