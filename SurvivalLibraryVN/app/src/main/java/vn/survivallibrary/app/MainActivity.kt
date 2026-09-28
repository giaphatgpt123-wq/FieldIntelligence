package vn.survivallibrary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SurvivalLibraryApp() }
    }
}

private val AppColors = lightColorScheme(
    primary = Color(0xFF0B6B3A),
    onPrimary = Color.White,
    secondary = Color(0xFF317B55),
    background = Color(0xFFF5F8F3),
    surface = Color.White,
    onSurface = Color(0xFF18231C),
    surfaceVariant = Color(0xFFE6EFE8),
    onSurfaceVariant = Color(0xFF526057)
)

private enum class AppTab(val label: String, val emoji: String) {
    HOME("Trang chủ", "⌂"),
    LIBRARY("Thư viện", "▦"),
    SCAN("Quét", "◎"),
    SAVED("Đã lưu", "♡")
}

private data class DemoSpeciesUi(
    val name: String,
    val group: String,
    val usage: UsageLevel,
    val emoji: String,
    val note: String
)

private val demoSpecies = listOf(
    DemoSpeciesUi("Rau muống", "Rau", UsageLevel.CHUA_PHAN_LOAI, "🥬", "Mẫu giao diện — chưa phát hành dữ liệu"),
    DemoSpeciesUi("Rau má", "Rau", UsageLevel.CHUA_PHAN_LOAI, "🌿", "Mẫu giao diện — chưa phát hành dữ liệu"),
    DemoSpeciesUi("Nghệ vàng", "Củ / thân rễ", UsageLevel.CHUA_PHAN_LOAI, "🫚", "Mẫu giao diện — chưa phát hành dữ liệu"),
    DemoSpeciesUi("Xoài", "Cây ăn quả", UsageLevel.CHUA_PHAN_LOAI, "🥭", "Mẫu giao diện — chưa phát hành dữ liệu")
)

@Composable
private fun SurvivalLibraryApp() {
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var detail by remember { mutableStateOf<DemoSpeciesUi?>(null) }

    MaterialTheme(colorScheme = AppColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (detail == null) {
                    NavigationBar(containerColor = Color.White) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(item.emoji, fontWeight = FontWeight.Black) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                if (detail != null) {
                    SpeciesDetailScreen(detail!!, onBack = { detail = null })
                } else {
                    when (tab) {
                        AppTab.HOME -> HomeScreen(onOpenLibrary = { tab = AppTab.LIBRARY }, onSpecies = { detail = it })
                        AppTab.LIBRARY -> LibraryScreen(onSpecies = { detail = it })
                        AppTab.SCAN -> ScanScreen()
                        AppTab.SAVED -> SavedScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpenLibrary: () -> Unit, onSpecies: (DemoSpeciesUi) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HeroHeader()

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                shape = RoundedCornerShape(22.dp),
                placeholder = { Text("Tìm cây, cá, nấm, côn trùng...") },
                leadingIcon = { Text("⌕") }
            )

            Text("TÔI CẦN GÌ?", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickNeed("💧", "Uống", Modifier.weight(1f))
                QuickNeed("🍽", "Ăn", Modifier.weight(1f))
                QuickNeed("🔎", "Nhận biết", Modifier.weight(1f))
                QuickNeed("⚠", "Tránh nguy hiểm", Modifier.weight(1f))
            }

            SectionTitle("Danh mục", "Xem tất cả", onOpenLibrary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryCard("🥬", "Rau", Modifier.weight(1f), onOpenLibrary)
                CategoryCard("🍄", "Nấm", Modifier.weight(1f), onOpenLibrary)
                CategoryCard("🐟", "Cá", Modifier.weight(1f), onOpenLibrary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryCard("🌸", "Hoa", Modifier.weight(1f), onOpenLibrary)
                CategoryCard("🌳", "Cây gỗ", Modifier.weight(1f), onOpenLibrary)
                CategoryCard("🐝", "Côn trùng", Modifier.weight(1f), onOpenLibrary)
            }

            Surface(
                color = Color(0xFFFFF3D6),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "DỮ LIỆU UI MẪU — hồ sơ thật chỉ xuất hiện sau khi vượt cổng kiểm chứng.",
                    Modifier.padding(14.dp),
                    color = Color(0xFF6A501A),
                    fontWeight = FontWeight.Bold
                )
            }

            SectionTitle("Ưu tiên ở Việt Nam", "Mở thư viện", onOpenLibrary)
            demoSpecies.forEach { SpeciesPreviewCard(it, onSpecies) }
        }
    }
}

@Composable
private fun HeroHeader() {
    Box(
        Modifier.fillMaxWidth().height(245.dp).background(
            Brush.verticalGradient(
                listOf(Color(0xFF9EDBB6), Color(0xFF4CA16D), Color(0xFF145B38))
            )
        )
    ) {
        Text("☁", Modifier.align(Alignment.TopStart).padding(start = 26.dp, top = 24.dp), color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.displayMedium)
        Text("☀", Modifier.align(Alignment.TopEnd).padding(end = 30.dp, top = 20.dp), color = Color(0xFFFFF0A8), style = MaterialTheme.typography.displayMedium)
        Text("🌲  🌳  🌿  🌲", Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), style = MaterialTheme.typography.displaySmall)

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = .92f)) {
                Text("🧭", Modifier.padding(14.dp), style = MaterialTheme.typography.headlineLarge)
            }
            Spacer(Modifier.height(10.dp))
            Text("THƯ VIỆN SINH TỒN", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
            Text("Việt Nam • trực quan • ưu tiên tiếng Việt", color = Color.White.copy(alpha = .9f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun QuickNeed(emoji: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(5.dp))
            Text(label, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CategoryCard(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.headlineLarge)
            Text(label, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text(action, Modifier.clickable(onClick = onClick), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpeciesPreviewCard(item: DemoSpeciesUi, onClick: (DemoSpeciesUi) -> Unit) {
    Card(
        onClick = { onClick(item) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE5F2E8)) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Text(item.emoji, style = MaterialTheme.typography.headlineLarge)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(item.group, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFF0F3EE)) {
                    Text(item.usage.label, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(onSpecies: (DemoSpeciesUi) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tất cả") }
    val categories = listOf("Tất cả", "Rau", "Củ / thân rễ", "Cây ăn quả", "Nấm", "Cá", "Hoa", "Côn trùng")
    val filtered = demoSpecies.filter {
        (category == "Tất cả" || it.group == category) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("THƯ VIỆN", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        Text("Tên Việt và ảnh đặt trước • nội dung khoa học mở khi cần", color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            placeholder = { Text("Tìm theo tên tiếng Việt") },
            leadingIcon = { Text("⌕") }
        )

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { label ->
                FilterChip(selected = category == label, onClick = { category = label }, label = { Text(label) })
            }
        }

        LibraryRules.displayOrder.take(3).forEachIndexed { index, label ->
            if (index == 0) {
                Text("Quy tắc hiển thị: ${LibraryRules.displayOrder.joinToString(" → ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Surface(color = Color(0xFFFFF3D6), shape = RoundedCornerShape(18.dp)) {
            Text("Đang dùng dữ liệu UI mẫu. Không xem đây là dữ liệu thư viện đã kiểm chứng.", Modifier.padding(14.dp), color = Color(0xFF6A501A), fontWeight = FontWeight.Bold)
        }

        if (filtered.isEmpty()) {
            EmptyState("Không có hồ sơ phù hợp trong dữ liệu mẫu")
        } else {
            filtered.forEach { SpeciesPreviewCard(it, onSpecies) }
        }
    }
}

@Composable
private fun ScanScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("QUÉT NHẬN DẠNG", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        Text("Khung dành cho AI offline trong ứng dụng. Model thật chưa được gắn ở mốc khởi tạo này.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Box(
            Modifier.fillMaxWidth().height(430.dp).background(
                Brush.verticalGradient(listOf(Color(0xFFB9DFC6), Color(0xFF6EAD83), Color(0xFF244F35))),
                RoundedCornerShape(28.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("◎", color = Color.White, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                Text("Camera + AI offline", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("Sẽ trả về ứng viên + UNKNOWN khi chưa đủ bằng chứng", Modifier.padding(horizontal = 28.dp), color = Color.White.copy(alpha = .9f), textAlign = TextAlign.Center)
            }
        }

        Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), enabled = false) {
            Text("CAMERA — CHỜ KẾT NỐI MODEL", fontWeight = FontWeight.Black)
        }
        Text("Không biến xác suất AI thành khẳng định loài. Đối tượng nguy cơ cao phải qua cổng an toàn trước khi hiển thị hướng dẫn.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SavedScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ĐÃ LƯU", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        EmptyState("Chưa có hồ sơ nào được lưu")
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌱", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(message, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpeciesDetailScreen(item: DemoSpeciesUi, onBack: () -> Unit) {
    var openSection by remember { mutableStateOf("Nhận biết") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("← Quay lại") }

        Box(
            Modifier.fillMaxWidth().height(250.dp).background(
                Brush.verticalGradient(listOf(Color(0xFFBFE5CA), Color(0xFF73B488), Color(0xFF347451))),
                RoundedCornerShape(28.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(item.emoji, style = MaterialTheme.typography.displayLarge)
        }

        Text(item.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFE5F2E8)) {
                Text(item.group, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
            }
            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFF0F3EE)) {
                Text(item.usage.label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
            }
        }

        Surface(color = Color(0xFFFFF3D6), shape = RoundedCornerShape(18.dp)) {
            Text(item.note, Modifier.padding(14.dp), color = Color(0xFF6A501A), fontWeight = FontWeight.Bold)
        }

        listOf("Nhận biết", "Dễ nhầm", "Cách dùng", "Cảnh báo", "Nguồn / Xem thêm").forEach { section ->
            Card(
                onClick = { openSection = if (openSection == section) "" else section },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section, Modifier.weight(1f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text(if (openSection == section) "−" else "+", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                    }
                    if (openSection == section) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Chưa có nội dung phát hành. Mục này chỉ nhận dữ liệu sau khi trường tương ứng vượt cổng kiểm chứng của thư viện.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
