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
 private lateinit var track:TrackRecorder
 private var listener:android.location.LocationListener?=null
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted) startGnss()}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val db=EmergencyBootstrap.database(this); val recovery=EmergencyBootstrap.recovery(this,db); lifecycleScope.launch{recovery.recover()}
  location=FieldLocationController(this)
  track=TrackRecorder(this)
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGnss() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
  setContent { EmergencyScreen(latestFix?.let{FieldPositionUi(it.latitude,it.longitude,it.accuracyM)}) }
 }
 private fun startGnss(){ if(listener==null) listener=location.start{latestFix=it;if(track.recording) track.append(it)} }
 override fun onDestroy(){ listener?.let{location.stop(it)}; super.onDestroy() }
}