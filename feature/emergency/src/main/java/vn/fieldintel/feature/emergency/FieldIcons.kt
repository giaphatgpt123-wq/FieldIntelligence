package vn.fieldintel.feature.emergency

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Familiar, colorful symbols from the earlier VN Sinh tồn home screen. */
@Composable
internal fun FieldIcon(section: AppSection?, modifier: Modifier = Modifier, color: Color = Color.White) {
    val glyph = when (section) {
        null -> "⌂"
        AppSection.FIELD -> "🗺️"
        AppSection.RECOGNITION -> "🌿"
        AppSection.SURVIVAL -> "🔥"
        AppSection.EMERGENCY -> "SOS"
        AppSection.PREP -> "🎒"
        AppSection.LIBRARY -> "📚"
        AppSection.TRAINING -> "🎓"
        AppSection.SETTINGS -> "⚙️"
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            color = color,
            fontSize = if (section == AppSection.EMERGENCY) 17.sp else if (section == null) 30.sp else 25.sp,
            lineHeight = 30.sp,
            fontWeight = if (section == AppSection.EMERGENCY) FontWeight.Black else FontWeight.Normal,
            maxLines = 1
        )
    }
}
