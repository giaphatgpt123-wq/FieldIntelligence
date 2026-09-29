package vn.duongodau.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.duongodau.app.core.*
import vn.duongodau.app.data.AdminRepository
import vn.duongodau.app.data.PilotRoadRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adminState = AdminRepository(this).load()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DuongODauApp(adminState)
                }
            }
        }
    }
}

@Composable
private fun DuongODauApp(adminState: AdminRepository.AdminDataState) {
    var selectedProvince by remember { mutableStateOf<AdminUnit?>(null) }
    var selectedCommune by remember { mutableStateOf<AdminUnit?>(null) }
    var searchText by remember { mutableStateOf("") }
    var currentBoundary by remember { mutableStateOf(true) }
    var historicalBoundary by remember { mutableStateOf(false) }
    var selectedVehicle by remember { mutableStateOf(VehicleClass.CAR_7) }
    var showPilotResult by remember { mutableStateOf(false) }

    val communes = selectedProvince?.let { adminState.communesByProvince[it.code].orEmpty() }.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Đường ở đâu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tra cứu đường • địa giới • cầu/phà • khả năng phương tiện",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!adminState.nationwideReady) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Dữ liệu hành chính P0 chưa đầy đủ", fontWeight = FontWeight.Bold)
                        Text("Đã có 34 tỉnh/thành dự phòng. Danh mục xã/phường toàn quốc chưa được đồng bộ vào bản build này; ứng dụng không giả định dữ liệu còn thiếu.")
                    }
                }
            }
        }

        item {
            Text("Khu vực", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            AdminDropdown(
                label = "Tỉnh / thành phố",
                selected = selectedProvince?.name,
                options = adminState.provinces,
                enabled = true,
                onSelected = {
                    selectedProvince = it
                    selectedCommune = null
                    showPilotResult = false
                }
            )
            Spacer(Modifier.height(8.dp))
            AdminDropdown(
                label = "Xã / phường / đặc khu",
                selected = selectedCommune?.name,
                options = communes,
                enabled = communes.isNotEmpty(),
                onSelected = {
                    selectedCommune = it
                    showPilotResult = false
                }
            )
            if (selectedProvince != null && communes.isEmpty()) {
                Text(
                    "Chưa có danh mục cấp xã cho ${selectedProvince?.name} trong asset hiện tại.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tên đường / mã đường / địa danh") },
                placeholder = { Text("Ví dụ: Đường A") },
                singleLine = true
            )
        }

        item {
            Text("Phương tiện", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleButton("7 chỗ", VehicleClass.CAR_7, selectedVehicle) { selectedVehicle = it }
                VehicleButton("16 chỗ", VehicleClass.BUS_16, selectedVehicle) { selectedVehicle = it }
                VehicleButton("29 chỗ", VehicleClass.BUS_29, selectedVehicle) { selectedVehicle = it }
            }
        }

        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = currentBoundary, onCheckedChange = { currentBoundary = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Ranh giới hiện hành")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = historicalBoundary, onCheckedChange = { historicalBoundary = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Địa giới cũ / lịch sử")
                    }
                }
            }
        }

        item {
            BoundaryPreview(
                currentBoundary = currentBoundary,
                historicalBoundary = historicalBoundary,
                placeLabel = selectedCommune?.name ?: selectedProvince?.name ?: "Chưa chọn khu vực"
            )
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showPilotResult = true },
                enabled = searchText.isNotBlank()
            ) {
                Text("Tra cứu đường")
            }
        }

        if (showPilotResult) {
            item {
                PilotRoadCard(selectedVehicle)
            }
        }

        item {
            Text(
                "P0 • nguồn hành chính: ${adminState.sourceLabel} • dữ liệu đường trên màn hình thử nghiệm được gắn SYNTHETIC/UNVERIFIED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selected ?: label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun VehicleButton(
    label: String,
    value: VehicleClass,
    selected: VehicleClass,
    onSelected: (VehicleClass) -> Unit
) {
    if (selected == value) {
        Button(onClick = { onSelected(value) }) { Text(label) }
    } else {
        OutlinedButton(onClick = { onSelected(value) }) { Text(label) }
    }
}

@Composable
private fun BoundaryPreview(currentBoundary: Boolean, historicalBoundary: Boolean, placeLabel: String) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Khung địa giới P0", fontWeight = FontWeight.Bold)
            Text(placeLabel, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (currentBoundary) {
                    drawRect(
                        color = Color(0xFF2E6F40),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.12f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.82f, size.height * 0.72f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                    )
                }
                if (historicalBoundary) {
                    drawRect(
                        color = Color(0xFF8A5A00),
                        topLeft = Offset(size.width * 0.17f, size.height * 0.20f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.66f, size.height * 0.60f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
                drawLine(Color(0xFF202020), Offset(size.width * 0.10f, size.height * 0.65f), Offset(size.width * 0.88f, size.height * 0.35f), 8f)
                drawLine(Color(0xFF555555), Offset(size.width * 0.35f, size.height * 0.78f), Offset(size.width * 0.55f, size.height * 0.20f), 6f)
            }
            Text(
                "Minh họa P0 – chưa phải bản đồ/ranh giới thật.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PilotRoadCard(vehicle: VehicleClass) {
    val result = remember(vehicle) { PilotRoadRepository.assess(vehicle) }
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(PilotRoadRepository.road.displayName, fontWeight = FontWeight.Bold)
            Text("Mã: ${PilotRoadRepository.road.code}")
            Text("Trạng thái tồn tại: ${PilotRoadRepository.segment.existence}")
            Text("Mặt đường: UNKNOWN • tải trọng: UNKNOWN • tình trạng: UNKNOWN")
            HorizontalDivider()
            Text("Route Guard: ${result.status}", fontWeight = FontWeight.Bold)
            if (result.unknowns.isNotEmpty()) {
                result.unknowns.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            Text(
                "Đây là bản ghi tổng hợp giả lập để kiểm thử logic, không phải dữ liệu đường thực tế.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
