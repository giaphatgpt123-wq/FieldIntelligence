package vn.survivallibrary.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PForest = Color(0xFF174F38)
private val PForestDark = Color(0xFF103D2A)
private val PCream = Color(0xFFF8F5EC)
private val PPaper = Color(0xFFFFFEFA)
private val PInk = Color(0xFF17231B)
private val PMuted = Color(0xFF69736B)
private val PLeaf = Color(0xFFEAF4E5)
private val PWarn = Color(0xFFFFF0C9)

private val PremiumColors = lightColorScheme(
    primary = Color(0xFF22613F),
    secondary = Color(0xFF4C7655),
    background = PCream,
    surface = PPaper,
    onSurface = PInk,
    surfaceVariant = PLeaf,
    onSurfaceVariant = PMuted
)

private enum class PremiumTab(val label: String, val icon: ImageVector) {
    HOME("Trang chủ", Icons.Filled.Home),
    EXPLORE("Khám phá", Icons.Filled.Search),
    SAVED("Đã lưu", Icons.Filled.Bookmark),
    LEARN("Học tập", Icons.Filled.MenuBook),
    PROFILE("Cá nhân", Icons.Filled.Person)
}

private data class NeedCardModel(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val category: String? = null
)

private val premiumNeeds = listOf(
    NeedCardModel("Uống", "Tìm nguồn nước an toàn", Icons.Filled.WaterDrop, Color(0xFF2E6D7A)),
    NeedCardModel("Ăn", "Thực phẩm từ thiên nhiên", Icons.Filled.Restaurant, Color(0xFF8B5C3D), "vegetables"),
    NeedCardModel("Ở", "Nơi trú ẩn, dựng lều", Icons.Filled.Terrain, Color(0xFF4F7048)),
    NeedCardModel("Tránh nguy hiểm", "Động vật, cây độc, thời tiết", Icons.Filled.Shield, Color(0xFF66503A), "danger")
)

@Composable
fun PremiumSurvivalApp() {
    var tab by remember { mutableStateOf(PremiumTab.HOME) }
    var detail by remember { mutableStateOf<LibraryRecordUi?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var scanner by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = PremiumColors) {
        Scaffold(
            containerColor = PCream,
            bottomBar = {
                if (detail == null && !scanner) {
                    NavigationBar(containerColor = PPaper) {
                        PremiumTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when {
                    scanner -> PremiumScanner { scanner = false }
                    detail != null -> PremiumDetail(detail!!) { detail = null }
                    tab == PremiumTab.HOME -> PremiumHome(
                        onExplore = { category = null; tab = PremiumTab.EXPLORE },
                        onCategory = { category = it; tab = PremiumTab.EXPLORE },
                        onScan = { scanner = true },
                        onRecord = { detail = it }
                    )
                    tab == PremiumTab.EXPLORE -> PremiumExplore(category) { detail = it }
                    tab == PremiumTab.SAVED -> PremiumEmpty(Icons.Filled.Bookmark, "Đã lưu", "Hồ sơ đã đánh dấu sẽ xuất hiện tại đây.")
                    tab == PremiumTab.LEARN -> PremiumLearn()
                    else -> PremiumProfile()
                }
            }
        }
    }
}

@Composable
private fun PremiumHome(
    onExplore: () -> Unit,
    onCategory: (String) -> Unit,
    onScan: () -> Unit,
    onRecord: (LibraryRecordUi) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumHeader()
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PremiumHero()
            PremiumSearch(onExplore)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumNeedCard(premiumNeeds[0], Modifier.weight(1f), onExplore)
                PremiumNeedCard(premiumNeeds[1], Modifier.weight(1f)) { onCategory("vegetables") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumNeedCard(premiumNeeds[2], Modifier.weight(1f), onExplore)
                PremiumNeedCard(premiumNeeds[3], Modifier.weight(1f)) { onCategory("danger") }
            }
            PremiumSectionTitle("Thường dùng ở Việt Nam", "Xem thêm", onExplore)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Thường dùng", "Hay dùng", "Dễ tìm", "Xem thêm").forEachIndexed { i, value ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (i == 0) PForest else PPaper,
                        border = if (i == 0) null else BorderStroke(1.dp, Color(0xFFD8DED7))
                    ) {
                        Text(value, Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = if (i == 0) Color.White else PInk, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
            PremiumDemoNote()
            SurvivalLibraryCatalog.demoRecords.take(3).forEach { PremiumRecordCard(it, onRecord) }
            PremiumScanBanner(onScan)
        }
    }
}

@Composable
private fun PremiumHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {}) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
        Image(painterResource(R.drawable.ic_app_logo), null, Modifier.size(38.dp))
        Spacer(Modifier.width(8.dp))
        Text("THƯ VIỆN SINH TỒN", Modifier.weight(1f), color = PForestDark, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 20.sp)
        IconButton(onClick = {}) { Icon(Icons.Filled.Notifications, contentDescription = "Thông báo", tint = PForest) }
    }
}

@Composable
private fun PremiumHero() {
    Card(Modifier.fillMaxWidth().height(205.dp), shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = PForest)) {
        Box(Modifier.fillMaxSize()) {
            PremiumLandscape(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xC0103023), Color(0x55103023), Color.Transparent))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp).fillMaxWidth(.78f)) {
                Text("Hiểu thiên nhiên\nSống an toàn hơn", color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 28.sp)
                Spacer(Modifier.height(5.dp))
                Text("Kiến thức hôm nay cho những hành trình ngày mai", color = Color.White.copy(.88f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PremiumLandscape(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFBBD9C6), Color(0xFF739C78), Color(0xFF315F46))))
        drawCircle(Color(0xFFFFE6A1), size.minDimension * .09f, Offset(size.width * .80f, size.height * .19f))
        val far = Path().apply {
            moveTo(0f, size.height * .72f); lineTo(size.width * .18f, size.height * .38f); lineTo(size.width * .35f, size.height * .64f)
            lineTo(size.width * .53f, size.height * .29f); lineTo(size.width * .72f, size.height * .64f); lineTo(size.width, size.height * .42f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(far, Color(0xFF52795F))
        val near = Path().apply {
            moveTo(0f, size.height * .84f); lineTo(size.width * .17f, size.height * .57f); lineTo(size.width * .33f, size.height * .80f)
            lineTo(size.width * .48f, size.height * .55f); lineTo(size.width * .66f, size.height * .85f); lineTo(size.width * .84f, size.height * .60f)
            lineTo(size.width, size.height * .80f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(near, Color(0xFF214D38))
        val river = Path().apply {
            moveTo(size.width * .63f, size.height * .56f)
            quadraticBezierTo(size.width * .57f, size.height * .72f, size.width * .67f, size.height * .82f)
            quadraticBezierTo(size.width * .82f, size.height * .93f, size.width * .48f, size.height)
            lineTo(size.width * .75f, size.height)
            quadraticBezierTo(size.width * .90f, size.height * .91f, size.width * .72f, size.height * .80f)
            quadraticBezierTo(size.width * .66f, size.height * .69f, size.width * .69f, size.height * .56f); close()
        }
        drawPath(river, Color(0xFFCCE4DB))
    }
}

@Composable
private fun PremiumSearch(onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(19.dp), color = Color.White, border = BorderStroke(2.dp, Color(0xFF6EA36B))) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = PForest); Spacer(Modifier.width(8.dp))
            Text("Tìm kiếm cây cỏ, thực phẩm, kỹ năng...", Modifier.weight(1f), color = PMuted, fontSize = 13.sp)
            Icon(Icons.Filled.Eco, null, tint = PForest)
        }
    }
}

@Composable
private fun PremiumNeedCard(item: NeedCardModel, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(138.dp), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = item.color)) {
        Box(Modifier.fillMaxSize().padding(13.dp)) {
            Surface(shape = CircleShape, color = Color.White.copy(.92f)) { Icon(item.icon, null, Modifier.padding(9.dp).size(24.dp), tint = item.color) }
            Column(Modifier.align(Alignment.BottomStart).padding(end = 22.dp)) {
                Text(item.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(item.subtitle, color = Color.White.copy(.84f), fontSize = 11.sp, lineHeight = 14.sp)
            }
            Surface(Modifier.align(Alignment.BottomEnd), shape = CircleShape, color = Color.White.copy(.18f)) { Icon(Icons.Filled.ArrowForward, null, Modifier.padding(6.dp).size(17.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun PremiumSectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 21.sp)
        Text(action, Modifier.clickable(onClick = onClick), color = PForest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Icon(Icons.Filled.ArrowForward, null, tint = PForest, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun PremiumDemoNote() {
    Surface(shape = RoundedCornerShape(17.dp), color = PWarn) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = Color(0xFF825B12)); Spacer(Modifier.width(8.dp))
            Text("Dữ liệu hiện tại là mẫu UI; chưa dùng để quyết định ăn, độc tính hoặc công dụng.", color = Color(0xFF6E4E12), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PremiumRecordCard(record: LibraryRecordUi, onClick: (LibraryRecordUi) -> Unit) {
    Card(onClick = { onClick(record) }, shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = PPaper), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(82.dp).background(Brush.linearGradient(listOf(Color(0xFFDCEBCF), Color(0xFF508258))), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Eco, null, tint = Color.White, modifier = Modifier.size(39.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.vietnameseName, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFFFE3A8)) { Text("DEMO", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF694A0B)) }
                }
                Text(SurvivalLibraryCatalog.category(record.categoryId)?.label.orEmpty(), color = PMuted, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFE7ECE7)) { Text(record.usageLevel.label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(5.dp))
                Text(record.summary, color = PMuted, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Icon(Icons.Filled.ArrowForward, null, tint = PForest)
        }
    }
}

@Composable
private fun PremiumScanBanner(onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = PForestDark)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color.White.copy(.14f)) { Icon(Icons.Filled.CameraAlt, null, Modifier.padding(11.dp).size(26.dp), tint = Color.White) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Nhận dạng nhanh bằng camera", color = Color.White, fontWeight = FontWeight.Black)
                Text("Khung AI offline • chưa bật model", color = Color.White.copy(.75f), fontSize = 11.sp)
            }
            Icon(Icons.Filled.ArrowForward, null, tint = Color.White)
        }
    }
}

@Composable
private fun PremiumExplore(initialCategory: String?, onRecord: (LibraryRecordUi) -> Unit) {
    var selected by remember(initialCategory) { mutableStateOf(initialCategory) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Danh mục tri thức", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = PForestDark)
        Text("Khám phá và hoàn thành thư viện sinh tồn", color = PMuted)
        premiumNeeds.forEach { item -> PremiumKnowledgeCard(item) { selected = item.category } }
        Text("Tiến độ thư viện", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 23.sp)
        PremiumProgress()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("Tất cả") })
            SurvivalLibraryCatalog.categories.forEach { c -> FilterChip(selected = selected == c.id, onClick = { selected = c.id }, label = { Text(c.label) }) }
        }
        PremiumDemoNote()
        val records = SurvivalLibraryCatalog.demoRecordsFor(selected)
        if (records.isEmpty()) PremiumEmpty(Icons.Filled.Search, "Chưa có hồ sơ", "Chưa có hồ sơ mẫu phù hợp với danh mục này.")
        else records.forEach { PremiumRecordCard(it, onRecord) }
    }
}

@Composable
private fun PremiumKnowledgeCard(item: NeedCardModel, onClick: () -> Unit) {
    Card(onClick = onClick, Modifier.fillMaxWidth().height(108.dp), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = item.color)) {
        Row(Modifier.fillMaxSize().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color.White.copy(.92f)) { Icon(item.icon, null, Modifier.padding(10.dp).size(25.dp), tint = item.color) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(item.subtitle, color = Color.White.copy(.82f), fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { 0f }, Modifier.fillMaxWidth().height(5.dp), color = Color(0xFFBFE2AA), trackColor = Color.White.copy(.18f))
                Text("0 mục đã phát hành", color = Color.White.copy(.8f), fontSize = 10.sp)
            }
            Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.ArrowForward, null, tint = Color.White)
        }
    }
}

@Composable
private fun PremiumProgress() {
    val verified = SurvivalLibraryCatalog.progress.sumOf { it.verified }
    val published = SurvivalLibraryCatalog.progress.sumOf { it.published }
    Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = PPaper), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = PLeaf) { Icon(Icons.Filled.Eco, null, Modifier.padding(10.dp), tint = PForest) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text("Đã phát hành", fontWeight = FontWeight.Bold); Text(published.toString(), color = PForest, fontSize = 27.sp, fontWeight = FontWeight.Black) }
                Text("Dữ liệu thật", color = PMuted, fontSize = 11.sp)
            }
            LinearProgressIndicator(progress = { if (verified == 0) 0f else published.toFloat() / verified.toFloat() }, Modifier.fillMaxWidth().height(6.dp), color = PForest, trackColor = Color(0xFFE3E8E2))
            Text("Không tạo số mục tiêu giả. Hồ sơ đạt chuẩn đến đâu mới hiển thị đến đó.", color = PMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PremiumDetail(record: LibraryRecordUi, onBack: () -> Unit) {
    var open1 by remember { mutableStateOf(true) }
    var open2 by remember { mutableStateOf(false) }
    var open3 by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawRect(Brush.verticalGradient(listOf(Color(0xFFD3E8B8), Color(0xFF699D5F), Color(0xFF1D4B34)))); repeat(7) { i -> drawOval(Color.White.copy(.09f), Offset(size.width * (.06f + i * .14f), size.height * (.12f + (i % 3) * .12f)), androidx.compose.ui.geometry.Size(size.width * .17f, size.height * .42f)) } }
            Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                PremiumRoundIcon(Icons.Filled.ArrowBack, onBack)
                Row { PremiumRoundIcon(Icons.Filled.Favorite) {}; Spacer(Modifier.width(5.dp)); PremiumRoundIcon(Icons.Filled.MoreVert) {} }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(17.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xCC2D754A)) { Text(record.usageLevel.label, Modifier.padding(horizontal = 11.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                Spacer(Modifier.height(7.dp))
                Text(record.vietnameseName, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 31.sp)
                Text("Hồ sơ DEMO • chưa phải dữ liệu kiểm chứng", color = Color.White.copy(.84f), fontSize = 11.sp)
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PremiumPill(Icons.Filled.Eco, "Tên Việt trước"); PremiumPill(Icons.Filled.Shield, "An toàn trước"); PremiumPill(Icons.Filled.CheckCircle, "Nguồn kiểm chứng")
            }
            PremiumInfoCard(Icons.Filled.Eco, "Nhận biết", open1, { open1 = !open1 }, "Sẽ hiển thị đặc điểm nhận biết ngắn, rõ và nhiều góc ảnh. Bản hiện tại chưa đưa mô tả loài thật vào ứng dụng.")
            PremiumInfoCard(Icons.Filled.Info, "Dễ nhầm", open2, { open2 = !open2 }, "Sẽ nêu các đối tượng dễ nhầm và điểm khác biệt có thể quan sát; AI không được tự khẳng định khi thiếu bằng chứng.")
            PremiumInfoCard(Icons.Filled.Restaurant, "Cách dùng", open3, { open3 = !open3 }, "Chỉ hiển thị công dụng hoặc cách dùng khi trường dữ liệu tương ứng đã qua cổng nguồn và kiểm chứng.")
            Surface(shape = RoundedCornerShape(19.dp), color = PWarn) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFBF7908)); Spacer(Modifier.width(8.dp))
                    Column { Text("Lưu ý an toàn", fontWeight = FontWeight.Black, color = Color(0xFF6E4E12)); Text("Chưa xác nhận mẫu vật qua ảnh. Không dùng nội dung DEMO để quyết định ăn, uống, điều trị hoặc tiếp xúc.", color = Color(0xFF6E4E12), fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
private fun PremiumPill(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = PPaper, border = BorderStroke(1.dp, Color(0xFFDCE2DA))) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(16.dp), tint = PForest); Spacer(Modifier.width(4.dp)); Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PremiumInfoCard(icon: ImageVector, title: String, open: Boolean, toggle: () -> Unit, body: String) {
    Card(onClick = toggle, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = PPaper)) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = PForest); Spacer(Modifier.width(7.dp)); Text(title, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 17.sp); Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) }
            if (open) { Spacer(Modifier.height(7.dp)); Text(body, color = PMuted, fontSize = 12.sp, lineHeight = 17.sp) }
        }
    }
}

@Composable
private fun PremiumRoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color.Black.copy(.28f)) { IconButton(onClick = onClick) { Icon(icon, null, tint = Color.White) } }
}

@Composable
private fun PremiumScanner(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Quay lại") }; Text("Quét nhận dạng", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 26.sp) }
        Box(Modifier.fillMaxWidth().height(450.dp).background(Brush.verticalGradient(listOf(Color(0xFF8DB697), Color(0xFF3A6349), Color(0xFF173B2A))), RoundedCornerShape(27.dp)), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(25.dp), color = Color.Transparent, border = BorderStroke(2.dp, Color.White.copy(.85f))) {
                Column(Modifier.size(240.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.CameraAlt, null, Modifier.size(56.dp), tint = Color.White); Spacer(Modifier.height(9.dp)); Text("Đưa mẫu vật vào khung", color = Color.White, fontWeight = FontWeight.Black); Text("Chụp nhiều góc khi cần", color = Color.White.copy(.78f), fontSize = 11.sp)
                }
            }
        }
        PremiumDemoNote(); PremiumEmpty(Icons.Filled.CameraAlt, "Model chưa cài", "Bản này chưa có model nhận dạng đã kiểm chứng nên không tự đoán tên loài.")
    }
}

@Composable
private fun PremiumLearn() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Học tập", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = PForestDark)
        Text("Quy tắc đọc dữ liệu và dùng thư viện an toàn", color = PMuted)
        PremiumRule(Icons.Filled.Eco, "Tiếng Việt trước", "Tên Việt, ảnh và nhận biết ngắn đặt trước; thuật ngữ khoa học mở khi cần.")
        PremiumRule(Icons.Filled.Shield, "An toàn có quyền ưu tiên", "Đối tượng nguy cơ cao chỉ phát hành khi có nguồn an toàn phù hợp.")
        PremiumRule(Icons.Filled.CheckCircle, "Đạt chuẩn đến đâu lên app đến đó", "Không đợi hoàn thiện cả danh mục mới cho người dùng xem hồ sơ đã đạt chuẩn.")
        PremiumRule(Icons.Filled.CloudDownload, "Offline trước", "Dữ liệu và model đã tải được thiết kế để dùng khi mất mạng.")
    }
}

@Composable
private fun PremiumRule(icon: ImageVector, title: String, body: String) {
    Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = PPaper)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = PLeaf) { Icon(icon, null, Modifier.padding(9.dp), tint = PForest) }; Spacer(Modifier.width(11.dp))
            Column { Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 17.sp); Spacer(Modifier.height(3.dp)); Text(body, color = PMuted, fontSize = 12.sp, lineHeight = 17.sp) }
        }
    }
}

@Composable
private fun PremiumProfile() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cá nhân", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = PForestDark)
        Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = PPaper)) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_app_logo), null, Modifier.size(60.dp)); Spacer(Modifier.width(11.dp))
                Column { Text("Thư viện Sinh tồn Việt Nam", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 17.sp); Text("Beta UI 0.3.0", color = PMuted) }
            }
        }
        PremiumRule(Icons.Filled.Settings, "Cài đặt", "Tùy chọn hiển thị, dữ liệu offline và quyền riêng tư sẽ đặt tại đây.")
        PremiumRule(Icons.Filled.CloudDownload, "Cập nhật thư viện", "Dữ liệu sẽ cập nhật theo gói độc lập để hạn chế cài lại APK.")
        PremiumDemoNote()
    }
}

@Composable
private fun PremiumEmpty(icon: ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = PLeaf) { Icon(icon, null, Modifier.padding(18.dp).size(36.dp), tint = PForest) }
        Spacer(Modifier.height(10.dp)); Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 22.sp); Spacer(Modifier.height(5.dp)); Text(body, color = PMuted, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}
