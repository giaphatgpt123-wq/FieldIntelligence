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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import vn.fieldintel.feature.emergency.FieldColors
import vn.fieldintel.feature.emergency.RegionScanPanel

/** Direct entry point for on-device validation of region scanning and automatic evidence capture. */
class LiveVisualSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = FieldColors) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF07181D))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    RegionScanPanel()
                }
            }
        }
    }
}
