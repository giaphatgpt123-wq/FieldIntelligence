package vn.duongodau.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.duongodau.app.core.AdminUnit
import vn.duongodau.app.core.Passability
import vn.duongodau.app.core.VehicleClass
import vn.duongodau.app.data.AdminRepository
import vn.duongodau.app.data.PilotRoadRepository
import vn.duongodau.app.data.RoadCatalogRepository

private val Navy = Color(0xFF071A2E)
private val Navy2 = Color(0xFF0B2944)
private val Teal = Color(0xFF0E6F73)
private val TealSoft = Color(0xFF1D8A8F)
private val Orange = Color(0xFFF4A229)
private val OrangeSoft = Color(0xFFFFC35A)
private val Ice = Color(0xFFF4F8FB)
private val Ink = Color(0xFF102A43)
private val Slate = Color(0xFF5C6F7B)
private val Good = Color(0xFF1A9B67)
private val Warning = Color(0xFFE58A16)
private val Danger = Color(0xFFD94B4B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adminState = AdminRepository(this).load()
        val roadCatalog = runCatching { RoadCatalogRepository(this).load() }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Teal,
                    secondary = Orange,
                    surface = Color.White,
                    background = Ice,
                    onPrimary = Color.White,
                    onSurface = Ink
                )
            ) {
                DuongODauPanel(adminState, roadCatalog)
            }
        }
    }
}

@Composable
private fun DuongODauPanel(
    adminState: AdminRepository.AdminDataState,
    roadCatalog: Result<RoadCatalogRepository.Catalog>
) {
    var selectedProvince by remember { mutableStateOf<AdminUnit?>(null) }
    var selectedCommune by remember { mutableStateOf<AdminUnit?>(null) }
    var searchText by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }

    val communes = selectedProvince?.let { adminState.communesByProvince[it.code].orEmpty() }.orEmpty()
    Scaffold(
        containerColor = Ice
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                BrandHero()
            }

            if (!adminState.nationwideReady) {
                item {
                    PaddingBox {
                        StatusBanner(
                            title = "Dữ liệu hành chính chưa đủ chuẩn toàn quốc",
                            body = adminState.sourceLabel,
                            tone = Danger
                        )
                    }
                }
            }

            item {
                PaddingBox {
                    SearchPanel(
                        selectedProvince = selectedProvince,
                        selectedCommune = selectedCommune,
                        provinces = adminState.provinces,
                        communes = communes,
                        searchText = searchText,
                        onProvinceSelected = {
                            selectedProvince = it
                            selectedCommune = null
                            searched = false
                        },
                        onCommuneSelected = {
                            selectedCommune = it
                            searched = false
                        },
                        onSearchTextChanged = {
                            searchText = it
                            searched = false
                        },
                        onSearch = { searched = searchText.isNotBlank() }
                    )
                }
            }

            item {
                PaddingBox {
                    ScopeSummaryCard(
                        selectedProvince = selectedProvince,
                        selectedCommune = selectedCommune
                    )
                }
            }

            item {
                PaddingBox {
                    RoadCatalogResults(
                        catalog = roadCatalog.getOrNull(),
                        searched = searched,
                        query = searchText,
                        selectedProvince = selectedProvince,
                        selectedCommune = selectedCommune
                    )
                }
            }

            item {
                PaddingBox {
                    Text(
                        "Thí điểm dữ liệu OpenStreetMap (ODbL) • © OpenStreetMap contributors. Chưa xác minh hiện trường; không dùng để quyết định hành trình.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Navy, Navy2, Teal)))
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    AppMark(Modifier.padding(7.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "Đường ",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp
                        )
                        Text(
                            "ở đâu",
                            color = OrangeSoft,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp
                        )
                    }
                    Text(
                        "Tra cứu đường • an toàn hành trình • dữ liệu theo khu vực",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("◎", color = OrangeSoft, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Road Intelligence", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Đường • cầu/phà • tải trọng • liên thông • cảnh báo",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRoundRect(
            brush = Brush.linearGradient(listOf(TealSoft, Navy2)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.22f)
        )
        val road = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.92f)
            cubicTo(
                size.width * 0.30f, size.height * 0.70f,
                size.width * 0.56f, size.height * 0.71f,
                size.width * 0.48f, size.height * 0.52f
            )
            cubicTo(
                size.width * 0.42f, size.height * 0.37f,
                size.width * 0.66f, size.height * 0.34f,
                size.width * 0.65f, size.height * 0.17f
            )
        }
        drawPath(road, color = Orange, style = Stroke(width = size.width * 0.10f, cap = StrokeCap.Round))
        drawPath(road, color = Color.White, style = Stroke(width = size.width * 0.022f, cap = StrokeCap.Round))

        val cx = size.width * 0.69f
        val cy = size.height * 0.34f
        drawCircle(Orange, size.width * 0.19f, Offset(cx, cy))
        val pin = Path().apply {
            moveTo(cx - size.width * 0.13f, cy + size.height * 0.08f)
            lineTo(cx, cy + size.height * 0.34f)
            lineTo(cx + size.width * 0.13f, cy + size.height * 0.08f)
            close()
        }
        drawPath(pin, Orange)
        drawCircle(Navy, size.width * 0.075f, Offset(cx, cy))
        drawCircle(Color.White, size.width * 0.035f, Offset(cx, cy))
    }
}

@Composable
private fun SearchPanel(
    selectedProvince: AdminUnit?,
    selectedCommune: AdminUnit?,
    provinces: List<AdminUnit>,
    communes: List<AdminUnit>,
    searchText: String,
    onProvinceSelected: (AdminUnit) -> Unit,
    onCommuneSelected: (AdminUnit) -> Unit,
    onSearchTextChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Chọn khu vực", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            AdminDropdown(
                label = "Tỉnh / Thành phố",
                selected = selectedProvince?.name,
                options = provinces,
                enabled = true,
                onSelected = onProvinceSelected
            )
            AdminDropdown(
                label = "Xã / Phường / Đặc khu",
                selected = selectedCommune?.name,
                options = communes,
                enabled = selectedProvince != null && communes.isNotEmpty(),
                onSelected = onCommuneSelected
            )

            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChanged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                label = { Text("Tên đường / mã đường") },
                placeholder = { Text("Ví dụ: Trần Phú, QL20, ĐT725...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = onSearch,
                enabled = searchText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Navy)
            ) {
                Text("Tra cứu đường", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RoadCatalogResults(
    catalog: RoadCatalogRepository.Catalog?,
    searched: Boolean,
    query: String,
    selectedProvince: AdminUnit?,
    selectedCommune: AdminUnit?
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Kết quả đường", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Gói thí điểm: ô tọa độ 10,33–10,44°B · 105,57–105,68°Đ. Chưa gán chính xác xã/phường.",
                color = Slate, fontSize = 13.sp
            )
            when {
                catalog == null -> Text("Không đọc được gói dữ liệu đường. Chưa có kết quả để hiển thị.", color = Danger)
                !searched -> Text("Nhập tên hoặc mã đường rồi bấm Tra cứu đường.", color = Slate)
                selectedCommune != null -> Text("Chưa có đối chiếu ranh giới xã cho gói thí điểm này. Không gán đường vào xã khi chưa kiểm chứng.", color = Warning)
                selectedProvince != null && selectedProvince.code != "82" ->
                    Text("Chưa có gói dữ liệu đường cho tỉnh/thành đã chọn.", color = Warning)
                else -> {
                    val found = catalog.search(query)
                    Text("Hiển thị ${found.size} đoạn đầu khớp • chưa xác minh hiện trường", color = Teal, fontWeight = FontWeight.Bold)
                    if (found.isEmpty()) Text("Không tìm thấy trong gói thí điểm; chưa thể kết luận đường không tồn tại.", color = Slate)
                    found.forEach { entry ->
                        HorizontalDivider()
                        Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${entry.id} • loại OSM: ${entry.roadClass}", color = Slate, fontSize = 13.sp)
                        Text(
                            "Mặt đường: ${entry.surface ?: "chưa rõ"} • tải trọng: ${entry.maxWeight ?: "chưa rõ"}",
                            color = Ink, fontSize = 13.sp
                        )
                        Text("Nguồn: ${entry.sourceUrl}", color = Teal, fontSize = 12.sp)
                        Text("Dữ liệu nguồn: ${entry.sourceTimestamp ?: "không rõ ngày"} • chưa kiểm chứng", color = Slate, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeSummaryCard(selectedProvince: AdminUnit?, selectedCommune: AdminUnit?) {
    Surface(shape = RoundedCornerShape(18.dp), color = Navy) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Phạm vi dữ liệu", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = Orange.copy(alpha = 0.20f)) {
                    Text("AUTO SCOPE", color = OrangeSoft, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 11.sp)
                }
            }
            Text(
                when {
                    selectedCommune != null -> "${selectedCommune.name}: chưa có gói đường được gán và kiểm chứng theo ranh giới xã"
                    selectedProvince != null -> "${selectedProvince.name}: bộ chọn hành chính sẵn sàng • dữ liệu đường hiện chỉ có lát cắt thí điểm"
                    else -> "Dữ liệu đường hiện có một lát cắt thí điểm; chưa có thư viện đường toàn quốc"
                },
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RoadIntelligenceGrid(searched: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile("Loại đường", if (searched) "Chưa xác minh" else "—", "▰", Modifier.weight(1f))
            InfoTile("Mặt đường", if (searched) "Chưa rõ" else "—", "▤", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile("Tình trạng", if (searched) "Chưa rõ" else "—", "≋", Modifier.weight(1f))
            InfoTile("Tải trọng", if (searched) "Chưa rõ" else "—", "⚖", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile("Kết nối", if (searched) "Đang kiểm" else "—", "⌘", Modifier.weight(1f))
            InfoTile("Cầu / Phà", if (searched) "Đang kiểm" else "—", "⌁", Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoTile(title: String, value: String, symbol: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.heightIn(min = 100.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(symbol, color = Teal, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(title, color = Slate, fontSize = 13.sp, maxLines = 1)
            Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RouteGuardCard(searched: Boolean, status: Passability, unknowns: List<String>) {
    val tone = when {
        !searched -> Teal
        status == Passability.BLOCKED -> Danger
        status == Passability.CONDITIONAL || status == Passability.UNKNOWN -> Warning
        else -> Good
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tone.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tone.copy(alpha = 0.50f))
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = tone) {
                Text("!", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Route Guard", color = tone, fontWeight = FontWeight.Black)
                Text(
                    when {
                        !searched -> "Chưa có tuyến để đánh giá"
                        status == Passability.BLOCKED -> "Không thông / phải đổi tuyến"
                        status == Passability.CONDITIONAL -> "Đi có điều kiện"
                        status == Passability.UNKNOWN -> "Chưa đủ dữ liệu để xác nhận an toàn"
                        else -> "Không phát hiện chặn tuyến trong dữ liệu hiện có"
                    },
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (searched && unknowns.isNotEmpty()) {
                    Text(unknowns.first(), color = Slate, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("›", color = tone, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoadViewCard(
    placeLabel: String,
    currentBoundary: Boolean,
    historicalBoundary: Boolean,
    onCurrentChanged: (Boolean) -> Unit,
    onHistoricalChanged: (Boolean) -> Unit,
    searched: Boolean
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Road View", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(placeLabel, color = Slate, fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = Teal.copy(alpha = 0.10f)) {
                    Text("Bản đồ nền: OFF", color = Teal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentBoundary,
                    onClick = { onCurrentChanged(!currentBoundary) },
                    label = { Text("Ranh hiện hành") }
                )
                FilterChip(
                    selected = historicalBoundary,
                    onClick = { onHistoricalChanged(!historicalBoundary) },
                    label = { Text("Địa giới cũ") }
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF173D43), Color(0xFF0B273A))))
            ) {
                if (currentBoundary) {
                    val boundary = Path().apply {
                        moveTo(size.width * 0.12f, size.height * 0.18f)
                        lineTo(size.width * 0.58f, size.height * 0.09f)
                        lineTo(size.width * 0.88f, size.height * 0.30f)
                        lineTo(size.width * 0.82f, size.height * 0.78f)
                        lineTo(size.width * 0.48f, size.height * 0.91f)
                        lineTo(size.width * 0.13f, size.height * 0.73f)
                        close()
                    }
                    drawPath(boundary, color = Color(0xFFB7ECEC), style = Stroke(width = 4f))
                }
                if (historicalBoundary) {
                    val old = Path().apply {
                        moveTo(size.width * 0.20f, size.height * 0.25f)
                        lineTo(size.width * 0.66f, size.height * 0.18f)
                        lineTo(size.width * 0.79f, size.height * 0.55f)
                        lineTo(size.width * 0.54f, size.height * 0.84f)
                        lineTo(size.width * 0.22f, size.height * 0.65f)
                        close()
                    }
                    drawPath(old, color = OrangeSoft.copy(alpha = 0.78f), style = Stroke(width = 3f))
                }

                val roads = listOf(
                    Pair(Offset(size.width * 0.07f, size.height * 0.68f), Offset(size.width * 0.92f, size.height * 0.34f)),
                    Pair(Offset(size.width * 0.24f, size.height * 0.88f), Offset(size.width * 0.55f, size.height * 0.16f)),
                    Pair(Offset(size.width * 0.38f, size.height * 0.80f), Offset(size.width * 0.83f, size.height * 0.73f))
                )
                roads.forEachIndexed { index, (a, b) ->
                    drawLine(
                        color = if (searched && index == 0) Orange else Color.White.copy(alpha = 0.62f),
                        start = a,
                        end = b,
                        strokeWidth = if (searched && index == 0) 8f else 4f,
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(Orange, radius = 16f, center = Offset(size.width * 0.58f, size.height * 0.47f))
                drawCircle(Color.White, radius = 6f, center = Offset(size.width * 0.58f, size.height * 0.47f))
            }

            Text(
                "Road View ưu tiên ranh giới + mạng đường + cầu/phà; nền MapLibre chỉ bật khi cần.",
                color = Slate,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AdminDropdown(
    label: String,
    selected: String?,
    options: List<AdminUnit>,
    enabled: Boolean,
    onSelected: (AdminUnit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { filter = ""; expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                selected ?: label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("⌄", fontSize = 18.sp)
        }
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(label, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Nhập tên để tìm") },
                        singleLine = true
                    )
                    val matches = remember(options, filter) {
                        options.filter { it.name.contains(filter.trim(), ignoreCase = true) }
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(matches, key = { it.code }) { option ->
                            Text(
                                option.name,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onSelected(option)
                                    expanded = false
                                }.padding(vertical = 15.dp, horizontal = 8.dp),
                                fontSize = 16.sp
                            )
                        }
                        if (matches.isEmpty()) item {
                            Text("Không tìm thấy khu vực", color = Slate, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text("Đóng") }
            }
        )
    }
}

@Composable
private fun VehicleChip(
    label: String,
    value: VehicleClass,
    selected: VehicleClass,
    onSelected: (VehicleClass) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelected(value) },
        label = { Text(label, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Orange,
            selectedLabelColor = Navy
        )
    )
}

@Composable
private fun BottomPanelNav(selected: Int, onSelected: (Int) -> Unit) {
    val items = listOf(
        "Tra cứu" to "⌕",
        "Lộ trình" to "↝",
        "Cảnh báo" to "!",
        "Thư viện" to "▦"
    )
    NavigationBar(containerColor = Navy) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelected(index) },
                icon = {
                    Text(
                        item.second,
                        color = if (selected == index) Navy else Color.White.copy(alpha = 0.76f),
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp
                    )
                },
                label = {
                    Text(
                        item.first,
                        color = if (selected == index) OrangeSoft else Color.White.copy(alpha = 0.74f),
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Orange
                )
            )
        }
    }
}

@Composable
private fun StatusBanner(title: String, body: String, tone: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tone.copy(alpha = 0.09f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tone.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = tone)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Slate)
        }
    }
}

@Composable
private fun PaddingBox(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 14.dp)) { content() }
}
