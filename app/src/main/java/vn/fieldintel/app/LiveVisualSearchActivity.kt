package vn.fieldintel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import vn.fieldintel.feature.emergency.FieldColors
import vn.fieldintel.feature.emergency.RegionScanAutoCapturePanel
import vn.fieldintel.feature.emergency.TfliteRegionModelRunner

/** Direct entry point for on-device validation of region scanning and automatic evidence capture. */
class LiveVisualSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val runner = remember { TfliteRegionModelRunner(applicationContext) }
            DisposableEffect(runner) {
                onDispose { runner.close() }
            }
            MaterialTheme(colorScheme = FieldColors) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF07181D))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    RegionScanAutoCapturePanel(runner = runner)
                }
            }
        }
    }
}
