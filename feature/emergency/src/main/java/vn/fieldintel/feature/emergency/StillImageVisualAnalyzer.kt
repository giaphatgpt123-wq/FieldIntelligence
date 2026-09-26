package vn.fieldintel.feature.emergency

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reuses the installed region detector/classifier for a photo selected from storage or captured
 * manually. Results are visual candidates only; they never imply edibility, toxicity or treatment.
 */
object StillImageVisualAnalyzer {
    private const val MAX_EDGE = 1280

    data class Result(
        val status: VisualModelStatus,
        val detections: List<VisualDetection>,
        val analyzedWidth: Int,
        val analyzedHeight: Int
    )

    fun analyze(bitmap: Bitmap, runner: LiveVisualModelRunner): Result {
        val status = runner.status()
        if (status.availability != VisualModelAvailability.READY) {
            return Result(status, emptyList(), bitmap.width, bitmap.height)
        }

        val prepared = downscale(bitmap)
        return try {
            val frame = prepared.toOwnedYuvFrame()
            Result(
                status = status,
                detections = runner.scanRegion(frame)
                    .filter { it.confidence in 0f..1f }
                    .sortedByDescending { it.confidence },
                analyzedWidth = prepared.width,
                analyzedHeight = prepared.height
            )
        } finally {
            if (prepared !== bitmap && !prepared.isRecycled) prepared.recycle()
        }
    }

    private fun downscale(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= MAX_EDGE) return source
        val scale = MAX_EDGE.toFloat() / longest.toFloat()
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun Bitmap.toOwnedYuvFrame(): LiveVisualFrameData {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, 0, 0, w, h)

        val yBytes = ByteArray(w * h)
        val uvWidth = (w + 1) / 2
        val uvHeight = (h + 1) / 2
        val uBytes = ByteArray(uvWidth * uvHeight)
        val vBytes = ByteArray(uvWidth * uvHeight)

        for (row in 0 until h) {
            for (col in 0 until w) {
                val pixel = pixels[row * w + col]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val y = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
                yBytes[row * w + col] = y.toByte()
            }
        }

        for (uvRow in 0 until uvHeight) {
            for (uvCol in 0 until uvWidth) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var samples = 0
                for (dy in 0..1) {
                    val row = uvRow * 2 + dy
                    if (row >= h) continue
                    for (dx in 0..1) {
                        val col = uvCol * 2 + dx
                        if (col >= w) continue
                        val pixel = pixels[row * w + col]
                        rSum += (pixel shr 16) and 0xff
                        gSum += (pixel shr 8) and 0xff
                        bSum += pixel and 0xff
                        samples += 1
                    }
                }
                val r = rSum.toFloat() / samples.coerceAtLeast(1)
                val g = gSum.toFloat() / samples.coerceAtLeast(1)
                val b = bSum.toFloat() / samples.coerceAtLeast(1)
                val u = (-0.168736f * r - 0.331264f * g + 0.5f * b + 128f).roundToInt().coerceIn(0, 255)
                val v = (0.5f * r - 0.418688f * g - 0.081312f * b + 128f).roundToInt().coerceIn(0, 255)
                val index = uvRow * uvWidth + uvCol
                uBytes[index] = u.toByte()
                vBytes[index] = v.toByte()
            }
        }

        return LiveVisualFrameData(
            timestampNanos = System.nanoTime(),
            width = w,
            height = h,
            rotationDegrees = 0,
            y = YuvPlaneData(yBytes, w, 1),
            u = YuvPlaneData(uBytes, uvWidth, 1),
            v = YuvPlaneData(vBytes, uvWidth, 1)
        )
    }
}
