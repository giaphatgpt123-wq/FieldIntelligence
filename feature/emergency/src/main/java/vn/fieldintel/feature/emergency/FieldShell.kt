package vn.fieldintel.feature.emergency

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val FieldColors = darkColorScheme(
    primary = Color(0xFF40DB86),
    onPrimary = Color(0xFF06281D),
    secondary = Color(0xFF9BC9BE),
    background = Color(0xFF071D22),
    onBackground = Color(0xFFF2F7F4),
    surface = Color(0xFF15313A),
    onSurface = Color(0xFFF2F7F4),
    surfaceVariant = Color(0xFF1D3D46),
    onSurfaceVariant = Color(0xFFD3E3DE),
    outline = Color(0xFF5A7879)
)
val FieldBackground = Brush.verticalGradient(listOf(Color(0xFF092A32), Color(0xFF06171C), Color(0xFF0E2830)))

@Composable
fun FieldHomePanel(position: FieldPositionUi?, onSelect: (AppSection) -> Unit) {
    val context = LocalContext.current
    val logoId = context.resources.getIdentifier("official_survival_logo", "drawable", context.packageName)
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF133638)), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(285.dp)) {
            Canvas(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5399AE), Color(0xFF173C48), Color(0xFF08221D))))) {
                val far = Path().apply {
                    moveTo(0f, size.height * .57f)
                    lineTo(size.width * .22f, size.height * .30f)
                    lineTo(size.width * .38f, size.height * .51f)
                    lineTo(size.width * .57f, size.height * .23f)
                    lineTo(size.width, size.height * .61f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(far, Color(0xFF355D59))
                val near = Path().apply {
                    moveTo(0f, size.height * .70f)
                    lineTo(size.width * .28f, size.height * .46f)
                    lineTo(size.width * .51f, size.height * .68f)
                    lineTo(size.width * .77f, size.height * .40f)
                    lineTo(size.width, size.height * .72f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(near, Color(0xFF163C34))
                val river = Path().apply {
                    moveTo(size.width * .28f, size.height)
                    cubicTo(size.width * .65f, size.height * .79f, size.width * .35f, size.height * .73f, size.width * .67f, size.height * .60f)
                }
                drawPath(river, Color(0xFF62B9C5), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 13f))
            }
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x44041319), Color(0x66051C1B), Color(0xDC051719)))).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (logoId != 0) Image(painterResource(logoId), "Biểu trưng la bàn núi rừng sông", Modifier.size(84.dp))
                else Text("🧭", style = MaterialTheme.typography.displayMedium)
                Text("FIELD INTELLIGENCE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Khám phá • Nhận biết • Sinh tồn an toàn", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                Surface(color = Color(0xCC08282A), shape = RoundedCornerShape(14.dp)) {
                    Text(if (position == null) "GPS đang chờ tín hiệu • bản đồ vẫn hoạt động offline" else "GPS %.5f, %.5f • ±%.0f m".format(position.latitude, position.longitude, position.accuracyM), modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    Text("CHỌN CHỨC NĂNG", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    listOf(
        listOf(AppSection.FIELD, AppSection.RECOGNITION, AppSection.SURVIVAL),
        listOf(AppSection.EMERGENCY, AppSection.PREP, AppSection.LIBRARY)
    ).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { section ->
                val tint = when(section) {
                    AppSection.FIELD -> Color(0xFF124934)
                    AppSection.RECOGNITION -> Color(0xFF125363)
                    AppSection.SURVIVAL -> Color(0xFF684225)
                    AppSection.EMERGENCY -> Color(0xFF72323A)
                    AppSection.PREP -> Color(0xFF423B75)
                    else -> Color(0xFF18446B)
                }
                Card(onClick = { onSelect(section) }, modifier = Modifier.weight(1f).height(112.dp), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = tint)) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(section.icon, style = MaterialTheme.typography.headlineSmall)
                        Text(section.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = { onSelect(AppSection.TRAINING) }, modifier = Modifier.fillMaxWidth()) { Text("🎓  Huấn luyện") }
}

@Composable
fun FieldBottomBar(current: AppSection?, onSelect: (AppSection?) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0A2229)) {
        listOf(
            Triple(null, "⌂", "Trang chủ"),
            Triple(AppSection.FIELD, "▣", "Bản đồ"),
            Triple(AppSection.RECOGNITION, "◎", "Quét"),
            Triple(AppSection.LIBRARY, "▤", "Lưu trữ"),
            Triple(AppSection.SETTINGS, "⚙", "Cài đặt")
        ).forEach { (section, icon, label) ->
            NavigationBarItem(
                selected = current == section,
                onClick = { onSelect(section) },
                icon = { Text(icon, style = MaterialTheme.typography.titleLarge) },
                label = { Text(label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = FieldColors.onPrimary, selectedTextColor = FieldColors.primary, indicatorColor = FieldColors.primary, unselectedIconColor = FieldColors.onSurfaceVariant, unselectedTextColor = FieldColors.onSurfaceVariant)
            )
        }
    }
}

@Composable
fun FieldSectionBanner(section: AppSection, onBack: () -> Unit) {
    val tone = when(section) {
        AppSection.FIELD -> Color(0xFF225744)
        AppSection.RECOGNITION -> Color(0xFF275841)
        AppSection.SURVIVAL -> Color(0xFF68412C)
        AppSection.EMERGENCY -> Color(0xFF753438)
        AppSection.PREP -> Color(0xFF404A83)
        AppSection.LIBRARY -> Color(0xFF245774)
        else -> Color(0xFF365A63)
    }
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = tone)) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(tone, Color(0xFF0D2930)))).padding(12.dp)) {
            TextButton(onClick = onBack) { Text("←  Trang chủ") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(section.icon, style = MaterialTheme.typography.displaySmall)
                Column {
                    Text(section.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(section.subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
