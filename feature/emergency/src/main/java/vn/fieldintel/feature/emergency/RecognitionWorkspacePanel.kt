package vn.fieldintel.feature.emergency

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The main app's recognition destination. Camera use cases unbind when this destination leaves composition. */
@Composable
fun RecognitionWorkspacePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modelGeneration by remember { mutableIntStateOf(0) }
    var showCamera by remember { mutableStateOf(true) }
    var installStatus by remember { mutableStateOf("") }
    val importer = remember(context) { VisualModelImportManager(context.applicationContext) }
    val runner = remember(context, modelGeneration) {
        StableRegionVisualModelRunner(InstalledVisualModelRunner(context.applicationContext))
    }
    DisposableEffect(runner) { onDispose { runner.close() } }
    val model = remember(runner) { runner.status() }

    val selectModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            installStatus = "Đang kiểm tra gói model…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { importer.install(it) }
                        ?: VisualModelImportManager.Result(false, "Không thể mở gói model.")
                }.getOrElse { VisualModelImportManager.Result(false, "Lỗi đọc gói model: ${it.message}") }
            }
            installStatus = result.message
            if (result.installed) modelGeneration++
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { showCamera = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showCamera) FieldColors.primary else Color(0xFF245860),
                    contentColor = if (showCamera) FieldColors.onPrimary else Color.White
                ),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("📷 QUÉT CAMERA", fontWeight = FontWeight.Bold) }
            Button(
                onClick = { showCamera = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!showCamera) FieldColors.primary else Color(0xFF245860),
                    contentColor = if (!showCamera) FieldColors.onPrimary else Color.White
                ),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("ẢNH TỪ MÁY", fontWeight = FontWeight.Bold) }
        }

        if (showCamera) {
            RegionScanAutoCapturePanel(runner = runner)
        } else {
            StillImageRecognitionPanel(modelGeneration = modelGeneration)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF245860), contentColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MODEL NHẬN DẠNG OFFLINE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (model.availability == VisualModelAvailability.READY)
                        "Model đã sẵn sàng. Kết quả camera chỉ là ứng viên để đối chiếu."
                    else "Chưa có model hợp lệ. Camera vẫn chụp và lưu bằng chứng; chưa tự gọi tên loài.",
                    color = Color(0xFFE0F3EE)
                )
                if (installStatus.isNotBlank()) Text(installStatus, color = Color(0xFFFFD166))
                OutlinedButton(
                    onClick = { selectModel.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("CÀI GÓI MODEL OFFLINE", color = Color.White) }
            }
        }
    }
}
