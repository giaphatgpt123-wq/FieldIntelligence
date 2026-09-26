package vn.fieldintel.feature.emergency

import android.Manifest
import android.os.SystemClock
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val AUTO_REGION_ANALYSIS_INTERVAL_NANOS = 250_000_000L

internal fun regionFieldGuidance(detections: List<VisualDetection>, modelReady: Boolean): String {
    if (!modelReady) return "Có thể quét và tự chụp bằng chứng; cần model hợp lệ để phân loại tự động."
    if (detections.isEmpty()) return "Lia camera chậm qua toàn vùng; giữ cảnh đủ sáng và tránh rung."
    val maxArea = detections.maxOf { detection ->
        (detection.box.right - detection.box.left) * (detection.box.bottom - detection.box.top)
    }
    if (maxArea < 0.06f) return "Đối tượng còn nhỏ trong khung. Tiến gần hơn hoặc dùng zoom, rồi giữ máy ổn định."
    val stable = detections.count { it.isStableRegionCandidate() }
    val verifying = detections.count { it.regionVerificationProgress() != null }
    return when {
        stable > 0 -> "Đã có vùng ổn định. Giữ máy thêm một nhịp để auto-capture lưu bằng chứng rõ."
        verifying > 0 -> "Đang xác minh $verifying vùng. Giữ máy ổn định hoặc chạm trực tiếp vào mẫu để lấy nét."
        else -> "Tiếp tục lia chậm; chạm vào mẫu cần xem để lấy nét."
    }
}

internal fun regionQualityLabel(quality: RegionFrameQuality?): String = when {
    quality == null -> "ĐANG ĐO CHẤT LƯỢNG"
    quality.tooDark -> "QUÁ TỐI"
    quality.tooBright -> "QUÁ SÁNG"
    quality.tooBlurred -> "CHƯA NÉT"
    else -> "ĐỦ ĐIỀU KIỆN CHỤP"
}

private fun regionQualityColor(quality: RegionFrameQuality?): Color = when {
    quality == null -> Color(0xFFFFD166)
    quality.acceptable -> FieldColors.primary
    quality.tooDark || quality.tooBright -> Color(0xFFFFA24C)
    else -> Color(0xFFFFD166)
}

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
    var frameQuality by remember { mutableStateOf<RegionFrameQuality?>(null) }
    var autoCaptureCount by remember { mutableIntStateOf(0) }
    var latestCapture by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Sẵn sàng quét vùng.") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (granted) scanning = true
        if (!granted) {
            scanning = false
            frameQuality = null
            statusText = "Cần quyền camera để quét vùng thực tế."
        }
    }

    val stableDetections = remember(detections) { detections.filter { it.isStableRegionCandidate() } }
    val verifyingCount = remember(detections) { detections.count { it.regionVerificationProgress() != null } }
    val summary = remember(stableDetections) { RegionScanClassifier.summarize(stableDetections) }
    val fieldGuidance = remember(detections, modelReady) { regionFieldGuidance(detections, modelReady) }
    val activeGuidance = frameQuality?.takeIf { !it.acceptable }?.guidance() ?: fieldGuidance

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF245860)),
            border = BorderStroke(1.dp, Color(0x3345E58C))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("QUÉT VÙNG THỰC TẾ", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Lia camera qua đám rau/cỏ/cây. App tự chọn lúc hình ổn định để chụp ảnh; khi model vùng sẵn sàng, các đối tượng trong cảnh được phân loại đồng thời.",
                    color = FieldColors.onSurfaceVariant
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (!cameraGranted) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                detections = emptyList()
                                frameQuality = null
                                (runner as? StableRegionVisualModelRunner)?.reset()
                                statusText = "Đang khởi động camera quét vùng…"
                                scanning = true
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(if (cameraGranted) "BẮT ĐẦU QUÉT VÙNG" else "CẤP QUYỀN CAMERA") }
                    if (scanning) {
                        OutlinedButton(
                            onClick = {
                                scanning = false
                                frameQuality = null
                                (runner as? StableRegionVisualModelRunner)?.reset()
                                statusText = "Đã dừng quét vùng."
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("DỪNG") }
                    }
                }

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
                                onQuality = { frameQuality = it },
                                onDetections = {
                                    detections = it
                                    statusText = if (modelReady) {
                                        val stable = it.count { detection -> detection.isStableRegionCandidate() }
                                        val verifying = it.count { detection -> detection.regionVerificationProgress() != null }
                                        when {
                                            stable > 0 -> "Đã có $stable vùng ổn định; tự chụp chỉ dùng các vùng này."
                                            verifying > 0 -> "Đang xác minh $verifying vùng; chưa tự chụp kết quả phân loại."
                                            else -> "Đang quét và phân loại toàn vùng."
                                        }
                                    } else {
                                        "Đang quét vùng; ảnh ổn định sẽ tự chụp và chờ model phân loại."
                                    }
                                },
                                onAutoCaptured = { path ->
                                    latestCapture = path
                                    autoCaptureCount += 1
                                    statusText = if (modelReady) {
                                        "Đã tự chụp ảnh với các kết quả đã ổn định và lưu metadata kèm ảnh."
                                    } else {
                                        "Đã tự chụp ảnh vùng ổn định; chưa gắn tên loài vì model chưa được cài."
                                    }
                                },
                                onControlStatus = { statusText = it },
                                onError = { statusText = it }
                            )
                            LiveVisualOverlay(detections = detections, target = null, modifier = Modifier.fillMaxSize())
                            Surface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xCC081A1F)
                            ) {
                                Text(
                                    if (modelReady) "AUTO SCAN • XÁC MINH • AUTO CAPTURE" else "AUTO SCAN • AUTO CAPTURE • CHỜ MODEL",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (modelReady) FieldColors.primary else Color(0xFFFFD166)
                                )
                            }
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 56.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xD9081A1F),
                                border = BorderStroke(1.dp, regionQualityColor(frameQuality).copy(alpha = .6f))
                            ) {
                                Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                                    Text(
                                        regionQualityLabel(frameQuality),
                                        color = regionQualityColor(frameQuality),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    frameQuality?.let { quality ->
                                        Text(
                                            "Sáng ${quality.meanLuma.toInt()} • nét ${quality.edgeStrength.toInt()}",
                                            color = Color.White.copy(alpha = .72f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
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

                if (scanning) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF204951)
                    ) {
                        Text(activeGuidance, modifier = Modifier.padding(11.dp), color = FieldColors.onSurfaceVariant)
                    }
                }


            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B6167))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("KẾT QUẢ VÙNG", style = MaterialTheme.typography.titleMedium)
                Text(statusText, color = FieldColors.onSurfaceVariant)
                if (!modelReady) {
                    Text(modelStatus.message, color = Color(0xFFFFD166))
                    Text("Ảnh vẫn được tự chụp, nhưng không sinh tên loài giả.", color = FieldColors.onSurfaceVariant)
                } else if (summary.items.isEmpty()) {
                    if (verifyingCount > 0) {
                        Text("$verifyingCount vùng đang xác minh qua nhiều frame; chưa đưa vào kết quả ổn định.", color = Color(0xFFFFD166))
                    } else {
                        Text("Chưa phát hiện đối tượng đủ điều kiện phân loại.", color = FieldColors.onSurfaceVariant)
                    }
                } else {
                    Text(
                        "${summary.totalObjects} vùng ổn định • ${summary.strongObjects} vùng đủ ngưỡng • ${summary.items.size} loại",
                        color = FieldColors.primary
                    )
                    if (verifyingCount > 0) {
                        Text("$verifyingCount vùng khác vẫn đang xác minh.", color = Color(0xFFFFD166))
                    }
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
                                    "${item.instances} vùng ổn định • ${(item.bestConfidence * 100).toInt()}% tốt nhất",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FieldColors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                frameQuality?.let { quality ->
                    Text(
                        "Chất lượng camera: ${regionQualityLabel(quality)} • sáng ${quality.meanLuma.toInt()} • nét ${quality.edgeStrength.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = regionQualityColor(quality)
                    )
                }
                Text("Ảnh tự chụp: $autoCaptureCount", style = MaterialTheme.typography.bodySmall)
                if (latestCapture != null) Text("Ảnh gần nhất và metadata đã lưu offline trên máy.", color = FieldColors.primary)
                lastFrame?.let {
                    Text("Camera ${it.width}×${it.height} • xoay ${it.rotationDegrees}°", style = MaterialTheme.typography.bodySmall, color = FieldColors.onSurfaceVariant)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF245860),
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
    onQuality: (RegionFrameQuality) -> Unit,
    onDetections: (List<VisualDetection>) -> Unit,
    onAutoCaptured: (String) -> Unit,
    onControlStatus: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnQuality by rememberUpdatedState(onQuality)
    val currentOnDetections by rememberUpdatedState(onDetections)
    val currentOnAutoCaptured by rememberUpdatedState(onAutoCaptured)
    val currentOnControlStatus by rememberUpdatedState(onControlStatus)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val metadataExecutor = remember { Executors.newSingleThreadExecutor() }
    val frameGate = remember { RegionFrameStabilityGate() }
    val adjustmentGate = remember { CameraAdjustmentGate() }
    var adjustmentState by remember { mutableStateOf(CameraAdjustmentGate.State.IDLE) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var torchEnabled by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }

    fun focusAt(x: Float, y: Float) {
        val camera = boundCamera ?: return
        if (previewView.width <= 0 || previewView.height <= 0) return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        val token = adjustmentGate.begin(SystemClock.elapsedRealtimeNanos())
        adjustmentState = CameraAdjustmentGate.State.FOCUSING
        val focusFuture = camera.cameraControl.startFocusAndMetering(action)
        focusFuture.addListener({
            runCatching { focusFuture.get() }.onSuccess { result ->
                adjustmentGate.focusCompleted(token, SystemClock.elapsedRealtimeNanos(), result.isFocusSuccessful)
                adjustmentState = adjustmentGate.state(SystemClock.elapsedRealtimeNanos())
            }
        }, ContextCompat.getMainExecutor(context))
        currentOnControlStatus("Đang lấy nét tại vùng đã chọn…")
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boundCamera) {
                    detectTapGestures { offset -> focusAt(offset.x, offset.y) }
                }
        )

        if (adjustmentState != CameraAdjustmentGate.State.IDLE) {
            Text(
                when (adjustmentState) {
                    CameraAdjustmentGate.State.FOCUSING -> "ĐANG LẤY NÉT"
                    CameraAdjustmentGate.State.EXPOSURE_SETTLING -> "ĐANG CÂN SÁNG"
                    CameraAdjustmentGate.State.READY -> "SẴN SÀNG"
                    CameraAdjustmentGate.State.IDLE -> ""
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
                color = FieldColors.primary
            )
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    linearZoom = (linearZoom - 0.20f).coerceIn(0f, 1f)
                    boundCamera?.cameraControl?.setLinearZoom(linearZoom)
                    currentOnControlStatus("Đã giảm zoom camera.")
                },
                enabled = boundCamera != null,
                shape = RoundedCornerShape(14.dp)
            ) { Text("− ZOOM") }
            OutlinedButton(
                onClick = {
                    focusAt(previewView.width / 2f, previewView.height / 2f)
                },
                enabled = boundCamera != null,
                shape = RoundedCornerShape(14.dp)
            ) { Text("LẤY NÉT") }
            OutlinedButton(
                onClick = {
                    linearZoom = (linearZoom + 0.20f).coerceIn(0f, 1f)
                    boundCamera?.cameraControl?.setLinearZoom(linearZoom)
                    currentOnControlStatus("Đã tăng zoom camera.")
                },
                enabled = boundCamera != null,
                shape = RoundedCornerShape(14.dp)
            ) { Text("+ ZOOM") }
            OutlinedButton(
                onClick = {
                    val camera = boundCamera ?: return@OutlinedButton
                    val next = !torchEnabled
                    camera.cameraControl.enableTorch(next)
                    torchEnabled = next
                    currentOnControlStatus(if (next) "Đã bật đèn hỗ trợ quét." else "Đã tắt đèn hỗ trợ quét.")
                },
                enabled = boundCamera != null && hasFlash,
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (torchEnabled) "TẮT ĐÈN" else "ĐÈN") }
        }
    }

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
                adjustmentGate.reset()
                adjustmentState = CameraAdjustmentGate.State.IDLE
                (runner as? StableRegionVisualModelRunner)?.reset()

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
                            val quality = frameGate.assess(frame)
                            val found = if (modelReady) runner.scanRegion(frame) else emptyList()
                            val stableFound = found.filter { it.isStableRegionCandidate() }
                            mainExecutor.execute {
                                if (!disposed) {
                                    currentOnQuality(quality)
                                    currentOnDetections(found)
                                }
                            }

                            val adjustmentReady = adjustmentGate.ready(SystemClock.elapsedRealtimeNanos())
                            val cameraState = adjustmentGate.state(SystemClock.elapsedRealtimeNanos())
                            mainExecutor.execute {
                                if (!disposed && adjustmentState != cameraState) adjustmentState = cameraState
                            }
                            val captureEligible = adjustmentReady && (!modelReady || stableFound.isNotEmpty())
                            if (!captureInFlight && frameGate.shouldCapture(timestamp, frame, captureEligible)) {
                                captureInFlight = true
                                val capturedAt = System.currentTimeMillis()
                                val captureDetections = if (modelReady) stableFound.toList() else emptyList()
                                val descriptor = if (modelReady) runner.status().descriptor else null
                                val hasCandidates = descriptor != null && captureDetections.isNotEmpty()
                                val folder = File(
                                    context.filesDir,
                                    if (hasCandidates) "region-scans/classified" else "region-scans/pending"
                                ).apply { mkdirs() }
                                val file = File(folder, "region-$capturedAt.jpg")
                                imageCapture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(file).build(),
                                    mainExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            captureInFlight = false
                                            if (disposed) return
                                            runCatching {
                                                metadataExecutor.execute {
                                                    runCatching {
                                                        RegionScanCaptureMetadata.write(
                                                            imageFile = file,
                                                            capturedAtEpochMs = capturedAt,
                                                            frameTimestampNanos = timestamp,
                                                            model = descriptor,
                                                            detections = captureDetections
                                                        )
                                                    }.onSuccess {
                                                        mainExecutor.execute {
                                                            if (!disposed) currentOnAutoCaptured(file.absolutePath)
                                                        }
                                                    }.onFailure { failure ->
                                                        mainExecutor.execute {
                                                            if (!disposed) currentOnError(
                                                                failure.message ?: "Đã chụp ảnh nhưng không thể lưu metadata phân loại"
                                                            )
                                                        }
                                                    }
                                                }
                                            }.onFailure { failure ->
                                                if (!disposed) currentOnError(
                                                    failure.message ?: "Không thể lên lịch lưu metadata ảnh vùng"
                                                )
                                            }
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

                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    analysis
                )
                boundCamera = camera
                hasFlash = camera.cameraInfo.hasFlashUnit()
                torchEnabled = false
                linearZoom = 0f
            }.onFailure { failure -> currentOnError(failure.message ?: "Không thể khởi động camera") }
        }, mainExecutor)

        onDispose {
            disposed = true
            frameGate.reset()
            adjustmentGate.reset()
            adjustmentState = CameraAdjustmentGate.State.IDLE
            (runner as? StableRegionVisualModelRunner)?.reset()
            boundCamera?.cameraControl?.enableTorch(false)
            boundCamera = null
            torchEnabled = false
            runCatching { if (providerFuture.isDone) providerFuture.get().unbindAll() }
            executor.shutdownNow()
            metadataExecutor.shutdownNow()
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
