package vn.fieldintel.feature.emergency

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class TrackSessionUiState { IDLE, RECORDING, PAUSED, FINISHED }

enum class AppSection(val label:String,val icon:String,val subtitle:String){
 FIELD("Thực địa","🗺️","GPS • hành trình • ghi nhận"), RECOGNITION("Nhận dạng","🌿","Camera • bằng chứng • phân biệt"),
 SURVIVAL("Sinh tồn","🔥","Nước • trú ẩn • định hướng"), EMERGENCY("Sự cố","SOS","Xử lý tình huống khẩn cấp"),
 PREP("Chuẩn bị","🎒","Trang bị • thuốc • checklist"), LIBRARY("Thư viện","📚","Tra cứu khoa học offline"),
 TRAINING("Huấn luyện","🎓","Học • mô phỏng • kiểm tra"), SETTINGS("Cài đặt","⚙","Cập nhật dữ liệu")
}

data class FieldPositionUi(val latitude:Double,val longitude:Double,val accuracyM:Float)
data class OfflineMapPointUi(val latitude:Double,val longitude:Double,val label:String?=null)
data class OfflineMapLineUi(val points:List<OfflineMapPointUi>)
data class OfflineMapPolygonUi(val points:List<OfflineMapPointUi>)

@Composable fun EmergencyScreen(position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,mapPoints:List<OfflineMapPointUi> = emptyList(),mapLines:List<OfflineMapLineUi> = emptyList(),mapPolygons:List<OfflineMapPolygonUi> = emptyList(),mapCredit:String?=null,onToggleTrack:()->Unit={},onFinishTrack:()->Unit={},onClearTrack:()->Unit={},updateStatus:String="Sẵn sàng",onCheckUpdate:()->Unit={},onRollbackUpdate:()->Unit={},mapCoverage:String?=null,imageStatus:String="Chưa chọn ảnh",imagePreview:android.graphics.Bitmap?=null,onPickImage:()->Unit={},onCameraImage:()->Unit={},observations:List<ObservationUi> = emptyList(),saveStatus:String="",onSaveObservation:(String)->Unit={},onDeleteObservation:(String)->Unit={}){ MaterialTheme(colorScheme=FieldColors) { FieldIntelligenceHome(position,recording,trackCount,trackDistanceM,trackStartedAt,trackBackRemainingM,trackBackBearingDeg,offTrackM,breadcrumbCount,breadcrumb,mapPackAvailable,mapPackFiles,mapPackBytes,mapPoints,mapLines,mapPolygons,mapCredit,onToggleTrack,onFinishTrack,onClearTrack,updateStatus,onCheckUpdate,onRollbackUpdate,mapCoverage,imageStatus,imagePreview,onPickImage,onCameraImage,observations,saveStatus,onSaveObservation,onDeleteObservation) } }

@Composable fun FieldIntelligenceHome(position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,mapPoints:List<OfflineMapPointUi> = emptyList(),mapLines:List<OfflineMapLineUi> = emptyList(),mapPolygons:List<OfflineMapPolygonUi> = emptyList(),mapCredit:String?=null,onToggleTrack:()->Unit={},onFinishTrack:()->Unit={},onClearTrack:()->Unit={},updateStatus:String="Sẵn sàng",onCheckUpdate:()->Unit={},onRollbackUpdate:()->Unit={},mapCoverage:String?=null,imageStatus:String="Chưa chọn ảnh",imagePreview:android.graphics.Bitmap?=null,onPickImage:()->Unit={},onCameraImage:()->Unit={},observations:List<ObservationUi> = emptyList(),saveStatus:String="",onSaveObservation:(String)->Unit={},onDeleteObservation:(String)->Unit={}){
 var selected by remember{mutableStateOf<AppSection?>(null)}
 if(selected!=null){SectionScreen(section=selected!!,position=position,recording=recording,trackCount=trackCount,trackDistanceM=trackDistanceM,trackStartedAt=trackStartedAt,trackBackRemainingM=trackBackRemainingM,trackBackBearingDeg=trackBackBearingDeg,offTrackM=offTrackM,breadcrumbCount=breadcrumbCount,breadcrumb=breadcrumb,mapPackAvailable=mapPackAvailable,mapPackFiles=mapPackFiles,mapPackBytes=mapPackBytes,mapPoints=mapPoints,mapLines=mapLines,mapPolygons=mapPolygons,mapCredit=mapCredit,onToggleTrack=onToggleTrack,onFinishTrack=onFinishTrack,onClearTrack=onClearTrack,updateStatus=updateStatus,onCheckUpdate=onCheckUpdate,onRollbackUpdate=onRollbackUpdate,mapCoverage=mapCoverage,imageStatus=imageStatus,imagePreview=imagePreview,onPickImage=onPickImage,onCameraImage=onCameraImage,observations=observations,saveStatus=saveStatus,onSaveObservation=onSaveObservation,onDeleteObservation=onDeleteObservation,onBack={selected=null},onSelect={selected=it});return}
 Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,bottomBar={FieldBottomBar(onSelect={selected=it},current=selected)}){pad->
  Column(Modifier.fillMaxSize().background(FieldBackground).padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   FieldHomePanel(position){selected=it}
  }
 }
}

@Composable private fun FeatureCard(s:AppSection,modifier:Modifier=Modifier,onOpen:(AppSection)->Unit){
 Card(onClick={onOpen(s)},modifier=modifier.height(126.dp),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.SpaceBetween){Text(s.icon,style=MaterialTheme.typography.headlineMedium);Text(s.label,fontWeight=FontWeight.Bold);Text(s.subtitle,style=MaterialTheme.typography.labelSmall)}}
}

@Composable private fun BottomBar(current:AppSection?,onSelect:(AppSection?)->Unit){NavigationBar{listOf<Triple<AppSection?,String,String>>(Triple(null,"⌂","Trang chủ"),Triple(AppSection.FIELD,"🗺","Bản đồ"),Triple(AppSection.RECOGNITION,"◎","Quét"),Triple(AppSection.LIBRARY,"▣","Lưu trữ"),Triple(AppSection.SETTINGS,"⚙","Cài đặt")).forEach{(section,icon,label)->NavigationBarItem(current==section,{onSelect(section)},icon={Text(icon)},label={Text(label)})}}}

@Composable fun SectionScreen(section:AppSection,position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,mapPoints:List<OfflineMapPointUi> = emptyList(),mapLines:List<OfflineMapLineUi> = emptyList(),mapPolygons:List<OfflineMapPolygonUi> = emptyList(),mapCredit:String?=null,onBack:()->Unit,onToggleTrack:()->Unit={},onFinishTrack:()->Unit={},onClearTrack:()->Unit={},updateStatus:String="Sẵn sàng",onCheckUpdate:()->Unit={},onRollbackUpdate:()->Unit={},mapCoverage:String?=null,imageStatus:String="Chưa chọn ảnh",imagePreview:android.graphics.Bitmap?=null,onPickImage:()->Unit={},onCameraImage:()->Unit={},observations:List<ObservationUi> = emptyList(),saveStatus:String="",onSaveObservation:(String)->Unit={},onDeleteObservation:(String)->Unit={},onSelect:(AppSection?)->Unit={}){
 var confirmClear by remember { mutableStateOf(false) }
 val trackState = when { recording -> TrackSessionUiState.RECORDING; trackCount == 0 -> TrackSessionUiState.IDLE; trackStartedAt != null -> TrackSessionUiState.PAUSED; else -> TrackSessionUiState.FINISHED }
 Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,bottomBar={FieldBottomBar(current=section,onSelect=onSelect)}){pad->Column(Modifier.fillMaxSize().background(FieldBackground).padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("←")};Text(section.icon+"  "+section.label,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
  Text(section.subtitle)
  when(section){
   AppSection.FIELD->{
    val mapSize=if(mapPackBytes<1024) "$mapPackBytes B" else "${mapPackBytes/1024} KB"
    val status=if(position==null) "Đang chờ tín hiệu vệ tinh" else "%.5f, %.5f  •  ±%.0f m".format(position.latitude,position.longitude,position.accuracyM)
    StatusCard("📍 Vị trí hiện tại",status);StatusCard("🗺 Bản đồ offline",when { !mapPackAvailable -> "Chưa có gói bản đồ • GPS và dấu vết vẫn hoạt động"; position != null && mapPoints.isEmpty() && mapLines.isEmpty() && mapPolygons.isEmpty() -> "Chưa có nét bản đồ trong gói"; else -> "Đã nạp $mapPackFiles tệp • $mapSize • chỉ hiển thị dữ liệu vector có trong gói" });val gpsCovered=mapCoverage?.startsWith("Trong vùng")==true;if(mapCoverage!=null) StatusCard(if(gpsCovered) "Vùng bản đồ tại GPS" else "⚠ Vùng xem trước — không phải vị trí hiện tại",mapCoverage!!);OfflineMapCanvas(position,breadcrumb,mapPoints,mapLines,mapPolygons,gpsCovered);if(mapCredit!=null) Text(mapCredit,style=MaterialTheme.typography.bodySmall);StatusCard("🥾 Hành trình","${trackState.name} • $trackCount điểm • ${String.format("%.2f",trackDistanceM/1000.0)} km");Button(onClick=onToggleTrack,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text(if(recording) "■  DỪNG GHI" else "▶  GHI HÀNH TRÌNH")};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onFinishTrack,enabled=trackCount>0,modifier=Modifier.weight(1f)){Text("KẾT THÚC")};OutlinedButton(onClick={confirmClear=true},enabled=trackCount>0,modifier=Modifier.weight(1f)){Text("XÓA")}};if(confirmClear){AlertDialog(onDismissRequest={confirmClear=false},title={Text("Xóa hành trình?")},text={Text("Toàn bộ dấu vết và dữ liệu TrackBack của hành trình hiện tại sẽ bị xóa.")},confirmButton={TextButton(onClick={confirmClear=false;onClearTrack()}){Text("XÓA")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("HỦY")}})};TrackBackCompass(trackBackBearingDeg,offTrackM,breadcrumbCount);StatusCard("↩ TrackBack",if(trackBackBearingDeg==null) "Chưa đủ dữ liệu quay lại" else "${String.format("%.2f",trackBackRemainingM/1000.0)} km còn lại • hướng ${trackBackBearingDeg.toInt()}° • $breadcrumbCount mốc\nLệch dấu vết ${offTrackM.toInt()} m${if(offTrackM>50) " • CẢNH BÁO LỆCH TUYẾN" else ""}\nBám dấu vết cũ không bảo đảm điều kiện đường hiện tại.");Action("📌  ĐÁNH DẤU VỊ TRÍ");Action("📏  ĐO KHOẢNG CÁCH")
   }
   AppSection.RECOGNITION->{RecognitionPanel(imageStatus,imagePreview,saveStatus,onPickImage,onCameraImage,onSaveObservation);OutlinedButton(onClick={onSelect(AppSection.LIBRARY)},modifier=Modifier.fillMaxWidth()){Text("Xem ghi nhận trong Thư viện")}}
   AppSection.SURVIVAL->{StatusCard("🧭 Hướng dẫn thực địa","Tìm nước • trú ẩn • lửa • định hướng");Action("TÌM NƯỚC");Action("LỀU TRẠI & TRÚ ẨN");Action("KỸ NĂNG SINH TỒN")}
   AppSection.EMERGENCY->{StatusCard("🚨 SỰ CỐ","Emergency Core • ưu tiên offline");listOf("SƠ CỨU VẾT THƯƠNG","BỊ RẮN CẮN","CÔN TRÙNG ĐỐT","NGỘ ĐỘC","MẤT PHƯƠNG HƯỚNG","TAI NẠN TRÊN BIỂN").forEach{Action(it)}}
   AppSection.PREP->{StatusCard("🎒 Chuẩn bị hành trình","Trang phục • thiết bị • thuốc • thực phẩm");Action("CHECKLIST CHUYẾN ĐI")}
   AppSection.LIBRARY->{SpeciesLibraryPanel(observations,onDeleteObservation)}
   AppSection.TRAINING->{StatusCard("🎓 LEARN MODE","Tình huống mô phỏng và kiểm tra kiến thức");Action("BẮT ĐẦU HUẤN LUYỆN")}
   AppSection.SETTINGS->{UpdateSettingsPanel(mapPackAvailable,mapPackFiles,mapPackBytes,updateStatus,onCheckUpdate,onRollbackUpdate)}
  }
 }}
}
@Composable fun UpdateSettingsPanel(mapPackAvailable:Boolean,mapPackFiles:Int,mapPackBytes:Long,status:String="Sẵn sàng",onCheck:()->Unit={},onRollback:()->Unit={}){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text("CẬP NHẬT DỮ LIỆU",fontWeight=FontWeight.Bold)
  Text(if(mapPackAvailable) "Bản đồ offline: "+mapPackFiles+" tệp • "+(mapPackBytes/1024/1024)+" MB" else "Bản đồ offline: chưa có dữ liệu")
  Text("Hiện chỉ hỗ trợ cập nhật dữ liệu bản đồ; kênh thư viện khoa học và nội dung sinh tồn chưa được tích hợp.",style=MaterialTheme.typography.bodySmall)
  Text("Trạng thái: "+status,style=MaterialTheme.typography.bodySmall)
  Button(onClick=onCheck,modifier=Modifier.fillMaxWidth()){Text("KIỂM TRA CẬP NHẬT")}
  OutlinedButton(onClick=onRollback,modifier=Modifier.fillMaxWidth()){Text("KHÔI PHỤC GÓI TRƯỚC")}
  Text("Gói mới chỉ được kích hoạt sau khi kiểm tra phiên bản, kích thước và SHA-256.",style=MaterialTheme.typography.labelSmall)
 }}
}

@Composable private fun StatusCard(title:String,text:String){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text(text)}}}
@Composable private fun Action(text:String){Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("$text • CHƯA KHẢ DỤNG")}}
@Composable fun TrackBackCompass(bearing:Double?,offTrackM:Double,breadcrumbCount:Int){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("DẪU VẾT QUAY LẠI",fontWeight=FontWeight.Bold);Canvas(Modifier.fillMaxWidth().height(150.dp)){val cx=size.width/2f;val cy=size.height/2f;val r=minOf(size.width,size.height)*0.38f;drawCircle(Color(0xFFE4EEE7),r,Offset(cx,cy));for(i in 0 until minOf(breadcrumbCount,12)){val a=Math.toRadians((i*30.0)-90.0);val rr=r*(0.25f+0.055f*i);drawCircle(Color(0xFF2E6B4E),5f,Offset(cx+(cos(a)*rr).toFloat(),cy+(sin(a)*rr).toFloat()))};if(bearing!=null){val a=Math.toRadians(bearing-90.0);drawLine(Color(0xFF42E48A),Offset(cx,cy),Offset(cx+(cos(a)*r*0.9).toFloat(),cy+(sin(a)*r*0.9).toFloat()),10f)}};Text(if(bearing==null)"Chưa đủ dữ liệu định hướng" else "Hướng kế tiếp ${bearing.toInt()}° • lệch ${offTrackM.toInt()} m")}}
}

@Composable
fun OfflineMapCanvas(position: FieldPositionUi?, breadcrumb: List<FieldPositionUi>, mapPoints: List<OfflineMapPointUi>, mapLines: List<OfflineMapLineUi>, mapPolygons: List<OfflineMapPolygonUi>, gpsCovered: Boolean = false) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var followGps by remember { mutableStateOf(true) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (gpsCovered) "SƠ ĐỒ VECTOR OFFLINE" else "VÙNG BẢN ĐỒ XEM TRƯỚC", fontWeight = FontWeight.Bold)
            Text("Sơ đồ vector chưa có địa hình hoặc chỉ dẫn. Không dùng để dẫn đường.", style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(330.dp).background(androidx.compose.ui.graphics.Color(0xFF0B272D), RoundedCornerShape(14.dp)).pointerInput(Unit) { detectTransformGestures { _, pan, gestureZoom, _ -> zoom = (zoom * gestureZoom).coerceIn(1f, 8f); if (kotlin.math.abs(gestureZoom - 1f) < 0.001f && (pan.x != 0f || pan.y != 0f)) followGps = false; if (!followGps) { panX += pan.x; panY += pan.y } } }) {
                val mapCoords = mapPoints.map { it.latitude to it.longitude } + mapLines.flatMap { line -> line.points.map { it.latitude to it.longitude } } + mapPolygons.flatMap { polygon -> polygon.points.map { it.latitude to it.longitude } }
                val coords = if (mapCoords.isNotEmpty()) mapCoords else breadcrumb.map { it.latitude to it.longitude } + listOfNotNull(position?.let { it.latitude to it.longitude })
                if (coords.isNotEmpty()) {
                    val minLat = coords.minOf { it.first }; val maxLat = coords.maxOf { it.first }
                    val minLon = coords.minOf { it.second }; val maxLon = coords.maxOf { it.second }
                    val midLat = (minLat + maxLat) / 2.0
                    val midLon = (minLon + maxLon) / 2.0
                    val longitudeScale = cos(Math.toRadians(midLat)).coerceAtLeast(0.01)
                    val projectedWidth = ((maxLon - minLon) * longitudeScale).coerceAtLeast(0.000001)
                    val projectedHeight = (maxLat - minLat).coerceAtLeast(0.000001)
                    val scale = minOf(size.width.toDouble() / projectedWidth, size.height.toDouble() / projectedHeight) * 0.9
                    fun baseX(lon: Double) = size.width.toDouble() / 2.0 + (lon - midLon) * longitudeScale * scale
                    fun baseY(lat: Double) = size.height.toDouble() / 2.0 - (lat - midLat) * scale
                    val gpsInMap = gpsCovered && position != null && (mapCoords.isEmpty() || (position.latitude in minLat..maxLat && position.longitude in minLon..maxLon))
                    val gpsBaseX = position?.takeIf { gpsInMap }?.let { baseX(it.longitude) }
                    val gpsBaseY = position?.takeIf { gpsInMap }?.let { baseY(it.latitude) }
                    val followX = if (followGps && gpsBaseX != null) (size.width.toDouble() / 2.0 - ((gpsBaseX - size.width.toDouble() / 2.0) * zoom.toDouble() + size.width.toDouble() / 2.0)) else panX.toDouble()
                    val followY = if (followGps && gpsBaseY != null) (size.height.toDouble() / 2.0 - ((gpsBaseY - size.height.toDouble() / 2.0) * zoom.toDouble() + size.height.toDouble() / 2.0)) else panY.toDouble()
                    fun x(lon: Double) = (((baseX(lon) - size.width.toDouble() / 2.0) * zoom) + size.width.toDouble() / 2.0 + followX).toFloat()
                    fun y(lat: Double) = (((baseY(lat) - size.height.toDouble() / 2.0) * zoom) + size.height.toDouble() / 2.0 + followY).toFloat()
                    mapPolygons.forEach { polygon -> if (polygon.points.size >= 3) { for (i in polygon.points.indices) { val a=polygon.points[i]; val b=polygon.points[(i+1)%polygon.points.size]; drawLine(Color(0xFF9BB8A5),Offset(x(a.longitude),y(a.latitude)),Offset(x(b.longitude),y(b.latitude)),3f) } } }
                    mapLines.forEach { line -> for (i in 1 until line.points.size) { val a=line.points[i-1]; val b=line.points[i]; drawLine(Color(0xFF85BBA1),Offset(x(a.longitude),y(a.latitude)),Offset(x(b.longitude),y(b.latitude)),4f) } }
                    mapPoints.forEach { drawCircle(Color(0xFF9FCBAB), 6f, Offset(x(it.longitude), y(it.latitude))) }
                    for (i in 1 until breadcrumb.size) drawLine(Color(0xFF2E6B4E), Offset(x(breadcrumb[i-1].longitude), y(breadcrumb[i-1].latitude)), Offset(x(breadcrumb[i].longitude), y(breadcrumb[i].latitude)), 6f)
                    position?.takeIf { gpsInMap }?.let { drawCircle(Color(0xFF163C2B), 10f, Offset(x(it.longitude), y(it.latitude))) }
                }
            }
            Text("Zoom ${String.format("%.1f", zoom)}× • ${if (followGps && gpsCovered && position != null) "Theo GPS" else "Xem vùng bản đồ"}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { followGps = true; panX = 0f; panY = 0f }, enabled = gpsCovered && position != null, modifier = Modifier.weight(1f)) { Text("Vị trí hiện tại") }; TextButton(onClick = { zoom = 1f; panX = 0f; panY = 0f; followGps = gpsCovered }, modifier = Modifier.weight(1f)) { Text("Căn lại") } }
            Text("${mapPoints.size} điểm • ${mapLines.size} đường • ${mapPolygons.size} vùng • ${breadcrumb.size} mốc hành trình • offline")
        }
    }
}
