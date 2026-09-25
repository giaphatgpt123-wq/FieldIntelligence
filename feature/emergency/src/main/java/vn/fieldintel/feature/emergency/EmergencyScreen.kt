package vn.fieldintel.feature.emergency

import androidx.compose.foundation.layout.*
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

@Composable fun EmergencyScreen(){ FieldIntelligenceHome() }

@Composable fun FieldIntelligenceHome(){
 var selected by remember{mutableStateOf<AppSection?>(null)}
 if(selected!=null){SectionScreen(selected!!){selected=null};return}
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

@Composable fun SectionScreen(section:AppSection,onBack:()->Unit){
 Scaffold(bottomBar={BottomBar()}){pad->Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("←")};Text(section.icon+"  "+section.label,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
  Text(section.subtitle)
  when(section){
   AppSection.FIELD->{StatusCard("📍 Vị trí hiện tại","Đang chờ dữ liệu GNSS thực");Action("▶  GHI HÀNH TRÌNH");Action("📌  ĐÁNH DẤU VỊ TRÍ");Action("📏  ĐO KHOẢNG CÁCH")}
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