package vn.fieldintel.feature.emergency

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manual photo + gallery-query path that runs in parallel with live region scanning.
 * The same installed offline model package is reused, without temporal tracking.
 */
@Composable
fun StillImageRecognitionPanel(modelGeneration: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember(modelGeneration) { InstalledVisualModelRunner(context.applicationContext) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var sourceLabel by remember { mutableStateOf("Chưa có ảnh") }
    var analysis by remember { mutableStateOf<StillImageVisualAnalyzer.Result?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveStatus by remember { mutableStateOf("") }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var selectedDetection by remember { mutableStateOf<VisualDetection?>(null) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(runner) {
        onDispose { runner.close() }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bitmap = decodeUriBounded(context, uri, 1800)
            if (bitmap != null) {
                preview = bitmap
                sourceLabel = "Ảnh từ máy • ${bitmap.width}×${bitmap.height} • đã chuẩn hóa hướng ảnh"
                error = null
                saveStatus = ""
                selectedDetection = null
            } else {
                error = "Không thể mở ảnh đã chọn."
            }
        }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (saved && file?.isFile == true) {
            val bitmap = decodeFileBounded(file, 1800)
            file.delete()
            if (bitmap != null) {
                preview = bitmap
                sourceLabel = "Ảnh chụp độ phân giải đầy đủ • ${bitmap.width}×${bitmap.height} • đã chuẩn hóa hướng ảnh"
                error = null
                saveStatus = ""
                selectedDetection = null
            } else {
                error = "Đã chụp nhưng không thể đọc ảnh."
            }
        } else if (file != null) {
            file.delete()
        }
    }

    fun startFullResolutionCapture() {
        runCatching {
            val dir = File(context.cacheDir, "camera-capture").apply { mkdirs() }
            val file = File.createTempFile("manual-", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCaptureFile = file
            takePhoto.launch(uri)
        }.onFailure { failure ->
            pendingCaptureFile = null
            error = failure.message ?: "Không thể mở camera chụp ảnh."
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (granted) {
            startFullResolutionCapture()
        } else {
            error = "Cần quyền camera để chụp ảnh. Chọn ảnh từ máy vẫn sử dụng được."
        }
    }

    LaunchedEffect(preview, runner) {
        val bitmap = preview
        analysis = null
        selectedDetection = null
        if (bitmap == null) return@LaunchedEffect
        analyzing = true
        error = null
        runCatching {
            withContext(Dispatchers.Default) { StillImageVisualAnalyzer.analyze(bitmap, runner) }
        }.onSuccess { analysis = it }
            .onFailure { error = it.message ?: "Không thể phân tích ảnh." }
        analyzing = false
    }

    val result = analysis
    val modelReady = result?.status?.availability == VisualModelAvailability.READY
    val detections = result?.detections.orEmpty()
    val summary = remember(detections) {
        RegionScanClassifier.summarize(detections)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2329)),
            border = BorderStroke(1.dp, Color(0x3345E58C))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CHỤP ẢNH / TRUY VẤN ẢNH", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    "Chụp ảnh độ phân giải đầy đủ hoặc chọn ảnh có sẵn. App dùng cùng model offline với quét vùng để tìm nhiều ứng viên trong ảnh.",
                    color = FieldColors.onSurfaceVariant
                )

                val bitmap = preview
                if (bitmap != null) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 430.dp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = sourceLabel,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        StillImageDetectionOverlay(
                            detections = detections,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            modifier = Modifier.fillMaxSize(),
                            selected = selectedDetection,
                            onDetectionTap = { selectedDetection = it }
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF07181D)
                    ) {
                        Text("Chụp ảnh hoặc chọn ảnh từ máy để bắt đầu.", modifier = Modifier.padding(24.dp), color = FieldColors.onSurfaceVariant)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (cameraGranted) startFullResolutionCapture()
                            else cameraPermission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("CHỤP ẢNH") }
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("CHỌN ẢNH") }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132D32))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PHÂN TÍCH ẢNH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(sourceLabel, color = FieldColors.onSurfaceVariant)

                if (analyzing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.heightIn(max = 24.dp), strokeWidth = 2.dp)
                        Text("Đang chạy model offline…", color = FieldColors.primary)
                    }
                } else if (error != null) {
                    Text(error!!, color = Color(0xFFFF8A80))
                } else if (preview == null) {
                    Text("Chưa có ảnh để phân tích.", color = FieldColors.onSurfaceVariant)
                } else if (result != null && !modelReady) {
                    Text(result.status.message, color = Color(0xFFFFD166))
                    Text("Ảnh vẫn có thể lưu/đối chiếu thủ công; app không sinh tên loài giả.", color = FieldColors.onSurfaceVariant)
                } else if (result != null && summary.items.isEmpty()) {
                    Text("Model không phát hiện ứng viên đủ ngưỡng trong ảnh này.", color = Color(0xFFFFD166))
                    Text("Không phát hiện không đồng nghĩa mẫu vật an toàn hoặc không tồn tại.", color = FieldColors.onSurfaceVariant)
                } else if (result != null) {
                    Text(
                        "${summary.totalObjects} vùng • ${summary.items.size} loại ứng viên • ảnh phân tích ${result.analyzedWidth}×${result.analyzedHeight}",
                        color = FieldColors.primary
                    )
                    Text(
                        "Chạm trực tiếp vào bounding box để chọn ứng viên và đối chiếu hồ sơ khoa học offline.",
                        color = FieldColors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    summary.items.take(12).forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0E252A)
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                Text(item.label, fontWeight = FontWeight.Bold)
                                item.scientificName?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = FieldColors.onSurfaceVariant)
                                }
                                Text(
                                    "${item.instances} vùng • ${(item.bestConfidence * 100).toInt()}% tốt nhất",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FieldColors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (preview != null) {
                    OutlinedButton(
                        onClick = {
                            val bitmap = preview ?: return@OutlinedButton
                            scope.launch {
                                saveStatus = "Đang lưu ảnh và metadata…"
                                val outcome = withContext(Dispatchers.IO) {
                                    runCatching { StillImageCaptureStore.save(context.applicationContext, bitmap, analysis) }
                                }
                                saveStatus = outcome.fold(
                                    onSuccess = { "Đã lưu offline ảnh + metadata kiểm tra lại." },
                                    onFailure = { "Lưu thất bại: ${it.message ?: "không rõ lỗi"}" }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !analyzing
                    ) { Text("LƯU ẢNH + KẾT QUẢ OFFLINE") }
                    if (saveStatus.isNotBlank()) {
                        Text(saveStatus, color = if (saveStatus.startsWith("Đã")) FieldColors.primary else FieldColors.onSurfaceVariant)
                    }
                }
            }
        }

        selectedDetection?.let { StillImageScientificMatchPanel(it) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF102A31),
            border = BorderStroke(1.dp, Color(0x33FFD166))
        ) {
            Text(
                "Kết quả ảnh tĩnh và camera đều chỉ là ứng viên nhận dạng. Không dùng confidence để tự kết luận ăn được, không độc hoặc có tác dụng chữa bệnh.",
                modifier = Modifier.padding(14.dp),
                color = FieldColors.onSurfaceVariant
            )
        }
    }
}

private fun boundedSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > maxEdge) sample *= 2
    return sample
}

private fun decodeUriBounded(context: Context, uri: Uri, maxEdge: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(
            input,
            null,
            BitmapFactory.Options().apply {
                inSampleSize = boundedSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    } ?: error("Không thể giải mã ảnh")
    val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    applyExifOrientation(decoded, orientation)
}.getOrNull()

private fun decodeFileBounded(file: File, maxEdge: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    val decoded = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = boundedSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    ) ?: error("Không thể giải mã ảnh")
    val orientation = ExifInterface(file).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    applyExifOrientation(decoded, orientation)
}.getOrNull()

private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return source
    }
    val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (transformed !== source && !source.isRecycled) source.recycle()
    return transformed
}
