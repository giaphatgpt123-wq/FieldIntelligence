package vn.fieldintel.feature.emergency

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2329)),
            border = BorderStroke(1.dp, Color(0x3345E58C))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0x263EEA91)) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Text("◎", style = MaterialTheme.typography.headlineMedium, color = FieldColors.primary, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("QUÉT NHẬN DẠNG", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text("Chụp rõ mẫu vật • lưu bằng chứng offline", color = FieldColors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF123A35), border = BorderStroke(1.dp, Color(0x5545E58C))) {
                        Text("OFFLINE", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = FieldColors.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF07181D),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .10f)),
                    shadowElevation = 8.dp
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(430.dp).background(
                            Brush.verticalGradient(listOf(Color(0xFF21454A), Color(0xFF10292F), Color(0xFF07181D)))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (preview != null) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "Ảnh thực địa được chọn",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x08000000), Color(0xA8000000)))))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = CircleShape, color = Color(0x1F45E58C), border = BorderStroke(1.dp, Color(0x5545E58C))) {
                                    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                                        Text("◎", style = MaterialTheme.typography.displayMedium, color = FieldColors.primary)
                                    }
                                }
                                Text("Đưa mẫu vật vào khung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text("Giữ máy ổn định • đủ sáng • chụp thêm góc khác khi cần", color = Color.White.copy(alpha=.78f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 34.dp))
                            }
                        }

                        Canvas(Modifier.fillMaxSize().padding(36.dp)) {
                            val c = Color(0xFF65F4A3)
                            val sw = 6f
                            val l = 44f
                            drawLine(c, Offset(0f,0f), Offset(l,0f), sw); drawLine(c, Offset(0f,0f), Offset(0f,l), sw)
                            drawLine(c, Offset(size.width,0f), Offset(size.width-l,0f), sw); drawLine(c, Offset(size.width,0f), Offset(size.width,l), sw)
                            drawLine(c, Offset(0f,size.height), Offset(l,size.height), sw); drawLine(c, Offset(0f,size.height), Offset(0f,size.height-l), sw)
                            drawLine(c, Offset(size.width,size.height), Offset(size.width-l,size.height), sw); drawLine(c, Offset(size.width,size.height), Offset(size.width,size.height-l), sw)
                        }

                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xB70B1F24),
                            border = BorderStroke(1.dp, Color.White.copy(alpha=.12f))
                        ) {
                            Text(if(preview==null) "SẴN SÀNG CHỤP" else "ẢNH ĐÃ NẠP • CHƯA PHÂN LOẠI", Modifier.padding(horizontal=14.dp, vertical=8.dp), color=Color.White, fontWeight=FontWeight.Bold, style=MaterialTheme.typography.labelLarge)
                        }

                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xD90A2025),
                            border = BorderStroke(1.dp, Color.White.copy(alpha=.10f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if(preview==null) "○" else "●", color=if(preview==null) Color(0xFFFFD166) else FieldColors.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(imageStatus, modifier=Modifier.weight(1f), color=Color.White, style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onCameraImage,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FieldColors.primary, contentColor = FieldColors.onPrimary)
                    ) { Text("CHỤP ẢNH", fontWeight = FontWeight.Black) }
                    OutlinedButton(
                        onClick = onPickImage,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0x8845E58C))
                    ) { Text("CHỌN ẢNH", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF152D31),
            border = BorderStroke(1.dp, Color(0x443FEA91))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0x3324E98A)) { Text("KẾT QUẢ", Modifier.padding(horizontal=10.dp,vertical=6.dp), color=FieldColors.primary, fontWeight=FontWeight.Bold) }
                    Spacer(Modifier.weight(1f))
                    Text("CHƯA XÁC ĐỊNH", color=Color(0xFFFFD166), fontWeight=FontWeight.Black, style=MaterialTheme.typography.titleMedium)
                }
                Text("Chưa có mô hình nhận dạng ảnh đã được kiểm chứng trong bản hiện tại.", fontWeight=FontWeight.Bold)
                Text("Ảnh này chỉ được dùng làm bằng chứng thực địa và đối chiếu thủ công. Ứng dụng không tự suy ra tên loài, tính ăn được, độc tính hoặc xử trí y khoa.", color=FieldColors.onSurfaceVariant, style=MaterialTheme.typography.bodyMedium)
            }
        }

        if (preview != null) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102C33)), modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("LƯU GHI NHẬN", fontWeight=FontWeight.ExtraBold, style=MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        label = { Text("Địa điểm, đặc điểm nhìn thấy hoặc ghi chú") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Button(
                        onClick = { onSaveObservation(note) },
                        enabled = note.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("LƯU OFFLINE", fontWeight = FontWeight.Black) }
                    if(saveStatus.isNotBlank()) Text(saveStatus, color = FieldColors.primary, fontWeight=FontWeight.Bold)
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
                Text("THƯ VIỆN THỰC ĐỊA", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
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
                    Surface(shape=CircleShape,color=Color(0x2245E58C)){Text("◎",Modifier.padding(10.dp),color=FieldColors.primary)}
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
                    Surface(shape=CircleShape,color=Color(0x2245E58C)){Text("◇",Modifier.padding(14.dp),style=MaterialTheme.typography.headlineMedium,color=FieldColors.primary)}
                    Text(selected.vietnameseName, style = MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.ExtraBold)
                    Text(selected.scientificName, fontWeight = FontWeight.Bold, color=FieldColors.primary)
                    Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFF17383F)){Text("Nhóm: ${selected.group}",Modifier.padding(12.dp))}
                    Text("Nguồn: " + selected.sourceName, fontWeight=FontWeight.Bold)
                    Text(selected.sourceScope, color=FieldColors.onSurfaceVariant)
                    InteractionSafetyPanel(selected.scientificName)
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
                    Surface(shape=CircleShape,color=Color(0x2245E58C)){Text("◇",Modifier.padding(10.dp),color=FieldColors.primary)}
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
