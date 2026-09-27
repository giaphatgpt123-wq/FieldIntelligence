package vn.fieldintel.feature.emergency

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class ObservationUi(val id:String,val note:String,val createdAt:Long,val photoPath:String)

@Composable
fun RecognitionPanel(imageStatus:String,preview:Bitmap?,saveStatus:String,onPickImage:()->Unit,onCameraImage:()->Unit,onSaveObservation:(String)->Unit){
    var note by remember{mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(30.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF0B2329)),border=BorderStroke(1.dp,Color(0x3345E58C))){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Surface(shape=RoundedCornerShape(16.dp),color=Color(0x263EEA91)){Box(Modifier.size(52.dp),contentAlignment=Alignment.Center){Text("◎",style=MaterialTheme.typography.headlineMedium,color=FieldColors.primary,fontWeight=FontWeight.Black)}}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){Text("QUÉT NHẬN DẠNG",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge,color=Color.White);Text("Chụp rõ mẫu vật • lưu bằng chứng offline",color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}
                    Surface(shape=RoundedCornerShape(999.dp),color=Color(0xFF123A35),border=BorderStroke(1.dp,Color(0x5545E58C))){Text("OFFLINE",Modifier.padding(horizontal=10.dp,vertical=6.dp),color=FieldColors.primary,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)}
                }
                Surface(shape=RoundedCornerShape(26.dp),color=Color(0xFF07181D),border=BorderStroke(1.dp,Color.White.copy(alpha=.10f)),shadowElevation=8.dp){
                    Box(Modifier.fillMaxWidth().height(430.dp).background(Brush.verticalGradient(listOf(Color(0xFF21454A),Color(0xFF10292F),Color(0xFF07181D)))),contentAlignment=Alignment.Center){
                        if(preview!=null){Image(preview.asImageBitmap(),"Ảnh thực địa được chọn",Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0x08000000),Color(0xA8000000)))))}
                        else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Surface(shape=CircleShape,color=Color(0x1F45E58C),border=BorderStroke(1.dp,Color(0x5545E58C))){Box(Modifier.size(92.dp),contentAlignment=Alignment.Center){Text("◎",style=MaterialTheme.typography.displayMedium,color=FieldColors.primary)}}
                            Text("Đưa mẫu vật vào khung",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,color=Color.White)
                            Text("Giữ máy ổn định • đủ sáng • chụp thêm góc khác khi cần",color=Color.White.copy(alpha=.78f),textAlign=TextAlign.Center,modifier=Modifier.padding(horizontal=34.dp))
                        }
                        Canvas(Modifier.fillMaxSize().padding(36.dp)){val c=Color(0xFF65F4A3);val sw=6f;val l=44f;drawLine(c,Offset(0f,0f),Offset(l,0f),sw);drawLine(c,Offset(0f,0f),Offset(0f,l),sw);drawLine(c,Offset(size.width,0f),Offset(size.width-l,0f),sw);drawLine(c,Offset(size.width,0f),Offset(size.width,l),sw);drawLine(c,Offset(0f,size.height),Offset(l,size.height),sw);drawLine(c,Offset(0f,size.height),Offset(0f,size.height-l),sw);drawLine(c,Offset(size.width,size.height),Offset(size.width-l,size.height),sw);drawLine(c,Offset(size.width,size.height),Offset(size.width,size.height-l),sw)}
                        Surface(Modifier.align(Alignment.TopCenter).padding(top=14.dp),RoundedCornerShape(999.dp),Color(0xB70B1F24),border=BorderStroke(1.dp,Color.White.copy(alpha=.12f))){Text(if(preview==null)"SẴN SÀNG CHỤP" else "ẢNH ĐÃ NẠP • CHƯA PHÂN LOẠI",Modifier.padding(horizontal=14.dp,vertical=8.dp),color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelLarge)}
                        Surface(Modifier.align(Alignment.BottomCenter).padding(14.dp),RoundedCornerShape(18.dp),Color(0xD90A2025),border=BorderStroke(1.dp,Color.White.copy(alpha=.10f))){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(if(preview==null)"○" else "●",color=if(preview==null)Color(0xFFFFD166) else FieldColors.primary);Spacer(Modifier.width(8.dp));Text(imageStatus,Modifier.weight(1f),color=Color.White,style=MaterialTheme.typography.bodyMedium)}}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Button(onClick=onCameraImage,modifier=Modifier.weight(1f).heightIn(min=64.dp),shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.buttonColors(containerColor=FieldColors.primary,contentColor=FieldColors.onPrimary)){Text("CHỤP ẢNH",fontWeight=FontWeight.Black)}
                    OutlinedButton(onClick=onPickImage,modifier=Modifier.weight(1f).heightIn(min=64.dp),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Color(0x8845E58C))){Text("CHỌN ẢNH",fontWeight=FontWeight.ExtraBold)}
                }
            }
        }
        Surface(shape=RoundedCornerShape(24.dp),color=Color(0xFF152D31),border=BorderStroke(1.dp,Color(0x443FEA91))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(10.dp),color=Color(0x3324E98A)){Text("KẾT QUẢ",Modifier.padding(horizontal=10.dp,vertical=6.dp),color=FieldColors.primary,fontWeight=FontWeight.Bold)};Spacer(Modifier.weight(1f));Text("CHƯA XÁC ĐỊNH",color=Color(0xFFFFD166),fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium)}
            Text("Chưa có mô hình nhận dạng ảnh đã được kiểm chứng trong bản hiện tại.",fontWeight=FontWeight.Bold,color=Color.White)
            Text("Ảnh này chỉ được dùng làm bằng chứng thực địa và đối chiếu thủ công. Ứng dụng không tự suy ra tên loài, tính ăn được, độc tính hoặc xử trí y khoa.",color=FieldColors.onSurfaceVariant)
        }}
        if(preview!=null) Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102C33)),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("LƯU GHI NHẬN",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium);OutlinedTextField(note,{note=it.take(500)},label={Text("Địa điểm, đặc điểm nhìn thấy hoặc ghi chú")},modifier=Modifier.fillMaxWidth(),minLines=3,shape=RoundedCornerShape(18.dp));Button(onClick={onSaveObservation(note)},enabled=note.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=60.dp),shape=RoundedCornerShape(18.dp)){Text("LƯU OFFLINE",fontWeight=FontWeight.Black)};if(saveStatus.isNotBlank())Text(saveStatus,color=FieldColors.primary,fontWeight=FontWeight.Bold)}
        }
    }
}

@Composable
fun SpeciesLibraryPanel(observations:List<ObservationUi> = emptyList(),onDeleteObservation:(String)->Unit={}){
    val context=LocalContext.current;val uriHandler=LocalUriHandler.current
    val store=remember(context){ScientificLibraryStore(context.applicationContext)};val storeStatus=remember{store.status()}
    val mediaStore=remember(context){ScientificMediaStore(context.applicationContext)}
    val localMediaIds=mediaStore.localMediaRecordIds()
    val localFishWithMedia by produceState(0L,storeStatus.installed,localMediaIds.size){value=if(storeStatus.installed)withContext(Dispatchers.IO){mediaStore.fishWithLocalMediaCount()}else 0L}
    LaunchedEffect(store){repeat(30){if(storeStatus.installed)return@LaunchedEffect;delay(1000L);store.status()}}
    var confirmDelete by remember{mutableStateOf<String?>(null)};var selectedObservation by remember{mutableStateOf<String?>(null)}
    var query by remember{mutableStateOf("")};var group by remember{mutableStateOf("Tất cả")};var selectedCollectionId by remember{mutableStateOf<String?>(null)};var selectedId by remember{mutableStateOf<String?>(null)}
    var visibleFishLimit by remember{mutableIntStateOf(FISH_PAGE_SIZE)}
    LaunchedEffect(query,group,selectedCollectionId){visibleFishLimit=FISH_PAGE_SIZE}

    if(confirmDelete!=null)AlertDialog(onDismissRequest={confirmDelete=null},title={Text("Xóa ghi nhận?")},text={Text("Ảnh và ghi chú sẽ bị xóa khỏi ứng dụng trên máy này.")},confirmButton={TextButton(onClick={val id=confirmDelete;confirmDelete=null;if(id!=null){onDeleteObservation(id);selectedObservation=null}}){Text("XÓA")}},dismissButton={TextButton(onClick={confirmDelete=null}){Text("HỦY")}})
    observations.firstOrNull{it.id==selectedObservation}?.let{observation->ObservationDetail(observation,onBack={selectedObservation=null},onDelete={confirmDelete=observation.id});return}

    val starterSelected=SpeciesCatalog.records.firstOrNull{it.id==selectedId};val externalSelected=selectedId?.let(store::findById);val selected=externalSelected?:starterSelected
    if(selected!=null){Column(verticalArrangement=Arrangement.spacedBy(14.dp)){OutlinedButton(onClick={selectedId=null},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)){Text("←  QUAY LẠI THƯ VIỆN",fontWeight=FontWeight.Bold)};SpeciesDetailCard(selected,uriHandler)};return}

    val selectedCollection=LibraryCollections.byId(selectedCollectionId)
    val curatedResults=selectedCollectionId?.let{id->
        when(id){
            "freshwater-fish"->{
                val starter=SpeciesCatalog.search(query,"Cá nước ngọt")
                val external=if(storeStatus.installed)store.search(query,"Cá nước ngọt",FISH_QUERY_LIMIT).filter{it.id in localMediaIds}else emptyList()
                (external+starter).distinctBy{it.scientificName.lowercase(Locale.ROOT)}
            }
            "wfo-plants"->if(storeStatus.installed&&query.trim().length>=2)store.search(query,"Thực vật",80) else emptyList()
            else->{val needle=query.trim().lowercase(Locale.ROOT);LibraryCollections.recordsFor(id).filter{r->needle.isBlank()||r.vietnameseName.lowercase(Locale.ROOT).contains(needle)||r.scientificName.lowercase(Locale.ROOT).contains(needle)}}
        }
    }.orEmpty()
    val starterResults=remember(query,group){SpeciesCatalog.search(query,group)}
    val groupBrowseAllowed=group=="Cá nước ngọt"
    val externalSearchRequested=selectedCollectionId==null&&storeStatus.installed&&(query.trim().length>=2||groupBrowseAllowed)
    val externalLimit=if(groupBrowseAllowed)FISH_QUERY_LIMIT else 80
    val externalRaw=if(externalSearchRequested)store.search(query,group,externalLimit) else emptyList()
    val externalResults=externalRaw.filter{it.group!="Cá nước ngọt"||it.id in localMediaIds}
    val externalSearching=externalSearchRequested&&store.isSearching(query,group,externalLimit)
    val externalSearchCompleted=externalSearchRequested&&store.isSearchCompleted(query,group,externalLimit)
    val results=if(selectedCollection!=null)curatedResults else if(groupBrowseAllowed)(externalResults+starterResults).distinctBy{it.scientificName.lowercase(Locale.ROOT)} else (starterResults+externalResults).distinctBy{it.scientificName.lowercase(Locale.ROOT)}
    val totalCount=if(storeStatus.installed&&storeStatus.recordCount>0)storeStatus.recordCount else SpeciesCatalog.records.size.toLong()
    val displayedFishCount=if(localFishWithMedia>0)localFishWithMedia else SpeciesCatalog.records.count{it.group=="Cá nước ngọt"}.toLong()
    val fishViewActive=selectedCollectionId=="freshwater-fish"||groupBrowseAllowed
    val visibleResultLimit=if(fishViewActive)visibleFishLimit else 80

    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        LibraryHero(totalCount,storeStatus,localFishWithMedia,observations.size)
        LibraryGroupGrid(group,displayedFishCount){group=it;selectedCollectionId=null}
        LibraryCollectionGrid(selectedCollectionId,displayedFishCount){id->selectedCollectionId=if(selectedCollectionId==id)null else id;group="Tất cả";query=if(selectedCollectionId=="wfo-plants")"Mangifera" else ""}
        if(selectedCollectionId=="wfo-plants"){
            Text("TRA THEO CHI • CHỌN MỘT NHÓM",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Mangifera","Musa","Citrus","Dioscorea","Zingiber","Curcuma","Ficus","Nepenthes").forEach{genus->FilterChip(selected=query.equals(genus,ignoreCase=true),onClick={query=genus},label={Text(genus)})}}
        }

        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF0C272C)),border=BorderStroke(1.dp,Color(0x3345E58C))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(selectedCollection==null)"TRA CỨU KHOA HỌC" else selectedCollection.label.uppercase(Locale.ROOT),fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge);if(selectedCollection!=null)Text(selectedCollection.subtitle,color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)};Surface(shape=RoundedCornerShape(999.dp),color=if(storeStatus.installed)Color(0x263EEA91) else Color(0x332F3436)){Text(if(selectedCollectionId=="wfo-plants")"WFO OFFLINE" else if(selectedCollectionId=="freshwater-fish"&&localFishWithMedia>0)"FISH + ẢNH OFFLINE" else if(selectedCollection!=null)"CURATED" else if(storeStatus.installed)"DATABASE READY" else "STARTER DATA",Modifier.padding(horizontal=10.dp,vertical=6.dp),color=if(storeStatus.installed||selectedCollection!=null)FieldColors.primary else FieldColors.onSurfaceVariant,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)}}
            OutlinedTextField(value=query,onValueChange={query=it.take(120)},label={Text(if(selectedCollectionId=="wfo-plants")"Tên khoa học hoặc chi (từ 2 ký tự)" else if(selectedCollectionId=="freshwater-fish")"Lọc cá theo tên Việt hoặc tên khoa học" else if(selectedCollection!=null)"Lọc trong bộ sưu tập" else if(storeStatus.installed)"Tên Việt hoặc tên khoa học (từ 2 ký tự)" else "Tên Việt hoặc tên khoa học")},leadingIcon={Text("⌕",style=MaterialTheme.typography.headlineSmall)},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp),singleLine=true,shape=RoundedCornerShape(20.dp))
            Text(when{
                selectedCollectionId=="freshwater-fish"&&localFishWithMedia>0->"${formatCount(localFishWithMedia)} hồ sơ cá có ảnh offline đã kiểm SHA/license • duyệt theo lô $FISH_PAGE_SIZE hồ sơ"
                selectedCollectionId=="freshwater-fish"->"Đang dùng ${displayedFishCount} hồ sơ cá lõi; gói cá khoa học có ảnh offline chưa được cài"
                selectedCollectionId=="wfo-plants"->if(storeStatus.installed)"${formatCount(storeStatus.recordCount)} tên phân loại offline • tối đa 80 kết quả mỗi truy vấn" else "Đang nạp dữ liệu WFO; thử mở lại thư viện sau ít phút"
                selectedCollection!=null->"${selectedCollection.recordIds.size} hồ sơ đã gắn nhãn điều hướng • nhãn không thay thế bằng chứng an toàn/công dụng"
                storeStatus.installed->"Đang dùng gói khoa học offline • ${formatCount(storeStatus.recordCount)} hồ sơ taxonomy"
                else->"Chưa cài gói SQLite khoa học lớn • đang dùng ${SpeciesCatalog.records.size} hồ sơ lõi trong APK"
            },color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        }}

        when{
            selectedCollectionId=="freshwater-fish"&&storeStatus.installed&&store.isSearching(query,"Cá nước ngọt",FISH_QUERY_LIMIT)->SearchStatusBanner("ĐANG NẠP CÁ CÓ ẢNH OFFLINE TỪ SQLITE…")
            selectedCollectionId=="wfo-plants"&&!storeStatus.installed->SearchStatusBanner("ĐANG NẠP THƯ VIỆN WFO OFFLINE…")
            selectedCollectionId=="wfo-plants"&&store.isSearching(query,"Thực vật",80)->SearchStatusBanner("ĐANG TÌM TRONG WFO OFFLINE…")
            selectedCollection!=null&&results.isEmpty()->SafetyBanner("${selectedCollection.label}: chưa có hồ sơ đủ điều kiện trong bộ dữ liệu hiện tại. Ứng dụng không tự gắn nhãn y khoa/độc tính chỉ từ taxonomy.")
            selectedCollection!=null->{Text("${selectedCollection.label.uppercase(Locale.ROOT)} • ${results.size}",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium);results.take(visibleResultLimit).forEach{r->SpeciesResultCard(r){selectedId=r.id}};if(fishViewActive)FishLoadMoreButton(visibleFishLimit,results.size){visibleFishLimit=(visibleFishLimit+FISH_PAGE_SIZE).coerceAtMost(FISH_QUERY_LIMIT)}}
            storeStatus.installed&&query.isBlank()&&!groupBrowseAllowed->{SafetyBanner("Các hồ sơ nổi bật dưới đây có tên tiếng Việt. Nhập từ 2 ký tự để tra thêm ${formatCount(storeStatus.recordCount)} tên khoa học offline.");results.take(80).forEach{r->SpeciesResultCard(r){selectedId=r.id}}}
            storeStatus.installed&&query.trim().length<2&&!groupBrowseAllowed->{SafetyBanner("Nhập ít nhất 2 ký tự để tra dữ liệu khoa học. Kết quả tiếng Việt có sẵn bên dưới.");results.take(80).forEach{r->SpeciesResultCard(r){selectedId=r.id}}}
            externalSearching->SearchStatusBanner(if(groupBrowseAllowed)"ĐANG NẠP CÁ CÓ ẢNH OFFLINE TỪ SQLITE…" else "ĐANG TÌM TRONG THƯ VIỆN OFFLINE…")
            externalSearchCompleted&&results.isEmpty()->SafetyBanner("Không tìm thấy hồ sơ phù hợp. Không tìm thấy không đồng nghĩa mẫu vật an toàn hoặc không tồn tại.")
            results.isEmpty()->SafetyBanner("Không tìm thấy hồ sơ phù hợp. Không tìm thấy trong dữ liệu không đồng nghĩa mẫu vật an toàn hoặc không tồn tại.")
            else->{Text("KẾT QUẢ • ${results.size}",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium);results.take(visibleResultLimit).forEach{r->SpeciesResultCard(r){selectedId=r.id}};if(fishViewActive)FishLoadMoreButton(visibleFishLimit,results.size){visibleFishLimit=(visibleFishLimit+FISH_PAGE_SIZE).coerceAtMost(FISH_QUERY_LIMIT)}}
        }
        HorizontalDivider();ObservationSection(observations){selectedObservation=it};DataProvenanceCard(storeStatus,localFishWithMedia)
    }
}

@Composable
private fun FishLoadMoreButton(shown:Int,total:Int,onMore:()->Unit){
    if(shown>=total)return
    OutlinedButton(onClick=onMore,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0x8845E58C))){
        Text("HIỆN THÊM ${minOf(FISH_PAGE_SIZE,total-shown)} • ĐÃ HIỆN ${minOf(shown,total)}/$total",fontWeight=FontWeight.Black)
    }
}

@Composable
private fun LibraryHero(totalCount:Long,status:ScientificLibraryStatus,localFishWithMedia:Long,observationCount:Int){
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(30.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent),border=BorderStroke(1.dp,Color(0x3345E58C))){Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF123E37),Color(0xFF102A31),Color(0xFF071A20)))).padding(20.dp)){
        Canvas(Modifier.matchParentSize()){val c=Color(0x2245E58C);drawCircle(c,radius=size.minDimension*.42f,center=Offset(size.width*.86f,size.height*.18f));drawCircle(Color(0x1139C6B0),radius=size.minDimension*.28f,center=Offset(size.width*.72f,size.height*.88f))}
        Column(verticalArrangement=Arrangement.spacedBy(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(16.dp),color=Color(0x263EEA91)){Text("◈",Modifier.padding(13.dp),color=FieldColors.primary,style=MaterialTheme.typography.headlineMedium)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("THƯ VIỆN KHOA HỌC",fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineSmall);Text("Taxonomy • media • nguồn • cảnh báo • ghi nhận",color=Color.White.copy(alpha=.72f))};Surface(shape=RoundedCornerShape(999.dp),color=Color(0xB7123932),border=BorderStroke(1.dp,Color(0x5545E58C))){Text("OFFLINE",Modifier.padding(horizontal=10.dp,vertical=6.dp),color=FieldColors.primary,fontWeight=FontWeight.Black)}}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){MetricBox(formatCount(totalCount),"HỒ SƠ",Modifier.weight(1f));MetricBox(if(localFishWithMedia>0)formatCount(localFishWithMedia) else "—","CÁ + ẢNH",Modifier.weight(1f));MetricBox(observationCount.toString(),"GHI NHẬN",Modifier.weight(1f))}
            Text(if(status.installed)"Gói khoa học lớn đã sẵn sàng tra cứu trên thiết bị." else "Gói khoa học lớn chưa được cài; ứng dụng đang dùng bộ lõi.",color=Color.White.copy(alpha=.86f),fontWeight=FontWeight.Bold)
        }
    }}
}

@Composable private fun MetricBox(value:String,label:String,modifier:Modifier=Modifier){Surface(modifier,RoundedCornerShape(18.dp),Color(0x8F0A2025),border=BorderStroke(1.dp,Color.White.copy(alpha=.09f))){Column(Modifier.padding(vertical=12.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(value,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium,color=FieldColors.primary);Text(label,style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.65f))}}}

@Composable
private fun LibraryGroupGrid(selected:String,fishCount:Long,onSelect:(String)->Unit){
    val items=listOf(Triple("Tất cả","◈","Tất cả nguồn"),Triple("Thực vật","🌿","Cây • cỏ • taxonomy"),Triple("Động vật","🐾","Động vật hoang dã"),Triple("Cá nước ngọt","🐟","${formatCount(fishCount)} hồ sơ sẵn sàng"),Triple("Côn trùng","🐝","Côn trùng • chân khớp"),Triple("Nấm","🍄","Nấm • taxonomy"))
    Text("PHÂN LOẠI NHANH",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium)
    items.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{(name,icon,subtitle)->Card(onClick={onSelect(name)},modifier=Modifier.weight(1f).heightIn(min=112.dp),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=if(selected==name)Color(0xFF17473E) else Color(0xFF102C33)),border=BorderStroke(1.dp,if(selected==name)Color(0x7745E58C) else Color.White.copy(alpha=.06f))){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(icon,style=MaterialTheme.typography.headlineMedium);Text(name,fontWeight=FontWeight.Black);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=FieldColors.onSurfaceVariant)}}};if(row.size==1)Spacer(Modifier.weight(1f))}}
}

@Composable
private fun LibraryCollectionGrid(selectedId:String?,fishCount:Long,onSelect:(String)->Unit){
    Text("BỘ SƯU TẬP CHUYÊN SÂU",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium);Text("Nhãn chuyên sâu chỉ lấy từ lớp dữ liệu đã được gắn rõ; taxonomy toàn cầu không tự suy ra công dụng hoặc độc tính.",color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
    LibraryCollections.items.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{item->val isSelected=selectedId==item.id;val badge=when{item.id=="wfo-plants"->"WFO";item.id=="freshwater-fish"->formatCount(fishCount);else->item.recordIds.size.toString()};Card(onClick={onSelect(item.id)},modifier=Modifier.weight(1f).heightIn(min=124.dp),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=if(isSelected)Color(0xFF3B3725) else Color(0xFF102C33)),border=BorderStroke(1.dp,if(isSelected)Color(0x88FFC857) else Color.White.copy(alpha=.06f))){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(item.icon,style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.weight(1f));Surface(shape=RoundedCornerShape(999.dp),color=Color(0x221FFFFFF)){Text(badge,Modifier.padding(horizontal=8.dp,vertical=4.dp),fontWeight=FontWeight.Black,color=FieldColors.primary)}};Text(item.label,fontWeight=FontWeight.Black);Text(item.subtitle,style=MaterialTheme.typography.bodySmall,color=FieldColors.onSurfaceVariant,maxLines=2)}}};if(row.size==1)Spacer(Modifier.weight(1f))}}
}

@Composable private fun SpeciesResultCard(record:SpeciesRecord,onClick:()->Unit){Card(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=94.dp),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF13323A)),border=BorderStroke(1.dp,Color.White.copy(alpha=.05f))){Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(16.dp),color=Color(0x2245E58C)){Text(groupIcon(record.group),Modifier.padding(12.dp),style=MaterialTheme.typography.titleLarge)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(record.vietnameseName,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium,maxLines=1);if(record.vietnameseName!=record.scientificName)Text(record.scientificName,color=FieldColors.primary,maxLines=1);Text(record.group+" • "+record.sourceName,color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,maxLines=1)};Text("›",style=MaterialTheme.typography.headlineSmall,color=FieldColors.primary)}}}

@Composable
private fun SpeciesDetailCard(selected:SpeciesRecord,uriHandler:androidx.compose.ui.platform.UriHandler){
    val context=LocalContext.current
    val localMedia by produceState<List<ScientificLocalMedia>>(emptyList(),selected.id){value=withContext(Dispatchers.IO){ScientificMediaStore(context.applicationContext).loadForRecord(selected.id,3)}}
    val collections=LibraryCollections.items.filter{selected.id in it.recordIds}
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102C33)),border=BorderStroke(1.dp,Color(0x3345E58C))){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(shape=RoundedCornerShape(20.dp),color=Color(0x2245E58C)){Text(groupIcon(selected.group),Modifier.padding(16.dp),style=MaterialTheme.typography.headlineLarge)};Text(selected.vietnameseName,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text(selected.scientificName,fontWeight=FontWeight.Bold,color=FieldColors.primary,style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){InfoChip(selected.group);InfoChip(if(localMedia.isNotEmpty())"ẢNH OFFLINE" else if(selected.sourceScope.contains("Ảnh tham chiếu có license"))"MEDIA METADATA" else "TAXONOMY")}
        ScientificMediaGallery(localMedia,uriHandler)
        if(collections.isNotEmpty()){Text("BỘ SƯU TẬP",fontWeight=FontWeight.Black);collections.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{InfoChip(it.label)}}};Text("Các nhãn trên dùng để điều hướng thư viện, không phải bằng chứng về công dụng, ăn được hoặc độc tính.",color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
        HorizontalDivider();Text("NGUỒN KHOA HỌC & MEDIA",fontWeight=FontWeight.Black);Text(selected.sourceName,fontWeight=FontWeight.Bold);Text(selected.sourceScope,color=FieldColors.onSurfaceVariant);InteractionSafetyPanel(selected.scientificName)
        if(selected.sourceUrl.isNotBlank())OutlinedButton(onClick={uriHandler.openUri(selected.sourceUrl)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)){Text("MỞ NGUỒN / MEDIA KHI CÓ MẠNG",fontWeight=FontWeight.Bold)}
        SafetyBanner("Ảnh tham chiếu và taxonomy không tự chứng minh mẫu vật trong ảnh người dùng, tính ăn được, độc tính, dược tính hoặc liều dùng.")
    }}
}

@Composable
private fun ScientificMediaGallery(media:List<ScientificLocalMedia>,uriHandler:androidx.compose.ui.platform.UriHandler){
    if(media.isEmpty())return
    Text("HÌNH ẢNH THAM CHIẾU OFFLINE • ${media.size}",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium)
    media.forEachIndexed{index,item->
        val bitmap by produceState<Bitmap?>(null,item.sha256){value=withContext(Dispatchers.IO){BitmapFactory.decodeByteArray(item.bytes,0,item.bytes.size)}}
        if(bitmap!=null){Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF071A20)),border=BorderStroke(1.dp,Color.White.copy(alpha=.08f))){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Image(bitmap!!.asImageBitmap(),"Ảnh tham chiếu offline ${index+1}",Modifier.fillMaxWidth().heightIn(min=220.dp,max=360.dp),contentScale=ContentScale.Fit);Column(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("Ảnh ${index+1} • ${item.license}",fontWeight=FontWeight.Bold,color=FieldColors.primary);val host=remember(item.sourceIdentifier){runCatching{URI(item.sourceIdentifier).host}.getOrNull().orEmpty()};if(host.isNotBlank())Text("Nguồn media: $host",color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall);if(item.sourceIdentifier.startsWith("https://"))TextButton(onClick={uriHandler.openUri(item.sourceIdentifier)}){Text("MỞ NGUỒN ẢNH KHI CÓ MẠNG")}}}}}
    }
}

@Composable private fun InfoChip(text:String){Surface(shape=RoundedCornerShape(999.dp),color=Color(0x263EEA91)){Text(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),color=FieldColors.primary,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)}}

@Composable private fun ObservationSection(observations:List<ObservationUi>,onOpen:(String)->Unit){Text("GHI NHẬN THỰC ĐỊA",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium);if(observations.isEmpty())Surface(shape=RoundedCornerShape(18.dp),color=Color(0xFF102A31)){Text("Chưa lưu mẫu quan sát.",Modifier.fillMaxWidth().padding(18.dp),color=FieldColors.onSurfaceVariant)};observations.take(20).forEach{record->Card(onClick={onOpen(record.id)},modifier=Modifier.fillMaxWidth().heightIn(min=82.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF13323A))){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=Color(0x2245E58C)){Text("◎",Modifier.padding(10.dp),color=FieldColors.primary)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(record.note,fontWeight=FontWeight.Bold,maxLines=2);Text(SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date(record.createdAt))+" • CHƯA XÁC ĐỊNH",color=FieldColors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)};Text("›",style=MaterialTheme.typography.headlineSmall,color=FieldColors.primary)}}}}

@Composable private fun ObservationDetail(observation:ObservationUi,onBack:()->Unit,onDelete:()->Unit){Column(verticalArrangement=Arrangement.spacedBy(14.dp)){OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)){Text("←  QUAY LẠI GHI NHẬN",fontWeight=FontWeight.Bold)};Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102C33))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(observation.note,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);val thumbnail=remember(observation.photoPath){val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(observation.photoPath,bounds);val options=BitmapFactory.Options().apply{inSampleSize=generateSequence(1){it*2}.first{s->maxOf(bounds.outWidth,bounds.outHeight)/s<=768}};BitmapFactory.decodeFile(observation.photoPath,options)};if(thumbnail!=null)Image(thumbnail.asImageBitmap(),"Ảnh ghi nhận offline",Modifier.fillMaxWidth().heightIn(min=280.dp,max=420.dp),contentScale=ContentScale.Fit);SafetyBanner("CHƯA XÁC ĐỊNH • ghi chú người dùng • chưa xác minh");OutlinedButton(onClick=onDelete,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)){Text("Xóa ghi nhận")}}}}}

@Composable
private fun DataProvenanceCard(status:ScientificLibraryStatus,localFishWithMedia:Long){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF0D242A))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("NGUỒN & TÍNH TOÀN VẸN",fontWeight=FontWeight.Black);if(status.installed){Text("Scientific library • ${status.sourceVersion.ifBlank{"offline"}}",fontWeight=FontWeight.Bold);if(status.sourceLicense.isNotBlank())Text("License: ${status.sourceLicense}",color=FieldColors.onSurfaceVariant);if(status.sourceDoi.isNotBlank())Text("DOI: ${status.sourceDoi}",color=FieldColors.onSurfaceVariant);Text("Phạm vi: ${status.scope}. Taxonomy/media tách khỏi lớp y khoa, độc tính và thực phẩm.",color=FieldColors.onSurfaceVariant);if(status.fishTaxa>0)Text("Cá: ${formatCount(localFishWithMedia)} có ảnh offline / ${formatCount(status.fishTaxa)} taxon • ${formatCount(status.fishPendingMedia)} đang chờ media nguồn",color=FieldColors.primary,fontWeight=FontWeight.Bold)}else Text("Chưa cài gói SQLite khoa học ngoài APK. Bộ lõi vẫn giữ nguồn riêng theo từng hồ sơ.",color=FieldColors.onSurfaceVariant)}}}

@Composable private fun SearchStatusBanner(text:String){Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFF12323A),border=BorderStroke(1.dp,Color(0x3345E58C))){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(modifier=Modifier.size(22.dp),strokeWidth=2.dp,color=FieldColors.primary);Text(text,color=FieldColors.primary,fontWeight=FontWeight.Bold)}}}
@Composable private fun SafetyBanner(text:String){Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFF2E2520),border=BorderStroke(1.dp,Color(0x33FFC857))){Text(text,Modifier.fillMaxWidth().padding(14.dp),color=Color(0xFFFFC857),fontWeight=FontWeight.Medium)}}
private fun formatCount(value:Long):String=when{value>=1_000_000->String.format(Locale.US,"%.2fM",value/1_000_000.0);value>=1_000->String.format(Locale.US,"%.1fK",value/1_000.0);else->value.toString()}
private fun groupIcon(group:String):String=when(group){"Thực vật"->"🌿";"Động vật"->"🐾";"Cá nước ngọt"->"🐟";"Côn trùng"->"🐝";"Nấm"->"🍄";else->"◈"}
private const val FISH_QUERY_LIMIT=1000
private const val FISH_PAGE_SIZE=80
