package vn.fieldintel.feature.emergency

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Google AIY Plants V1/3: single-subject image classification, not object detection.
 * The labels are embedded in the upstream TFLite metadata. Scores are candidate rankings,
 * never specimen verification, edibility, toxicity, or medical advice.
 */
internal object BundledPlantImageClassifier {
    private const val MODEL_ASSET = "visual-model/aiy-plants-v1.tflite"
    private const val EXPECTED_SHA256 = "9ff2cc02d066fc266045ce299f04fb76907d9313d576881b61c255aa19433521"
    private const val MIN_SCORE = 0.70f
    data class Candidate(val scientificName: String, val score: Float)

    fun classify(context: Context, bitmap: Bitmap): List<Candidate> {
        val model = File(context.cacheDir, "aiy-plants-v1.tflite")
        if (!model.isFile || model.sha256() != EXPECTED_SHA256) {
            val incoming = File(context.cacheDir, "aiy-plants-v1.incoming")
            try {
                context.assets.open(MODEL_ASSET).use { source ->
                    incoming.outputStream().use { target -> source.copyTo(target) }
                }
                require(incoming.sha256() == EXPECTED_SHA256) { "Model ảnh không khớp SHA-256" }
                require(incoming.renameTo(model)) { "Không cài được model ảnh vào bộ nhớ đệm" }
            } finally {
                incoming.delete()
            }
        }
        val mapped = FileInputStream(model).channel.use { channel ->
            channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(MIN_SCORE)
            .build()
        val classifier = ImageClassifier.createFromBufferAndOptions(mapped, options)
        try {
            return classifier.classify(TensorImage.fromBitmap(bitmap))
                .flatMap { it.categories }
                .mapNotNull { category ->
                    // The raw category label is a /m/... identifier; the en display name is
                    // the scientific name in the model's metadata. Never show the opaque id.
                    val label = category.displayName.trim()
                    val words = label.split(Regex("\\s+"))
                    if (words.size < 2 ||
                        words[0].firstOrNull()?.isUpperCase() != true ||
                        words[1].firstOrNull()?.isLowerCase() != true ||
                        category.score < MIN_SCORE
                    ) null else Candidate(label, category.score)
                }
                .distinctBy { it.scientificName }
                .sortedByDescending { it.score }
        } finally {
            classifier.close()
        }
    }

    private fun File.sha256(): String {
        if (!isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
