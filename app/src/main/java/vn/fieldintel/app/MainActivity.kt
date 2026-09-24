package vn.fieldintel.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import vn.fieldintel.feature.emergency.EmergencyScreen
class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); setContent { EmergencyScreen() } } }
