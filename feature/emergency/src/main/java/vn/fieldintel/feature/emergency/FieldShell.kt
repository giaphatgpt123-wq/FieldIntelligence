package vn.fieldintel.feature.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val FieldColors = darkColorScheme(
    primary = Color(0xFF45E58C),
    onPrimary = Color(0xFF03261A),
    secondary = Color(0xFF9AD9C5),
    background = Color(0xFF06191E),
    onBackground = Color(0xFFF5FAF7),
    surface = Color(0xFF102B32),
    onSurface = Color(0xFFF5FAF7),
    surfaceVariant = Color(0xFF193740),
    onSurfaceVariant = Color(0xFFD4E6E0),
    outline = Color(0xFF6A8587)
)

val FieldBackground = Brush.verticalGradient(
    listOf(Color(0xFF08252C), Color(0xFF06171C), Color(0xFF0B2026))
)

private data class HomeTileStyle(val top: Color, val bottom: Color, val accent: Color)

private fun tileStyle(section: AppSection): HomeTileStyle = when (section) {
    AppSection.FIELD -> HomeTileStyle(Color(0xFF176244), Color(0xFF0C3A29), Color(0xFF58EE9B))
    AppSection.RECOGNITION -> HomeTileStyle(Color(0xFF197381), Color(0xFF104A55), Color(0xFF79EEF0))
    AppSection.SURVIVAL -> HomeTileStyle(Color(0xFF945D33), Color(0xFF5B351F), Color(0xFFFFBD72))
    AppSection.EMERGENCY -> HomeTileStyle(Color(0xFFA64A53), Color(0xFF602A31), Color(0xFFFF8990))
    AppSection.PREP -> HomeTileStyle(Color(0xFF655EA3), Color(0xFF3A356A), Color(0xFFC4B8FF))
    else -> HomeTileStyle(Color(0xFF2A6FA4), Color(0xFF173F64), Color(0xFF99D3FF))
}

private fun sectionGlyph(section: AppSection): String = when (section) {
    AppSection.FIELD -> "⌖"
    AppSection.RECOGNITION -> "◎"
    AppSection.SURVIVAL -> "△"
    AppSection.EMERGENCY -> "SOS"
    AppSection.PREP -> "✓"
    AppSection.LIBRARY -> "▤"
    AppSection.TRAINING -> "◇"
    AppSection.SETTINGS -> "⚙"
}

@Composable
fun FieldHomePanel(position: FieldPositionUi?, onSelect: (AppSection) -> Unit) {
    val context = LocalContext.current
    val logoId = context.resources.getIdentifier("official_survival_logo", "drawable", context.packageName)

    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12363A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth().height(334.dp)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF86C4D3), Color(0xFF3C7078), Color(0xFF214B45), Color(0xFF0B2926))
                        )
                    )
            ) {
                drawCircle(Color(0x55F8F3C2), radius = 34f, center = Offset(size.width * .78f, size.height * .18f))

                val far = Path().apply {
                    moveTo(0f, size.height * .58f)
                    lineTo(size.width * .18f, size.height * .31f)
                    lineTo(size.width * .34f, size.height * .50f)
                    lineTo(size.width * .54f, size.height * .23f)
                    lineTo(size.width * .70f, size.height * .44f)
                    lineTo(size.width * .84f, size.height * .32f)
                    lineTo(size.width, size.height * .58f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(far, Color(0xFF47776F))

                val mid = Path().apply {
                    moveTo(0f, size.height * .70f)
                    lineTo(size.width * .25f, size.height * .45f)
                    lineTo(size.width * .43f, size.height * .63f)
                    lineTo(size.width * .66f, size.height * .39f)
                    lineTo(size.width * .83f, size.height * .59f)
                    lineTo(size.width, size.height * .49f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(mid, Color(0xFF27564B))

                val near = Path().apply {
                    moveTo(0f, size.height * .83f)
                    lineTo(size.width * .28f, size.height * .60f)
                    lineTo(size.width * .48f, size.height * .76f)
                    lineTo(size.width * .74f, size.height * .52f)
                    lineTo(size.width, size.height * .75f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(near, Color(0xFF123A31))

                val river = Path().apply {
                    moveTo(size.width * .25f, size.height)
                    cubicTo(size.width * .60f, size.height * .82f, size.width * .39f, size.height * .72f, size.width * .68f, size.height * .56f)
                }
                drawPath(river, Color(0xFF78DCE8), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14f))
            }

            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x12051418), Color(0x26061718), Color(0xA805171A), Color(0xF005171A))
                    )
                )
            )

            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xD910382F),
                    border = BorderStroke(1.dp, Color(0x773FEA91)),
                    shadowElevation = 8.dp
                ) {
                    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                        if (logoId != 0) {
                            Image(painterResource(logoId), "Biểu trưng la bàn núi rừng sông", Modifier.size(86.dp))
                        } else {
                            Text("⌖", style = MaterialTheme.typography.displayMedium, color = FieldColors.primary)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "FIELD INTELLIGENCE",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Khám phá • Nhận biết • Sinh tồn an toàn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE4F2EC),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                Surface(
                    color = Color(0xDF0A2C2D),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0x445DE9A3))
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text(if (position == null) "○" else "●", color = if (position == null) Color(0xFFFFD166) else FieldColors.primary)
                        Text(
                            if (position == null) "GPS đang chờ tín hiệu • dữ liệu offline vẫn sẵn sàng"
                            else "GPS %.5f, %.5f  •  ±%.0f m".format(position.latitude, position.longitude, position.accuracyM),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF0F7F4),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("CHỌN CHỨC NĂNG", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Công cụ chính ngoài thực địa", style = MaterialTheme.typography.bodyMedium, color = FieldColors.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(999.dp), color = Color(0x1A45E58C)) {
            Text(
                "OFFLINE",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                color = FieldColors.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }

    listOf(
        listOf(AppSection.FIELD, AppSection.RECOGNITION),
        listOf(AppSection.SURVIVAL, AppSection.EMERGENCY),
        listOf(AppSection.PREP, AppSection.LIBRARY)
    ).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { section ->
                val style = tileStyle(section)
                Card(
                    onClick = { onSelect(section) },
                    modifier = Modifier.weight(1f).heightIn(min = 158.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, style.accent.copy(alpha = .35f))
                ) {
                    Box(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(style.top, style.bottom))).padding(16.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(17.dp), color = Color(0x28000000)) {
                            Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    sectionGlyph(section),
                                    style = if (section == AppSection.EMERGENCY) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(section.label, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .86f), maxLines = 2)
                        }
                    }
                }
            }
        }
    }

    Surface(
        onClick = { onSelect(AppSection.TRAINING) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF12333A),
        border = BorderStroke(1.dp, Color(0x663EDB86))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Color(0x223EDB86)) {
                Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                    Text(sectionGlyph(AppSection.TRAINING), style = MaterialTheme.typography.titleLarge, color = FieldColors.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Huấn luyện", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Mô phỏng tình huống và kiểm tra kỹ năng", style = MaterialTheme.typography.bodyMedium, color = FieldColors.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = FieldColors.primary)
        }
    }
}

@Composable
fun FieldBottomBar(current: AppSection?, onSelect: (AppSection?) -> Unit) {
    Surface(
        color = Color(0xFF081F25),
        shadowElevation = 14.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0x3315E58D))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(94.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomItem("⌂", "Trang chủ", current == null, false) { onSelect(null) }
            BottomItem("⌖", "Bản đồ", current == AppSection.FIELD, false) { onSelect(AppSection.FIELD) }
            BottomItem("◎", "Quét", current == AppSection.RECOGNITION, true) { onSelect(AppSection.RECOGNITION) }
            BottomItem("▤", "Lưu trữ", current == AppSection.LIBRARY, false) { onSelect(AppSection.LIBRARY) }
            BottomItem("⚙", "Cài đặt", current == AppSection.SETTINGS, false) { onSelect(AppSection.SETTINGS) }
        }
    }
}

@Composable
private fun RowScope.BottomItem(
    icon: String,
    label: String,
    selected: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 72.dp),
        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(
                shape = CircleShape,
                color = when {
                    emphasized -> FieldColors.primary
                    selected -> Color(0x303FEA91)
                    else -> Color.Transparent
                },
                border = if (emphasized) BorderStroke(3.dp, Color(0xFF0C3434)) else null,
                shadowElevation = if (emphasized) 8.dp else 0.dp
            ) {
                Box(
                    modifier = Modifier.size(if (emphasized) 62.dp else 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        icon,
                        style = if (emphasized) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                        color = if (emphasized) FieldColors.onPrimary else if (selected) FieldColors.primary else FieldColors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                color = if (selected || emphasized) FieldColors.primary else FieldColors.onSurfaceVariant,
                fontWeight = if (selected || emphasized) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun FieldSectionBanner(section: AppSection, onBack: () -> Unit) {
    val tone = when (section) {
        AppSection.FIELD -> Color(0xFF1C5C43)
        AppSection.RECOGNITION -> Color(0xFF24675B)
        AppSection.SURVIVAL -> Color(0xFF7A5239)
        AppSection.EMERGENCY -> Color(0xFF843D43)
        AppSection.PREP -> Color(0xFF494F8E)
        AppSection.LIBRARY -> Color(0xFF296485)
        else -> Color(0xFF365A63)
    }

    Card(
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tone),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .10f))
    ) {
        Column(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(tone, Color(0xFF0B272D)))).padding(14.dp)
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0x25000000),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .14f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color(0x28FFFFFF)) {
                        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                            Text("←", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Quay lại", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Về Trang chủ", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .78f))
                    }
                    Text("⌂", style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = .82f))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0x26000000)) {
                    Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                        Text(
                            sectionGlyph(section),
                            style = if (section == AppSection.EMERGENCY) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(section.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(3.dp))
                    Text(section.subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .84f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
