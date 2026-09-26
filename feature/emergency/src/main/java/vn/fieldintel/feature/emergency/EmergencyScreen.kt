package vn.fieldintel.feature.emergency

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

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

@Composable
fun EmergencyScreen(
 position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,
 trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,
 breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,
 mapPoints:List<OfflineMapPointUi> = emptyList(),mapLines:List<OfflineMapLineUi> = emptyList(),mapPolygons:List<OfflineMapPolygonUi> = emptyList(),
 mapCredit:String?=null,onToggleTrack:()->Unit={},onFinishTrack:()->Unit={},onClearTrack:()->Unit={},updateStatus:String="Sẵn sàng",
 onCheckUpdate:()->Unit={},onRollbackUpdate:()->Unit={},mapCoverage:String?=null,imageStatus:String="Chưa chọn ảnh",
 imagePreview:android.graphics.Bitmap?=null,onPickImage:()->Unit={},onCameraImage:()->Unit={},observations:List<ObservationUi> = emptyList(),
 saveStatus:String="",onSaveObservation:(String)->Unit={},onDeleteObservation:(String)->Unit={},onOpenRegionScan:()->Unit={}
){
 MaterialTheme(colorScheme=FieldColors){
  CompositionLocalProvider(LocalContentColor provides Color.White) {
  FieldIntelligenceHome(position,recording,trackCount,trackDistanceM,trackStartedAt,trackBackRemainingM,trackBackBearingDeg,offTrackM,breadcrumbCount,breadcrumb,mapPackAvailable,mapPackFiles,mapPackBytes,mapPoints,mapLines,mapPolygons,mapCredit,onToggleTrack,onFinishTrack,onClearTrack,updateStatus,onCheckUpdate,onRollbackUpdate,mapCoverage,imageStatus,imagePreview,onPickImage,onCameraImage,observations,saveStatus,onSaveObservation,onDeleteObservation,onOpenRegionScan)
  }
 }
}

@Composable
fun FieldIntelligenceHome(
 position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,
 trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,
 breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,
 mapPoints:List<OfflineMapPointUi> = emptyList(),mapLines:List<OfflineMapLineUi> = emptyList(),mapPolygons:List<OfflineMapPolygonUi> = emptyList(),
 mapCredit:String?=null,onToggleTrack:()->Unit={},onFinishTrack:()->Unit={},onClearTrack:()->Unit={},updateStatus:String="Sẵn sàng",
 onCheckUpdate:()->Unit={},onRollbackUpdate:()->Unit={},mapCoverage:String?=null,imageStatus:String="Chưa chọn ảnh",
 imagePreview:android.graphics.Bitmap?=null,onPickImage:()->Unit={},onCameraImage:()->Unit={},observations:List<ObservationUi> = emptyList(),
 saveStatus:String="",onSaveObservation:(String)->Unit={},onDeleteObservation:(String)->Unit={},onOpenRegionScan:()->Unit={}
){
 var selected by remember{mutableStateOf<AppSection?>(null)}
 if(selected!=null){
  SectionScreen(selected!!,position,recording,trackCount,trackDistanceM,trackStartedAt,trackBackRemainingM,trackBackBearingDeg,offTrackM,breadcrumbCount,breadcrumb,mapPackAvailable,mapPackFiles,mapPackBytes,mapPoints,mapLines,mapPolygons,mapCredit,onBack={selected=null},onToggleTrack=onToggleTrack,onFinishTrack=onFinishTrack,onClearTrack=onClearTrack,updateStatus=updateStatus,onCheckUpdate=onCheckUpdate,onRollbackUpdate=onRollbackUpdate,mapCoverage=mapCoverage,imageStatus=imageStatus,imagePreview=imagePreview,onPickImage=onPickImage,onCameraImage=onCameraImage,observations=observations,saveStatus=saveStatus,onSaveObservation=onSaveObservation,onDeleteObservation=onDeleteObservation,onOpenRegionScan=onOpenRegionScan,onSelect={if(it==AppSection.RECOGNITION) onOpenRegionScan() else selected=it})
  return
 }
 Scaffold(containerColor=Color.Transparent,bottomBar={FieldBottomBar(onSelect={if(it==AppSection.RECOGNITION) onOpenRegionScan() else selected=it},current=selected)}){pad->
  Column(Modifier.fillMaxSize().background(FieldBackground).padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   FieldHomePanel(position){if(it==AppSection.RECOGNITION) onOpenRegionScan() else selected=it}
  }
 }
}

@Composable
private fun FeatureCard(s:AppSection,modifier:Modifier=Modifier,onOpen:(AppSection)->Unit){
 Card(onClick={onOpen(s)},modifier=modifier.height(138.dp),shape=RoundedCornerShape(22.dp)){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.SpaceBetween){
   Text(s.icon,style=MaterialTheme.typography.headlineMedium);Text(s.label,fontWeight=FontWeight.Bold);Text(s.subtitle,style=MaterialTheme.typography.labelSmall)
  }
 }
}

@Composable
fun SectionScreen(
 section:AppSection,position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,
 trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,
 breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,
 mapPoints:List<OfflineMapPointUi> = emptyList(),mapLines:List<OfflineMapLineUi> = emptyList(),mapPolygons:List<OfflineMapPolygonUi> = emptyList(),
 mapCredit:String?=null,onBack:()->Unit,onToggleTrack:()->Unit={},onFinishTrack:()->Unit={},onClearTrack:()->Unit={},
 updateStatus:String="Sẵn sàng",onCheckUpdate:()->Unit={},onRollbackUpdate:()->Unit={},mapCoverage:String?=null,
 imageStatus:String="Chưa chọn ảnh",imagePreview:android.graphics.Bitmap?=null,onPickImage:()->Unit={},onCameraImage:()->Unit={},
 observations:List<ObservationUi> = emptyList(),saveStatus:String="",onSaveObservation:(String)->Unit={},onDeleteObservation:(String)->Unit={},
 onSelect:(AppSection?)->Unit={},onOpenRegionScan:()->Unit={}
){
 var confirmClear by remember { mutableStateOf(false) }
 var liveSearchMode by remember(section) { mutableStateOf(false) }
 val trackState = when { recording -> TrackSessionUiState.RECORDING; trackCount == 0 -> TrackSessionUiState.IDLE; trackStartedAt != null -> TrackSessionUiState.PAUSED; else -> TrackSessionUiState.FINISHED }
 Scaffold(containerColor=Color.Transparent,bottomBar={FieldBottomBar(current=section,onSelect=onSelect)}){pad->
  Column(Modifier.fillMaxSize().background(FieldBackground).padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   FieldSectionBanner(section,onBack)
   when(section){
    AppSection.FIELD->{
     val mapSize=if(mapPackBytes<1024) "$mapPackBytes B" else "${mapPackBytes/1024} KB"
     val gpsCovered=mapCoverage?.startsWith("Trong vùng")==true
     FieldPositionPanel(position)
     FieldInfoCard("🗺️","Bản đồ offline",when { !mapPackAvailable -> "Chưa có gói bản đồ • GPS và dấu vết vẫn hoạt động"; position != null && mapPoints.isEmpty() && mapLines.isEmpty() && mapPolygons.isEmpty() -> "Gói hiện tại chưa có nét bản đồ tại vùng này"; else -> "Đã nạp $mapPackFiles tệp • $mapSize • dữ liệu vector offline" }, if(mapPackAvailable) Color(0xFF4BE08E) else Color(0xFFFFC857))
     if(mapCoverage!=null) FieldInfoCard(if(gpsCovered) "◎" else "⚠","Phạm vi hiển thị",mapCoverage,if(gpsCovered) Color(0xFF4BE08E) else Color(0xFFFFC857))
     OfflineMapCanvas(position,breadcrumb,mapPoints,mapLines,mapPolygons,gpsCovered)
     if(mapCredit!=null) Text(mapCredit,style=MaterialTheme.typography.bodySmall,color=FieldColors.onSurfaceVariant)
     FieldTrackPanel(trackState,recording,trackCount,trackDistanceM,onToggleTrack,onFinishTrack,{confirmClear=true})
     if(confirmClear){AlertDialog(onDismissRequest={confirmClear=false},title={Text("Xóa hành trình?")},text={Text("Toàn bộ dấu vết và dữ liệu TrackBack của hành trình hiện tại sẽ bị xóa.")},confirmButton={TextButton(onClick={confirmClear=false;onClearTrack()}){Text("XÓA")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("HỦY")}})}
     TrackBackCompass(trackBackBearingDeg,offTrackM,breadcrumbCount)
     FieldInfoCard("↩","TrackBack",if(trackBackBearingDeg==null) "Chưa đủ dữ liệu quay lại" else "${String.format("%.2f",trackBackRemainingM/1000.0)} km còn lại • hướng ${trackBackBearingDeg.toInt()}° • $breadcrumbCount mốc\nLệch dấu vết ${offTrackM.toInt()} m${if(offTrackM>50) " • CẢNH BÁO LỆCH TUYẾN" else ""}\nBám dấu vết cũ không bảo đảm điều kiện đường hiện tại.",if(offTrackM>50) Color(0xFFFF8A80) else Color(0xFF78DCE8))
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){LargeFieldAction("📌","Đánh dấu",Modifier.weight(1f));LargeFieldAction("📏","Đo khoảng cách",Modifier.weight(1f))}
    }
    AppSection.RECOGNITION->{
     Button(onClick=onOpenRegionScan,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)){Text("MỞ CAMERA QUÉT VÙNG",fontWeight=FontWeight.Bold)}
     RecognitionPanel(imageStatus,imagePreview,saveStatus,onPickImage,onCameraImage,onSaveObservation)
     OutlinedButton(onClick={onSelect(AppSection.LIBRARY)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("Xem ghi nhận trong Thư viện")}
    }
    AppSection.SURVIVAL->{SurvivalPanel()}
    AppSection.EMERGENCY->{EmergencyPanel()}
    AppSection.PREP->{PreparationPanel()}
    AppSection.LIBRARY->{SpeciesLibraryPanel(observations,onDeleteObservation)}
    AppSection.TRAINING->{TrainingPanel()}
    AppSection.SETTINGS->{UpdateSettingsPanel(mapPackAvailable,mapPackFiles,mapPackBytes,updateStatus,onCheckUpdate,onRollbackUpdate)}
   }
  }
 }
}

@Composable
private fun SurvivalPanel(){
 SectionIntroCard("🔥","SINH TỒN NGOÀI THỰC ĐỊA","Ưu tiên quyết định đơn giản, rõ bước và dùng được khi mất mạng.",Color(0xFFFFB45F))
 val items=listOf(
  Triple("💧","Tìm & xử lý nước","Nguồn nước • lọc • đun • cảnh báo"),
  Triple("⛺","Trú ẩn","Mưa • gió • giữ nhiệt • chọn vị trí"),
  Triple("🧭","Định hướng","La bàn • dấu mốc • TrackBack"),
  Triple("🔥","Lửa & nhiệt","Nhóm lửa • giữ lửa • an toàn"),
  Triple("🍃","Thực phẩm tự nhiên","Chỉ tra cứu nguồn đã kiểm chứng; không suy đoán ăn được"),
  Triple("🪢","Nút dây & dựng trại","Thao tác nền tảng ngoài thực địa")
 )
 items.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{(icon,title,subtitle)->SectionTile(icon,title,subtitle,Color(0xFFFFB45F),Modifier.weight(1f))};if(row.size==1)Spacer(Modifier.weight(1f))}}
 SafetyNote("Nội dung hướng dẫn chi tiết chỉ hiển thị khi có dữ liệu đã được kiểm chứng. Các nút chưa có nội dung chuẩn hóa vẫn được đánh dấu chưa khả dụng.")
}

@Composable
private fun EmergencyPanel(){
 SectionIntroCard("SOS","XỬ LÝ SỰ CỐ","Ưu tiên tình huống khẩn cấp, thao tác lớn và dễ đọc dưới áp lực.",Color(0xFFFF7C84))
 listOf(
  Triple("🩹","Sơ cứu vết thương","Chảy máu • băng ép • theo dõi"),
  Triple("🐍","Rắn cắn","Giữ yên • giảm vận động • tìm trợ giúp"),
  Triple("🐝","Côn trùng đốt","Đánh giá phản ứng • cảnh báo dị ứng nặng"),
  Triple("⚠","Ngộ độc","Ngừng phơi nhiễm • bảo toàn mẫu • hỗ trợ y tế"),
  Triple("🧭","Mất phương hướng","Dừng lại • định vị • quay dấu vết"),
  Triple("🌊","Tai nạn trên biển","Nổi • giữ nhiệt • tín hiệu cứu nạn")
 ).forEach{(icon,title,subtitle)->EmergencyRow(icon,title,subtitle)}
 SafetyNote("Các mục hiện là giao diện điều hướng. Quy trình y khoa/chống độc chưa được coi là hoàn tất nếu chưa có nguồn chuyên môn và kiểm thử nội dung.")
}

@Composable
private fun PreparationPanel(){
 SectionIntroCard("🎒","CHUẨN BỊ HÀNH TRÌNH","Checklist trực quan, ưu tiên đồ thiết yếu trước khi đi rừng hoặc đi biển.",Color(0xFFB9ABFF))
 val items=listOf(
  Triple("👕","Trang phục","Lớp mặc • mưa • nắng • giày"),
  Triple("🧰","Thiết bị","Đèn • pin • dao cụ hợp pháp • dây"),
  Triple("💊","Túi y tế","Vật tư sơ cứu cơ bản • thuốc cá nhân"),
  Triple("🥤","Nước & thực phẩm","Dự phòng • bảo quản • khẩu phần"),
  Triple("📍","Định vị","Bản đồ offline • pin dự phòng • kế hoạch tuyến"),
  Triple("📣","Liên lạc","Người liên hệ • thời điểm check-in • tín hiệu")
 )
 items.forEachIndexed{index,(icon,title,subtitle)->ChecklistRow(icon,title,subtitle,index<2)}
 SafetyNote("Danh sách mua sắm, giá và thuốc cụ thể chưa được coi là dữ liệu hoàn chỉnh cho đến khi có nguồn và tiêu chí cập nhật rõ ràng.")
}

@Composable
private fun TrainingPanel(){
 SectionIntroCard("🎓","HUẤN LUYỆN","Mô phỏng thao tác trước khi ra thực địa.",Color(0xFF6FE6E7))
 SectionTile("🧭","Bài định hướng","Tình huống la bàn, dấu mốc và TrackBack",Color(0xFF6FE6E7),Modifier.fillMaxWidth())
 SectionTile("🩹","Bài xử lý sự cố","Mô phỏng quyết định theo từng bước",Color(0xFFFF7C84),Modifier.fillMaxWidth())
 SafetyNote("Chế độ huấn luyện hiện mới là khung giao diện; chưa có bộ tình huống được kiểm chứng.")
}

@Composable
private fun SectionIntroCard(icon:String,title:String,subtitle:String,accent:Color){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102A31),contentColor=Color.White)){
  Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(accent.copy(alpha=.20f),Color.Transparent)))){
   Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
    Surface(shape=RoundedCornerShape(18.dp),color=accent.copy(alpha=.16f)){Box(Modifier.size(58.dp),contentAlignment=Alignment.Center){Text(icon,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black,color=Color.White)}}
    Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge,color=Color.White);Spacer(Modifier.height(3.dp));Text(subtitle,color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}
   }
  }
 }
}

@Composable
private fun SectionTile(icon:String,title:String,subtitle:String,accent:Color,modifier:Modifier=Modifier){
 Card(onClick={},enabled=false,modifier=modifier.heightIn(min=126.dp),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(disabledContainerColor=Color(0xFF112E35),disabledContentColor=Color.White,contentColor=Color.White)){
  Column(Modifier.fillMaxSize().padding(15.dp),verticalArrangement=Arrangement.SpaceBetween){
   Surface(shape=CircleShape,color=accent.copy(alpha=.14f)){Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){Text(icon,style=MaterialTheme.typography.titleLarge)}}
   Column{Text(title,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(3.dp));Text(subtitle,style=MaterialTheme.typography.bodySmall,color=FieldColors.onSurfaceVariant)}
  }
 }
}

@Composable
private fun EmergencyRow(icon:String,title:String,subtitle:String){
 Surface(modifier=Modifier.fillMaxWidth().heightIn(min=78.dp),shape=RoundedCornerShape(20.dp),color=Color(0xFF172B31),contentColor=Color.White,border=androidx.compose.foundation.BorderStroke(1.dp,Color(0x33FF7C84))){
  Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
   Surface(shape=CircleShape,color=Color(0x22FF7C84)){Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){Text(icon,style=MaterialTheme.typography.titleLarge)}}
   Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium,color=Color.White);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=FieldColors.onSurfaceVariant);Text("Chưa có hướng dẫn đã kiểm chứng",style=MaterialTheme.typography.labelSmall,color=Color(0xFFFFD166))}
  }
 }
}

@Composable
private fun ChecklistRow(icon:String,title:String,subtitle:String,priority:Boolean){
 Surface(modifier=Modifier.fillMaxWidth().heightIn(min=76.dp),shape=RoundedCornerShape(20.dp),color=Color(0xFF142D36),contentColor=Color.White){
  Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
   Surface(shape=RoundedCornerShape(16.dp),color=Color(0x22B9ABFF)){Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){Text(icon,style=MaterialTheme.typography.titleLarge)}}
   Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(title,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium);if(priority){Spacer(Modifier.width(8.dp));Surface(shape=RoundedCornerShape(999.dp),color=Color(0x22FFD166)){Text("ƯU TIÊN",Modifier.padding(horizontal=8.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,color=Color(0xFFFFD166),fontWeight=FontWeight.Bold)}}};Text(subtitle,color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
   Text("•",style=MaterialTheme.typography.headlineSmall,color=Color(0xFFB9ABFF))
  }
 }
}

@Composable
private fun SafetyNote(text:String){
 Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=Color(0xFF102A31),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0x33FFC857))){
  Row(Modifier.padding(14.dp),verticalAlignment=Alignment.Top){Text("ⓘ",color=Color(0xFFFFC857),style=MaterialTheme.typography.titleLarge);Spacer(Modifier.width(10.dp));Text(text,color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
 }
}

@Composable private fun FieldPositionPanel(position:FieldPositionUi?){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102F35),contentColor=Color.White)){
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=Color(0x2245E58C)){Text("📍",Modifier.padding(12.dp),style=MaterialTheme.typography.headlineSmall)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("VỊ TRÍ HIỆN TẠI",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium);Text(if(position==null)"Đang chờ tín hiệu vệ tinh" else "GPS đã khóa vị trí",color=if(position==null) Color(0xFFFFC857) else FieldColors.primary,style=MaterialTheme.typography.bodyMedium)}}
   if(position!=null){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){FieldMetricChip("Vĩ độ","%.5f".format(position.latitude),Modifier.weight(1f));FieldMetricChip("Kinh độ","%.5f".format(position.longitude),Modifier.weight(1f));FieldMetricChip("Sai số","±%.0f m".format(position.accuracyM),Modifier.weight(1f))}} else Text("Hãy ra khu vực thoáng để nhận tín hiệu tốt hơn. Các dữ liệu offline vẫn sử dụng được.",color=FieldColors.onSurfaceVariant)
  }
 }
}

@Composable private fun FieldMetricChip(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier=modifier,shape=RoundedCornerShape(16.dp),color=Color(0xFF173D43),contentColor=Color.White){Column(Modifier.padding(horizontal=10.dp,vertical=11.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(label,style=MaterialTheme.typography.labelSmall,color=FieldColors.onSurfaceVariant);Text(value,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium,color=Color.White)}}}

@Composable private fun FieldInfoCard(icon:String,title:String,text:String,accent:Color){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102A31))){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.Top){Surface(shape=CircleShape,color=accent.copy(alpha=.14f)){Text(icon,Modifier.padding(11.dp),style=MaterialTheme.typography.titleLarge)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium,color=Color.White);Spacer(Modifier.height(4.dp));Text(text,color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}}}}

@Composable private fun FieldTrackPanel(trackState:TrackSessionUiState,recording:Boolean,trackCount:Int,trackDistanceM:Double,onToggleTrack:()->Unit,onFinishTrack:()->Unit,onClear:()->Unit){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102A31))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("🥾",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("HÀNH TRÌNH",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium,color=Color.White);Text("${trackState.name} • $trackCount điểm • ${String.format("%.2f",trackDistanceM/1000.0)} km",color=FieldColors.onSurfaceVariant)}};Button(onClick=onToggleTrack,modifier=Modifier.fillMaxWidth().heightIn(min=62.dp),shape=RoundedCornerShape(18.dp)){Text(if(recording) "■  DỪNG GHI HÀNH TRÌNH" else "▶  BẮT ĐẦU GHI HÀNH TRÌNH",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleSmall)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(onClick=onFinishTrack,enabled=trackCount>0,modifier=Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)){Text("✓ KẾT THÚC",fontWeight=FontWeight.Bold)};OutlinedButton(onClick=onClear,enabled=trackCount>0,modifier=Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)){Text("🗑 XÓA",fontWeight=FontWeight.Bold)}}}}
}

@Composable private fun LargeFieldAction(icon:String,label:String,modifier:Modifier=Modifier){Button(onClick={},enabled=false,modifier=modifier.heightIn(min=68.dp),shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(icon,style=MaterialTheme.typography.titleLarge);Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)}}}

@Composable fun UpdateSettingsPanel(mapPackAvailable:Boolean,mapPackFiles:Int,mapPackBytes:Long,status:String="Sẵn sàng",onCheck:()->Unit={},onRollback:()->Unit={}){
 SectionIntroCard("⚙","CẬP NHẬT DỮ LIỆU","Kiểm tra, kích hoạt và khôi phục gói dữ liệu an toàn.",Color(0xFF8CCBFF))
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102A31))){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text("🗺️",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Bản đồ offline",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium,color=Color.White);Text(if(mapPackAvailable) "$mapPackFiles tệp • ${mapPackBytes/1024/1024} MB" else "Chưa có dữ liệu",color=if(mapPackAvailable) FieldColors.primary else Color(0xFFFFC857))}}
  HorizontalDivider(color=Color.White.copy(alpha=.08f))
  Text("Trạng thái",style=MaterialTheme.typography.labelMedium,color=FieldColors.onSurfaceVariant);Text(status,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyLarge,color=Color.White)
  Button(onClick=onCheck,modifier=Modifier.fillMaxWidth().heightIn(min=60.dp),shape=RoundedCornerShape(18.dp)){Text("↻  KIỂM TRA CẬP NHẬT",fontWeight=FontWeight.ExtraBold)}
  OutlinedButton(onClick=onRollback,modifier=Modifier.fillMaxWidth().heightIn(min=58.dp),shape=RoundedCornerShape(18.dp)){Text("↩  KHÔI PHỤC GÓI TRƯỚC",fontWeight=FontWeight.Bold)}
 }}
 SafetyNote("Hiện chỉ hỗ trợ cập nhật dữ liệu bản đồ. Kênh thư viện khoa học và nội dung sinh tồn chưa được tích hợp; gói mới chỉ kích hoạt sau kiểm tra phiên bản, kích thước và SHA-256.")
}

@Composable private fun StatusCard(title:String,text:String){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text(text)}}}
@Composable private fun Action(text:String){Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth().heightIn(min=58.dp),shape=RoundedCornerShape(16.dp)){Text("$text • CHƯA KHẢ DỤNG")}}

@Composable fun TrackBackCompass(bearing:Double?,offTrackM:Double,breadcrumbCount:Int){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102A31))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("DẤU VẾT QUAY LẠI",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium);Canvas(Modifier.fillMaxWidth().height(190.dp)){val cx=size.width/2f;val cy=size.height/2f;val r=minOf(size.width,size.height)*0.39f;drawCircle(Color(0xFF173D43),r,Offset(cx,cy));drawCircle(Color(0xFF31555A),r,Offset(cx,cy),style=androidx.compose.ui.graphics.drawscope.Stroke(width=4f));for(i in 0 until minOf(breadcrumbCount,12)){val a=Math.toRadians((i*30.0)-90.0);val rr=r*(0.25f+0.055f*i);drawCircle(Color(0xFF6ED9A2),6f,Offset(cx+(cos(a)*rr).toFloat(),cy+(sin(a)*rr).toFloat()))};if(bearing!=null){val a=Math.toRadians(bearing-90.0);drawLine(Color(0xFF45E58C),Offset(cx,cy),Offset(cx+(cos(a)*r*0.9).toFloat(),cy+(sin(a)*r*0.9).toFloat()),12f);drawCircle(Color(0xFF45E58C),9f,Offset(cx,cy))}};Text(if(bearing==null)"Chưa đủ dữ liệu định hướng" else "Hướng kế tiếp ${bearing.toInt()}° • lệch ${offTrackM.toInt()} m",style=MaterialTheme.typography.bodyMedium,color=if(offTrackM>50) Color(0xFFFF8A80) else FieldColors.onSurfaceVariant)}}
}

@Composable
fun OfflineMapCanvas(position: FieldPositionUi?, breadcrumb: List<FieldPositionUi>, mapPoints: List<OfflineMapPointUi>, mapLines: List<OfflineMapLineUi>, mapPolygons: List<OfflineMapPolygonUi>, gpsCovered: Boolean = false) {
 var zoom by remember { mutableFloatStateOf(1f) }
 var panX by remember { mutableFloatStateOf(0f) }
 var panY by remember { mutableFloatStateOf(0f) }
 var followGps by remember { mutableStateOf(true) }
 Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF102A31))) {
  Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if (gpsCovered) "BẢN ĐỒ OFFLINE" else "VÙNG BẢN ĐỒ XEM TRƯỚC", fontWeight = FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium);Text("Kéo để di chuyển • chụm để phóng to", style = MaterialTheme.typography.bodySmall,color=FieldColors.onSurfaceVariant)};Surface(shape=RoundedCornerShape(999.dp),color=Color(0x2245E58C)){Text("${String.format("%.1f", zoom)}×",Modifier.padding(horizontal=12.dp,vertical=7.dp),color=FieldColors.primary,fontWeight=FontWeight.Bold)}}
   Box(Modifier.fillMaxWidth().height(390.dp).background(Brush.verticalGradient(listOf(Color(0xFF12353B),Color(0xFF081F25))),RoundedCornerShape(18.dp))){
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { _, pan, gestureZoom, _ -> zoom = (zoom * gestureZoom).coerceIn(1f, 8f); if (kotlin.math.abs(gestureZoom - 1f) < 0.001f && (pan.x != 0f || pan.y != 0f)) followGps = false; if (!followGps) { panX += pan.x; panY += pan.y } } }) {
     val mapCoords = mapPoints.map { it.latitude to it.longitude } + mapLines.flatMap { line -> line.points.map { it.latitude to it.longitude } } + mapPolygons.flatMap { polygon -> polygon.points.map { it.latitude to it.longitude } }
     val coords = if (mapCoords.isNotEmpty()) mapCoords else breadcrumb.map { it.latitude to it.longitude } + listOfNotNull(position?.let { it.latitude to it.longitude })
     if (coords.isNotEmpty()) {
      val minLat = coords.minOf { it.first }
      val maxLat = coords.maxOf { it.first }
      val minLon = coords.minOf { it.second }
      val maxLon = coords.maxOf { it.second }
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
      mapPolygons.forEach { polygon -> if (polygon.points.size >= 3) { for (i in polygon.points.indices) { val a=polygon.points[i]; val b=polygon.points[(i+1)%polygon.points.size]; drawLine(Color(0xFF8FAFA0),Offset(x(a.longitude),y(a.latitude)),Offset(x(b.longitude),y(b.latitude)),3f) } } }
      mapLines.forEach { line -> for (i in 1 until line.points.size) { val a=line.points[i-1]; val b=line.points[i]; drawLine(Color(0xFFB7D8C4),Offset(x(a.longitude),y(a.latitude)),Offset(x(b.longitude),y(b.latitude)),5f) } }
      mapPoints.forEach { drawCircle(Color(0xFFFFD166), 7f, Offset(x(it.longitude), y(it.latitude))) }
      for (i in 1 until breadcrumb.size) drawLine(Color(0xFF45E58C), Offset(x(breadcrumb[i-1].longitude), y(breadcrumb[i-1].latitude)), Offset(x(breadcrumb[i].longitude), y(breadcrumb[i].latitude)), 7f)
      position?.takeIf { gpsInMap }?.let { drawCircle(Color(0x5539D98A), 18f, Offset(x(it.longitude), y(it.latitude))); drawCircle(Color(0xFF45E58C), 9f, Offset(x(it.longitude), y(it.latitude))) }
     }
    }
    Column(Modifier.align(Alignment.CenterEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){MapRoundButton("+"){zoom=(zoom*1.35f).coerceAtMost(8f)};MapRoundButton("−"){zoom=(zoom/1.35f).coerceAtLeast(1f)}}
   }
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { followGps = true; panX = 0f; panY = 0f }, enabled = gpsCovered && position != null, modifier = Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)) { Text("◎  VỊ TRÍ CỦA TÔI",fontWeight=FontWeight.Bold) }; OutlinedButton(onClick = { zoom = 1f; panX = 0f; panY = 0f; followGps = gpsCovered }, modifier = Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)) { Text("↺  CĂN LẠI",fontWeight=FontWeight.Bold) } }
   Text("${mapPoints.size} điểm • ${mapLines.size} đường • ${mapPolygons.size} vùng • ${breadcrumb.size} mốc hành trình • offline",style=MaterialTheme.typography.bodySmall,color=FieldColors.onSurfaceVariant);Text("Sơ đồ vector chưa có địa hình hoặc chỉ dẫn. Không dùng để dẫn đường.", style = MaterialTheme.typography.labelSmall,color=Color(0xFFFFC857))
  }
 }
}

@Composable private fun MapRoundButton(label:String,onClick:()->Unit){FilledTonalButton(onClick=onClick,modifier=Modifier.size(52.dp),shape=CircleShape,contentPadding=PaddingValues(0.dp)){Text(label,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}}
