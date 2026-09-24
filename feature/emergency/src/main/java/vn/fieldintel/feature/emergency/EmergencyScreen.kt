package vn.fieldintel.feature.emergency

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class AppSection(val label:String,val icon:String){
 FIELD("THỰC ĐỊA","🗺"), RECOGNITION("NHẬN DẠNG","🔎"), SURVIVAL("SINH TỒN","🧭"),
 EMERGENCY("SỰ CỐ","🚨"), PREP("CHUẨN BỊ","🎒"), LIBRARY("THƯ VIỆN","📚"), TRAINING("HUẤN LUYỆN","🎓")
}

@Composable fun EmergencyScreen(){ FieldIntelligenceHome() }

@Composable fun FieldIntelligenceHome(){
 var selected by remember { mutableStateOf<AppSection?>(null) }
 if(selected!=null){ SectionScreen(selected!!){ selected=null }; return }
 Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("FIELD INTELLIGENCE",style=MaterialTheme.typography.headlineMedium)
  Text("Offline-first • Dữ liệu cục bộ • Emergency luôn sẵn sàng")
  HorizontalDivider()
  AppSection.entries.forEach { section ->
   Button(onClick={selected=section},modifier=Modifier.fillMaxWidth()){ Text(section.icon+"  "+section.label) }
  }
  Spacer(Modifier.weight(1f))
  Text("GNSS: CHƯA XÁC ĐỊNH   •   OFFLINE CORE: READY",style=MaterialTheme.typography.labelMedium)
 }
}
@Composable fun SectionScreen(section:AppSection,onBack:()->Unit){
 Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  OutlinedButton(onClick=onBack){Text("← QUAY LẠI")}
  Text(section.icon+"  "+section.label,style=MaterialTheme.typography.headlineMedium)
  when(section){
   AppSection.FIELD -> { Text("Vị trí • Track • Breadcrumb • Ghi nhận thực địa"); Button(onClick={},modifier=Modifier.fillMaxWidth()){Text("BẮT ĐẦU GHI NHẬN")}}
   AppSection.RECOGNITION -> { Text("Ảnh → đối tượng sinh học → bằng chứng → giả thuyết"); Button(onClick={},modifier=Modifier.fillMaxWidth()){Text("CHỤP / CHỌN ẢNH")}; Text("UNKNOWN là kết quả hợp lệ.")}
   AppSection.SURVIVAL -> { Text("Điều hướng • Nước • Nơi trú • Tín hiệu • Đánh giá rủi ro"); Text("Không suy diễn đường an toàn từ track.")}
   AppSection.EMERGENCY -> { Text("Emergency Core hoạt động độc lập, ưu tiên offline."); listOf("NGƯỜI KHÔNG PHẢN ỨNG","KHÓ THỞ","CHẢY MÁU NẶNG","CHẤN THƯƠNG","KHÔNG RÕ TÌNH TRẠNG").forEach{Button(onClick={},modifier=Modifier.fillMaxWidth()){Text(it)}} }
   AppSection.PREP -> Text("Checklist chuyến đi • thiết bị • nước • pin • bản đồ offline")
   AppSection.LIBRARY -> Text("Tra cứu taxonomy • morphology • diagnostic • confusion • evidence")
   AppSection.TRAINING -> Text("LEARN mode • tình huống mô phỏng • kiểm tra kiến thức")
  }
 }
}