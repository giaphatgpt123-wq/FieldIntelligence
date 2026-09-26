package vn.fieldintel.feature.emergency

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** Draws model candidates over the live preview. It does not create detections itself. */
@Composable
fun LiveVisualOverlay(
    detections: List<VisualDetection>,
    target: LiveVisualSearchTarget?,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val matching = target?.let { requested -> detections.filter { LiveVisualTargetMatcher.matches(requested, it) } }.orEmpty()

    Canvas(modifier.fillMaxSize()) {
        detections.forEach { detection ->
            val isTarget = matching.contains(detection)
            val color = if (isTarget) Color(0xFF45E58C) else Color(0xFFFFD166)
            val left = detection.box.left * size.width
            val top = detection.box.top * size.height
            val width = (detection.box.right - detection.box.left) * size.width
            val height = (detection.box.bottom - detection.box.top) * size.height
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = if (isTarget) 6f else 3f)
            )
            val label = buildString {
                append(if (isTarget) "ỨNG VIÊN • " else "ĐỐI TƯỢNG • ")
                append(detection.label)
                append(" • ")
                append((detection.confidence * 100).toInt())
                append('%')
            }
            val textLayout = textMeasurer.measure(
                text = label,
                style = TextStyle(color = color, fontSize = 12.sp)
            )
            val textY = (top - textLayout.size.height - 8f).coerceAtLeast(0f)
            drawText(textLayout, topLeft = Offset(left, textY))
        }
    }
}
