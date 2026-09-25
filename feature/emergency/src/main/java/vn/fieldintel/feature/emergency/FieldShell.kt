package vn.fieldintel.feature.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

private data class HomeTileStyle(
    val top: Color,
    val bottom: Color,
    val accent: Color
)

private fun tileStyle(section: AppSection): HomeTileStyle = when (section) {
    AppSection.FIELD -> HomeTileStyle(Color(0xFF15573D), Color(0xFF0D392A), Color(0xFF52E592))
    AppSection.RECOGNITION -> HomeTileStyle(Color(0xFF176B76), Color(0xFF10444E), Color(0xFF6FE6E7))
    AppSection.SURVIVAL -> HomeTileStyle(Color(0xFF8A532C), Color(0xFF57321E), Color(0xFFFFB45F))
    AppSection.EMERGENCY -> HomeTileStyle(Color(0xFF98434C), Color(0xFF5D2930), Color(0xFFFF7C84))
    AppSection.PREP -> HomeTileStyle(Color(0xFF5C5595), Color(0xFF373364), Color(0xFFB9ABFF))
    else -> HomeTileStyle(Color(0xFF246394), Color(0xFF173D61), Color(0xFF8CCBFF))
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
        Box(Modifier.fillMaxWidth().height(318.dp)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF74B5C6),
                                Color(0xFF315F69),
                                Color(0xFF173F3B),
                                Color(0xFF0B2926)
                            )
                        )
                    )
            ) {
                // Distant mist band
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x55FFFFFF), Color.Transparent)
                    ),
                    topLeft = Offset.Zero,
                    size = size.copy(height = size.height * .42f)
                )

                val far = Path().apply {
                    moveTo(0f, size.height * .57f)
                    lineTo(size.width * .18f, size.height * .29f)
                    lineTo(size.width * .34f, size.height * .50f)
                    lineTo(size.width * .54f, size.height * .22f)
                    lineTo(size.width * .70f, size.height * .43f)
                    lineTo(size.width * .82f, size.height * .31f)
                    lineTo(size.width, size.height * .58f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(far, Color(0xFF3F6E68))

                val mid = Path().apply {
                    moveTo(0f, size.height * .69f)
                    lineTo(size.width * .25f, size.height * .45f)
                    lineTo(size.width * .43f, size.height * .62f)
                    lineTo(size.width * .66f, size.height * .39f)
                    lineTo(size.width * .83f, size.height * .58f)
                    lineTo(size.width, size.height * .48f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(mid, Color(0xFF234F46))

                val near = Path().apply {
                    moveTo(0f, size.height * .82f)
                    lineTo(size.width * .28f, size.height * .59f)
                    lineTo(size.width * .48f, size.height * .75f)
                    lineTo(size.width * .74f, size.height * .51f)
                    lineTo(size.width, size.height * .74f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(near, Color(0xFF123A31))

                val river = Path().apply {
                    moveTo(size.width * .27f, size.height)
                    cubicTo(
                        size.width * .60f,
                        size.height * .82f,
                        size.width * .38f,
                        size.height * .72f,
                        size.width * .67f,
                        size.height * .56f
                    )
                }
                drawPath(
                    river,
                    Color(0xFF77D8E4),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f)
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x16051418),
                                Color(0x26061718),
                                Color(0xB805171A),
                                Color(0xF005171A)
                            )
                        )
                    )
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xD910382F),
                    border = BorderStroke(1.dp, Color(0x663FEA91))
                ) {
                    Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
                        if (logoId != 0) {
                            Image(
                                painterResource(logoId),
                                "Biểu trưng la bàn núi rừng sông",
                                Modifier.size(78.dp)
                            )
                        } else {
                            Text("🧭", style = MaterialTheme.typography.displayMedium)
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
                    color = Color(0xD90A2C2D),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0x335DE9A3))
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (position == null) "○" else "●", color = if (position == null) Color(0xFFFFD166) else FieldColors.primary)
                        Text(
                            if (position == null)
                                "GPS đang chờ tín hiệu • vẫn dùng được dữ liệu offline"
                            else
                                "GPS %.5f, %.5f  •  ±%.0f m".format(
                                    position.latitude,
                                    position.longitude,
                                    position.accuracyM
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFF0F7F4)
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
        Column {
            Text(
                "CHỌN CHỨC NĂNG",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                "Truy cập nhanh công cụ ngoài thực địa",
                style = MaterialTheme.typography.bodySmall,
                color = FieldColors.onSurfaceVariant
            )
        }
        Surface(shape = RoundedCornerShape(999.dp), color = Color(0x1A45E58C)) {
            Text(
                "OFFLINE FIRST",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = FieldColors.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }

    listOf(
        listOf(AppSection.FIELD, AppSection.RECOGNITION, AppSection.SURVIVAL),
        listOf(AppSection.EMERGENCY, AppSection.PREP, AppSection.LIBRARY)
    ).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { section ->
                val style = tileStyle(section)
                Card(
                    onClick = { onSelect(section) },
                    modifier = Modifier.weight(1f).height(126.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, style.accent.copy(alpha = .20f))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(style.top, style.bottom)))
                            .padding(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x22000000)
                        ) {
                            Text(
                                section.icon,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        Column(
                            modifier = Modifier.align(Alignment.BottomStart),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                section.label,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                section.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = .76f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    Surface(
        onClick = { onSelect(AppSection.TRAINING) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF12333A),
        border = BorderStroke(1.dp, Color(0x553EDB86))
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Color(0x223EDB86)) {
                Text("🎓", Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Huấn luyện", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Tình huống mô phỏng và kiểm tra kỹ năng", style = MaterialTheme.typography.bodySmall, color = FieldColors.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = FieldColors.primary)
        }
    }
}

@Composable
fun FieldBottomBar(current: AppSection?, onSelect: (AppSection?) -> Unit) {
    Surface(
        color = Color(0xFF081F25),
        shadowElevation = 12.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0x2215E58D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(76.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomItem(null, "⌂", "Trang chủ", current == null, false) { onSelect(null) }
            BottomItem(AppSection.FIELD, "▣", "Bản đồ", current == AppSection.FIELD, false) { onSelect(AppSection.FIELD) }
            BottomItem(AppSection.RECOGNITION, "◎", "Quét", current == AppSection.RECOGNITION, true) { onSelect(AppSection.RECOGNITION) }
            BottomItem(AppSection.LIBRARY, "▤", "Lưu trữ", current == AppSection.LIBRARY, false) { onSelect(AppSection.LIBRARY) }
            BottomItem(AppSection.SETTINGS, "⚙", "Cài đặt", current == AppSection.SETTINGS, false) { onSelect(AppSection.SETTINGS) }
        }
    }
}

@Composable
private fun RowScope.BottomItem(
    section: AppSection?,
    icon: String,
    label: String,
    selected: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = when {
                    emphasized -> FieldColors.primary
                    selected -> Color(0x263FEA91)
                    else -> Color.Transparent
                },
                border = if (emphasized) BorderStroke(3.dp, Color(0xFF0C3434)) else null
            ) {
                Box(
                    modifier = Modifier.size(if (emphasized) 48.dp else 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        icon,
                        style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        color = if (emphasized) FieldColors.onPrimary else if (selected) FieldColors.primary else FieldColors.onSurfaceVariant
                    )
                }
            }
            if (!emphasized) Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
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
        AppSection.FIELD -> Color(0xFF1C5540)
        AppSection.RECOGNITION -> Color(0xFF245E52)
        AppSection.SURVIVAL -> Color(0xFF765038)
        AppSection.EMERGENCY -> Color(0xFF7F3A40)
        AppSection.PREP -> Color(0xFF454D86)
        AppSection.LIBRARY -> Color(0xFF275D7D)
        else -> Color(0xFF365A63)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tone),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(tone, Color(0xFF0B272D))))
                .padding(14.dp)
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 2.dp)) {
                Text("←  Trang chủ", color = Color.White.copy(alpha = .86f))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0x22000000)) {
                    Text(section.icon, Modifier.padding(12.dp), style = MaterialTheme.typography.headlineMedium)
                }
                Column {
                    Text(section.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(section.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .78f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
