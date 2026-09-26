package vn.fieldintel.feature.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SpecialistEvidencePanel(speciesId: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val store = remember(context) { SpecialistEvidenceStore(context.applicationContext) }
    val packStatus = remember(speciesId) { store.status() }
    val packRecords = remember(speciesId) { store.forSpecies(speciesId) }
    val records = if (packRecords.isNotEmpty()) packRecords else SpecialistEvidenceCatalog.forSpecies(speciesId)
    if (records.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("BẰNG CHỨNG CHUYÊN NGÀNH", fontWeight = FontWeight.Black)
        Text(
            if (packRecords.isNotEmpty()) "Nguồn dữ liệu: gói evidence offline • ${packStatus.recordCount} hồ sơ"
            else "Nguồn dữ liệu: bộ lõi đã kiểm duyệt trong ứng dụng",
            color = FieldColors.onSurfaceVariant
        )
        records.forEach { record ->
            val accent = when (record.domain) {
                EvidenceDomain.TOXICOLOGY -> Color(0xFFFF8A80)
                EvidenceDomain.VIETNAM_TRADITIONAL_MEDICINE -> Color(0xFFFFC857)
                EvidenceDomain.HERBAL_MEDICINE_MONOGRAPH -> FieldColors.primary
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF132D32)),
                border = BorderStroke(1.dp, accent.copy(alpha = .35f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(record.title, fontWeight = FontWeight.Black, color = accent)
                    Text(record.statement, fontWeight = FontWeight.Medium)
                    if (record.plantPart.isNotBlank()) Text("Bộ phận: ${record.plantPart}", color = FieldColors.onSurfaceVariant)
                    Text("Nguồn: ${record.sourceName}", fontWeight = FontWeight.Bold)
                    if (record.sourceRecord.isNotBlank()) Text(record.sourceRecord, color = FieldColors.onSurfaceVariant)
                    Text(record.scopeNote, color = FieldColors.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { uriHandler.openUri(record.sourceUrl) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("MỞ NGUỒN CHÍNH THỨC") }
                }
            }
        }
    }
}
