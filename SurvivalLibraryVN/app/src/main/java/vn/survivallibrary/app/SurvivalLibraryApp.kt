package vn.survivallibrary.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
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

private val SurvivalColors = lightColorScheme(
    primary = Color(0xFF146B43),
    onPrimary = Color.White,
    secondary = Color(0xFF4B8065),
    background = Color(0xFFF5F1E7),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF1D2A22),
    surfaceVariant = Color(0xFFE8EFE7),
    onSurfaceVariant = Color(0xFF58645C)
)

private enum class AppTab(val label: String, val icon: String) {
    HOME("Trang chủ", "⌂"),
    LIBRARY("Danh mục", "▦"),
    SCAN("Quét ảnh", "◎"),
    SAVED("Đã lưu", "♡"),
    PROGRESS("Tiến độ", "≡")
}

@Composable
fun SurvivalLibraryApp() {
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var detail by remember { mutableStateOf<LibraryRecordUi?>(null) }
    var requestedCategory by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = SurvivalColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (detail == null) {
                    NavigationBar(containerColor = Color(0xFFFFFEFA)) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(item.icon, fontWeight = FontWeight.Black) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                val selected = detail
                if (selected != null) {
                    RecordDetailScreen(selected, onBack = { detail = null })
                } else {
                    when (tab) {
                        AppTab.HOME -> HomeScreen(
                            onOpenLibrary = {
                                requestedCategory = null
                                tab = AppTab.LIBRARY
                            },
                            onCategory = { categoryId ->
                                requestedCategory = categoryId
                                tab = AppTab.LIBRARY
                            },
                            onScan = { tab = AppTab.SCAN },
                            onProgress = { tab = AppTab.PROGRESS },
                            onRecord = { detail = it }
                        )
                        AppTab.LIBRARY -> LibraryScreen(
                            initialCategoryId = requestedCategory,
                            onRecord = { detail = it }
                        )
                        AppTab.SCAN -> ScanScreen()
                        AppTab.SAVED -> SavedScreen()
                        AppTab.PROGRESS -> ProgressScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenLibrary: () -> Unit,
    onCategory: (String) -> Unit,
    onScan: () -> Unit,
    onProgress: () -> Unit,
    onRecord: (LibraryRecordUi) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScenicHero()

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SearchLauncher(onOpenLibrary)

            Text("TÌM THEO NHU CẦU", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeedCard("💧", "Uống", Modifier.weight(1f), onOpenLibrary)
                NeedCard("🍽", "Ăn", Modifier.weight(1f), onOpenLibrary)
                NeedCard("🔎", "Nhận biết", Modifier.weight(1f), onScan)
                NeedCard("⚠", "Tránh nguy hiểm", Modifier.weight(1f)) { onCategory("danger") }
            }

            SectionHeader("Danh mục thư viện", "Xem tất cả", onOpenLibrary)
            CategoryGrid(onCategory)

            CameraBanner(onScan)

            SectionHeader("Thường gặp ở Việt Nam", "Quy tắc", onProgress)
            Surface(
                color = Color(0xFFFFF3D8),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Chưa có hồ sơ thật đủ chuẩn cho vùng ưu tiên. Các thẻ dưới đây chỉ để kiểm tra giao diện.",
                    Modifier.padding(14.dp),
                    color = Color(0xFF70551B),
                    fontWeight = FontWeight.Bold
                )
            }
            SurvivalLibraryCatalog.demoRecords.take(3).forEach { record ->
                RecordPreviewCard(record, onRecord)
            }
        }
    }
}

@Composable
private fun ScenicHero() {
    Box(
        Modifier.fillMaxWidth().height(285.dp).background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFFD8ECE5),
                    Color(0xFF86B99C),
                    Color(0xFF3F7A55),
                    Color(0xFF245439)
                )
            )
        )
    ) {
        Text("☀", Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = 24.dp), style = MaterialTheme.typography.displayMedium)
        Text("⌁   ⛰   ⛰   ⌁", Modifier.align(Alignment.Center).padding(top = 42.dp), color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.headlineLarge)
        Text("🌲   🌳   🌿   🌲   🌳", Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), style = MaterialTheme.typography.headlineLarge)

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = .94f)) {
                Text("🧭", Modifier.padding(15.dp), style = MaterialTheme.typography.headlineLarge)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "THƯ VIỆN SINH TỒN",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                "Thiên nhiên Việt Nam • hiểu để an toàn và sử dụng đúng",
                color = Color.White.copy(alpha = .92f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xCFFFFFFF)) {
                Text(
                    "TIẾNG VIỆT TRƯỚC • OFFLINE READY",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFF1D5B3A),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun SearchLauncher(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Text("⌕", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text("Tìm tên: rau, củ, nấm, cá, côn trùng...", Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}

@Composable
private fun NeedCard(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(5.dp))
            Text(label, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text(action, Modifier.clickable(onClick = onClick), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryGrid(onCategory: (String) -> Unit) {
    SurvivalLibraryCatalog.categories.chunked(2).forEach { rowItems ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowItems.forEach { category ->
                CategoryCard(category, Modifier.weight(1f), onCategory)
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CategoryCard(category: LibraryCategory, modifier: Modifier, onCategory: (String) -> Unit) {
    Card(
        onClick = { onCategory(category.id) },
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE7F2E8)) {
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    Text(category.icon, style = MaterialTheme.typography.headlineMedium)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(category.label, fontWeight = FontWeight.Black)
                Text(category.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CameraBanner(onScan: () -> Unit) {
    Card(
        onClick = onScan,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF174F38))
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color(0xFF2D6D51)) {
                Text("◎", Modifier.padding(14.dp), color = Color.White, style = MaterialTheme.typography.headlineLarge)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Nhận dạng nhanh bằng camera", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("Khung AI offline đã sẵn sàng để gắn model", color = Color.White.copy(alpha = .82f))
            }
            Text("›", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun LibraryScreen(initialCategoryId: String?, onRecord: (LibraryRecordUi) -> Unit) {
    var query by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(initialCategoryId) }
    var usageFilter by remember { mutableStateOf<UsageLevel?>(null) }

    LaunchedEffect(initialCategoryId) { categoryId = initialCategoryId }

    val records = SurvivalLibraryCatalog.demoRecordsFor(categoryId, query).filter { record ->
        usageFilter == null || record.usageLevel == usageFilter
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("THƯ VIỆN", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        Text("Tên tiếng Việt và ảnh nhận biết đặt trước • nội dung sâu mở khi cần", color = MaterialTheme.colorScheme.onSurfaceVariant)

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
            FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("Tất cả") })
            SurvivalLibraryCatalog.categories.forEach { category ->
                FilterChip(
                    selected = categoryId == category.id,
                    onClick = { categoryId = category.id },
                    label = { Text("${category.icon} ${category.label}") }
                )
            }
        }

        Text("Mức sử dụng", fontWeight = FontWeight.ExtraBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = usageFilter == null, onClick = { usageFilter = null }, label = { Text("Tất cả") })
            UsageLevel.entries.forEach { level ->
                FilterChip(selected = usageFilter == level, onClick = { usageFilter = level }, label = { Text(level.label) })
            }
        }

        Surface(color = Color(0xFFFFF3D8), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "DỮ LIỆU MẪU UI — chưa có hồ sơ thật nào được tính là đã phát hành.",
                Modifier.padding(14.dp),
                color = Color(0xFF70551B),
                fontWeight = FontWeight.Bold
            )
        }

        if (records.isEmpty()) {
            EmptyState("Chưa có hồ sơ mẫu phù hợp với bộ lọc này")
        } else {
            records.forEach { record -> RecordPreviewCard(record, onRecord) }
        }
    }
}

@Composable
private fun RecordPreviewCard(record: LibraryRecordUi, onRecord: (LibraryRecordUi) -> Unit) {
    Card(
        onClick = { onRecord(record) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE7F2E8)) {
                Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                    Text(record.visual, style = MaterialTheme.typography.headlineLarge)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.vietnameseName, Modifier.weight(1f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    if (record.isDemo) DemoBadge()
                }
                Text(SurvivalLibraryCatalog.category(record.categoryId)?.label.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                UsageBadge(record.usageLevel)
                Text(record.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun DemoBadge() {
    Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFFFE6A8)) {
        Text("DEMO", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF6A4C00), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun UsageBadge(level: UsageLevel) {
    val color = when (level) {
        UsageLevel.THUONG_DUNG -> Color(0xFFDDF3E4)
        UsageLevel.HAY_DUNG -> Color(0xFFE5F0D9)
        UsageLevel.IT_DUNG -> Color(0xFFFFF1C9)
        UsageLevel.HIEM_DUNG -> Color(0xFFFFE1C5)
        UsageLevel.KHONG_CO_KHA_NANG_DUNG -> Color(0xFFE8E8E8)
        UsageLevel.CHUA_PHAN_LOAI -> Color(0xFFEAEDEA)
    }
    Surface(shape = RoundedCornerShape(999.dp), color = color) {
        Text(level.label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ScanScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("QUÉT NHẬN DẠNG", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        Text("Kiến trúc camera đã dành chỗ cho model offline. Bản này chưa gắn model nhận dạng thật.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Box(
            Modifier.fillMaxWidth().height(430.dp).background(
                Brush.verticalGradient(listOf(Color(0xFFB8D9C5), Color(0xFF6B9B78), Color(0xFF234B35))),
                RoundedCornerShape(28.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = .16f)) {
                    Text("◎", Modifier.padding(22.dp), color = Color.White, style = MaterialTheme.typography.displayMedium)
                }
                Spacer(Modifier.height(12.dp))
                Text("Đưa mẫu vật vào khung", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("Chụp nhiều góc khi model yêu cầu", color = Color.White.copy(alpha = .82f))
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFFF3D8)) {
            Text(
                "CHƯA CÓ MODEL ĐƯỢC KIỂM CHỨNG — ứng dụng không tự đoán tên loài, tính ăn được, độc tính hoặc công dụng.",
                Modifier.padding(14.dp),
                color = Color(0xFF70551B),
                fontWeight = FontWeight.Bold
            )
        }

        listOf(
            "🌿 Thực vật" to "Chưa cài model",
            "🍄 Nấm" to "Chưa cài model",
            "🐟 Cá / thủy sản" to "Chưa cài model",
            "🐝 Côn trùng" to "Chưa cài model"
        ).forEach { (label, status) ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFFEFA), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFFD8DED9))
        ) {
            Text("QUÉT ẢNH — CHỜ MODEL", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ProgressScreen() {
    val progress = SurvivalLibraryCatalog.progress
    val collected = progress.sumOf { it.collected }
    val media = progress.sumOf { it.withVerifiedMedia }
    val verified = progress.sumOf { it.verified }
    val published = progress.sumOf { it.published }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("TIẾN ĐỘ THƯ VIỆN", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        Text("Theo dõi từng danh mục; hoàn thiện đến đâu phát hành đến đó.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Thu thập", collected.toString(), Modifier.weight(1f))
            MetricCard("Có ảnh", media.toString(), Modifier.weight(1f))
            MetricCard("Kiểm chứng", verified.toString(), Modifier.weight(1f))
            MetricCard("Đã lên app", published.toString(), Modifier.weight(1f))
        }

        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFE5F2E8), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Mục tiêu số lượng đang để trống có chủ ý. Chỉ điền khi đã xác định phạm vi; không tạo số liệu giả để làm đẹp bảng.",
                Modifier.padding(14.dp),
                color = Color(0xFF245239),
                fontWeight = FontWeight.Bold
            )
        }

        progress.forEach { item ->
            ProgressCategoryCard(item)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA))) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProgressCategoryCard(item: LibraryProgress) {
    val category = SurvivalLibraryCatalog.category(item.categoryId) ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA))
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.icon, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(if (item.targetCount == null) "Mục tiêu: chưa đặt" else "Mục tiêu: ${item.targetCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${item.published} đã lên app", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniMetric("Thu thập", item.collected)
                MiniMetric("Ảnh", item.withVerifiedMedia)
                MiniMetric("Tên Việt", item.vietnameseNameReviewed)
                MiniMetric("Kiểm chứng", item.verified)
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SavedScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("♡", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("CHƯA CÓ MỤC ĐÃ LƯU", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text("Khi thư viện thật được nạp, hồ sơ đã lưu sẽ xuất hiện ở đây.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordDetailScreen(record: LibraryRecordUi, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Text("← QUAY LẠI", fontWeight = FontWeight.Bold)
        }

        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFFE7F2E8)) {
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        Text(record.visual, style = MaterialTheme.typography.displayLarge)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.vietnameseName, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(8.dp))
                    if (record.isDemo) DemoBadge()
                }
                Text(SurvivalLibraryCatalog.category(record.categoryId)?.label.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                UsageBadge(record.usageLevel)
            }
        }

        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFE7C2), modifier = Modifier.fillMaxWidth()) {
            Text(
                "HỒ SƠ DEMO — không sử dụng nội dung này để quyết định ăn, dùng làm thuốc hoặc xử lý nguy hiểm.",
                Modifier.padding(14.dp),
                color = Color(0xFF6E4A0E),
                fontWeight = FontWeight.Black
            )
        }

        DetailSection("Nhận biết", "Chưa có nội dung đã kiểm chứng.")
        DetailSection("Dễ nhầm", "Chưa có nội dung đã kiểm chứng.")
        DetailSection("Cách dùng / công dụng", "Chưa có nội dung đã kiểm chứng.")
        DetailSection("Lưu ý an toàn", "Chưa có nguồn an toàn chuyên ngành cho hồ sơ mẫu này.")
        DetailSection("Tên khoa học / nguồn", "Chỉ hiển thị khi hồ sơ thật đã được đối chiếu và vượt cổng phát hành.")
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFFEFA), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("○", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
