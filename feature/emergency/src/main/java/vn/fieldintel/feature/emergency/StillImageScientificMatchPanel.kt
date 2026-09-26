package vn.fieldintel.feature.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a selected visual candidate to an exact scientific-name taxonomy record when available.
 * Visual confidence is intentionally kept separate from taxonomy, specialist evidence and safety.
 */
@Composable
fun StillImageScientificMatchPanel(detection: VisualDetection) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val store = remember(context) { ScientificLibraryStore(context.applicationContext) }
    val scientificName = detection.scientificName?.trim().orEmpty()
    var record by remember(detection) { mutableStateOf<SpeciesRecord?>(null) }
    var loading by remember(detection) { mutableStateOf(scientificName.isNotBlank()) }
    var lookupFinished by remember(detection) { mutableStateOf(false) }

    LaunchedEffect(scientificName) {
        if (scientificName.isBlank()) {
            loading = false
            lookupFinished = true
            record = null
            return@LaunchedEffect
        }
        loading = true
        lookupFinished = false
        val starter = SpeciesCatalog.records.firstOrNull {
            it.scientificName.equals(scientificName, ignoreCase = true)
        }
        record = starter ?: withContext(Dispatchers.IO) {
            store.findByScientificNames(listOf(scientificName), limit = 8)
                .firstOrNull { it.scientificName.equals(scientificName, ignoreCase = true) }
        }
        loading = false
        lookupFinished = true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102C33)),
        border = BorderStroke(1.dp, Color(0x3345E58C))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("ỨNG VIÊN ĐÃ CHỌN", fontWeight = FontWeight.Black)
            Text(detection.label, fontWeight = FontWeight.Bold)
            if (scientificName.isNotBlank()) {
                Text(scientificName, color = FieldColors.primary, fontWeight = FontWeight.Bold)
            }
            Text(
                "Độ tin cậy hình ảnh ${(detection.confidence * 100).toInt()}% • không phải kết luận định danh hay an toàn.",
                color = FieldColors.onSurfaceVariant
            )

            when {
                scientificName.isBlank() -> Text(
                    "Model chưa cung cấp tên khoa học chuẩn nên không tự nối nhãn này vào hồ sơ taxonomy.",
                    color = Color(0xFFFFD166)
                )
                loading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 22.dp), strokeWidth = 2.dp)
                    Text("Đang đối chiếu thư viện khoa học offline…", color = FieldColors.onSurfaceVariant)
                }
                record != null -> {
                    val matched = record!!
                    Text("HỒ SƠ TAXONOMY", fontWeight = FontWeight.Black, color = FieldColors.primary)
                    Text(matched.vietnameseName, fontWeight = FontWeight.Bold)
                    Text(matched.scientificName, color = FieldColors.primary)
                    Text("Nhóm: ${matched.group}", color = FieldColors.onSurfaceVariant)
                    Text("Nguồn: ${matched.sourceName}", fontWeight = FontWeight.Bold)
                    Text(matched.sourceScope, color = FieldColors.onSurfaceVariant)
                    if (matched.sourceUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { uriHandler.openUri(matched.sourceUrl) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("MỞ NGUỒN KHOA HỌC") }
                    }
                    SpecialistEvidencePanel(speciesId = matched.id, scientificName = matched.scientificName)
                    InteractionSafetyPanel(matched.scientificName)
                }
                lookupFinished -> Text(
                    "Chưa tìm thấy hồ sơ taxonomy khớp chính xác trong dữ liệu offline hiện có. Không tự thay bằng loài gần giống.",
                    color = Color(0xFFFFD166)
                )
            }
        }
    }
}
