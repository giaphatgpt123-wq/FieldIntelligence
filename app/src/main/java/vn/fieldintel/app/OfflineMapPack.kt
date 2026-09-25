package vn.fieldintel.app

import android.content.Context
import java.io.File

data class OfflineMapPackState(
    val available: Boolean,
    val fileCount: Int,
    val bytes: Long,
    val regionCount: Int = 0,
    val valid: Boolean = true
)

data class OfflineMapRegion(
    val id: String,
    val name: String,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val sourceFile: File
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLon..maxLon
}

class OfflineMapPack(context: Context) {
    private val root = File(context.filesDir, "offline-map").apply { mkdirs() }

    fun regions(): List<OfflineMapRegion> = root.walkTopDown()
        .filter { it.isFile && it.extension.equals("region", ignoreCase = true) }
        .mapNotNull { parseRegion(it) }
        .toList()

    fun state(): OfflineMapPackState {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        val regionFiles = files.filter { it.extension.equals("region", ignoreCase = true) }
        val parsed = regionFiles.mapNotNull { parseRegion(it) }
        return OfflineMapPackState(
            available = parsed.isNotEmpty(),
            fileCount = files.size,
            bytes = files.sumOf { it.length() },
            regionCount = parsed.size,
            valid = regionFiles.size == parsed.size
        )
    }

    fun regionFor(lat: Double, lon: Double): OfflineMapRegion? =
        regions().filter { it.contains(lat, lon) }.minByOrNull {
            (it.maxLat - it.minLat) * (it.maxLon - it.minLon)
        }

    fun rootPath(): String = root.absolutePath

    private fun parseRegion(file: File): OfflineMapRegion? = try {
        val values = file.readLines()
            .mapNotNull { line ->
                val split = line.split("=", limit = 2)
                if (split.size == 2) split[0].trim() to split[1].trim() else null
            }.toMap()
        val minLat = values["minLat"]?.toDouble() ?: return null
        val minLon = values["minLon"]?.toDouble() ?: return null
        val maxLat = values["maxLat"]?.toDouble() ?: return null
        val maxLon = values["maxLon"]?.toDouble() ?: return null
        if (minLat !in -90.0..90.0 || maxLat !in -90.0..90.0 ||
            minLon !in -180.0..180.0 || maxLon !in -180.0..180.0 ||
            minLat >= maxLat || minLon >= maxLon) return null
        OfflineMapRegion(
            id = values["id"] ?: file.nameWithoutExtension,
            name = values["name"] ?: file.nameWithoutExtension,
            minLat = minLat, minLon = minLon, maxLat = maxLat, maxLon = maxLon,
            sourceFile = file
        )
    } catch (_: Exception) { null }
}