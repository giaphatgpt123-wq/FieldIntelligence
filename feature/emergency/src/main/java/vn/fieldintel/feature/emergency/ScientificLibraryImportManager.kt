package vn.fieldintel.feature.emergency

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/** Installs scientific SQLite packs selected through Android's Storage Access Framework. */
class ScientificLibraryImportManager(private val context: Context) {
    enum class PackType(
        val fileName: String,
        val previousName: String,
        val expectedSchemaVersion: Int,
        val expectedScope: String,
        val maxBytes: Long
    ) {
        TAXONOMY(
            fileName = ScientificLibraryStore.DATABASE_NAME,
            previousName = "wfo-taxonomy.previous.sqlite",
            expectedSchemaVersion = 1,
            expectedScope = "taxonomy-only",
            maxBytes = 768L * 1024L * 1024L
        ),
        SPECIALIST_EVIDENCE(
            fileName = SpecialistEvidenceStore.DATABASE_NAME,
            previousName = "specialist-evidence.previous.sqlite",
            expectedSchemaVersion = 2,
            expectedScope = "specialist-evidence-only",
            maxBytes = 128L * 1024L * 1024L
        )
    }

    data class ImportResult(
        val type: PackType,
        val installedBytes: Long,
        val recordCount: Long,
        val message: String
    )

    data class BundleImportResult(
        val taxonomy: ImportResult,
        val specialistEvidence: ImportResult,
        val message: String
    )

    /** Imports the GitHub Actions artifact ZIP containing both required SQLite files. */
    fun importBundle(uri: Uri): BundleImportResult {
        val directory = libraryDirectory()
        val staged = PackType.entries.associateWith { type ->
            File(directory, ".${type.fileName}.incoming").also { it.delete() }
        }
        val found = mutableSetOf<PackType>()
        try {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val baseName = entry.name.substringAfterLast('/')
                        val type = PackType.entries.firstOrNull { it.fileName == baseName } ?: continue
                        require(type !in found) { "Gói ZIP có tệp trùng: ${type.fileName}" }
                        copyZipEntryBounded(zip, staged.getValue(type), type.maxBytes)
                        found += type
                    }
                }
            } ?: error("Không thể mở gói ZIP đã chọn")

            require(found == PackType.entries.toSet()) {
                "Gói ZIP phải chứa ${PackType.entries.joinToString { it.fileName }}"
            }

            val counts = PackType.entries.associateWith { type -> validate(staged.getValue(type), type) }
            return activateBundle(staged, counts)
        } finally {
            staged.values.forEach { it.delete() }
        }
    }

    fun import(uri: Uri, type: PackType): ImportResult {
        val directory = libraryDirectory()
        val staging = File(directory, ".${type.fileName}.incoming")
        staging.delete()
        val copied = copyBounded(uri, staging, type.maxBytes)
        require(copied > 0L) { "Gói dữ liệu rỗng" }

        return try {
            val recordCount = validate(staging, type)
            activateSingle(staging, type, recordCount)
        } finally {
            staging.delete()
        }
    }

    private fun activateBundle(
        staged: Map<PackType, File>,
        counts: Map<PackType, Long>
    ): BundleImportResult {
        val directory = libraryDirectory()
        val targets = PackType.entries.associateWith { File(directory, it.fileName) }
        val previous = PackType.entries.associateWith { File(directory, it.previousName) }
        val existedBefore = PackType.entries.associateWith { targets.getValue(it).isFile }

        PackType.entries.forEach { type ->
            val target = targets.getValue(type)
            val backup = previous.getValue(type)
            if (target.isFile && target.length() > 0L) target.copyTo(backup, overwrite = true) else backup.delete()
        }

        try {
            PackType.entries.forEach { type -> atomicReplace(staged.getValue(type), targets.getValue(type)) }
        } catch (failure: Throwable) {
            val restoreFailures = mutableListOf<String>()
            PackType.entries.forEach { type ->
                val target = targets.getValue(type)
                val backup = previous.getValue(type)
                runCatching {
                    if (existedBefore.getValue(type)) {
                        require(backup.isFile && backup.length() > 0L) { "Thiếu bản sao ${type.fileName}" }
                        val restore = File(directory, ".${type.fileName}.restore")
                        backup.copyTo(restore, overwrite = true)
                        atomicReplace(restore, target)
                    } else {
                        target.delete()
                    }
                }.onFailure { restoreFailures += "${type.fileName}: ${it.message}" }
            }
            val suffix = if (restoreFailures.isEmpty()) "đã hoàn nguyên gói trước" else "hoàn nguyên lỗi: ${restoreFailures.joinToString()}"
            throw IllegalStateException("Kích hoạt bundle thất bại; $suffix", failure)
        }

        val taxonomy = resultFor(PackType.TAXONOMY, targets.getValue(PackType.TAXONOMY), counts.getValue(PackType.TAXONOMY))
        val evidence = resultFor(PackType.SPECIALIST_EVIDENCE, targets.getValue(PackType.SPECIALIST_EVIDENCE), counts.getValue(PackType.SPECIALIST_EVIDENCE))
        return BundleImportResult(
            taxonomy = taxonomy,
            specialistEvidence = evidence,
            message = "Đã cài thư viện khoa học: ${taxonomy.recordCount} taxonomy + ${evidence.recordCount} evidence"
        )
    }

    private fun activateSingle(staging: File, type: PackType, recordCount: Long): ImportResult {
        val directory = libraryDirectory()
        val target = File(directory, type.fileName)
        val previous = File(directory, type.previousName)
        if (target.isFile && target.length() > 0L) target.copyTo(previous, overwrite = true)
        atomicReplace(staging, target)
        return resultFor(type, target, recordCount)
    }

    private fun resultFor(type: PackType, target: File, recordCount: Long): ImportResult = ImportResult(
        type = type,
        installedBytes = target.length(),
        recordCount = recordCount,
        message = when (type) {
            PackType.TAXONOMY -> "Đã cài taxonomy SQLite: $recordCount hồ sơ"
            PackType.SPECIALIST_EVIDENCE -> "Đã cài specialist evidence SQLite: $recordCount hồ sơ"
        }
    )

    private fun atomicReplace(staging: File, target: File) {
        try {
            Files.move(
                staging.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (failure: Throwable) {
            staging.delete()
            throw IllegalStateException("Không thể kích hoạt ${target.name} theo cơ chế atomic", failure)
        }
    }

    private fun libraryDirectory(): File = File(context.filesDir, ScientificLibraryStore.DIRECTORY_NAME).apply {
        mkdirs()
        require(isDirectory) { "Không thể tạo thư mục thư viện khoa học" }
    }

    private fun copyBounded(uri: Uri, target: File, maxBytes: Long): Long {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "Gói dữ liệu vượt giới hạn ${maxBytes / 1024 / 1024} MB" }
                    output.write(buffer, 0, read)
                }
                output.flush()
                total
            }
        } ?: error("Không thể mở tệp đã chọn")
    }

    private fun copyZipEntryBounded(zip: ZipInputStream, target: File, maxBytes: Long): Long {
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "${target.name} vượt giới hạn ${maxBytes / 1024 / 1024} MB" }
                output.write(buffer, 0, read)
            }
            output.flush()
            require(total > 0L) { "${target.name} rỗng" }
            return total
        }
    }

    private fun validate(file: File, type: PackType): Long {
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).use { db ->
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            }
            require(integrity.equals("ok", ignoreCase = true)) { "SQLite integrity_check thất bại: $integrity" }

            val requiredTables = when (type) {
                PackType.TAXONOMY -> setOf("meta", "taxon")
                PackType.SPECIALIST_EVIDENCE -> setOf("meta", "evidence")
            }
            val actualTables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                null
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            require(requiredTables.all(actualTables::contains)) { "Thiếu bảng SQLite bắt buộc" }

            val meta = db.rawQuery("SELECT key, value FROM meta", null).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1).trimJsonString())
                }
            }
            require(meta["schemaVersion"]?.toIntOrNull() == type.expectedSchemaVersion) {
                "Schema không tương thích: ${meta["schemaVersion"] ?: "không rõ"}"
            }
            require(meta["scope"] == type.expectedScope) { "Scope dữ liệu không đúng: ${meta["scope"] ?: "không rõ"}" }

            if (type == PackType.SPECIALIST_EVIDENCE) {
                require(meta["doseRecommendationsIncluded"] != "true") { "Gói evidence chứa khuyến nghị liều không được phép" }
                require(meta["treatmentRecommendationsIncluded"] != "true") { "Gói evidence chứa khuyến nghị điều trị không được phép" }
                require(meta["imageIdentificationIncluded"] != "true") { "Gói evidence chứa claim nhận dạng ảnh không được phép" }
            }

            val table = if (type == PackType.TAXONOMY) "taxon" else "evidence"
            val actualCount = db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                require(cursor.moveToFirst()) { "Không đọc được số lượng hồ sơ" }
                cursor.getLong(0)
            }
            require(actualCount > 0L) { "Gói dữ liệu không có hồ sơ" }
            val declaredCount = meta["recordCount"]?.toLongOrNull()
            if (declaredCount != null) require(declaredCount == actualCount) {
                "recordCount không khớp: khai báo $declaredCount, thực tế $actualCount"
            }
            return actualCount
        }
    }

    private fun String.trimJsonString(): String {
        val value = trim()
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value.substring(1, value.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return value
    }
}
