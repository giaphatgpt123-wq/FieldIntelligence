package vn.fieldintel.feature.emergency

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas

/** Draws model candidates over the live preview. It does not create detections itself. */
@Composable
fun LiveVisualOverlay(
    detections: List<VisualDetection>,
    target: LiveVisualSearchTarget?,
    modifier: Modifier = Modifier
) {
    val matching = target?.let { requested -> detections.filter { LiveVisualTargetMatcher.matches(requested, it) } }.orEmpty()

    Canvas(modifier.fillMaxSize()) {
        detections.forEach { detection ->
            val isTarget = matching.contains(detection)
            val stable = detection.isStableRegionCandidate()
            val verifying = detection.regionVerificationProgress()
            val color = when {
                stable || isTarget -> Color(0xFF45E58C)
                else -> Color(0xFFFFD166)
            }
            val left = detection.box.left * size.width
            val top = detection.box.top * size.height
            val width = (detection.box.right - detection.box.left) * size.width
            val height = (detection.box.bottom - detection.box.top) * size.height
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = if (stable || isTarget) 6f else 3f)
            )

            if (target == null && (stable || verifying != null)) {
                val stateText = if (stable) {
                    "ĐÃ ỔN ĐỊNH"
                } else {
                    "ĐANG XÁC MINH ${verifying!!.first}/${verifying.second}"
                }
                val name = detection.scientificName?.takeIf { it.isNotBlank() } ?: detection.label
                val label = "$stateText • $name"
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 30f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    this.color = android.graphics.Color.WHITE
                    setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
                }
                val baseline = (top - 10f).coerceAtLeast(paint.textSize + 4f)
                drawContext.canvas.nativeCanvas.drawText(label, left.coerceAtLeast(6f), baseline, paint)
            }
        }
    }
}
