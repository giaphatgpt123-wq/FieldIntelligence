package vn.fieldintel.feature.emergency

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.min

/** Draws normalized model candidates over a ContentScale.Fit still image. */
@Composable
fun StillImageDetectionOverlay(
    detections: List<VisualDetection>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    if (imageWidth <= 0 || imageHeight <= 0 || detections.isEmpty()) return

    Canvas(modifier = modifier) {
        val scale = min(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
        val renderedWidth = imageWidth * scale
        val renderedHeight = imageHeight * scale
        val offsetX = (size.width - renderedWidth) / 2f
        val offsetY = (size.height - renderedHeight) / 2f

        detections.forEach { detection ->
            val left = offsetX + detection.box.left * renderedWidth
            val top = offsetY + detection.box.top * renderedHeight
            val width = (detection.box.right - detection.box.left) * renderedWidth
            val height = (detection.box.bottom - detection.box.top) * renderedHeight
            val color = if (detection.confidence >= 0.70f) Color(0xFF45E58C) else Color(0xFFFFD166)

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 5f)
            )

            val name = detection.scientificName?.takeIf { it.isNotBlank() } ?: detection.label
            val label = "$name ${(detection.confidence * 100).toInt()}%"
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 28f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                this.color = android.graphics.Color.WHITE
                setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
            }
            val baseline = (top - 8f).coerceAtLeast(paint.textSize + 3f)
            drawContext.canvas.nativeCanvas.drawText(label, left.coerceAtLeast(6f), baseline, paint)
        }
    }
}
