package vn.fieldintel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import vn.fieldintel.feature.emergency.EmergencyScreen

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val db=EmergencyBootstrap.database(this)
  val recovery=EmergencyBootstrap.recovery(this,db)
  lifecycleScope.launch { recovery.recover() }
  setContent { EmergencyScreen() }
 }
}