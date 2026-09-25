package vn.fieldintel.app

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

data class OfflineMapPackState(
    val available: Boolean,
    val fileCount: Int,
    val bytes: Long,
    val regionCount: Int = 0,
    val valid: Boolean = true
)

data class OfflineMapFeature(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null
)

data class OfflineMapLine(val points: List<OfflineMapFeature>, val label: String? = null)
data class OfflineMapPolygon(val points: List<OfflineMapFeature>, val label: String? = null)

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

class OfflineMapPack(private val context: Context) {
    private val root = File(context.filesDir, "offline-map").apply { mkdirs() }

    init { bootstrapAssetsIfNeeded() }

    private fun bootstrapAssetsIfNeeded() {
        val assetDir = "offline-map"
        val names = runCatching { context.assets.list(assetDir)?.toList().orEmpty() }.getOrDefault(emptyList())
        names.filter { it.endsWith(".region", true) || it.endsWith(".points", true) || it.endsWith(".lines", true) || it.endsWith(".polygons", true) }
            .forEach { name ->
                val target = File(root, name)
                if (!target.exists() || target.length() == 0L) {
                    val temp = File(root, "$name.tmp")
                    runCatching {
                        context.assets.open("$assetDir/$name").use { input ->
                            temp.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (!temp.renameTo(target)) {
                            temp.copyTo(target, overwrite = true)
                            temp.delete()
                        }
                    }.onFailure { temp.delete() }
                }
            }
    }

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

    fun features(region: OfflineMapRegion): List<OfflineMapFeature> {
        val featureFile = File(region.sourceFile.parentFile, region.id + ".points")
        if (!featureFile.exists()) return emptyList()
        return featureFile.readLines().mapNotNull { line ->
            val parts = line.split("\t", limit = 3)
            if (parts.size < 2) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            if (!region.contains(lat, lon)) return@mapNotNull null
            OfflineMapFeature(lat, lon, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
        }
    }

    fun lines(region: OfflineMapRegion): List<OfflineMapLine> = parseGeometry(File(region.sourceFile.parentFile, region.id + ".lines"), region).map { OfflineMapLine(it) }

    fun polygons(region: OfflineMapRegion): List<OfflineMapPolygon> = parseGeometry(File(region.sourceFile.parentFile, region.id + ".polygons"), region).filter { it.size >= 3 }.map { OfflineMapPolygon(it) }

    private fun parseGeometry(file: File, region: OfflineMapRegion): List<List<OfflineMapFeature>> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val points = line.split(";").mapNotNull { token ->
                val pair = token.trim().split(",", limit = 2)
                if (pair.size != 2) return@mapNotNull null
                val lat = pair[0].toDoubleOrNull() ?: return@mapNotNull null
                val lon = pair[1].toDoubleOrNull() ?: return@mapNotNull null
                if (!region.contains(lat, lon)) return@mapNotNull null
                OfflineMapFeature(lat, lon)
            }
            points.takeIf { it.size >= 2 }
        }
    }

    fun installUpdatePackage(packageFile: File): OfflineMapPackState {
        val incoming = File(context.filesDir, "offline-map-incoming")
        val backup = File(context.filesDir, "offline-map-backup")
        incoming.deleteRecursively(); incoming.mkdirs()
        try {
            ZipInputStream(packageFile.inputStream().buffered()).use { zip ->
                var entryCount = 0
                var totalBytes = 0L
                val maxEntries = 256
                val maxTotalBytes = 64L * 1024L * 1024L
                val maxEntryBytes = 16L * 1024L * 1024L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = File(entry.name).name
                    val allowed = name.endsWith(".region", true) || name.endsWith(".points", true) ||
                        name.endsWith(".lines", true) || name.endsWith(".polygons", true)
                    if (!allowed || name != entry.name) continue
                    require(++entryCount <= maxEntries) { "Gói bản đồ có quá nhiều tệp" }
                    val target = File(incoming, name)
                    target.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        var entryBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            totalBytes += read
                            require(entryBytes <= maxEntryBytes) { "Tệp bản đồ vượt giới hạn kích thước" }
                            require(totalBytes <= maxTotalBytes) { "Gói bản đồ vượt giới hạn giải nén" }
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
            val regionFiles = incoming.listFiles()?.filter { it.extension.equals("region", true) }.orEmpty()
            require(regionFiles.isNotEmpty()) { "Gói không có region hợp lệ" }
            require(regionFiles.all { parseRegion(it) != null }) { "Region metadata không hợp lệ" }
            backup.deleteRecursively()
            var swapStarted = false
            try {
                if (root.exists()) {
                    if (!root.renameTo(backup)) {
                        root.copyRecursively(backup, overwrite = true)
                        root.deleteRecursively()
                    }
                }
                swapStarted = true
                if (!incoming.renameTo(root)) {
                    incoming.copyRecursively(root, overwrite = true)
                    incoming.deleteRecursively()
                }
                require(state().available) { "Gói bản đồ sau cài đặt không khả dụng" }
                backup.deleteRecursively()
            } catch (swapError: Exception) {
                if (swapStarted) {
                    root.deleteRecursively()
                    if (backup.exists()) {
                        if (!backup.renameTo(root)) {
                            backup.copyRecursively(root, overwrite = true)
                            backup.deleteRecursively()
                        }
                    } else root.mkdirs()
                }
                throw swapError
            }
        } catch (e: Exception) {
            incoming.deleteRecursively()
            throw e
        }
        return state()
    }

    fun rootPath(): String = root.absolutePath

    private fun parseRegion(file: File): OfflineMapRegion? {
        return try {
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
        } catch (_: Exception) {
            null
        }
    }
}