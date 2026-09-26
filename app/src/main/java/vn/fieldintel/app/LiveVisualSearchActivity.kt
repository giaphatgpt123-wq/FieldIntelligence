package vn.fieldintel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.fieldintel.feature.emergency.FieldColors
import vn.fieldintel.feature.emergency.InstalledVisualModelRunner
import vn.fieldintel.feature.emergency.RegionScanAutoCapturePanel
import vn.fieldintel.feature.emergency.StableRegionVisualModelRunner
import vn.fieldintel.feature.emergency.StillImageRecognitionPanel
import vn.fieldintel.feature.emergency.VisualModelImportManager

/** Direct entry point for validating live region scan and still-image recognition on device. */
class LiveVisualSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var runnerGeneration by remember { mutableIntStateOf(0) }
            var mode by remember { mutableStateOf("LIVE") }
            var installStatus by remember {
                mutableStateOf("Cài gói model offline đã kiểm tra SHA-256. Hỗ trợ detector một tầng hoặc detector + classifier hai tầng.")
            }
            val scope = rememberCoroutineScope()
            val importer = remember { VisualModelImportManager(applicationContext) }
            val runner = remember(runnerGeneration) {
                StableRegionVisualModelRunner(InstalledVisualModelRunner(applicationContext))
            }
            DisposableEffect(runner) { onDispose { runner.close() } }

            val selectModelBundle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    scope.launch {
                        installStatus = "Đang kiểm tra SHA-256 và cấu trúc gói model…"
                        val result = withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.use { importer.install(it) }
                                ?: VisualModelImportManager.Result(false, "Không thể mở gói model đã chọn.")
                        }
                        installStatus = result.message
                        if (result.installed) runnerGeneration += 1
                    }
                }
            }

            MaterialTheme(colorScheme = FieldColors) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF07181D))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF102A31)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("MODEL NHẬN DẠNG", style = MaterialTheme.typography.titleMedium)
                            Text(
                                installStatus,
                                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                                color = Color(0xFFB8C8CC)
                            )
                            Button(
                                onClick = { selectModelBundle.launch(arrayOf("application/zip", "application/octet-stream")) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("CÀI GÓI MODEL OFFLINE")
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { mode = "LIVE" },
                            enabled = mode != "LIVE",
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("QUÉT CAMERA", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { mode = "PHOTO" },
                            enabled = mode != "PHOTO",
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("ẢNH / THƯ VIỆN", fontWeight = FontWeight.Bold) }
                    }

                    if (mode == "LIVE") {
                        RegionScanAutoCapturePanel(runner = runner)
                    } else {
                        StillImageRecognitionPanel(modelGeneration = runnerGeneration)
                    }
                }
            }
        }
    }
}
