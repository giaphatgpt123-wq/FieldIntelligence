package vn.survivallibrary.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private val DForest = Color(0xFF174F38)
private val DDark = Color(0xFF103D2A)
private val DCream = Color(0xFFF8F5EC)
private val DPaper = Color(0xFFFFFEFA)
private val DMuted = Color(0xFF68736B)
private val DLeaf = Color(0xFFEAF4E5)
private val DWarn = Color(0xFFFFF0C9)

private enum class DTab(val label: String, val icon: ImageVector) {
    HOME("Trang chủ", Icons.Filled.Home), EXPLORE("Khám phá", Icons.Filled.Search),
    SAVED("Đã lưu", Icons.Filled.Bookmark), LEARN("Học tập", Icons.Filled.MenuBook), PROFILE("Cá nhân", Icons.Filled.Person)
}
private data class DNeed(val title: String, val subtitle: String, val icon: ImageVector, val color: Color, val category: String? = null)
private val dNeeds = listOf(
    DNeed("Uống", "Tìm nguồn nước an toàn", Icons.Filled.WaterDrop, Color(0xFF2E6D7A)),
    DNeed("Ăn", "Thực phẩm từ thiên nhiên", Icons.Filled.Restaurant, Color(0xFF8B5C3D), "vegetables"),
    DNeed("Ở", "Nơi trú ẩn, dựng lều", Icons.Filled.Terrain, Color(0xFF4F7048)),
    DNeed("Tránh nguy hiểm", "Động vật, cây độc, thời tiết", Icons.Filled.Shield, Color(0xFF66503A), "danger")
)

@Composable
fun DemoSurvivalApp() {
    var tab by remember { mutableStateOf(DTab.HOME) }
    var detail by remember { mutableStateOf<LibraryRecordUi?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    MaterialTheme(lightColorScheme(primary = Color(0xFF22613F), background = DCream, surface = DPaper, surfaceVariant = DLeaf, onSurfaceVariant = DMuted)) {
        Scaffold(
            containerColor = DCream,
            bottomBar = {
                if (detail == null) NavigationBar(containerColor = DPaper) {
                    DTab.entries.forEach { item ->
                        NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label, fontSize = 10.sp) })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                if (detail != null) DDetail(detail!!) { detail = null }
                else when (tab) {
                    DTab.HOME -> DHome(onExplore = { category = null; tab = DTab.EXPLORE }, onCategory = { category = it; tab = DTab.EXPLORE }, onRecord = { detail = it })
                    DTab.EXPLORE -> DExplore(category, onRecord = { detail = it })
                    DTab.SAVED -> DEmpty(Icons.Filled.Bookmark, "Đã lưu", "Hồ sơ đã đánh dấu sẽ xuất hiện tại đây.")
                    DTab.LEARN -> DLearn()
                    DTab.PROFILE -> DProfile()
                }
            }
        }
    }
}

@Composable
private fun DHome(onExplore: () -> Unit, onCategory: (String) -> Unit, onRecord: (LibraryRecordUi) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DHeader()
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DHero()
            Surface(Modifier.fillMaxWidth().clickable(onClick = onExplore), RoundedCornerShape(18.dp), Color.White, border = BorderStroke(2.dp, Color(0xFF6EA36B))) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = DForest); Spacer(Modifier.width(8.dp)); Text("Tìm kiếm cây cỏ, thực phẩm, kỹ năng...", Modifier.weight(1f), color = DMuted, fontSize = 13.sp); Icon(Icons.Filled.Eco, null, tint = DForest)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DNeedCard(dNeeds[0], Modifier.weight(1f), onExplore); DNeedCard(dNeeds[1], Modifier.weight(1f)) { onCategory("vegetables") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DNeedCard(dNeeds[2], Modifier.weight(1f), onExplore); DNeedCard(dNeeds[3], Modifier.weight(1f)) { onCategory("danger") }
            }
            DTitle("Thường dùng ở Việt Nam", onExplore)
            DWarning()
            SurvivalLibraryCatalog.demoRecords.take(3).forEach { DRecord(it, onRecord) }
        }
    }
}

@Composable
private fun DHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {}) { Icon(Icons.Filled.Menu, "Menu") }
        Image(painterResource(R.drawable.ic_app_logo), null, Modifier.size(38.dp)); Spacer(Modifier.width(8.dp))
        Text("THƯ VIỆN SINH TỒN", Modifier.weight(1f), color = DDark, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 20.sp)
        IconButton(onClick = {}) { Icon(Icons.Filled.Notifications, "Thông báo", tint = DForest) }
    }
}

@Composable
private fun DHero() {
    Card(Modifier.fillMaxWidth().height(205.dp), RoundedCornerShape(25.dp), CardDefaults.cardColors(containerColor = DForest)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFBBD9C6), Color(0xFF739C78), Color(0xFF315F46))))
                drawCircle(Color(0xFFFFE6A1), size.minDimension * .09f, Offset(size.width * .80f, size.height * .19f))
                val far = Path().apply { moveTo(0f,size.height*.72f); lineTo(size.width*.18f,size.height*.38f); lineTo(size.width*.35f,size.height*.64f); lineTo(size.width*.53f,size.height*.29f); lineTo(size.width*.72f,size.height*.64f); lineTo(size.width,size.height*.42f); lineTo(size.width,size.height); lineTo(0f,size.height); close() }
                drawPath(far, Color(0xFF52795F))
                val near = Path().apply { moveTo(0f,size.height*.84f); lineTo(size.width*.17f,size.height*.57f); lineTo(size.width*.33f,size.height*.80f); lineTo(size.width*.48f,size.height*.55f); lineTo(size.width*.66f,size.height*.85f); lineTo(size.width*.84f,size.height*.60f); lineTo(size.width,size.height*.80f); lineTo(size.width,size.height); lineTo(0f,size.height); close() }
                drawPath(near, Color(0xFF214D38))
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xC0103023), Color(0x55103023), Color.Transparent))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text("Hiểu thiên nhiên\nSống an toàn hơn", color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 28.sp)
                Text("Kiến thức hôm nay cho những hành trình ngày mai", color = Color.White.copy(.86f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DNeedCard(item: DNeed, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(136.dp), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = item.color)) {
        Box(Modifier.fillMaxSize().padding(13.dp)) {
            Surface(shape = CircleShape, color = Color.White.copy(.92f)) { Icon(item.icon, null, Modifier.padding(9.dp).size(24.dp), tint = item.color) }
            Column(Modifier.align(Alignment.BottomStart).padding(end = 20.dp)) { Text(item.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 20.sp); Text(item.subtitle, color = Color.White.copy(.84f), fontSize = 11.sp, lineHeight = 14.sp) }
            Icon(Icons.Filled.ArrowForward, null, Modifier.align(Alignment.BottomEnd), tint = Color.White)
        }
    }
}

@Composable
private fun DTitle(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 21.sp); Text("Xem thêm", Modifier.clickable(onClick = onClick), color = DForest, fontWeight = FontWeight.Bold, fontSize = 12.sp); Icon(Icons.Filled.ArrowForward, null, Modifier.size(15.dp), tint = DForest) }
}

@Composable
private fun DWarning() {
    Surface(shape = RoundedCornerShape(17.dp), color = DWarn) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Info, null, tint = Color(0xFF825B12)); Spacer(Modifier.width(8.dp)); Text("Dữ liệu hiện tại là mẫu UI; chưa dùng để quyết định ăn, độc tính hoặc công dụng.", color = Color(0xFF6E4E12), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun DRecord(record: LibraryRecordUi, onClick: (LibraryRecordUi) -> Unit) {
    Card(onClick = { onClick(record) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = DPaper), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(82.dp).background(Brush.linearGradient(listOf(Color(0xFFDCEBCF), Color(0xFF508258))), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Eco, null, Modifier.size(39.dp), tint = Color.White) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(record.vietnameseName, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(SurvivalLibraryCatalog.category(record.categoryId)?.label.orEmpty(), color = DMuted, fontSize = 11.sp); Spacer(Modifier.height(4.dp)); Text("DEMO • ${record.usageLevel.label}", color = DForest, fontWeight = FontWeight.Bold, fontSize = 10.sp); Text(record.summary, color = DMuted, fontSize = 11.sp, lineHeight = 14.sp) }; Icon(Icons.Filled.ArrowForward, null, tint = DForest)
        }
    }
}

@Composable
private fun DExplore(initialCategory: String?, onRecord: (LibraryRecordUi) -> Unit) {
    var selected by remember(initialCategory) { mutableStateOf(initialCategory) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Danh mục tri thức", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = DDark); Text("Khám phá và hoàn thành thư viện sinh tồn", color = DMuted)
        dNeeds.forEach { item -> Card(onClick = { selected = item.category }, modifier = Modifier.fillMaxWidth().height(104.dp), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = item.color)) { Row(Modifier.fillMaxSize().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = Color.White.copy(.92f)) { Icon(item.icon, null, Modifier.padding(10.dp), tint = item.color) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(item.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 20.sp); Text(item.subtitle, color = Color.White.copy(.82f), fontSize = 11.sp); Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(0xFFBFE2AA), trackColor = Color.White.copy(.18f)); Text("0 mục đã phát hành", color = Color.White.copy(.8f), fontSize = 10.sp) }; Icon(Icons.Filled.ArrowForward, null, tint = Color.White) } } }
        Text("Tiến độ thư viện", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 23.sp); DProgress(); DWarning()
        val records = SurvivalLibraryCatalog.demoRecordsFor(selected); if (records.isEmpty()) DEmpty(Icons.Filled.Search, "Chưa có hồ sơ", "Chưa có hồ sơ mẫu phù hợp với danh mục này.") else records.forEach { DRecord(it, onRecord) }
    }
}

@Composable
private fun DProgress() {
    val verified = SurvivalLibraryCatalog.progress.sumOf { it.verified }; val published = SurvivalLibraryCatalog.progress.sumOf { it.published }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = DPaper)) { Column(Modifier.padding(15.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = DLeaf) { Icon(Icons.Filled.Eco, null, Modifier.padding(10.dp), tint = DForest) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Đã phát hành", fontWeight = FontWeight.Bold); Text(published.toString(), color = DForest, fontWeight = FontWeight.Black, fontSize = 27.sp) }; Text("Dữ liệu thật", color = DMuted, fontSize = 11.sp) }; Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { if (verified == 0) 0f else published.toFloat()/verified.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp), color = DForest, trackColor = Color(0xFFE3E8E2)); Spacer(Modifier.height(7.dp)); Text("Không tạo số mục tiêu giả. Hồ sơ đạt chuẩn đến đâu mới hiển thị đến đó.", color = DMuted, fontSize = 11.sp) } }
}

@Composable
private fun DDetail(record: LibraryRecordUi, onBack: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp).background(Brush.verticalGradient(listOf(Color(0xFFD3E8B8), Color(0xFF699D5F), Color(0xFF1D4B34))))) {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).background(Color.Black.copy(.25f), CircleShape)) { Icon(Icons.Filled.ArrowBack, "Quay lại", tint = Color.White) }
            Column(Modifier.align(Alignment.BottomStart).padding(17.dp)) { Text(record.vietnameseName, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 31.sp); Text("Hồ sơ DEMO • chưa phải dữ liệu kiểm chứng", color = Color.White.copy(.84f), fontSize = 11.sp) }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Card(onClick = { open = !open }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = DPaper)) { Column(Modifier.padding(13.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Eco, null, tint = DForest); Spacer(Modifier.width(7.dp)); Text("Nhận biết", Modifier.weight(1f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 17.sp); Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) }; if (open) { Spacer(Modifier.height(7.dp)); Text("Sẽ hiển thị đặc điểm nhận biết ngắn, rõ và nhiều góc ảnh. Bản hiện tại chưa đưa mô tả loài thật vào ứng dụng.", color = DMuted, fontSize = 12.sp) } } }
            DWarning()
        }
    }
}

@Composable
private fun DLearn() { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Học tập", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = DDark); DRule(Icons.Filled.Eco, "Tiếng Việt trước", "Tên Việt, ảnh và nhận biết ngắn đặt trước."); DRule(Icons.Filled.Shield, "An toàn có quyền ưu tiên", "Đối tượng nguy cơ cao chỉ phát hành khi có nguồn phù hợp."); DRule(Icons.Filled.CheckCircle, "Đạt chuẩn đến đâu lên app đến đó", "Không đợi hoàn thiện cả danh mục mới phát hành hồ sơ đạt chuẩn.") } }
@Composable
private fun DProfile() { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Cá nhân", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = DDark); Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = DPaper)) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Image(painterResource(R.drawable.ic_app_logo), null, Modifier.size(60.dp)); Spacer(Modifier.width(11.dp)); Column { Text("Thư viện Sinh tồn Việt Nam", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 17.sp); Text("Beta UI 0.3.0", color = DMuted) } } }; DRule(Icons.Filled.Settings, "Cài đặt", "Tùy chọn hiển thị và dữ liệu offline sẽ đặt tại đây.") } }
@Composable
private fun DRule(icon: ImageVector, title: String, body: String) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = DPaper)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) { Surface(shape = CircleShape, color = DLeaf) { Icon(icon, null, Modifier.padding(9.dp), tint = DForest) }; Spacer(Modifier.width(11.dp)); Column { Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 17.sp); Text(body, color = DMuted, fontSize = 12.sp) } } } }
@Composable
private fun DEmpty(icon: ImageVector, title: String, body: String) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(shape = CircleShape, color = DLeaf) { Icon(icon, null, Modifier.padding(18.dp).size(36.dp), tint = DForest) }; Spacer(Modifier.height(10.dp)); Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 22.sp); Spacer(Modifier.height(5.dp)); Text(body, color = DMuted, textAlign = TextAlign.Center, fontSize = 12.sp) } }
