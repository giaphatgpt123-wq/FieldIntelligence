package vn.fieldintel.feature.emergency

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class ObservationUi(val id:String,val note:String,val createdAt:Long,val photoPath:String)

@Composable
fun RecognitionPanel(imageStatus: String, preview: Bitmap?, saveStatus:String, onPickImage: () -> Unit, onCameraImage: () -> Unit, onSaveObservation:(String)->Unit) {
    var note by remember { mutableStateOf("") }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102C33)),
        border = BorderStroke(1.dp, Color(0x3345E58C))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0x2245E58C)) {
                    Text("🌿", Modifier.padding(12.dp), style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NHẬN DẠNG THỰC ĐỊA", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    Text("Ảnh • bằng chứng • đối chiếu thủ công", color = FieldColors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0A2228),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp, max = 440.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF173A3F), Color(0xFF0C252A), Color(0xFF071A1F))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Ảnh được chọn để đối chiếu thủ công",
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = CircleShape, color = Color(0x223FEA91)) {
                                Text("◎", Modifier.padding(horizontal = 22.dp, vertical = 16.dp), style = MaterialTheme.typography.displaySmall, color = FieldColors.primary)
                            }
                            Text("Chụp hoặc chọn ảnh", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Ưu tiên ảnh rõ nét, đủ sáng, nhiều góc độ để đối chiếu.",
                                color = FieldColors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCameraImage,
                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("📷  CHỤP ẢNH", fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(
                    onClick = onPickImage,
                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("🖼  THƯ VIỆN", fontWeight = FontWeight.Bold)
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF17383F)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (preview == null) "○" else "●", color = if (preview == null) Color(0xFFFFC857) else FieldColors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(imageStatus, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (preview != null) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Ghi chú mẫu, địa điểm hoặc đặc điểm nhìn thấy") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(18.dp)
                )
                Button(
                    onClick = { onSaveObservation(note) },
                    enabled = note.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("💾  LƯU GHI NHẬN OFFLINE", fontWeight = FontWeight.ExtraBold)
                }
                if(saveStatus.isNotBlank()) Text(saveStatus, color = FieldColors.primary)
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF2E2520),
                border = BorderStroke(1.dp, Color(0x55FFC857))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("KẾT QUẢ: CHƯA XÁC ĐỊNH", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFC857), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Hiện chưa có mô hình nhận dạng ảnh đã được kiểm chứng. Ứng dụng không tự suy ra tên loài, tính ăn được hoặc cách xử trí từ ảnh.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Dùng ảnh và ghi chú để lưu bằng chứng thực địa, sau đó đối chiếu với thư viện khoa học.",
                        color = FieldColors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun SpeciesLibraryPanel(observations:List<ObservationUi> = emptyList(),onDeleteObservation:(String)->Unit={}) {
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var selectedObservation by remember { mutableStateOf<String?>(null) }

    if(confirmDelete != null) AlertDialog(
        onDismissRequest={confirmDelete=null},
        title={Text("Xóa ghi nhận?")},
        text={Text("Ảnh và ghi chú sẽ bị xóa khỏi ứng dụng trên máy này.")},
        confirmButton={TextButton(onClick={val id=confirmDelete;confirmDelete=null;if(id!=null){onDeleteObservation(id);selectedObservation=null}}){Text("XÓA")}},
        dismissButton={TextButton(onClick={confirmDelete=null}){Text("HỦY")}}
    )

    val observation = observations.firstOrNull { it.id == selectedObservation }
    if(observation != null) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(onClick = { selectedObservation = null }, modifier = Modifier.fillMaxWidth().heightIn(min=56.dp), shape = RoundedCornerShape(18.dp)) {
                Text("←  QUAY LẠI GHI NHẬN", fontWeight = FontWeight.Bold)
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102C33))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(observation.note, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val thumbnail = remember(observation.photoPath) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(observation.photoPath, bounds)
                        val options = BitmapFactory.Options().apply { inSampleSize = generateSequence(1) { it * 2 }.first { s -> maxOf(bounds.outWidth, bounds.outHeight) / s <= 768 } }
                        BitmapFactory.decodeFile(observation.photoPath, options)
                    }
                    if(thumbnail != null) Image(thumbnail.asImageBitmap(), "Ảnh ghi nhận offline", Modifier.fillMaxWidth().heightIn(min=280.dp,max=420.dp), contentScale = ContentScale.Fit)
                    Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFF2E2520)){Text("CHƯA XÁC ĐỊNH • ghi chú người dùng • chưa xác minh",Modifier.padding(12.dp),color=Color(0xFFFFC857),fontWeight=FontWeight.Bold)}
                    OutlinedButton(onClick={confirmDelete=observation.id}, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp), shape=RoundedCornerShape(16.dp)) { Text("Xóa ghi nhận") }
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(24.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF102C33))) {
            Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("📚  THƯ VIỆN THỰC ĐỊA", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                Text("Ghi nhận trên máy + danh mục khoa học offline", color = FieldColors.onSurfaceVariant)
            }
        }

        Text("GHI NHẬN CỦA TÔI", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        if(observations.isEmpty()) {
            Surface(shape=RoundedCornerShape(18.dp),color=Color(0xFF102A31)) {
                Text("Chưa lưu mẫu quan sát.", Modifier.fillMaxWidth().padding(18.dp), color=FieldColors.onSurfaceVariant)
            }
        }
        observations.take(20).forEach { record ->
            Card(onClick = { selectedObservation = record.id }, modifier = Modifier.fillMaxWidth().heightIn(min=82.dp), shape=RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF13323A))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                    Surface(shape=CircleShape,color=Color(0x2245E58C)){Text("📷",Modifier.padding(10.dp))}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(record.note, fontWeight=FontWeight.Bold, maxLines=2)
                        Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.createdAt)) + " • CHƯA XÁC ĐỊNH", color=FieldColors.onSurfaceVariant, style=MaterialTheme.typography.bodySmall)
                    }
                    Text("›", style=MaterialTheme.typography.headlineSmall, color=FieldColors.primary)
                }
            }
        }

        HorizontalDivider()
        var query by remember { mutableStateOf("") }
        var group by remember { mutableStateOf("Tất cả") }
        var selectedId by remember { mutableStateOf<String?>(null) }
        val uriHandler = LocalUriHandler.current
        val groups = listOf("Tất cả", "Thực vật", "Động vật", "Côn trùng", "Nấm")
        val selected = SpeciesCatalog.records.firstOrNull { it.id == selectedId }

        if (selected != null) {
            OutlinedButton(onClick = { selectedId = null }, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp), shape=RoundedCornerShape(18.dp)) { Text("←  QUAY LẠI DANH SÁCH",fontWeight=FontWeight.Bold) }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF102C33))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape=CircleShape,color=Color(0x2245E58C)){Text("🌿",Modifier.padding(14.dp),style=MaterialTheme.typography.headlineMedium)}
                    Text(selected.vietnameseName, style = MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.ExtraBold)
                    Text(selected.scientificName, fontWeight = FontWeight.Bold, color=FieldColors.primary)
                    Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFF17383F)){Text("Nhóm: ${selected.group}",Modifier.padding(12.dp))}
                    Text("Nguồn: " + selected.sourceName, fontWeight=FontWeight.Bold)
                    Text(selected.sourceScope, color=FieldColors.onSurfaceVariant)
                    OutlinedButton(onClick = { uriHandler.openUri(selected.sourceUrl) }, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp), shape=RoundedCornerShape(16.dp)) { Text("MỞ NGUỒN KHI CÓ MẠNG") }
                    Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFF2E2520)){Text("Chưa có hình đối chiếu nhiều góc độ; không dùng mục này để nhận dạng hay quyết định ăn/uống/chữa trị.",Modifier.padding(14.dp),color=Color(0xFFFFC857))}
                }
            }
            return
        }

        Text("DANH MỤC KHOA HỌC", fontWeight=FontWeight.ExtraBold, style=MaterialTheme.typography.titleMedium)
        Text("${SpeciesCatalog.records.size} hồ sơ tên khoa học offline • chưa có nhận dạng ảnh",color=FieldColors.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Tìm tên Việt hoặc tên khoa học") },
            modifier = Modifier.fillMaxWidth().heightIn(min=58.dp),
            singleLine = true,
            shape=RoundedCornerShape(18.dp)
        )
        groups.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected = group == option,
                        onClick = { group = option },
                        label = { Text(option, fontWeight=FontWeight.Bold) },
                        modifier=Modifier.heightIn(min=48.dp)
                    )
                }
            }
        }
        val results = SpeciesCatalog.search(query, group)
        if (results.isEmpty()) Text("Chưa có hồ sơ phù hợp trong dữ liệu offline. Không kết luận loài từ việc không tìm thấy.",color=Color(0xFFFFC857))
        results.forEach { record ->
            Card(onClick = { selectedId = record.id }, modifier = Modifier.fillMaxWidth().heightIn(min=88.dp), shape = RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF13323A))) {
                Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
                    Surface(shape=CircleShape,color=Color(0x2245E58C)){Text("🌿",Modifier.padding(10.dp))}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(record.vietnameseName, fontWeight = FontWeight.ExtraBold, style=MaterialTheme.typography.titleMedium)
                        Text(record.scientificName, color=FieldColors.primary)
                        Text(record.group + " • " + record.sourceName, color=FieldColors.onSurfaceVariant, style=MaterialTheme.typography.bodySmall)
                    }
                    Text("›",style=MaterialTheme.typography.headlineSmall,color=FieldColors.primary)
                }
            }
        }
    }
}
