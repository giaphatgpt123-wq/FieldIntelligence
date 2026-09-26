package vn.fieldintel.feature.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InteractionSafetyPanel(query: String) {
    val uriHandler = LocalUriHandler.current
    val interactions = InteractionCatalog.findForEntity(query)
    val speciesId = SpeciesCatalog.records.firstOrNull { it.scientificName == query }?.id

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("XUNG ĐỘT & CẢNH BÁO", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)

        if (interactions.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF132D33),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
            ) {
                Text(
                    "Chưa có tương tác đã được xác minh cho mục này. Không có dữ liệu không đồng nghĩa với an toàn.",
                    Modifier.fillMaxWidth().padding(14.dp),
                    color = FieldColors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            interactions.forEach { item ->
                val accent = when (item.severity) {
                    InteractionSeverity.CRITICAL -> Color(0xFFFF6B6B)
                    InteractionSeverity.HIGH -> Color(0xFFFF8A80)
                    InteractionSeverity.CAUTION -> Color(0xFFFFC857)
                    InteractionSeverity.INFO -> Color(0xFF78DCE8)
                    InteractionSeverity.UNKNOWN -> Color(0xFFB0BEC5)
                }
                val evidenceLabel = when (item.evidence) {
                    EvidenceLevel.CONFIRMED -> "ĐÃ XÁC NHẬN"
                    EvidenceLevel.PROBABLE -> "CÓ KHẢ NĂNG"
                    EvidenceLevel.POSSIBLE -> "CÓ THỂ"
                    EvidenceLevel.TRADITIONAL_CLAIM -> "GHI NHẬN TRUYỀN THỐNG"
                    EvidenceLevel.INSUFFICIENT_EVIDENCE -> "CHƯA ĐỦ BẰNG CHỨNG"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102A31)),
                    border = BorderStroke(1.dp, accent.copy(alpha = .35f))
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(999.dp), color = accent.copy(alpha = .14f)) {
                                Text(evidenceLabel, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text("${item.a.label}  ×  ${item.b.label}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        Text(item.summary, color = FieldColors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        item.mechanism?.takeIf { it.isNotBlank() }?.let {
                            Text("Cơ chế: $it", color = FieldColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (item.atRisk.isNotEmpty()) {
                            Text("Nhóm cần thận trọng: ${item.atRisk.joinToString()}", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Khuyến nghị: ${item.action}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        item.sources.forEach { source ->
                            OutlinedButton(
                                onClick = { uriHandler.openUri(source.url) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("NGUỒN: ${source.authority}", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        SpecialistEvidencePanel(speciesId = speciesId.orEmpty(), scientificName = query)
    }
}
