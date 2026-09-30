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
import androidx.compose.material3.Surface
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
 * Shows the complete Top-N decision for one selected region. Taxonomy is opened only when the
 * verdict of the whole region is STRONG_CANDIDATE; a high primary score alone is not sufficient.
 */
@Composable
fun StillImageScientificMatchPanel(detection: VisualDetection) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val store = remember(context) { ScientificLibraryStore(context.applicationContext) }
    val mediaStore = remember(context) { ScientificMediaStore(context.applicationContext) }
    val group = RegionRecognitionSelectionRegistry.find(detection)
    val candidates = group?.candidates.orEmpty().ifEmpty { listOf(detection) }
    val verdict = group?.verdict ?: PlantRecognitionPolicy.evaluate(
        candidates.map { RecognitionScore(it.scientificName ?: it.label, it.confidence) }
    ).verdict
    val primary = candidates.first()
    val scientificName = primary.scientificName?.trim().orEmpty()
    val canOpenProfile = verdict == RecognitionVerdict.STRONG_CANDIDATE

    var record by remember(primary, verdict) { mutableStateOf<SpeciesRecord?>(null) }
    var localMedia by remember(primary, verdict) { mutableStateOf<List<ScientificLocalMedia>>(emptyList()) }
    var loading by remember(primary, verdict) { mutableStateOf(canOpenProfile && scientificName.isNotBlank()) }
    var lookupFinished by remember(primary, verdict) { mutableStateOf(false) }

    LaunchedEffect(scientificName, verdict) {
        if (!canOpenProfile || scientificName.isBlank()) {
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
        border = BorderStroke(
            1.dp,
            when (verdict) {
                RecognitionVerdict.STRONG_CANDIDATE -> Color(0x6645E58C)
                RecognitionVerdict.AMBIGUOUS -> Color(0x66FFD166)
                RecognitionVerdict.UNKNOWN -> Color(0x66FF8A80)
            }
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("KẾT QUẢ VÙNG ĐÃ CHỌN", fontWeight = FontWeight.Black)
            Text(
                recognitionVerdictLabel(verdict),
                color = when (verdict) {
                    RecognitionVerdict.STRONG_CANDIDATE -> FieldColors.primary
                    RecognitionVerdict.AMBIGUOUS -> Color(0xFFFFD166)
                    RecognitionVerdict.UNKNOWN -> Color(0xFFFF8A80)
                },
                fontWeight = FontWeight.Black
            )

            candidates.take(PlantRecognitionPolicy.MAX_CANDIDATES).forEachIndexed { index, candidate ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (index == 0) Color(0xFF16353B) else Color(0xFF0C252B)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Ứng viên ${index + 1}", fontWeight = FontWeight.Bold, color = FieldColors.primary)
                        Text(candidate.label, fontWeight = FontWeight.Bold)
                        candidate.scientificName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = FieldColors.onSurfaceVariant)
                        }
                        Text(
                            "${(candidate.confidence * 100).toInt()}% • điểm model",
                            color = FieldColors.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                "Điểm model chỉ dùng để xếp hạng ứng viên, không phải xác nhận định danh hay an toàn.",
                color = FieldColors.onSurfaceVariant
            )

            when {
                !canOpenProfile -> {
                    Text(
                        "CHƯA MỞ HỒ SƠ KHOA HỌC",
                        color = Color(0xFFFFD166),
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (verdict == RecognitionVerdict.AMBIGUOUS) {
                            "Các ứng viên còn quá gần nhau. Hãy chụp thêm góc lá, thân, hoa hoặc quả để tách loài."
                        } else {
                            "Chưa có ứng viên đủ điều kiện. Hãy chụp gần hơn, đủ sáng và giữ mẫu chính rõ trong khung."
                        },
                        color = FieldColors.onSurfaceVariant
                    )
                }
                scientificName.isBlank() -> Text(
                    "Ứng viên mạnh nhưng model chưa cung cấp tên khoa học chuẩn nên không tự nối vào taxonomy.",
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
