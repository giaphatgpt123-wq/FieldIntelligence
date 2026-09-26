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
import androidx.compose.foundation.layout.Spacer
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

private const val REGION_INFERENCE_INTERVAL_NANOS = 250_000_000L

/**
 * Region scan camera: detects every model-supported object in view instead of looking for one target.
 * Automatic photos are taken only after the visible classification set remains stable for several
 * inference frames. A saved photo is evidence for later review, not proof of edibility or safety.
 */
@Composable
fun RegionScanCamera(
    active: Boolean,
    runner: LiveVisualModelRunner = NoVerifiedLiveVisualModel,
    modifier: Modifier = Modifier,
    onFrame: (LiveFrameInfo) -> Unit = {},
    onDetections: (List<VisualDetection>) -> Unit = {},
    onAutoCaptured: (String) -> Unit = {},
    onCameraError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnDetections by rememberUpdatedState(onDetections)
    val currentOnAutoCaptured by rememberUpdatedState(onAutoCaptured)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureGate = remember { RegionAutoCaptureGate() }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(active, lifecycleOwner, previewView, runner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var lastInferenceTimestamp = 0L
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val modelReady = runner.status().availability == VisualModelAvailability.READY

        providerFuture.addListener({
            if (disposed) return@addListener
            runCatching {
                val provider = providerFuture.get()
                provider.unbindAll()
                captureGate.reset()
                if (!active) return@runCatching

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val timestamp = image.imageInfo.timestamp
                        val frameInfo = LiveFrameInfo(
                            timestampNanos = timestamp,
                            width = image.width,
                            height = image.height,
                            rotationDegrees = image.imageInfo.rotationDegrees
                        )
                        mainExecutor.execute { if (!disposed) currentOnFrame(frameInfo) }

                        if (modelReady && timestamp - lastInferenceTimestamp >= REGION_INFERENCE_INTERVAL_NANOS) {
                            lastInferenceTimestamp = timestamp
                            val frame = image.toOwnedRegionFrameData()
                            val detections = runner.scanRegion(frame)
                            mainExecutor.execute {
                                if (!disposed) currentOnDetections(detections)
                            }

                            if (captureGate.shouldCapture(timestamp, detections)) {
                                val directory = File(context.filesDir, "region-scans").apply { mkdirs() }
                                val file = File(directory, "region-${System.currentTimeMillis()}.jpg")
                                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                                imageCapture.takePicture(
                                    options,
                                    mainExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            if (!disposed) currentOnAutoCaptured(file.absolutePath)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            if (!disposed) currentOnCameraError(
                                                exception.message ?: "Không thể tự chụp ảnh vùng quét"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    } catch (failure: Throwable) {
                        mainExecutor.execute {
                            if (!disposed) currentOnCameraError(
                                failure.message ?: "Lỗi phân tích vùng camera"
                            )
                        }
                    } finally {
                        image.close()
                    }
                }

                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    analysis
                )
            }.onFailure { failure ->
                currentOnCameraError(failure.message ?: "Không thể khởi động camera")
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            captureGate.reset()
            runCatching { if (providerFuture.isDone) providerFuture.get().unbindAll() }
            analysisExecutor.shutdownNow()
        }
    }
}

private fun androidx.camera.core.ImageProxy.toOwnedRegionFrameData(): LiveVisualFrameData {
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

@Composable
fun RegionScanPanel(
    runner: LiveVisualModelRunner = NoVerifiedLiveVisualModel
) {
    val context = LocalContext.current
    val modelStatus = remember(runner) { runner.status() }
    val modelReady = modelStatus.availability == VisualModelAvailability.READY
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanning by remember { mutableStateOf(false) }
    var detections by remember { mutableStateOf(emptyList<VisualDetection>()) }
    var lastFrame by remember { mutableStateOf<LiveFrameInfo?>(null) }
    var latestCapture by remember { mutableStateOf<String?>(null) }
    var captureCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (!granted) {
            scanning = false
            error = "Cần quyền camera để quét vùng thực tế."
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("QUÉT VÙNG THỰC TẾ", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Lia camera qua khu vực. App phân vùng các đối tượng nhìn thấy, gom theo loại và tự chụp ảnh khi cảnh ổn định để lưu bằng chứng phân loại.",
                    color = FieldColors.onSurfaceVariant
                )

                if (cameraGranted && scanning) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(450.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Black,
                        border = BorderStroke(1.dp, Color(0x5545E58C))
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            RegionScanCamera(
                                active = true,
                                runner = runner,
                                modifier = Modifier.fillMaxSize(),
                                onFrame = { lastFrame = it },
                                onDetections = { found ->
                                    detections = found
                                    error = null
                                },
                                onAutoCaptured = { path ->
                                    latestCapture = path
                                    captureCount += 1
                                },
                                onCameraError = { message -> error = message }
                            )
                            LiveVisualOverlay(
                                detections = detections,
                                target = null,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xCC081A1F)
                            ) {
                                Text(
                                    if (modelReady) "ĐANG QUÉT TOÀN VÙNG" else "CAMERA LIVE • MODEL CHƯA SẴN SÀNG",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (modelReady) FieldColors.primary else Color(0xFFFFD166)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF102A31)
                    ) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (!cameraGranted) "Cấp quyền camera để quét toàn bộ vùng trước máy."
                                else "Bấm BẮT ĐẦU QUÉT VÙNG rồi lia camera chậm.",
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
                                latestCapture = null
                                error = null
                                scanning = true
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(if (!cameraGranted) "CẤP QUYỀN CAMERA" else "BẮT ĐẦU QUÉT VÙNG")
                    }
                    if (scanning) {
                        OutlinedButton(
                            onClick = { scanning = false },
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PHÂN LOẠI TRONG KHUNG HÌNH", style = MaterialTheme.typography.titleMedium)
                if (!modelReady) {
                    Text(modelStatus.message, color = Color(0xFFFFD166))
                    Text(
                        "Không tạo tên loài giả khi chưa có model vùng đã kiểm chứng.",
                        color = FieldColors.onSurfaceVariant
                    )
                } else if (summary.items.isEmpty()) {
                    Text("Chưa có đối tượng đủ điều kiện phân loại.", color = FieldColors.onSurfaceVariant)
                } else {
                    Text(
                        "${summary.totalObjects} đối tượng • ${summary.items.size} loại trong cảnh",
                        color = FieldColors.primary
                    )
                    summary.items.take(12).forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0E252A)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.label, style = MaterialTheme.typography.titleSmall)
                                item.scientificName?.let {
                                    Text(it, color = FieldColors.onSurfaceVariant)
                                }
                                Text(
                                    "${item.instances} vùng • độ tin cậy tốt nhất ${(item.bestConfidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FieldColors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("Ảnh tự chụp: $captureCount", style = MaterialTheme.typography.bodySmall)
                latestCapture?.let {
                    Text("Ảnh gần nhất đã lưu offline trên máy.", color = FieldColors.primary)
                }
                lastFrame?.let {
                    Text(
                        "Camera ${it.width}×${it.height} • xoay ${it.rotationDegrees}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = FieldColors.onSurfaceVariant
                    )
                }
                error?.let { Text(it, color = Color(0xFFFF8A80)) }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF102A31),
            border = BorderStroke(1.dp, Color(0x33FFD166))
        ) {
            Text(
                "Kết quả camera là ứng viên phân loại hình ảnh. Không dùng score hình ảnh để tự kết luận ăn được, không độc hoặc có tác dụng chữa bệnh; các kết luận đó phải qua lớp bằng chứng khoa học riêng.",
                modifier = Modifier.padding(14.dp),
                color = FieldColors.onSurfaceVariant
            )
        }
    }
}
