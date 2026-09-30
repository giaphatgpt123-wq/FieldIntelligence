package vn.fieldintel.feature.emergency

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a selected visual candidate to the same species in the offline taxonomy pack.
 * Canonical model labels may omit authorship; the resolver accepts that representation without
 * falling back to a neighbouring species. Visual confidence remains separate from taxonomy/safety.
 *
 * A weak visual candidate is deliberately not resolved into a taxonomy profile. This prevents the
 * UI from turning a low-confidence model suggestion into what looks like a confirmed species page.
 */
@Composable
fun StillImageScientificMatchPanel(detection: VisualDetection) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val store = remember(context) { ScientificLibraryStore(context.applicationContext) }
    val mediaStore = remember(context) { ScientificMediaStore(context.applicationContext) }
    val scientificName = detection.scientificName?.trim().orEmpty()
    val strongEnoughForProfile = detection.confidence >= PlantRecognitionPolicy.STRONG_SCORE
    var record by remember(detection) { mutableStateOf<SpeciesRecord?>(null) }
    var localMedia by remember(detection) { mutableStateOf<List<ScientificLocalMedia>>(emptyList()) }
    var loading by remember(detection) { mutableStateOf(strongEnoughForProfile && scientificName.isNotBlank()) }
    var lookupFinished by remember(detection) { mutableStateOf(false) }

    LaunchedEffect(scientificName, strongEnoughForProfile) {
        if (!strongEnoughForProfile || scientificName.isBlank()) {
            loading = false
            lookupFinished = true
            record = null
            localMedia = emptyList()
            return@LaunchedEffect
        }
        loading = true
        lookupFinished = false
        val matched = withContext(Dispatchers.IO) {
            ScientificNameResolver.resolve(store, scientificName)
        }
        record = matched
        localMedia = if (matched != null) {
            withContext(Dispatchers.IO) { mediaStore.loadForRecord(matched.id, 3) }
        } else emptyList()
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
                !strongEnoughForProfile -> {
                    Text(
                        "CHƯA MỞ HỒ SƠ KHOA HỌC • ứng viên hình ảnh chưa đạt ngưỡng mạnh ${"%.0f".format(PlantRecognitionPolicy.STRONG_SCORE * 100)}%.",
                        color = Color(0xFFFFD166),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Hãy chụp gần hơn, rõ lá/hoa/quả hoặc xem các ứng viên khác của vùng. Ứng dụng không tự biến gợi ý mơ hồ thành đúng loài.",
                        color = FieldColors.onSurfaceVariant
                    )
                }
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
                    Text("HỒ SƠ TAXONOMY ĐỐI CHIẾU", fontWeight = FontWeight.Black, color = FieldColors.primary)
                    Text(matched.vietnameseName, fontWeight = FontWeight.Bold)
                    Text(matched.scientificName, color = FieldColors.primary)
                    Text("Nhóm: ${matched.group} • ${localMedia.size} ảnh tham chiếu offline", color = FieldColors.onSurfaceVariant)
                    CompactReferenceMedia(localMedia)
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
                    "Chưa tìm thấy đúng loài trong taxonomy offline. Ứng dụng không tự thay bằng loài gần giống hoặc cùng chi.",
                    color = Color(0xFFFFD166)
                )
            }
        }
    }
}

@Composable
private fun CompactReferenceMedia(media: List<ScientificLocalMedia>) {
    val item = media.firstOrNull() ?: return
    val bitmap by produceState<Bitmap?>(null, item.sha256) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(item.bytes, 0, item.bytes.size)
        }
    }
    if (bitmap != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF071A20))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Image(
                    bitmap!!.asImageBitmap(),
                    contentDescription = "Ảnh tham chiếu khoa học offline",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 300.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    "Ảnh tham chiếu • ${item.license}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    color = FieldColors.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
