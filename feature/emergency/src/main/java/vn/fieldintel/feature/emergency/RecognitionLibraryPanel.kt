package vn.fieldintel.feature.emergency

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RecognitionPanel(imageStatus: String, preview: Bitmap?, onPickImage: () -> Unit, onCameraImage: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ẢNH KHẢO SÁT", fontWeight = FontWeight.Bold)
            Text("Ảnh chỉ được tiếp nhận trên thiết bị; chưa có mô hình nhận dạng đã kiểm chứng.")
            Button(onClick = onCameraImage, modifier = Modifier.fillMaxWidth()) { Text("Chụp ảnh") }
            OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) { Text("Chọn ảnh từ máy") }
            Text(imageStatus)
            if (preview != null) Image(bitmap = preview.asImageBitmap(), contentDescription = "Ảnh được chọn để đối chiếu thủ công", modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit)
            Text("KẾT QUẢ: CHƯA XÁC ĐỊNH", fontWeight = FontWeight.Bold)
            Text("Không suy ra tên loài, tính ăn được hay cách xử trí từ ảnh. Tra cứu thư viện theo tên chỉ để tham khảo nguồn phân loại.")
        }
    }
}

@Composable
fun SpeciesLibraryPanel() {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Tất cả") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val groups = listOf("Tất cả", "Thực vật", "Động vật", "Côn trùng", "Nấm")
    val selected = SpeciesCatalog.records.firstOrNull { it.id == selectedId }
    if (selected != null) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { selectedId = null }) { Text("← Danh sách") }
                Text(selected.vietnameseName, style = MaterialTheme.typography.headlineSmall)
                Text(selected.scientificName, fontWeight = FontWeight.Bold)
                Text("Nhóm: " + selected.group)
                Text("Nguồn: " + selected.sourceName)
                Text(selected.sourceScope)
                OutlinedButton(onClick = { uriHandler.openUri(selected.sourceUrl) }) { Text("Mở nguồn khi có mạng") }
                Text("Chưa có hình đối chiếu nhiều góc độ; không dùng mục này để nhận dạng hay quyết định ăn/uống/chữa trị.")
            }
        }
        return
    }
    Text("Danh mục mẫu offline: ${SpeciesCatalog.records.size} hồ sơ tên khoa học. Chưa có dữ liệu nhận dạng ảnh.")
    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Tìm tên Việt hoặc tên khoa học") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    groups.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { option ->
                FilterChip(selected = group == option, onClick = { group = option }, label = { Text(option) })
            }
        }
    }
    val results = SpeciesCatalog.search(query, group)
    if (results.isEmpty()) Text("Chưa có hồ sơ phù hợp trong dữ liệu offline. Không kết luận loài từ việc không tìm thấy.")
    results.forEach { record ->
        Card(onClick = { selectedId = record.id }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(record.vietnameseName, fontWeight = FontWeight.Bold)
                Text(record.scientificName)
                Text(record.group + " • " + record.sourceName)
            }
        }
    }
}
