package vn.fieldintel.feature.emergency

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import java.util.concurrent.Executors

private const val MIN_INFERENCE_INTERVAL_NANOS = 250_000_000L

/**
 * Live CameraX preview and frame stream for target-oriented field search.
 *
 * The analyzer only invokes a model runner whose status is READY. Camera frames by themselves never
 * become a taxon, edibility, toxicity or treatment conclusion.
 */
@Composable
fun LiveVisualSearchCamera(
    active: Boolean,
    target: LiveVisualSearchTarget? = null,
    runner: LiveVisualModelRunner = NoVerifiedLiveVisualModel,
    modifier: Modifier = Modifier,
    onFrame: (LiveFrameInfo) -> Unit = {},
    onDetections: (List<VisualDetection>) -> Unit = {},
    onCameraError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnDetections by rememberUpdatedState(onDetections)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(active, lifecycleOwner, previewView, target?.normalizedQuery, target?.scientificName, runner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var lastInferenceTimestamp = 0L
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val ready = runner.status().availability == VisualModelAvailability.READY
        val activeTarget = target?.takeIf { it.isValid }

        providerFuture.addListener({
            if (disposed) return@addListener
            runCatching {
                val provider = providerFuture.get()
                provider.unbindAll()
                if (!active) return@runCatching

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val timestamp = image.imageInfo.timestamp
                        val info = LiveFrameInfo(
                            timestampNanos = timestamp,
                            width = image.width,
                            height = image.height,
                            rotationDegrees = image.imageInfo.rotationDegrees
                        )
                        mainExecutor.execute { if (!disposed) currentOnFrame(info) }

                        if (
                            ready && activeTarget != null &&
                            timestamp - lastInferenceTimestamp >= MIN_INFERENCE_INTERVAL_NANOS
                        ) {
                            lastInferenceTimestamp = timestamp
                            val frame = image.toOwnedFrameData()
                            val detections = runner.detect(frame, activeTarget)
                            mainExecutor.execute {
                                if (!disposed) currentOnDetections(detections)
                            }
                        }
                    } catch (failure: Throwable) {
                        mainExecutor.execute {
                            if (!disposed) currentOnCameraError(failure.message ?: "Lỗi phân tích khung hình camera")
                        }
                    } finally {
                        image.close()
                    }
                }
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }.onFailure { failure ->
                currentOnCameraError(failure.message ?: "Không thể khởi động camera")
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            runCatching { if (providerFuture.isDone) providerFuture.get().unbindAll() }
            analysisExecutor.shutdownNow()
        }
    }
}

private fun androidx.camera.core.ImageProxy.toOwnedFrameData(): LiveVisualFrameData {
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

/** First usable UI shell for the requested “tìm trong thực tế” mode. */
@Composable
fun LiveVisualSearchPanel(
    initialTarget: String = "",
    modelReady: Boolean = false,
    runner: LiveVisualModelRunner = NoVerifiedLiveVisualModel
) {
    val context = LocalContext.current
    val modelStatus = remember(runner) { runner.status() }
    val effectiveModelReady = modelReady || modelStatus.availability == VisualModelAvailability.READY
    val tracker = remember { LiveVisualTemporalTracker() }
    var targetText by remember { mutableStateOf(initialTarget) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var scanning by remember { mutableStateOf(false) }
    var uiState by remember { mutableStateOf(LiveVisualSearchUiState()) }
    var detections by remember { mutableStateOf(emptyList<VisualDetection>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (!granted) {
            scanning = false
            uiState = uiState.copy(
                phase = LiveVisualSearchPhase.ERROR,
                message = "Cần quyền camera để quét môi trường xung quanh."
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2329)),
            border = BorderStroke(1.dp, Color(0x3345E58C))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("TÌM TRONG THỰC TẾ", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Nhập loài cần tìm, sau đó lia camera chậm quanh môi trường. Hệ thống chỉ báo ứng viên khi có mô hình đã kiểm chứng.",
                    color = FieldColors.onSurfaceVariant
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ví dụ: rau má / Centella asiatica") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )

                if (cameraGranted && scanning) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(430.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Black,
                        border = BorderStroke(1.dp, Color(0x5545E58C))
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            LiveVisualSearchCamera(
                                active = true,
                                target = uiState.target,
                                runner = runner,
                                modifier = Modifier.fillMaxSize(),
                                onFrame = { frame -> uiState = LiveVisualSearchReducer.frame(uiState, frame) },
                                onDetections = { found ->
                                    detections = found
                                    val target = uiState.target
                                    if (target != null) {
                                        tracker.observe(target, found)?.let { candidate ->
                                            uiState = LiveVisualSearchReducer.candidate(uiState, candidate)
                                        }
                                    }
                                },
                                onCameraError = { message ->
                                    scanning = false
                                    uiState = uiState.copy(phase = LiveVisualSearchPhase.ERROR, message = message)
                                }
                            )
                            LiveVisualOverlay(
                                detections = detections,
                                target = uiState.target,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xCC081A1F)
                            ) {
                                Text(
                                    if (effectiveModelReady) "ĐANG QUÉT" else "CAMERA LIVE • MODEL CHƯA SẴN SÀNG",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (effectiveModelReady) FieldColors.primary else Color(0xFFFFD166)
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
                                if (!cameraGranted) "Cấp quyền camera để bắt đầu quét trực tiếp."
                                else "Nhập mục tiêu và bấm BẮT ĐẦU QUÉT.",
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
                                val target = LiveVisualSearchTarget(targetText)
                                if (target.isValid) {
                                    tracker.reset()
                                    detections = emptyList()
                                    uiState = LiveVisualSearchReducer.start(target, effectiveModelReady)
                                    scanning = true
                                } else {
                                    uiState = uiState.copy(
                                        phase = LiveVisualSearchPhase.ERROR,
                                        message = "Nhập tên loài cần tìm, ít nhất 2 ký tự."
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(if (!cameraGranted) "CẤP QUYỀN CAMERA" else "BẮT ĐẦU QUÉT")
                    }
                    if (scanning) {
                        OutlinedButton(
                            onClick = {
                                scanning = false
                                tracker.reset()
                                detections = emptyList()
                                uiState = LiveVisualSearchUiState()
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("DỪNG") }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF132D32)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TRẠNG THÁI: ${uiState.phase.name}", style = MaterialTheme.typography.labelLarge)
                Text(uiState.message, color = FieldColors.onSurfaceVariant)
                if (!effectiveModelReady) {
                    Text(modelStatus.message, color = Color(0xFFFFD166), style = MaterialTheme.typography.bodySmall)
                }
                uiState.lastFrame?.let { frame ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Camera ${frame.width}×${frame.height} • xoay ${frame.rotationDegrees}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = FieldColors.onSurfaceVariant
                    )
                }
                if (detections.isNotEmpty()) {
                    Text(
                        "${detections.size} đối tượng từ model • cần ổn định qua nhiều khung hình trước khi báo ứng viên",
                        style = MaterialTheme.typography.bodySmall,
                        color = FieldColors.onSurfaceVariant
                    )
                }
            }
        }
    }
}
