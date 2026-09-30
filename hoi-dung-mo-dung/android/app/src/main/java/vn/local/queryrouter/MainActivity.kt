package vn.local.queryrouter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.local.queryrouter.core.*
import vn.local.queryrouter.storage.*

private val Blue = Color(0xFF2F6BFF)
private val Navy = Color(0xFF17315E)
private val Soft = Color(0xFFF5F8FF)
private val Muted = Color(0xFF68758B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainApp() }
    }
}

@Composable
private fun MainApp() {
    var tab by remember { mutableIntStateOf(0) }
    var lastResult by remember { mutableStateOf<QueryResult?>(null) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Soft, surface = Color.White)) {
        Scaffold(
            containerColor = Soft,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("Tra cứu","Kết quả","Cá nhân","Ứng dụng","Danh bạ","Cài đặt").forEachIndexed { index, label ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text((index + 1).toString(), fontSize = 10.sp) }, label = { Text(label, fontSize = 9.sp, maxLines = 1) })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> SearchScreen { lastResult = it }
                    1 -> ResultScreen(lastResult)
                    2 -> PersonalScreen()
                    3 -> AppsScreen()
                    4 -> ContactsScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 27.sp)
        subtitle?.let { Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun SearchScreen(onResolved: (QueryResult) -> Unit) {
    val context = LocalContext.current
    val custom = remember { CustomAppStore(context) }
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<QueryResult?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Hỏi đúng – Mở đúng", "Bạn muốn làm gì?")
        Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Nhập nội dung cần tra cứu…") }, singleLine = true)
                Button(onClick = { result = LocalQueryEngine.resolve(query, custom.functions()); result?.let(onResolved) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(16.dp)) { Text("TRA CỨU") }
            }
        }
        result?.let { ResultCard(it) }
    }
}

@Composable
private fun ResultCard(result: QueryResult) {
    Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(when(result.confidence){ ConfidenceState.CONFIRMED->"Đã xác định"; ConfidenceState.NEED_MORE_INFO->"Cần thêm thông tin"; ConfidenceState.AMBIGUOUS->"Có nhiều khả năng"; else->"Chưa xác định" }, color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            result.function?.let { Text(it.title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp)); Text("${it.field} • ${it.service}", color = Muted, fontSize = 12.sp) }
            Text(result.shortAnswer, color = Navy, modifier = Modifier.padding(top = 10.dp))
            result.clarification?.let { Text(it, color = Muted, modifier = Modifier.padding(top = 8.dp)) }
            result.sourceRefs.forEach { Text("Căn cứ: ${it.title} — ${it.reference}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun ResultScreen(result: QueryResult?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Kết quả", "Kết luận ngắn → điều kiện → căn cứ → hành động")
        if (result == null) Card(Modifier.padding(16.dp).fillMaxWidth()) { Text("Chưa có kết quả tra cứu.", modifier = Modifier.padding(18.dp), color = Muted) } else ResultCard(result)
    }
}

@Composable
private fun PersonalScreen() {
    val context = LocalContext.current
    val vault = remember { PersonalVault(context) }
    var items by remember { mutableStateOf(vault.list()) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Cá nhân", "Dữ liệu chỉ lưu trên thiết bị")
        Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Tên") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(content, { content = it }, label = { Text("Nội dung") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Button(onClick = { if(title.isNotBlank() && content.isNotBlank()){ vault.add(title, content, "Ghi chú"); items = vault.list(); title=""; content="" } }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("LƯU") }
            }
        }
        items.forEach { item -> Card(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(item.title, fontWeight = FontWeight.Bold, color = Navy); Text(item.content, color = Muted) } } }
    }
}

@Composable
private fun AppsScreen() {
    val context = LocalContext.current
    val store = remember { CustomAppStore(context) }
    var apps by remember { mutableStateOf(store.apps()) }
    var name by remember { mutableStateOf("") }; var field by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var pkg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Quản lý ứng dụng", "Thêm app và mô tả chức năng để tra cứu cục bộ")
        Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            OutlinedTextField(name,{name=it},label={Text("Tên ứng dụng")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(field,{field=it},label={Text("Lĩnh vực")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(description,{description=it},label={Text("Mô tả / chức năng")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(pkg,{pkg=it},label={Text("Package Android")},modifier=Modifier.fillMaxWidth())
            Button(onClick={ if(name.isNotBlank()){ store.add(name, field.ifBlank{"Khác"}, description, name, description, pkg, RouteType.PACKAGE, pkg); apps=store.apps(); name=""; field=""; description=""; pkg="" } },modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Text("THÊM ỨNG DỤNG")}
        } }
        (LocalKnowledge.apps + apps).forEach { app -> Card(Modifier.padding(horizontal=16.dp,vertical=5.dp).fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(app.name,fontWeight=FontWeight.Bold,color=Navy);Text(app.field,color=Blue,fontSize=12.sp);Text(app.description,color=Muted,fontSize=13.sp)}} }
    }
}

@Composable
private fun ContactsScreen() {
    val context = LocalContext.current
    val store = remember { ContactStore(context) }
    var contacts by remember { mutableStateOf(store.list()) }
    var name by remember { mutableStateOf("") }; var position by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Danh bạ", "Liên hệ báo vụ việc, phản ánh và công việc cần thiết")
        Card(Modifier.padding(horizontal=16.dp).fillMaxWidth()){Column(Modifier.padding(16.dp)){
            OutlinedTextField(name,{name=it},label={Text("Họ và tên (Đơn vị)")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(position,{position=it},label={Text("Chức vụ")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(phone,{phone=it},label={Text("Số điện thoại")},modifier=Modifier.fillMaxWidth())
            Button(onClick={if(name.isNotBlank() && phone.isNotBlank()){store.add(name,position,phone);contacts=store.list();name="";position="";phone=""}},modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Text("THÊM LIÊN HỆ")}
        }}
        contacts.forEach { item -> Card(Modifier.padding(horizontal=16.dp,vertical=5.dp).fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(item.nameOrUnit,fontWeight=FontWeight.Bold,color=Navy);Text(item.position,color=Muted);Text(item.phone,color=Blue);Button(onClick={ val s=item.phone.filter{it.isDigit()||it=='+'}; if(s.isNotBlank()) context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$s"))) },modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("GỌI")}}} }
    }
}

@Composable
private fun SettingsScreen() {
    val h = LibraryInspector.health()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Cài đặt")
        Card(Modifier.padding(16.dp).fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Tình trạng thư viện ẩn",fontWeight=FontWeight.Bold,color=Navy);Text("${h.fieldCount} lĩnh vực • ${h.functionCount} chức năng • ${h.sourceCount} nhóm nguồn",color=Muted);Text("${h.verifiedRouteCount} đường gọi đã xác minh",color=Muted)}}
        Card(Modifier.padding(horizontal=16.dp).fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color(0xFFEAF8EF))){Text("Không khai báo INTERNET hoặc CALL_PHONE. Không cloud, analytics hay telemetry.",modifier=Modifier.padding(16.dp),color=Color(0xFF12663B),fontWeight=FontWeight.Bold)}
    }
}
