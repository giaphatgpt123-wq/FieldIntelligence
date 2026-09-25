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

class MainActivity:ComponentActivity(){
 private var latestFix by mutableStateOf<FieldFix?>(null)
 private lateinit var location:FieldLocationController
 private lateinit var tracks:FieldTrackStore
 private lateinit var mapPack:OfflineMapPack
 private var activeMapRegionId:String?=null
 private var recording by mutableStateOf(false)
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
  location=FieldLocationController(this); mapPack=OfflineMapPack(this); mapPackState=mapPack.state(); tracks=FieldTrackStore(this); val initial=tracks.summary(); trackCount=initial.points; trackDistanceM=initial.distanceM; trackStartedAt=initial.startedAt ?: sessionPrefs.getLong("trackStartedAt",0L).takeIf{it>0L}; recording=sessionPrefs.getBoolean("recording",false)
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGnss() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
  setContent { EmergencyScreen(latestFix?.let{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)},recording,trackCount,trackDistanceM,trackStartedAt,trackBack.remainingM,trackBack.bearingDeg,trackBack.offTrackM,trackBack.breadcrumb.size,trackBack.breadcrumb.map{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)},mapPackState.available,mapPackState.fileCount,mapPackState.bytes,mapPoints,mapLines,mapPolygons,{ if(!recording && trackCount==0) trackStartedAt=System.currentTimeMillis(); recording=!recording; sessionPrefs.edit().putBoolean("recording",recording).putLong("trackStartedAt",trackStartedAt?:0L).apply() }) }
 }
 override fun onStart(){ super.onStart(); if(::location.isInitialized && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGnss() }
 override fun onStop(){ listener?.let{location.stop(it)}; listener=null; super.onStop() }
 private fun startGnss(){ if(listener==null) listener=location.start{latestFix=it;val region=mapPack.regionFor(it.latitude,it.longitude);if(region?.id!=activeMapRegionId){activeMapRegionId=region?.id;mapPoints=region?.let{r->mapPack.features(r).map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)}}?:emptyList();mapLines=region?.let{r->mapPack.lines(r).map{line->OfflineMapLineUi(line.points.map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)})}}?:emptyList();mapPolygons=region?.let{r->mapPack.polygons(r).map{poly->OfflineMapPolygonUi(poly.points.map{p->OfflineMapPointUi(p.latitude,p.longitude,p.label)})}}?:emptyList()};trackBack=TrackBack.state(tracks.load(),it);if(recording && tracks.append(it)){val s=tracks.summary();trackCount=s.points;trackDistanceM=s.distanceM;trackStartedAt=s.startedAt}} }
 override fun onDestroy(){ listener?.let{location.stop(it)};listener=null;super.onDestroy() }
}