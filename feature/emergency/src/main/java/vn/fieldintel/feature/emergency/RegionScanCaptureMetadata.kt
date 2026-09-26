package vn.fieldintel.feature.emergency

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the exact visual candidates associated with an automatically captured region image.
 * This sidecar is provenance for later review only; it never asserts food, toxicity or medical safety.
 */
object RegionScanCaptureMetadata {
    fun write(
        imageFile: File,
        capturedAtEpochMs: Long,
        frameTimestampNanos: Long,
        model: VisualModelDescriptor?,
        detections: List<VisualDetection>
    ): File {
        require(imageFile.name.endsWith(".jpg", ignoreCase = true))
        val sidecar = File(imageFile.parentFile, imageFile.nameWithoutExtension + ".json")
        val classificationState = if (model != null && detections.isNotEmpty()) "CANDIDATES_RECORDED" else "PENDING_OR_UNKNOWN"
        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("imageFile", imageFile.name)
            .put("capturedAtEpochMs", capturedAtEpochMs)
            .put("frameTimestampNanos", frameTimestampNanos)
            .put("classificationState", classificationState)
            .put("visualResultIsSafetyConclusion", false)

        if (model != null) {
            root.put(
                "model",
                JSONObject()
                    .put("id", model.id)
                    .put("version", model.version)
                    .put("sha256", model.sha256)
                    .put("sourceName", model.sourceName)
                    .put("license", model.license)
            )
        } else {
            root.put("model", JSONObject.NULL)
        }

        val candidates = JSONArray()
        detections.forEach { detection ->
            candidates.put(
                JSONObject()
                    .put("label", detection.label)
                    .put("scientificName", detection.scientificName ?: JSONObject.NULL)
                    .put("confidence", detection.confidence.toDouble())
                    .put(
                        "box",
                        JSONObject()
                            .put("left", detection.box.left.toDouble())
                            .put("top", detection.box.top.toDouble())
                            .put("right", detection.box.right.toDouble())
                            .put("bottom", detection.box.bottom.toDouble())
                    )
            )
        }
        root.put("candidates", candidates)
        sidecar.writeText(root.toString(2) + "\n", Charsets.UTF_8)
        return sidecar
    }
}
