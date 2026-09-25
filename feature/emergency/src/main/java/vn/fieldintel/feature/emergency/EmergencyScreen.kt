package vn.fieldintel.feature.emergency

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
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

enum class AppSection(val label:String,val icon:String,val subtitle:String){
 FIELD("Thực địa","🗺️","GPS • hành trình • ghi nhận"), RECOGNITION("Nhận dạng","🌿","Camera • bằng chứng • phân biệt"),
 SURVIVAL("Sinh tồn","🔥","Nước • trú ẩn • định hướng"), EMERGENCY("Sự cố","SOS","Xử lý tình huống khẩn cấp"),
 PREP("Chuẩn bị","🎒","Trang bị • thuốc • checklist"), LIBRARY("Thư viện","📚","Tra cứu khoa học offline"),
 TRAINING("Huấn luyện","🎓","Học • mô phỏng • kiểm tra")
}

data class FieldPositionUi(val latitude:Double,val longitude:Double,val accuracyM:Float)

@Composable fun EmergencyScreen(position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,onToggleTrack:()->Unit={}){ FieldIntelligenceHome(position,recording,trackCount,trackDistanceM,trackStartedAt,trackBackRemainingM,trackBackBearingDeg,offTrackM,breadcrumbCount,breadcrumb,mapPackAvailable,mapPackFiles,mapPackBytes,onToggleTrack) }

@Composable fun FieldIntelligenceHome(position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,onToggleTrack:()->Unit={}){
 var selected by remember{mutableStateOf<AppSection?>(null)}
 if(selected!=null){SectionScreen(section=selected!!,position=position,recording=recording,trackCount=trackCount,trackDistanceM=trackDistanceM,trackStartedAt=trackStartedAt,trackBackRemainingM=trackBackRemainingM,trackBackBearingDeg=trackBackBearingDeg,onToggleTrack=onToggleTrack,onBack={selected=null});return}
 Scaffold(bottomBar={BottomBar()}){pad->
  Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Text("VN SINH TỒN",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
   Text("Khám phá • Nhận biết • Sinh tồn an toàn",style=MaterialTheme.typography.bodyMedium)
   Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp)){Text("🧭  SẴN SÀNG THỰC ĐỊA",fontWeight=FontWeight.Bold);Text("Offline Core sẵn sàng • GPS đang kiểm tra")}}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){FeatureCard(AppSection.FIELD,Modifier.weight(1f)){selected=it};FeatureCard(AppSection.RECOGNITION,Modifier.weight(1f)){selected=it}}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){FeatureCard(AppSection.SURVIVAL,Modifier.weight(1f)){selected=it};FeatureCard(AppSection.EMERGENCY,Modifier.weight(1f)){selected=it}}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){FeatureCard(AppSection.PREP,Modifier.weight(1f)){selected=it};FeatureCard(AppSection.LIBRARY,Modifier.weight(1f)){selected=it}}
   OutlinedButton(onClick={selected=AppSection.TRAINING},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Text("🎓  HUẤN LUYỆN")}
  }
 }
}

@Composable private fun FeatureCard(s:AppSection,modifier:Modifier=Modifier,onOpen:(AppSection)->Unit){
 Card(onClick={onOpen(s)},modifier=modifier.height(126.dp),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.SpaceBetween){Text(s.icon,style=MaterialTheme.typography.headlineMedium);Text(s.label,fontWeight=FontWeight.Bold);Text(s.subtitle,style=MaterialTheme.typography.labelSmall)}}
}

@Composable private fun BottomBar(){NavigationBar{NavigationBarItem(true,{},icon={Text("⌂")},label={Text("Trang chủ")});NavigationBarItem(false,{},icon={Text("🗺")},label={Text("Bản đồ")});NavigationBarItem(false,{},icon={Text("◎")},label={Text("Quét")});NavigationBarItem(false,{},icon={Text("▣")},label={Text("Lưu trữ")});NavigationBarItem(false,{},icon={Text("⚙")},label={Text("Cài đặt")})}}

@Composable fun SectionScreen(section:AppSection,position:FieldPositionUi?=null,recording:Boolean=false,trackCount:Int=0,trackDistanceM:Double=0.0,trackStartedAt:Long?=null,trackBackRemainingM:Double=0.0,trackBackBearingDeg:Double?=null,offTrackM:Double=0.0,breadcrumbCount:Int=0,breadcrumb:List<FieldPositionUi> = emptyList(),mapPackAvailable:Boolean=false,mapPackFiles:Int=0,mapPackBytes:Long=0,onBack:()->Unit,onToggleTrack:()->Unit={}){
 Scaffold(bottomBar={BottomBar()}){pad->Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("←")};Text(section.icon+"  "+section.label,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
  Text(section.subtitle)
  when(section){
   AppSection.FIELD->{
    val status=if(position==null) "Đang chờ tín hiệu vệ tinh" else "%.5f, %.5f  •  ±%.0f m".format(position.latitude,position.longitude,position.accuracyM)
    StatusCard("📍 Vị trí hiện tại",status);StatusCard("🥾 Hành trình",if(recording) "ĐANG GHI • $trackCount điểm • ${String.format("%.2f",trackDistanceM/1000.0)} km" else "$trackCount điểm • ${String.format("%.2f",trackDistanceM/1000.0)} km");Button(onClick=onToggleTrack,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text(if(recording) "■  DỪNG GHI" else "▶  GHI HÀNH TRÌNH")};StatusCard("🗺 Nền bản đồ offline",if(mapPackAvailable) "Đã nạp • $mapPackFiles tệp • ${mapPackBytes/1024/1024} MB" else "Chưa có map pack • breadcrumb vẫn hoạt động");BreadcrumbMap(position,breadcrumb);TrackBackCompass(trackBackBearingDeg,offTrackM,breadcrumbCount);StatusCard("↩ TrackBack",if(trackBackBearingDeg==null) "Chưa đủ dữ liệu quay lại" else "${String.format("%.2f",trackBackRemainingM/1000.0)} km còn lại • hướng ${trackBackBearingDeg.toInt()}° • $breadcrumbCount mốc\nLệch dấu vết ${offTrackM.toInt()} m${if(offTrackM>50) " • CẢNH BÁO LỆCH TUYẾN" else ""}\nBám dấu vết cũ không bảo đảm điều kiện đường hiện tại.");Action("📌  ĐÁNH DẤU VỊ TRÍ");Action("📏  ĐO KHOẢNG CÁCH")
   }
   AppSection.RECOGNITION->{StatusCard("🌿 Nhận dạng sinh học","Thực vật • động vật • côn trùng • nấm");Action("📷  CHỤP / CHỌN ẢNH");Text("Kết quả UNKNOWN luôn được phép khi bằng chứng chưa đủ.")}
   AppSection.SURVIVAL->{StatusCard("🧭 Hướng dẫn thực địa","Tìm nước • trú ẩn • lửa • định hướng");Action("TÌM NƯỚC");Action("LỀU TRẠI & TRÚ ẨN");Action("KỸ NĂNG SINH TỒN")}
   AppSection.EMERGENCY->{StatusCard("🚨 SỰ CỐ","Emergency Core • ưu tiên offline");listOf("SƠ CỨU VẾT THƯƠNG","BỊ RẮN CẮN","CÔN TRÙNG ĐỐT","NGỘ ĐỘC","MẤT PHƯƠNG HƯỚNG","TAI NẠN TRÊN BIỂN").forEach{Action(it)}}
   AppSection.PREP->{StatusCard("🎒 Chuẩn bị hành trình","Trang phục • thiết bị • thuốc • thực phẩm");Action("CHECKLIST CHUYẾN ĐI")}
   AppSection.LIBRARY->{StatusCard("📚 Thư viện offline","Taxonomy • morphology • evidence");Action("THỰC VẬT");Action("ĐỘNG VẬT");Action("CÔN TRÙNG");Action("NẤM")}
   AppSection.TRAINING->{StatusCard("🎓 LEARN MODE","Tình huống mô phỏng và kiểm tra kiến thức");Action("BẮT ĐẦU HUẤN LUYỆN")}
  }
 }}
}
@Composable private fun StatusCard(title:String,text:String){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text(text)}}}
@Composable private fun Action(text:String){Button(onClick={},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text(text)}}
@Composable fun TrackBackCompass(bearing:Double?,offTrackM:Double,breadcrumbCount:Int){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("DẪU VẾT QUAY LẠI",fontWeight=FontWeight.Bold);Canvas(Modifier.fillMaxWidth().height(150.dp)){val cx=size.width/2f;val cy=size.height/2f;val r=minOf(size.width,size.height)*0.38f;drawCircle(Color(0xFFE4EEE7),r,Offset(cx,cy));for(i in 0 until minOf(breadcrumbCount,12)){val a=Math.toRadians((i*30.0)-90.0);val rr=r*(0.25f+0.055f*i);drawCircle(Color(0xFF2E6B4E),5f,Offset(cx+(cos(a)*rr).toFloat(),cy+(sin(a)*rr).toFloat()))};if(bearing!=null){val a=Math.toRadians(bearing-90.0);drawLine(Color(0xFF163C2B),Offset(cx,cy),Offset(cx+(cos(a)*r*0.9).toFloat(),cy+(sin(a)*r*0.9).toFloat()),10f)}};Text(if(bearing==null)"Chưa đủ dữ liệu định hướng" else "Hướng kế tiếp ${bearing.toInt()}° • lệch ${offTrackM.toInt()} m")}}
}

@Composable
fun BreadcrumbMap(position: FieldPositionUi?, points: List<FieldPositionUi>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BẢN ĐỒ DẤU VẾT OFFLINE", fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                if (points.size > 1) {
                    val all = if (position == null) points else points + position
                    val minLat = all.minOf { it.latitude }
                    val maxLat = all.maxOf { it.latitude }
                    val minLon = all.minOf { it.longitude }
                    val maxLon = all.maxOf { it.longitude }
                    val latSpan = (maxLat - minLat).coerceAtLeast(0.000001)
                    val lonSpan = (maxLon - minLon).coerceAtLeast(0.000001)
                    fun mapX(lon: Double): Float = ((lon - minLon) / lonSpan * size.width).toFloat()
                    fun mapY(lat: Double): Float = (size.height - ((lat - minLat) / latSpan * size.height)).toFloat()
                    for (i in 1 until points.size) {
                        drawLine(Color(0xFF2E6B4E), Offset(mapX(points[i-1].longitude), mapY(points[i-1].latitude)), Offset(mapX(points[i].longitude), mapY(points[i].latitude)), 6f)
                    }
                    position?.let { drawCircle(Color(0xFF163C2B), 10f, Offset(mapX(it.longitude), mapY(it.latitude))) }
                }
            }
            Text(if (points.size < 2) "Chưa đủ điểm để vẽ đường đã đi" else "${points.size} mốc breadcrumb • hiển thị không cần Internet")
        }
    }
}