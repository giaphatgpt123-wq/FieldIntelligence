package vn.fieldintel.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import vn.fieldintel.feature.emergency.EmergencyScreen
import vn.fieldintel.feature.emergency.FieldPositionUi
import vn.fieldintel.feature.emergency.OfflineMapPointUi
import vn.fieldintel.feature.emergency.OfflineMapLineUi
import vn.fieldintel.feature.emergency.OfflineMapPolygonUi

enum class TrackSessionState { IDLE, RECORDING, PAUSED, FINISHED }

class MainActivity:ComponentActivity(){
 private var latestFix by mutableStateOf<FieldFix?>(null)
 private lateinit var location:FieldLocationController
 private lateinit var tracks:FieldTrackStore
 private lateinit var mapPack:OfflineMapPack
 private lateinit var updates:DataUpdateManager
 private var updateStatus by mutableStateOf("Sẵn sàng")
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
 private var listener:android.location.LocationListener?=null
 private val sessionPrefs by lazy { getSharedPreferences("field-session", MODE_PRIVATE) }
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted) startGnss()}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val db=EmergencyBootstrap.database(this); val recovery=EmergencyBootstrap.recovery(this,db); lifecycleScope.launch{recovery.recover()}
  location=FieldLocationController(this); mapPack=OfflineMapPack(this); updates=DataUpdateManager(this); recoverMapSwapIfNeeded(); mapPackState=mapPack.state(); tracks=FieldTrackStore(this); val initial=tracks.summary(); trackCount=initial.points; trackDistanceM=initial.distanceM; trackStartedAt=initial.startedAt ?: sessionPrefs.getLong("trackStartedAt",0L).takeIf{it>0L}; trackSessionState=runCatching{TrackSessionState.valueOf(sessionPrefs.getString("trackState",null)?:if(sessionPrefs.getBoolean("recording",false)) "RECORDING" else if(initial.points>0) "PAUSED" else "IDLE")}.getOrDefault(TrackSessionState.IDLE); recording=trackSessionState==TrackSessionState.RECORDING
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGnss() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
  setContent { EmergencyScreen(latestFix?.let{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)},recording,trackCount,trackDistanceM,trackStartedAt,trackBack.remainingM,trackBack.bearingDeg,trackBack.offTrackM,trackBack.breadcrumb.size,trackBack.breadcrumb.map{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)},mapPackState.available,mapPackState.fileCount,mapPackState.bytes,mapPoints,mapLines,mapPolygons,{ if(!recording && trackCount==0) trackStartedAt=System.currentTimeMillis(); recording=!recording; trackSessionState=if(recording) TrackSessionState.RECORDING else TrackSessionState.PAUSED; sessionPrefs.edit().putBoolean("recording",recording).putString("trackState",trackSessionState.name).putLong("trackStartedAt",trackStartedAt?:0L).apply() },{ recording=false; trackSessionState=TrackSessionState.FINISHED; sessionPrefs.edit().putBoolean("recording",false).putString("trackState",trackSessionState.name).apply() },{ recording=false; trackSessionState=TrackSessionState.IDLE; tracks.clear(); trackCount=0; trackDistanceM=0.0; trackStartedAt=null; trackBack=TrackBackState(null,0.0,null); sessionPrefs.edit().clear().putString("trackState",TrackSessionState.IDLE.name).apply() },updateStatus,{ checkConfiguredDataUpdate() },{ rollbackDataUpdate() }) }
 }
 override fun onStart(){ super.onStart(); if(::location.isInitialized && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGnss() }
 override fun onStop(){ listener?.let{location.stop(it)}; listener=null; super.onStop() }
 private fun startGnss(){ if(listener==null) listener=location.start{latestFix=it;val region=mapPack.regionFor(it.latitude,it.longitude);if(region?.id!=activeMapRegionId){activeMapRegionId=region?.id;mapPoints=region?.let{r->mapPack.features(r).map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)}}?:emptyList();mapLines=region?.let{r->mapPack.lines(r).map{line->OfflineMapLineUi(line.points.map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)})}}?:emptyList();mapPolygons=region?.let{r->mapPack.polygons(r).map{poly->OfflineMapPolygonUi(poly.points.map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)})}}?:emptyList()};trackBack=TrackBack.state(tracks.load(),it);if(recording && tracks.append(it)){val s=tracks.summary();trackCount=s.points;trackDistanceM=s.distanceM;trackStartedAt=s.startedAt}} }
 private fun reloadOfflineMap(){
  mapPackState=mapPack.state()
  val fix=latestFix
  if(fix==null){activeMapRegionId=null;mapPoints=emptyList();mapLines=emptyList();mapPolygons=emptyList();return}
  val region=mapPack.regionFor(fix.latitude,fix.longitude)
  activeMapRegionId=region?.id
  mapPoints=region?.let{r->mapPack.features(r).map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)}}?:emptyList()
  mapLines=region?.let{r->mapPack.lines(r).map{line->OfflineMapLineUi(line.points.map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)})}}?:emptyList()
  mapPolygons=region?.let{r->mapPack.polygons(r).map{poly->OfflineMapPolygonUi(poly.points.map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)})}}?:emptyList()
 }

 fun installOfflineMapUpdate(packageFile:java.io.File):OfflineMapPackState{
  val state=mapPack.installUpdatePackage(packageFile)
  reloadOfflineMap()
  return state
 }

 fun checkConfiguredDataUpdate(){
  if(!UpdateConfig.configured){updateStatus="Kênh cập nhật nằm trong repo riêng tư; ứng dụng chưa thể tải gói. Cần nguồn dữ liệu riêng không yêu cầu đăng nhập.";return}
  applyDataUpdate(UpdateConfig.MANIFEST_URL,UpdateConfig.PACKAGE_URL)
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
  val old=updates.previousPackage()
  if(old==null){updateStatus="Không có bản để khôi phục";return}
  val current=updates.activePackage()
  updateStatus=runCatching{
   updates.markMapSwapPending()
   installOfflineMapUpdate(old)
   val restored=updates.rollback()
   if(!restored.applied) error(restored.message)
   updates.finishMapSwap()
   "Đã khôi phục bản đồ và gói dữ liệu"
  }.getOrElse{failure->
   if(current!=null) runCatching{installOfflineMapUpdate(current);updates.finishMapSwap()}
   "Khôi phục thất bại • ${failure.message?:"không rõ lỗi"}"
  }
 }

 fun applyDataUpdate(manifestUrl:String,packageUrl:String){
  lifecycleScope.launch{
   updateStatus="Đang kiểm tra cập nhật…"
   val result=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
    runCatching{
     val manifest=updates.parseManifest(updates.fetchText(manifestUrl))
     val downloaded=updates.download(packageUrl,manifest,packageManager.getPackageInfo(packageName,0).longVersionCode.toInt())
     if(!downloaded.applied) error(downloaded.message)
     manifest.version to (updates.stagedPackage(manifest.version)?:error("Không tìm thấy gói đã xác minh"))
    }
   }
   result.onSuccess{(version,staged)->
    updateStatus="Đang cài bản đồ…"
    val current=updates.activePackage()
    runCatching{
     updates.markMapSwapPending()
     installOfflineMapUpdate(staged)
     val activated=updates.activateVersion(version)
     if(!activated.applied) error(activated.message)
     updates.finishMapSwap()
    }.onSuccess{updateStatus="Cập nhật thành công • dữ liệu đã nạp lại"}
     .onFailure{failure->
      val restored=runCatching{
       if(current!=null) installOfflineMapUpdate(current)
       else mapPack.restoreBundledMap()
       updates.finishMapSwap()
      }
      updateStatus=if(restored.isSuccess) "Cập nhật thất bại • ${failure.message?:"không rõ lỗi"}"
       else "Cập nhật thất bại; không thể khôi phục bản đồ • ${restored.exceptionOrNull()?.message}"
     }
   }.onFailure{updateStatus="Cập nhật thất bại • "+(it.message?:"không rõ lỗi")}
  }
 }

 override fun onDestroy(){ listener?.let{location.stop(it)};listener=null;super.onDestroy() }
}