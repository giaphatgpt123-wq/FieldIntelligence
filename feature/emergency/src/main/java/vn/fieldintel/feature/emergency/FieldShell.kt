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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val FieldColors = darkColorScheme(
    primary = Color(0xFF52E99A),
    onPrimary = Color(0xFF022A1B),
    secondary = Color(0xFFA9E2D0),
    background = Color(0xFF19434A),
    onBackground = Color(0xFFF6FBF8),
    surface = Color(0xFF24545C),
    onSurface = Color(0xFFF6FBF8),
    surfaceVariant = Color(0xFF31656B),
    onSurfaceVariant = Color(0xFFD5E6E0),
    outline = Color(0xFF6A8587)
)

val FieldBackground = Brush.verticalGradient(
    listOf(Color(0xFF316D74), Color(0xFF20545B), Color(0xFF19434A))
)

private data class HomeTileStyle(
    val sky: Color,
    val mid: Color,
    val deep: Color,
    val accent: Color
)

private fun tileStyle(section: AppSection): HomeTileStyle = when (section) {
    AppSection.FIELD -> HomeTileStyle(Color(0xFF4B9A76), Color(0xFF1F6D4D), Color(0xFF103D2D), Color(0xFF66F0A7))
    AppSection.RECOGNITION -> HomeTileStyle(Color(0xFF58A9AE), Color(0xFF1D6C72), Color(0xFF0C434A), Color(0xFF7CF4EA))
    AppSection.SURVIVAL -> HomeTileStyle(Color(0xFFC5905A), Color(0xFF875A36), Color(0xFF51351F), Color(0xFFFFC47B))
    AppSection.EMERGENCY -> HomeTileStyle(Color(0xFFD47A72), Color(0xFF9D454D), Color(0xFF5C2630), Color(0xFFFF9A93))
    AppSection.PREP -> HomeTileStyle(Color(0xFF8E84C9), Color(0xFF62589B), Color(0xFF383461), Color(0xFFD2C9FF))
    else -> HomeTileStyle(Color(0xFF62A0C6), Color(0xFF2F7099), Color(0xFF174764), Color(0xFFA5DAFF))
}

@Composable
fun FieldHomePanel(position: FieldPositionUi?, onSelect: (AppSection) -> Unit) {
    val context = LocalContext.current
    val logoId = context.resources.getIdentifier("official_survival_logo", "drawable", context.packageName)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
    ) {
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            ScenicHero()
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x22040F11),
                            Color(0x8505171A),
                            Color(0xDD05171A)
                        )
                    )
                )
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0x6B0A2B2C),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))
                    ) {
                        Text(
                            "OFFLINE READY",
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = FieldColors.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color(0x7B0A2B2C),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))
                    ) {
                        Text("⋯", Modifier.padding(horizontal = 13.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }

                Spacer(Modifier.height(28.dp))
                Surface(
                    shape = CircleShape,
                    color = Color(0xD10B302B),
                    border = BorderStroke(1.dp, Color(0x9952E99A)),
                    shadowElevation = 12.dp
                ) {
                    Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        if (logoId != 0) {
                            Image(painterResource(logoId), "Biểu trưng la bàn núi rừng sông", Modifier.size(82.dp))
                        } else {
                            Text("⌖", style = MaterialTheme.typography.displayMedium, color = FieldColors.primary)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "FIELD INTELLIGENCE",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Khám phá • Nhận biết • Sinh tồn",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = .88f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xC60A292B),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = CircleShape, color = if (position == null) Color(0x33FFD166) else Color(0x3352E99A)) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Text(if (position == null) "○" else "●", color = if (position == null) Color(0xFFFFD166) else FieldColors.primary)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (position == null) "Đang lấy vị trí" else "Vị trí hiện tại",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                if (position == null) "Dữ liệu offline vẫn sử dụng được"
                                else "%.5f, %.5f  •  ±%.0f m".format(position.latitude, position.longitude, position.accuracyM),
                                color = Color.White.copy(alpha = .78f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("›", color = FieldColors.primary, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("KHÁM PHÁ NHANH", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Các công cụ chính ngoài thực địa", style = MaterialTheme.typography.bodyMedium, color = FieldColors.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(999.dp), color = Color(0x1A52E99A), border = BorderStroke(1.dp, Color(0x3352E99A))) {
            Text(
                "6 CÔNG CỤ",
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                color = FieldColors.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
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
                DemoHomeTile(section, Modifier.weight(1f)) { onSelect(section) }
            }
        }
    }

    Surface(
        onClick = { onSelect(AppSection.TRAINING) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF102F36),
        border = BorderStroke(1.dp, Color(0x3352E99A))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0x2252E99A)) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    FieldIcon(AppSection.TRAINING, Modifier.size(31.dp), FieldColors.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Huấn luyện thực địa", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Mô phỏng tình huống và kiểm tra kỹ năng", style = MaterialTheme.typography.bodyMedium, color = FieldColors.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = Color(0x1852E99A)) {
                Text("›", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineSmall, color = FieldColors.primary)
            }
        }
    }
}

@Composable
private fun ScenicHero() {
    Canvas(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFFB5E5EC), Color(0xFF79BCC3), Color(0xFF4A8D80), Color(0xFF225B4B))
            )
        )
    ) {
        drawCircle(Color(0x44FFF2B7), radius = size.minDimension * .095f, center = Offset(size.width * .78f, size.height * .20f))

        val far = Path().apply {
            moveTo(0f, size.height * .60f)
            lineTo(size.width * .14f, size.height * .39f)
            lineTo(size.width * .26f, size.height * .49f)
            lineTo(size.width * .46f, size.height * .25f)
            lineTo(size.width * .61f, size.height * .47f)
            lineTo(size.width * .74f, size.height * .31f)
            lineTo(size.width, size.height * .57f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(far, Color(0xFF557F76))

        val mid = Path().apply {
            moveTo(0f, size.height * .72f)
            lineTo(size.width * .20f, size.height * .48f)
            lineTo(size.width * .37f, size.height * .64f)
            lineTo(size.width * .58f, size.height * .42f)
            lineTo(size.width * .75f, size.height * .63f)
            lineTo(size.width, size.height * .46f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(mid, Color(0xFF2E5C4F))

        val near = Path().apply {
            moveTo(0f, size.height * .83f)
            lineTo(size.width * .22f, size.height * .66f)
            lineTo(size.width * .40f, size.height * .78f)
            lineTo(size.width * .65f, size.height * .57f)
            lineTo(size.width * .85f, size.height * .72f)
            lineTo(size.width, size.height * .67f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(near, Color(0xFF173C32))

        val river = Path().apply {
            moveTo(size.width * .20f, size.height)
            cubicTo(size.width * .48f, size.height * .83f, size.width * .39f, size.height * .73f, size.width * .69f, size.height * .56f)
        }
        drawPath(river, Color(0xFF89E5EC), style = Stroke(width = 16f))
        drawPath(river, Color(0x88FFFFFF), style = Stroke(width = 4f))
    }
}

@Composable
private fun DemoHomeTile(section: AppSection, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = tileStyle(section)
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 176.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, style.accent.copy(alpha = .30f))
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(style.sky, style.mid, style.deep)))) {
                drawCircle(style.accent.copy(alpha = .18f), radius = size.minDimension * .26f, center = Offset(size.width * .82f, size.height * .17f))
                val ridge = Path().apply {
                    moveTo(0f, size.height * .65f)
                    lineTo(size.width * .28f, size.height * .42f)
                    lineTo(size.width * .48f, size.height * .61f)
                    lineTo(size.width * .72f, size.height * .36f)
                    lineTo(size.width, size.height * .60f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(ridge, style.deep.copy(alpha = .70f))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x22000000), Color(0xB9000000)))))

            Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(17.dp), color = Color(0x65041317), border = BorderStroke(1.dp, Color.White.copy(alpha = .11f))) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            FieldIcon(section, Modifier.size(30.dp), Color.White)
                        }
                    }
                    Surface(shape = CircleShape, color = Color(0x44041317)) {
                        Text("↗", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White.copy(alpha = .88f), fontWeight = FontWeight.Bold)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(section.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .83f), maxLines = 2)
                    Box(Modifier.padding(top = 4.dp).width(42.dp).height(3.dp).background(style.accent, RoundedCornerShape(999.dp)))
                }
            }
        }
    }
}

@Composable
fun FieldBottomBar(current: AppSection?, onSelect: (AppSection?) -> Unit) {
    Surface(
        color = Color(0xFFF0FAF6),
        shadowElevation = 18.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFB5D8D0))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(96.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomItem(null, "Trang chủ", current == null, false) { onSelect(null) }
            BottomItem(AppSection.FIELD, "Bản đồ", current == AppSection.FIELD, false) { onSelect(AppSection.FIELD) }
            BottomItem(AppSection.RECOGNITION, "Quét", current == AppSection.RECOGNITION, true) { onSelect(AppSection.RECOGNITION) }
            BottomItem(AppSection.LIBRARY, "Lưu trữ", current == AppSection.LIBRARY, false) { onSelect(AppSection.LIBRARY) }
            BottomItem(AppSection.SETTINGS, "Cài đặt", current == AppSection.SETTINGS, false) { onSelect(AppSection.SETTINGS) }
        }
    }
}

@Composable
private fun RowScope.BottomItem(
    icon: AppSection?,
    label: String,
    selected: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 74.dp),
        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 3.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(
                shape = CircleShape,
                color = when {
                    emphasized -> FieldColors.primary
                    selected -> Color(0xFFCEEDE0)
                    else -> Color.Transparent
                },
                border = when {
                    emphasized -> BorderStroke(3.dp, Color(0xFF9EE1BE))
                    selected -> BorderStroke(1.dp, Color(0x5552E99A))
                    else -> null
                },
                shadowElevation = if (emphasized) 10.dp else 0.dp
            ) {
                Box(Modifier.size(if (emphasized) 64.dp else 46.dp), contentAlignment = Alignment.Center) {
                    FieldIcon(icon, Modifier.size(if (emphasized) 34.dp else 29.dp), if (emphasized) FieldColors.onPrimary else Color(0xFF1D575A), navigation = true)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = Color(0xFF15474B),
                fontWeight = if (selected || emphasized) FontWeight.ExtraBold else FontWeight.Medium
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
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tone),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .10f))
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(tone, Color(0xFF0B272D))))) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = .04f), radius = size.minDimension * .52f, center = Offset(size.width * .92f, size.height * .18f))
            }
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Surface(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x26000000),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .14f))
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0x26000000), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
                        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                            FieldIcon(section, Modifier.size(38.dp), Color.White)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(section.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
                        Spacer(Modifier.height(3.dp))
                        Text(section.subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .84f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
