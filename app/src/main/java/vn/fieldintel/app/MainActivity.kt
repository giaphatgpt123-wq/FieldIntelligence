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

class MainActivity:ComponentActivity(){
 private var latestFix by mutableStateOf<FieldFix?>(null)
 private lateinit var location:FieldLocationController
 private lateinit var tracks:FieldTrackStore
 private var recording by mutableStateOf(false)
 private var trackCount by mutableIntStateOf(0)
 private var trackDistanceM by mutableDoubleStateOf(0.0)
 private var trackStartedAt by mutableStateOf<Long?>(null)
 private var trackBack by mutableStateOf(TrackBackState(null,0.0,null))
 private var mapPackState by mutableStateOf(OfflineMapPackState(false,0,0))
 private var listener:android.location.LocationListener?=null
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted) startGnss()}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val db=EmergencyBootstrap.database(this); val recovery=EmergencyBootstrap.recovery(this,db); lifecycleScope.launch{recovery.recover()}
  location=FieldLocationController(this); mapPackState=OfflineMapPack(this).state(); tracks=FieldTrackStore(this); val initial=tracks.summary(); trackCount=initial.points; trackDistanceM=initial.distanceM; trackStartedAt=initial.startedAt
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGnss() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
  setContent { EmergencyScreen(latestFix?.let{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)},recording,trackCount,trackDistanceM,trackStartedAt,trackBack.remainingM,trackBack.bearingDeg,trackBack.offTrackM,trackBack.breadcrumb.size,trackBack.breadcrumb.map{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)},mapPackState.available,mapPackState.fileCount,mapPackState.bytes,{ if(!recording && trackCount==0) trackStartedAt=System.currentTimeMillis(); recording=!recording }) }
 }
 private fun startGnss(){ if(listener==null) listener=location.start{latestFix=it;trackBack=TrackBack.state(tracks.load(),it);if(recording && tracks.append(it)){val s=tracks.summary();trackCount=s.points;trackDistanceM=s.distanceM;trackStartedAt=s.startedAt}} }
 override fun onDestroy(){ listener?.let{location.stop(it)};super.onDestroy() }
}