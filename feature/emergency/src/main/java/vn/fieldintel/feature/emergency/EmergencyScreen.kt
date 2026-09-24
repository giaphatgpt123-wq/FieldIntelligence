package vn.fieldintel.feature.emergency
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun EmergencyScreen(){
 Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("🚨 SỰ CỐ", style=MaterialTheme.typography.headlineMedium)
  Text("Offline core • GNSS/độ chính xác sẽ hiển thị tại đây")
  listOf("NGƯỜI KHÔNG PHẢN ỨNG","KHÓ THỞ","CHẢY MÁU NẶNG","CHẤN THƯƠNG","CẮN / ĐỐT / NGỘ ĐỘC","TAI NẠN NƯỚC","KHÔNG RÕ TÌNH TRẠNG").forEach { Button(onClick={}, modifier=Modifier.fillMaxWidth()){ Text(it) } }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){ OutlinedButton(onClick={}){Text("📍 VỊ TRÍ")}; OutlinedButton(onClick={}){Text("📣 CẦU CỨU")} }
 }
}
