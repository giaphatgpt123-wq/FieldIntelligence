package vn.fieldintel.feature.emergency

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

private const val AUTO_REGION_ANALYSIS_INTERVAL_NANOS = 250_000_000L

/**
 * Region scan mode requested for field use:
 * - no target is entered;
 * - the whole view is scanned;
 * - a stable view automatically triggers a photo;
 * - if a verified model is ready, every supported object is classified in the same frame.
 *
 * Automatic capture works even when the model is absent so field evidence can be collected first.
 * Missing model state never fabricates taxon names.
 */
@Composable
fun RegionScanAutoCapturePanel(
    runner: LiveVisualModelRunner = NoVerifiedLiveVisualModel
) {
    val context = LocalContext.current
    val modelStatus = remember(runner) { runner.status() }
    val modelReady = modelStatus.availability == VisualModelAvailability.READY
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var scanning by remember { mutableStateOf(false) }
    var detections by remember { mutableStateOf(emptyList<VisualDetection>()) }
    var lastFrame by remember { mutableStateOf<LiveFrameInfo?>(null) }
    var autoCaptureCount by remember { mutableIntStateOf(0) }
    var latestCapture by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Sẵn sàng quét vùng.") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (!granted) {
            scanning = false
            statusText = "Cần quyền camera để quét vùng thực tế."
        }
    }

    val summary = remember(detections) { RegionScanClassifier.summarize(detections) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2329)),
            border = BorderStroke(1.dp, Color(0x3345E58C))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("QUÉT VÙNG THỰC TẾ", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Lia camera qua đám rau/cỏ/cây. App tự chọn lúc hình ổn định để chụp ảnh; khi model vùng sẵn sàng, các đối tượng trong cảnh được phân loại đồng thời.",
                    color = FieldColors.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().height(470.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Black,
                    border = BorderStroke(1.dp, Color(0x5545E58C))
                ) {
                    if (cameraGranted && scanning) {
                        Box(Modifier.fillMaxSize()) {
                            RegionAutoCaptureCamera(
                                runner = runner,
                                modelReady = modelReady,
                                modifier = Modifier.fillMaxSize(),
                                onFrame = { lastFrame = it },
                                onDetections = {
                                    detections = it
                                    statusText = if (modelReady) {
                                        "Đang quét và phân loại toàn vùng."
                                    } else {
                                        "Đang quét vùng; ảnh ổn định sẽ tự chụp và chờ model phân loại."
                                    }
                                },
                                onAutoCaptured = { path ->
                                    latestCapture = path
                                    autoCaptureCount += 1
                                    statusText = if (modelReady) {
                                        "Đã tự chụp ảnh vùng ổn định và giữ kết quả phân loại hiện tại."
                                    } else {
                                        "Đã tự chụp ảnh vùng ổn định; chưa gắn tên loài vì model chưa được cài."
                                    }
                                },
                                onError = { statusText = it }
                            )
                            LiveVisualOverlay(detections = detections, target = null, modifier = Modifier.fillMaxSize())
                            Surface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xCC081A1F)
                            ) {
                                Text(
                                    if (modelReady) "AUTO SCAN • AUTO CAPTURE • PHÂN LOẠI" else "AUTO SCAN • AUTO CAPTURE • CHỜ MODEL",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (modelReady) FieldColors.primary else Color(0xFFFFD166)
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (!cameraGranted) "Cấp quyền camera để bắt đầu." else "Bấm BẮT ĐẦU QUÉT VÙNG.",
                                color = FieldColors.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (!cameraGranted) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                detections = emptyList()
                                statusText = "Đang khởi động camera quét vùng…"
                                scanning = true
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(if (cameraGranted) "BẮT ĐẦU QUÉT VÙNG" else "CẤP QUYỀN CAMERA") }
                    if (scanning) {
                        OutlinedButton(
                            onClick = { scanning = false; statusText = "Đã dừng quét vùng." },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("DỪNG") }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132D32))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("KẾT QUẢ VÙNG", style = MaterialTheme.typography.titleMedium)
                Text(statusText, color = FieldColors.onSurfaceVariant)
                if (!modelReady) {
                    Text(modelStatus.message, color = Color(0xFFFFD166))
                    Text("Ảnh vẫn được tự chụp, nhưng không sinh tên loài giả.", color = FieldColors.onSurfaceVariant)
                } else if (summary.items.isEmpty()) {
                    Text("Chưa phát hiện đối tượng đủ điều kiện phân loại.", color = FieldColors.onSurfaceVariant)
                } else {
                    Text(
                        "${summary.totalObjects} vùng • ${summary.strongObjects} vùng đủ ngưỡng • ${summary.items.size} loại",
                        color = FieldColors.primary
                    )
                    summary.items.take(12).forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0E252A)
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                Text(item.label, style = MaterialTheme.typography.titleSmall)
                                item.scientificName?.let { Text(it, color = FieldColors.onSurfaceVariant) }
                                Text(
                                    "${item.instances} vùng • ${(item.bestConfidence * 100).toInt()}% tốt nhất",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FieldColors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text("Ảnh tự chụp: $autoCaptureCount", style = MaterialTheme.typography.bodySmall)
                if (latestCapture != null) Text("Ảnh gần nhất đã lưu offline trên máy.", color = FieldColors.primary)
                lastFrame?.let {
                    Text("Camera ${it.width}×${it.height} • xoay ${it.rotationDegrees}°", style = MaterialTheme.typography.bodySmall, color = FieldColors.onSurfaceVariant)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF102A31),
            border = BorderStroke(1.dp, Color(0x33FFD166))
        ) {
            Text(
                "Phân loại hình ảnh chỉ là ứng viên nhận dạng. Không dùng kết quả camera để tự kết luận ăn được, không độc hoặc có tác dụng chữa bệnh.",
                modifier = Modifier.padding(14.dp),
                color = FieldColors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RegionAutoCaptureCamera(
    runner: LiveVisualModelRunner,
    modelReady: Boolean,
    modifier: Modifier,
    onFrame: (LiveFrameInfo) -> Unit,
    onDetections: (List<VisualDetection>) -> Unit,
    onAutoCaptured: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnDetections by rememberUpdatedState(onDetections)
    val currentOnAutoCaptured by rememberUpdatedState(onAutoCaptured)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val frameGate = remember { RegionFrameStabilityGate() }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner, previewView, runner, modelReady) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var lastAnalysisTimestamp = 0L
        var captureInFlight = false

        providerFuture.addListener({
            if (disposed) return@addListener
            runCatching {
                val provider = providerFuture.get()
                provider.unbindAll()
                frameGate.reset()

                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { image ->
                    try {
                        val timestamp = image.imageInfo.timestamp
                        val frameInfo = LiveFrameInfo(timestamp, image.width, image.height, image.imageInfo.rotationDegrees)
                        mainExecutor.execute { if (!disposed) currentOnFrame(frameInfo) }

                        if (timestamp - lastAnalysisTimestamp >= AUTO_REGION_ANALYSIS_INTERVAL_NANOS) {
                            lastAnalysisTimestamp = timestamp
                            val frame = image.toOwnedAutoRegionFrame()
                            val found = if (modelReady) runner.scanRegion(frame) else emptyList()
                            mainExecutor.execute { if (!disposed) currentOnDetections(found) }

                            if (!captureInFlight && frameGate.shouldCapture(timestamp, frame)) {
                                captureInFlight = true
                                val folder = File(context.filesDir, if (modelReady) "region-scans/classified" else "region-scans/pending")
                                    .apply { mkdirs() }
                                val file = File(folder, "region-${System.currentTimeMillis()}.jpg")
                                imageCapture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(file).build(),
                                    mainExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            captureInFlight = false
                                            if (!disposed) currentOnAutoCaptured(file.absolutePath)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            captureInFlight = false
                                            if (!disposed) currentOnError(exception.message ?: "Không thể tự chụp ảnh vùng")
                                        }
                                    }
                                )
                            }
                        }
                    } catch (failure: Throwable) {
                        mainExecutor.execute { if (!disposed) currentOnError(failure.message ?: "Lỗi phân tích camera vùng") }
                    } finally {
                        image.close()
                    }
                }

                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
            }.onFailure { failure -> currentOnError(failure.message ?: "Không thể khởi động camera") }
        }, mainExecutor)

        onDispose {
            disposed = true
            frameGate.reset()
            runCatching { if (providerFuture.isDone) providerFuture.get().unbindAll() }
            executor.shutdownNow()
        }
    }
}

private fun androidx.camera.core.ImageProxy.toOwnedAutoRegionFrame(): LiveVisualFrameData {
    require(planes.size >= 3) { "Camera frame không phải YUV_420_888 ba mặt phẳng" }
    fun copyPlane(index: Int): YuvPlaneData {
        val plane = planes[index]
        val duplicate = plane.buffer.duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return YuvPlaneData(bytes, plane.rowStride, plane.pixelStride)
    }
    return LiveVisualFrameData(
        timestampNanos = imageInfo.timestamp,
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
        y = copyPlane(0),
        u = copyPlane(1),
        v = copyPlane(2)
    )
}
