package vn.survivallibrary.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Forest = Color(0xFF123F2C)
private val Forest2 = Color(0xFF1D5A3D)
private val Cream = Color(0xFFF7F3E8)
private val Paper = Color(0xFFFFFEFA)
private val Sage = Color(0xFFE9F1E4)
private val Muted = Color(0xFF667168)
private val Gold = Color(0xFFD7B465)
private val Warning = Color(0xFFFFE9B6)

private val PremiumScheme = lightColorScheme(
    primary = Forest2,
    onPrimary = Color.White,
    background = Cream,
    surface = Paper,
    surfaceVariant = Sage,
    onSurface = Color(0xFF19231D),
    onSurfaceVariant = Muted
)

private enum class PremiumTab(val label: String, val icon: ImageVector) {
    HOME("Trang chủ", Icons.Filled.Home),
    EXPLORE("Khám phá", Icons.Filled.Search),
    SAVED("Đã lưu", Icons.Filled.Bookmark),
    LEARN("Học tập", Icons.Filled.MenuBook),
    PROFILE("Cá nhân", Icons.Filled.Person)
}

private data class DemoVisual(
    val id: String,
    val title: String,
    val subtitle: String,
    val categoryId: String,
    val imageRes: Int
)

private val demoVisuals = listOf(
    DemoVisual("demo-rau-muong", "Rau muống", "Ảnh minh họa giao diện", "vegetables", R.drawable.rau_muong_demo),
    DemoVisual("demo-ca-rot", "Cà rốt", "Ảnh minh họa giao diện", "roots", R.drawable.carrot_demo),
    DemoVisual("demo-la-xanh", "Lá thực vật", "Ảnh minh họa giao diện", "medicinal-plants", R.drawable.leaf_demo)
)

@Composable
fun PremiumSurvivalApp() {
    val context = LocalContext.current
    val db = remember { OfflineLibraryDb(context.applicationContext) }
    DisposableEffect(db) { onDispose { db.close() } }

    var tab by remember { mutableStateOf(PremiumTab.HOME) }
    var detail by remember { mutableStateOf<DemoVisual?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(db.favoriteIds()) }

    fun toggleFavorite(id: String) {
        val next = !favorites.contains(id)
        db.setFavorite(id, next)
        favorites = db.favoriteIds()
    }

    MaterialTheme(colorScheme = PremiumScheme) {
        Scaffold(
            containerColor = Cream,
            bottomBar = {
                if (detail == null) {
                    NavigationBar(containerColor = Paper) {
                        PremiumTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val current = detail
                if (current != null) {
                    PremiumDetail(
                        item = current,
                        favorite = favorites.contains(current.id),
                        onFavorite = { toggleFavorite(current.id) },
                        onBack = { detail = null }
                    )
                } else {
                    when (tab) {
                        PremiumTab.HOME -> PremiumHome(
                            onExplore = { selectedCategory = null; tab = PremiumTab.EXPLORE },
                            onCategory = { selectedCategory = it; tab = PremiumTab.EXPLORE },
                            onDetail = { detail = it }
                        )
                        PremiumTab.EXPLORE -> PremiumExplore(
                            initialCategory = selectedCategory,
                            onDetail = { detail = it }
                        )
                        PremiumTab.SAVED -> PremiumSaved(
                            savedIds = favorites,
                            onDetail = { detail = it }
                        )
                        PremiumTab.LEARN -> PremiumLearn()
                        PremiumTab.PROFILE -> PremiumProfile(
                            publishedCount = db.publishedCount(),
                            verifiedCount = db.verifiedCount(),
                            savedCount = favorites.size
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumHome(
    onExplore: () -> Unit,
    onCategory: (String) -> Unit,
    onDetail: (DemoVisual) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PremiumHeader()
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroCard()
            SearchLauncher(onExplore)
            NeedGrid(onCategory)

            SectionTitle("Thường dùng ở Việt Nam", "Xem thư viện", onExplore)
            Surface(
                color = Color(0xFFF1E8CD),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Khu vực này chỉ nhận hồ sơ thật sau khi vượt cổng kiểm chứng. Các thẻ hiện tại được gắn DEMO để kiểm tra giao diện.",
                    Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = Color(0xFF655423)
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                demoVisuals.forEach { item -> DemoPhotoCard(item, onDetail) }
            }

            SectionTitle("Khám phá theo danh mục", "Tất cả", onExplore)
            SurvivalLibraryCatalog.categories.take(6).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { category -> CategoryTile(category, Modifier.weight(1f)) { onCategory(category.id) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PremiumHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon_demo),
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("THƯ VIỆN SINH TỒN", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, color = Forest, fontSize = 20.sp)
            Text("Tri thức thiên nhiên cho cuộc sống an toàn hơn", color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Filled.Eco, null, tint = Forest2)
    }
}

@Composable
private fun HeroCard() {
    Box(
        Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(24.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.hero_scene),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x22000000), Color(0xC9122D21)))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Surface(color = Color(0xBFFFFFFF), shape = RoundedCornerShape(999.dp)) {
                Text("KIẾN THỨC VIỆT NAM", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Forest, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Hiểu thiên nhiên\nSống an toàn hơn", color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 30.sp)
            Text("Ưu tiên tiếng Việt • ảnh nhận biết • an toàn trước", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SearchLauncher(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, Color(0xFF76A974))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = Forest)
            Spacer(Modifier.width(9.dp))
            Text("Tìm cây cỏ, thực phẩm, kỹ năng...", Modifier.weight(1f), color = Muted, fontSize = 13.sp)
            Icon(Icons.Filled.LocalFlorist, null, tint = Forest2)
        }
    }
}

@Composable
private fun NeedGrid(onCategory: (String) -> Unit) {
    val needs = listOf(
        Triple("Uống", "Tìm nguồn nước an toàn", Icons.Filled.WaterDrop),
        Triple("Ăn", "Thực phẩm từ thiên nhiên", Icons.Filled.Restaurant),
        Triple("Ở", "Nơi trú ẩn, dựng lều", Icons.Filled.Terrain),
        Triple("Tránh nguy hiểm", "Động vật, cây độc, thời tiết", Icons.Filled.Shield)
    )
    needs.chunked(2).forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEachIndexed { columnIndex, item ->
                val index = rowIndex * 2 + columnIndex
                val category = when (index) {
                    1 -> "vegetables"
                    3 -> "danger"
                    else -> null
                }
                NeedTile(item.first, item.second, item.third, Modifier.weight(1f)) {
                    onCategory(category ?: "")
                }
            }
        }
        if (rowIndex == 0) Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NeedTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Forest)
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Forest2, Forest)))) {
            Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(25.dp))
                Spacer(Modifier.height(5.dp))
                Text(title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(subtitle, color = Color.White.copy(alpha = .82f), fontSize = 10.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 21.sp, color = Forest)
        Text(action, Modifier.clickable(onClick = onClick), color = Forest2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Icon(Icons.Filled.ChevronRight, null, tint = Forest2, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DemoPhotoCard(item: DemoVisual, onClick: (DemoVisual) -> Unit) {
    Card(
        onClick = { onClick(item) },
        modifier = Modifier.width(150.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper)
    ) {
        Image(
            painter = painterResource(item.imageRes),
            contentDescription = item.title,
            modifier = Modifier.fillMaxWidth().height(105.dp),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, Modifier.weight(1f), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                DemoChip()
            }
            Text(item.subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DemoChip() {
    Surface(color = Warning, shape = RoundedCornerShape(999.dp)) {
        Text("DEMO", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Color(0xFF765A0C), fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CategoryTile(category: LibraryCategory, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper)
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Sage, shape = CircleShape) {
                Text(category.icon, Modifier.padding(10.dp), fontSize = 22.sp)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(category.label, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(category.subtitle, color = Muted, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PremiumExplore(initialCategory: String?, onDetail: (DemoVisual) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember(initialCategory) { mutableStateOf(initialCategory?.takeIf { it.isNotBlank() }) }
    val filtered = demoVisuals.filter { item ->
        (category == null || item.categoryId == category) && (query.isBlank() || item.title.contains(query, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("Danh mục tri thức", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = Forest)
        Text("Khám phá thư viện sinh tồn Việt Nam", color = Muted)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Tìm theo tên tiếng Việt") }
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Tất cả") })
            SurvivalLibraryCatalog.categories.forEach { c ->
                FilterChip(selected = category == c.id, onClick = { category = c.id }, label = { Text(c.label) })
            }
        }

        Surface(color = Sage, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Tiến độ thư viện", fontWeight = FontWeight.Black, color = Forest)
                Text("Dữ liệu thật hiện chưa được nạp vào bản phát hành này. Bộ đếm DEMO không được tính vào tiến độ.", color = Muted, fontSize = 11.sp)
            }
        }

        if (filtered.isEmpty()) {
            EmptyCard("Chưa có hồ sơ demo phù hợp. Dữ liệu thật sẽ xuất hiện ngay khi vượt cổng kiểm chứng.")
        } else {
            filtered.forEach { item -> ExploreRecordCard(item, onDetail) }
        }
    }
}

@Composable
private fun ExploreRecordCard(item: DemoVisual, onDetail: (DemoVisual) -> Unit) {
    Card(
        onClick = { onDetail(item) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Paper)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(item.imageRes), null, Modifier.size(92.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 19.sp)
                    DemoChip()
                }
                Text("Chưa phân loại mức sử dụng", color = Forest2, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Nội dung đang ở chế độ minh họa UI, không dùng để nhận dạng hay quyết định sử dụng.", color = Muted, fontSize = 10.sp, lineHeight = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Forest2)
        }
    }
}

@Composable
private fun PremiumDetail(item: DemoVisual, favorite: Boolean, onFavorite: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            Image(painterResource(item.imageRes), item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent, Color(0xDD0D2A1E)))))
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleAction(Icons.Filled.ArrowBack, onBack)
                CircleAction(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, onFavorite)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                DemoChip()
                Spacer(Modifier.height(8.dp))
                Text(item.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 32.sp)
                Text("Hồ sơ minh họa • chưa phát hành dữ liệu thật", color = Color.White.copy(alpha = .86f))
            }
        }

        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(Icons.Filled.Eco, "Tiếng Việt trước", Modifier.weight(1f))
                StatusPill(Icons.Filled.Shield, "An toàn trước", Modifier.weight(1f))
            }
            DetailSection("Nhận biết", "Dữ liệu nhận biết chưa được phát hành. Hồ sơ này hiện chỉ dùng để kiểm tra bố cục và hình ảnh.", Icons.Filled.Eco)
            DetailSection("Dễ nhầm", "Chưa có dữ liệu kiểm chứng. Ứng dụng không tự suy đoán các loài tương tự.", Icons.Filled.Search)
            DetailSection("Cách dùng", "Chưa hiển thị công dụng hoặc cách dùng khi chưa có nguồn chuyên ngành đạt chuẩn.", Icons.Filled.Restaurant)
            Surface(color = Warning, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFF9A6900))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Lưu ý an toàn", fontWeight = FontWeight.Black, color = Color(0xFF805800))
                        Text("Không dùng nội dung DEMO để quyết định ăn, uống, làm thuốc hoặc xử lý tình huống nguy hiểm.", fontSize = 11.sp, color = Color(0xFF6A530F))
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleAction(icon: ImageVector, onClick: () -> Unit) {
    Surface(color = Color(0xB8000000), shape = CircleShape) {
        IconButton(onClick = onClick) { Icon(icon, null, tint = Color.White) }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, label: String, modifier: Modifier) {
    Surface(modifier, color = Sage, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Forest2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DetailSection(title: String, text: String, icon: ImageVector) {
    var expanded by remember { mutableStateOf(true) }
    Surface(color = Paper, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Forest2)
                Spacer(Modifier.width(9.dp))
                Text(title, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (expanded) Text(text, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PremiumSaved(savedIds: Set<String>, onDetail: (DemoVisual) -> Unit) {
    val saved = demoVisuals.filter { it.id in savedIds }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Đã lưu", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = Forest)
        Text("Mục đã lưu được giữ trên thiết bị để dùng lại khi offline.", color = Muted)
        if (saved.isEmpty()) EmptyCard("Chưa có hồ sơ nào được lưu.")
        else saved.forEach { ExploreRecordCard(it, onDetail) }
    }
}

@Composable
private fun PremiumLearn() {
    val rules = listOf(
        "Ưu tiên tên tiếng Việt và đối tượng thường gặp tại Việt Nam.",
        "Ảnh là dữ liệu nhận biết; ảnh không chắc chắn không làm ảnh đại diện.",
        "Không công bố công dụng, độc tính hoặc tính ăn được nếu chưa đủ nguồn.",
        "AI nhận dạng chỉ đưa khả năng; không thay thế xác nhận loài.",
        "Hồ sơ đạt chuẩn đến đâu được phát hành đến đó, không chờ đủ cả danh mục."
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Học tập & nguyên tắc", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = Forest)
        Text("Các quy tắc cốt lõi được đặt ngay trong trải nghiệm người dùng.", color = Muted)
        rules.forEachIndexed { index, text ->
            Surface(color = Paper, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Surface(color = Sage, shape = CircleShape) {
                        Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Black, color = Forest2)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(text, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun PremiumProfile(publishedCount: Int, verifiedCount: Int, savedCount: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Trạng thái ứng dụng", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 28.sp, color = Forest)
        Text("Bản mới dùng cơ sở dữ liệu cục bộ, tách dữ liệu thật khỏi nội dung DEMO.", color = Muted)
        MetricRow("Cơ sở dữ liệu offline", "Đang hoạt động", Icons.Filled.CheckCircle)
        MetricRow("Hồ sơ đã kiểm chứng", verifiedCount.toString(), Icons.Filled.Shield)
        MetricRow("Hồ sơ đã phát hành", publishedCount.toString(), Icons.Filled.MenuBook)
        MetricRow("Mục đã lưu", savedCount.toString(), Icons.Filled.Bookmark)
        Surface(color = Warning, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Bản này chưa nạp dữ liệu sinh tồn thật. Số 0 là chủ ý để tránh tạo cảm giác thư viện đã hoàn thiện khi chưa có bằng chứng.", Modifier.padding(14.dp), color = Color(0xFF6A530F), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, icon: ImageVector) {
    Surface(color = Paper, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Sage, shape = CircleShape) { Icon(icon, null, tint = Forest2, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(value, color = Forest2, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(color = Paper, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(20.dp), color = Muted)
    }
}
