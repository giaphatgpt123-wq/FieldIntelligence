package vn.fieldintel.feature.emergency

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.min

internal fun stillImageHitTest(
    detections: List<VisualDetection>,
    tapX: Float,
    tapY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Int,
    imageHeight: Int
): VisualDetection? {
    if (imageWidth <= 0 || imageHeight <= 0 || canvasWidth <= 0f || canvasHeight <= 0f) return null
    val scale = min(canvasWidth / imageWidth.toFloat(), canvasHeight / imageHeight.toFloat())
    val renderedWidth = imageWidth * scale
    val renderedHeight = imageHeight * scale
    val offsetX = (canvasWidth - renderedWidth) / 2f
    val offsetY = (canvasHeight - renderedHeight) / 2f
    val nx = (tapX - offsetX) / renderedWidth
    val ny = (tapY - offsetY) / renderedHeight
    if (nx !in 0f..1f || ny !in 0f..1f) return null

    val selectedGroup = buildRegionRecognitionGroups(detections)
        .asSequence()
        .filter { nx >= it.box.left && nx <= it.box.right && ny >= it.box.top && ny <= it.box.bottom }
        .minByOrNull { (it.box.right - it.box.left) * (it.box.bottom - it.box.top) }
        ?: return null

    // A tap may only open a scientific profile from a region whose full Top-N decision is strong.
    // This prevents cases such as 75% vs 74% from being treated as a confident identification.
    return selectedGroup.primary?.takeIf {
        selectedGroup.verdict == RecognitionVerdict.STRONG_CANDIDATE
    }
}

/** Draws one box per detected region over a ContentScale.Fit still image. */
@Composable
fun StillImageDetectionOverlay(
    detections: List<VisualDetection>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
    selected: VisualDetection? = null,
    onDetectionTap: (VisualDetection) -> Unit = {}
) {
    if (imageWidth <= 0 || imageHeight <= 0 || detections.isEmpty()) return
    val groups = buildRegionRecognitionGroups(detections)

    Canvas(
        modifier = modifier.pointerInput(detections, imageWidth, imageHeight) {
            detectTapGestures { tap ->
                stillImageHitTest(
                    detections = detections,
                    tapX = tap.x,
                    tapY = tap.y,
                    canvasWidth = size.width.toFloat(),
                    canvasHeight = size.height.toFloat(),
                    imageWidth = imageWidth,
                    imageHeight = imageHeight
                )?.let(onDetectionTap)
            }
        }
    ) {
        val scale = min(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
        val renderedWidth = imageWidth * scale
        val renderedHeight = imageHeight * scale
        val offsetX = (size.width - renderedWidth) / 2f
        val offsetY = (size.height - renderedHeight) / 2f

        groups.forEach { group ->
            val detection = group.primary ?: return@forEach
            val left = offsetX + group.box.left * renderedWidth
            val top = offsetY + group.box.top * renderedHeight
            val width = (group.box.right - group.box.left) * renderedWidth
            val height = (group.box.bottom - group.box.top) * renderedHeight
            val isSelected = selected?.let { candidate ->
                candidate.trackHint?.takeIf { it.isNotBlank() } == detection.trackHint?.takeIf { it.isNotBlank() } ||
                    candidate.box == detection.box
            } ?: false
            val color = when {
                isSelected -> Color(0xFF78DCE8)
                group.verdict == RecognitionVerdict.STRONG_CANDIDATE -> Color(0xFF45E58C)
                group.verdict == RecognitionVerdict.AMBIGUOUS -> Color(0xFFFFD166)
                else -> Color(0xFFFF8A80)
            }

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = if (isSelected) 8f else 5f)
            )

            val candidateCount = group.candidates.size
            val label = if (candidateCount > 1) {
                "${recognitionVerdictLabel(group.verdict)} • $candidateCount ứng viên"
            } else {
                val name = detection.scientificName?.takeIf { it.isNotBlank() } ?: detection.label
                "$name ${(detection.confidence * 100).toInt()}%"
            }
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
