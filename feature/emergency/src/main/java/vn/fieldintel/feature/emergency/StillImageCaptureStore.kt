package vn.fieldintel.feature.emergency

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/** Stores a manual/gallery image and the exact visual candidates used for later review. */
object StillImageCaptureStore {
    data class Saved(val imageFile: File, val metadataFile: File)

    fun save(
        context: Context,
        bitmap: Bitmap,
        analysis: StillImageVisualAnalyzer.Result?
    ): Saved {
        val now = System.currentTimeMillis()
        val hasCandidates = analysis?.status?.descriptor != null && analysis.detections.isNotEmpty()
        val folder = File(
            context.filesDir,
            if (hasCandidates) "still-image-scans/classified" else "still-image-scans/pending"
        ).apply { mkdirs() }
        val imageFile = File(folder, "still-$now.jpg")
        imageFile.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "Không thể mã hóa ảnh JPEG" }
        }
        val metadata = RegionScanCaptureMetadata.write(
            imageFile = imageFile,
            capturedAtEpochMs = now,
            frameTimestampNanos = System.nanoTime(),
            model = analysis?.status?.descriptor,
            detections = analysis?.detections.orEmpty()
        )
        return Saved(imageFile, metadata)
    }
}
