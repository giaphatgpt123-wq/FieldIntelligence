package vn.fieldintel.feature.emergency

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class AppSection(val label:String,val icon:String){
 FIELD("THỰC ĐỊA","🗺"), RECOGNITION("NHẬN DẠNG","🔎"), SURVIVAL("SINH TỒN","🧭"),
 EMERGENCY("SỰ CỐ","🚨"), PREP("CHUẨN BỊ","🎒"), LIBRARY("THƯ VIỆN","📚"), TRAINING("HUẤN LUYỆN","🎓")
}

@Composable fun EmergencyScreen(){ FieldIntelligenceHome() }

@Composable fun FieldIntelligenceHome(){
 Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("FIELD INTELLIGENCE",style=MaterialTheme.typography.headlineMedium)
  Text("Offline-first • Dữ liệu cục bộ • Emergency luôn sẵn sàng")
  HorizontalDivider()
  AppSection.entries.forEach { section ->
   Button(onClick={},modifier=Modifier.fillMaxWidth()){ Text(section.icon+"  "+section.label) }
  }
  Spacer(Modifier.weight(1f))
  Text("GNSS: CHƯA XÁC ĐỊNH   •   OFFLINE CORE: READY",style=MaterialTheme.typography.labelMedium)
 }
}