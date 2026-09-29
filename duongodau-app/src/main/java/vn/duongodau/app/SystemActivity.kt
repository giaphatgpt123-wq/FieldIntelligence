package vn.duongodau.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.duongodau.app.interop.AppInterop
import vn.duongodau.app.ui.SystemPanel

class SystemActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = AppInterop.parseIncoming(intent)
        setContent {
            MaterialTheme {
                SystemScreen(
                    initialBridgeText = incoming?.roadName.orEmpty(),
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SystemScreen(initialBridgeText: String, onClose: () -> Unit) {
    val navy = Color(0xFF071A2E)
    val ice = Color(0xFFF4F8FB)
    val orange = Color(0xFFF4A229)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ice)
    ) {
        Surface(color = navy, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Hệ thống ứng dụng", color = Color.White, fontWeight = FontWeight.Black)
                Text(
                    "Cập nhật ổn định • liên thông ứng dụng • chống lỗi tải/cài",
                    color = Color.White.copy(alpha = 0.76f)
                )
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = orange, contentColor = navy),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Quay lại Đường ở đâu", fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SystemPanel(initialBridgeText = initialBridgeText) }
        }
    }
}
