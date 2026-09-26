package vn.fieldintel.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.fieldintel.feature.emergency.EmergencyScreen
import vn.fieldintel.feature.emergency.FieldPositionUi
import vn.fieldintel.feature.emergency.OfflineMapPointUi
import vn.fieldintel.feature.emergency.OfflineMapLineUi
import vn.fieldintel.feature.emergency.OfflineMapPolygonUi
import vn.fieldintel.feature.emergency.ObservationUi
import vn.fieldintel.feature.emergency.ScientificLibraryImportManager
import vn.fieldintel.feature.emergency.ScientificLibraryStore

enum class TrackSessionState { IDLE, RECORDING, PAUSED, FINISHED }

class MainActivity:ComponentActivity(){
 private var latestFix by mutableStateOf<FieldFix?>(null)
 private lateinit var location:FieldLocationController
 private lateinit var tracks:FieldTrackStore
 private lateinit var mapPack:OfflineMapPack
 private lateinit var updates:DataUpdateManager
 private lateinit var observationStore:ObservationStore
 private lateinit var scientificImporter:ScientificLibraryImportManager
 private var observations by mutableStateOf(emptyList<ObservationUi>())
 private var observationSaveStatus by mutableStateOf("")
 private var updateStatus by mutableStateOf("Sẵn sàng")
 private var updateBusy=false
 private var scientificImportBusy=false
 private var activeMapRegionId:String?=null
 private var recording by mutableStateOf(false)
 private var trackSessionState by mutableStateOf(TrackSessionState.IDLE)
 private var trackCount by mutableIntStateOf(0)
 private var trackDistanceM by mutableDoubleStateOf(0.0)
 private var trackStartedAt by mutableStateOf<Long?>(null)
 private var trackBack by mutableStateOf(TrackBackState(null,0.0,null))
 private var mapPackState by mutableStateOf(OfflineMapPackState(false,0,0))
 private var mapPoints by mutableStateOf(emptyList<OfflineMapPointUi>())
 private var mapLines by mutableStateOf(emptyList<OfflineMapLineUi>())
 private var mapPolygons by mutableStateOf(emptyList<OfflineMapPolygonUi>())
 private var mapCredit by mutableStateOf<String?>(null)
 private var mapCoverage by mutableStateOf<String?>(null)
 private var imageStatus by mutableStateOf("Chưa chọn ảnh. Kết quả: CHƯA XÁC ĐỊNH.")
 private var imagePreview by mutableStateOf<Bitmap?>(null)
 private var listener:android.location.LocationListener?=null
 private val sessionPrefs by lazy { getSharedPreferences("field-session", MODE_PRIVATE) }
 private val selectPhoto=registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if(uri!=null) { imagePreview=readPreview(uri); imageStatus=if(imagePreview!=null) "Đã chọn ảnh; xem mẫu bên dưới. Chưa phân tích tự động." else "Không thể mở ảnh này; hãy chọn ảnh khác." } }
 private val takePhoto=registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> if(bitmap!=null) { imagePreview=bitmap; imageStatus="Đã chụp ảnh; xem mẫu bên dưới. Chưa phân tích tự động." } }
 private val selectScientificBundle=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) importScientificBundle(uri) }
 private fun readPreview(uri:Uri):Bitmap? = runCatching {
  val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
  contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
  require(bounds.outWidth>0 && bounds.outHeight>0)
  val options=BitmapFactory.Options().apply { inSampleSize=generateSequence(1) { it*2 }.first { sample -> maxOf(bounds.outWidth,bounds.outHeight)/sample<=1024 }; inPreferredConfig=Bitmap.Config.RGB_565 }
  contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
 }.getOrNull()
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted) startGnss()}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val db=EmergencyBootstrap.database(this); val recovery=EmergencyBootstrap.recovery(this,db); lifecycleScope.launch{recovery.recover()}
  location=FieldLocationController(this); mapPack=OfflineMapPack(this); updates=DataUpdateManager(this); observationStore=ObservationStore(this); scientificImporter=ScientificLibraryImportManager(applicationContext); lifecycleScope.launch {
   val result=withContext(Dispatchers.IO){ runCatching { scientificImporter.installBundledIfMissing() } }
   result.onSuccess { installed -> if(installed!=null) updateStatus=installed.message+" • mở Thư viện để tra cứu" }
     .onFailure { updateStatus="Chưa nạp được thư viện tích hợp: "+(it.message?:"không rõ lỗi") }
  }; observations=observationStore.load(); recoverMapSwapIfNeeded(); mapPackState=mapPack.state(); loadMapRegion(mapPack.regions().firstOrNull()); mapCoverage=if(mapPackState.available) "Đang hiển thị vùng bản đồ đã nạp; chờ GPS để xác định vị trí." else null; tracks=FieldTrackStore(this); val initial=tracks.summary(); trackCount=initial.points; trackDistanceM=initial.distanceM; trackStartedAt=initial.startedAt ?: sessionPrefs.getLong("trackStartedAt",0L).takeIf{it>0L}; trackSessionState=runCatching{TrackSessionState.valueOf(sessionPrefs.getString("trackState",null)?:if(sessionPrefs.getBoolean("recording",false)) "RECORDING" else if(initial.points>0) "PAUSED" else "IDLE")}.getOrDefault(TrackSessionState.IDLE); recording=trackSessionState==TrackSessionState.RECORDING
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGnss() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
  setContent { EmergencyScreen(latestFix?.let{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)},recording,trackCount,trackDistanceM,trackStartedAt,trackBack.remainingM,trackBack.bearingDeg,trackBack.offTrackM,trackBack.breadcrumb.size,trackBack.breadcrumb.map{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)},mapPackState.available,mapPackState.fileCount,mapPackState.bytes,mapPoints,mapLines,mapPolygons,mapCredit,{ if(!recording && trackCount==0) trackStartedAt=System.currentTimeMillis(); recording=!recording; trackSessionState=if(recording) TrackSessionState.RECORDING else TrackSessionState.PAUSED; sessionPrefs.edit().putBoolean("recording",recording).putString("trackState",trackSessionState.name).putLong("trackStartedAt",trackStartedAt?:0L).apply() },{ recording=false; trackSessionState=TrackSessionState.FINISHED; sessionPrefs.edit().putBoolean("recording",false).putString("trackState",trackSessionState.name).apply() },{ recording=false; trackSessionState=TrackSessionState.IDLE; tracks.clear(); trackCount=0; trackDistanceM=0.0; trackStartedAt=null; trackBack=TrackBackState(null,0.0,null); sessionPrefs.edit().clear().putString("trackState",TrackSessionState.IDLE.name).apply() },updateStatus,{ checkConfiguredDataUpdate() },{ rollbackDataUpdate() },mapCoverage,imageStatus,imagePreview,{selectPhoto.launch("image/*")},{takePhoto.launch(null)},observations,observationSaveStatus,{ note -> val bitmap=imagePreview; observationSaveStatus=if(bitmap==null) "Chưa có ảnh để lưu." else runCatching { observationStore.save(bitmap,note); observations=observationStore.load(); "Đã lưu ảnh và ghi chú trong máy • chưa xác định loài." }.getOrElse { "Lưu thất bại: "+(it.message?:"không rõ lỗi") } },{ id -> runCatching { observationStore.delete(id); observations=observationStore.load() }.onFailure { observationSaveStatus="Xóa thất bại: "+(it.message?:"không rõ lỗi") } }) }
 }
 override fun onStart(){ super.onStart(); if(::location.isInitialized && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGnss() }
 override fun onStop(){ listener?.let{location.stop(it)}; listener=null; super.onStop() }
 private fun loadMapRegion(region:OfflineMapRegion?) {
  activeMapRegionId=region?.id
  mapCredit=if(region?.id?.startsWith("osm-")==true) "© OpenStreetMap contributors • ODbL 1.0" else null
  mapPoints=region?.let { r -> mapPack.features(r).map { p -> OfflineMapPointUi(p.latitude,p.longitude,p.label) } } ?: emptyList()
  mapLines=region?.let { r -> mapPack.lines(r).map { line -> OfflineMapLineUi(line.points.map { p -> OfflineMapPointUi(p.latitude,p.longitude,p.label) }) } } ?: emptyList()
  mapPolygons=region?.let { r -> mapPack.polygons(r).map { poly -> OfflineMapPolygonUi(poly.points.map { p -> OfflineMapPointUi(p.latitude,p.longitude,p.label) }) } } ?: emptyList()
 }
 private fun startGnss(){ if(listener==null) listener=location.start { fix ->
  latestFix=fix
  val region=mapPack.regionFor(fix.latitude,fix.longitude)
  val displayRegion=region ?: mapPack.regions().firstOrNull()
  if(displayRegion?.id!=activeMapRegionId) loadMapRegion(displayRegion)
  mapCoverage=coverageDescription(region)
  trackBack=TrackBack.state(tracks.load(),fix)
  if(recording && tracks.append(fix)){val s=tracks.summary();trackCount=s.points;trackDistanceM=s.distanceM;trackStartedAt=s.startedAt}
 } }
 private fun reloadOfflineMap(){
  mapPackState=mapPack.state()
  val fix=latestFix
  val region=fix?.let { mapPack.regionFor(it.latitude,it.longitude) }
  loadMapRegion(region ?: mapPack.regions().firstOrNull())
  mapCoverage=if(fix!=null) coverageDescription(region) else if(mapPackState.available) "Đang hiển thị vùng bản đồ đã nạp; chờ GPS để xác định vị trí." else null
 }

 private fun coverageDescription(region:OfflineMapRegion?):String {
  if(region!=null) return "Trong vùng ${region.name}: %.5f–%.5f°B, %.5f–%.5f°Đ".format(region.minLat,region.maxLat,region.minLon,region.maxLon)
  val regions=mapPack.regions()
  if(regions.isEmpty()) return "Không có vùng bản đồ hợp lệ trong gói."
  val bounds=regions.take(2).joinToString("; ") { "%.5f–%.5f°B, %.5f–%.5f°Đ".format(it.minLat,it.maxLat,it.minLon,it.maxLon) }
  return "GPS ngoài vùng bản đồ đã nạp ($bounds). ${regions.size} vùng khả dụng; chưa có đường tại tọa độ hiện tại."
 }

 fun installOfflineMapUpdate(packageFile:java.io.File):OfflineMapPackState{
  val state=mapPack.installUpdatePackage(packageFile)
  reloadOfflineMap()
  return state
 }

 fun checkConfiguredDataUpdate(){
  if(!UpdateConfig.configured){
   if(scientificImportBusy){updateStatus="Đang cài thư viện khoa học…";return}
   updateStatus="Chọn ZIP WFO đầy đủ hoặc WFO mobile để cài taxonomy + bằng chứng chuyên ngành offline."
   selectScientificBundle.launch(arrayOf("application/zip","application/octet-stream","application/x-zip-compressed"))
   return
  }
  applyDataUpdate(UpdateConfig.MANIFEST_URL,UpdateConfig.PACKAGE_URL)
 }

 private fun importScientificBundle(uri:Uri){
  if(scientificImportBusy) return
  scientificImportBusy=true
  lifecycleScope.launch{
   updateStatus="Đang kiểm tra integrity, schema, scope và số lượng hồ sơ…"
   val outcome=withContext(Dispatchers.IO){runCatching { scientificImporter.importBundle(uri) }}
   if(outcome.isSuccess){
    ScientificLibraryStore(applicationContext).refreshAfterImport()
    updateStatus=outcome.getOrThrow().message+" • mở Thư viện để tra cứu"
   } else {
    updateStatus="Cài thư viện khoa học thất bại • "+(outcome.exceptionOrNull()?.message?:"không rõ lỗi")
   }
   scientificImportBusy=false
  }
 }

 private fun recoverMapSwapIfNeeded(){
  if(!updates.needsMapRecovery()) return
  runCatching {
   val current=updates.activePackage()
   if(current!=null) mapPack.installUpdatePackage(current) else mapPack.restoreBundledMap()
   updates.finishMapSwap()
  }.onFailure { updateStatus="Cần khôi phục bản đồ: "+(it.message?:"không rõ lỗi") }
 }

 private fun rollbackDataUpdate(){
  if(updateBusy) return
  val old=updates.previousPackage()
  if(old==null){updateStatus="Không có bản để khôi phục";return}
  updateBusy=true
  lifecycleScope.launch{
   updateStatus="Đang khôi phục bản đồ…"
   try {
    val outcome=withContext(Dispatchers.IO){
     val current=updates.activePackage()
     runCatching{
      updates.markMapSwapPending()
      mapPack.installUpdatePackage(old)
      val restored=updates.rollback()
      if(!restored.applied) error(restored.message)
      updates.finishMapSwap()
      "Đã khôi phục bản đồ và gói dữ liệu"
     }.getOrElse{failure->
      val recovered=runCatching{
       if(current!=null) mapPack.installUpdatePackage(current) else mapPack.restoreBundledMap()
       updates.finishMapSwap()
      }
      if(recovered.isSuccess) "Khôi phục thất bại • ${failure.message?:"không rõ lỗi"}"
      else "Khôi phục thất bại; bản đồ cần khôi phục • ${recovered.exceptionOrNull()?.message}"
     }
    }
    reloadOfflineMap()
    updateStatus=outcome
   } finally {updateBusy=false}
  }
 }

 fun applyDataUpdate(manifestUrl:String,packageUrl:String){
  if(updateBusy) return
  updateBusy=true
  lifecycleScope.launch{
   updateStatus="Đang kiểm tra cập nhật…"
   try {
    val outcome=withContext(Dispatchers.IO){
     runCatching{
      val manifest=updates.parseManifest(updates.fetchText(manifestUrl))
      val downloaded=updates.download(packageUrl,manifest,packageManager.getPackageInfo(packageName,0).longVersionCode.toInt())
      if(!downloaded.applied) error(downloaded.message)
      val staged=updates.stagedPackage(manifest.version)?:error("Không tìm thấy gói đã xác minh")
      val current=updates.activePackage()
      try {
       updates.markMapSwapPending()
       mapPack.installUpdatePackage(staged)
       val activated=updates.activateVersion(manifest.version)
       if(!activated.applied) error(activated.message)
       updates.finishMapSwap()
       "Cập nhật thành công • dữ liệu đã nạp lại"
      } catch(failure:Exception){
       val recovered=runCatching{
        if(current!=null) mapPack.installUpdatePackage(current) else mapPack.restoreBundledMap()
        updates.finishMapSwap()
       }
       if(recovered.isSuccess) "Cập nhật thất bại • ${failure.message?:"không rõ lỗi"}"
       else "Cập nhật thất bại; bản đồ cần khôi phục • ${recovered.exceptionOrNull()?.message}"
      }
     }.getOrElse { "Cập nhật thất bại • "+(it.message?:"không rõ lỗi") }
    }
    reloadOfflineMap()
    updateStatus=outcome
   } finally {updateBusy=false}
  }
 }

 override fun onDestroy(){ listener?.let{location.stop(it)};listener=null;super.onDestroy() }
}