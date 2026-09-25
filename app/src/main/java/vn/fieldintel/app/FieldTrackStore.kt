package vn.fieldintel.app

import android.content.Context
import java.io.File
import kotlin.math.*

data class TrackPoint(val latitude: Double, val longitude: Double, val accuracyM: Float, val timeMs: Long)
data class TrackSummary(val points: Int, val distanceM: Double, val startedAt: Long?, val endedAt: Long?)

class FieldTrackStore(context: Context) {
    private val file = File(context.filesDir, "field-track.tsv")
    private var cachedPoints: MutableList<TrackPoint> = readFile().toMutableList()
    private var cachedDistanceM: Double = calculateDistance(cachedPoints)

    fun clear() {
        if (file.exists()) file.delete()
        cachedPoints.clear()
        cachedDistanceM = 0.0
    }

    fun append(fix: FieldFix): Boolean {
        if (fix.accuracyM <= 0f || fix.accuracyM > 50f) return false
        val point = TrackPoint(fix.latitude, fix.longitude, fix.accuracyM, fix.timeMs)
        val previous = cachedPoints.lastOrNull()
        val segmentM = if (previous != null) {
            val dt = fix.timeMs - previous.timeMs
            if (dt <= 0L) return false
            val d = distance(previous, point)
            if (d / (dt / 1000.0) > 15.0) return false
            d
        } else 0.0
        return try {
            file.appendText(listOf(fix.latitude, fix.longitude, fix.accuracyM, fix.timeMs).joinToString("\t") + "\n")
            cachedPoints.add(point)
            cachedDistanceM += segmentM
            true
        } catch (_: Exception) { false }
    }

    fun load(): List<TrackPoint> = cachedPoints.toList()

    fun summary(): TrackSummary = TrackSummary(
        cachedPoints.size,
        cachedDistanceM,
        cachedPoints.firstOrNull()?.timeMs,
        cachedPoints.lastOrNull()?.timeMs
    )

    private fun readFile(): List<TrackPoint> = if (!file.exists()) emptyList() else file.readLines().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size != 4) null else try {
            TrackPoint(p[0].toDouble(), p[1].toDouble(), p[2].toFloat(), p[3].toLong())
        } catch (_: Exception) { null }
    }

    private fun calculateDistance(points: List<TrackPoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += distance(points[i - 1], points[i])
        return total
    }

    private fun distance(a: TrackPoint, b: TrackPoint): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(b.latitude)
        val dp = Math.toRadians(b.latitude - a.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
