package vn.fieldintel.feature.emergency
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.fieldintel.domain.emergency.PackSlot
import vn.fieldintel.domain.emergency.PositionHealth
import vn.fieldintel.domain.emergency.RecoveryState

data class EmergencyUiState(
 val offline:Boolean=true, val packSlot:PackSlot=PackSlot.MINIMAL_UI,
 val positionHealth:PositionHealth=PositionHealth.LOST,
 val latitude:Double?=null, val longitude:Double?=null, val accuracyM:Double?=null,
 val lastReliableLatitude:Double?=null, val lastReliableLongitude:Double?=null,
 val recovery:RecoveryState=RecoveryState.NoActiveIncident
)

@Composable fun EmergencyScreen(state:EmergencyUiState=EmergencyUiState()){
 Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("🚨 SỰ CỐ", style=MaterialTheme.typography.headlineMedium)
  Text(if(state.offline) "OFFLINE CORE • Sẵn sàng cục bộ" else "ONLINE • Emergency Core vẫn ưu tiên cục bộ")
  Text("Protocol: "+state.packSlot.name)
  Text("GNSS: "+state.positionHealth.name+(state.accuracyM?.let{" • ±"+it.toInt()+" m"}?:""))
  if(state.latitude!=null && state.longitude!=null) Text("Vị trí hiện tại: %.5f, %.5f".format(state.latitude,state.longitude))
  else if(state.lastReliableLatitude!=null && state.lastReliableLongitude!=null) Text("Vị trí tin cậy gần nhất: %.5f, %.5f".format(state.lastReliableLatitude,state.lastReliableLongitude))
  else Text("Chưa có vị trí tin cậy")
  when(val r=state.recovery){
   is RecoveryState.ReconfirmRequired -> Text("⚠ Cần xác nhận lại thao tác đang thực hiện trước khi tiếp tục.")
   is RecoveryState.Conflict -> Text("⚠ Dữ liệu phục hồi có xung đột. Không tự suy đoán trạng thái.")
   is RecoveryState.Resumable -> Text("Có sự cố đang hoạt động • revision "+r.lastRevision)
   RecoveryState.NoActiveIncident -> Unit
  }
  listOf("NGƯỜI KHÔNG PHẢN ỨNG","KHÓ THỞ","CHẢY MÁU NẶNG","CHẤN THƯƠNG","CẮN / ĐỐT / NGỘ ĐỘC","TAI NẠN NƯỚC","KHÔNG RÕ TÌNH TRẠNG").forEach{ Button(onClick={},modifier=Modifier.fillMaxWidth()){Text(it)} }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){ OutlinedButton(onClick={}){Text("📍 VỊ TRÍ")}; OutlinedButton(onClick={}){Text("📣 CẦU CỨU")} }
  if(state.packSlot==PackSlot.MINIMAL_UI) Text("Chế độ tối thiểu: không có protocol pack an toàn. Không tự tạo hướng dẫn y khoa.")
 }
}