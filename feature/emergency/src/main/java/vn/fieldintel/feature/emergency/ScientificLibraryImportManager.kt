package vn.fieldintel.feature.emergency

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject

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

    private data class ManifestEntry(
        val sha256: String,
        val sizeBytes: Long,
        val schemaVersion: Int,
        val scope: String
    )

    /** Imports the GitHub Actions artifact ZIP containing both required SQLite files and manifest. */
    fun importBundle(uri: Uri): BundleImportResult =
        context.contentResolver.openInputStream(uri)?.use(::importBundle)
            ?: error("Không thể mở gói ZIP đã chọn")

    /** Installs the source-verified pack packaged inside the APK on a clean installation. */
    fun installBundledIfMissing(): BundleImportResult? {
        val directory = libraryDirectory()
        if (PackType.entries.any { File(directory, it.fileName).isFile }) return null
        return context.assets.open("scientific-library/FieldIntelligence-WFO-mobile.zip").use(::importBundle)
    }

    private fun importBundle(input: InputStream): BundleImportResult {
        val directory = libraryDirectory()
        val staged = PackType.entries.associateWith { type ->
            File(directory, ".${type.fileName}.incoming").also { it.delete() }
        }
        val found = mutableSetOf<PackType>()
        var manifestText: String? = null
        var entryCount = 0
        try {
            input.buffered().use { source ->
                ZipInputStream(source).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entryCount += 1
                        require(entryCount <= MAX_ZIP_ENTRIES) { "Gói ZIP có quá nhiều mục" }
                        if (entry.isDirectory) continue
                        val baseName = entry.name.substringAfterLast('/')
                        if (baseName == MANIFEST_NAME) {
                            require(manifestText == null) { "Gói ZIP có manifest trùng" }
                            manifestText = readZipTextBounded(zip, MAX_MANIFEST_BYTES)
                            continue
                        }
                        val type = PackType.entries.firstOrNull { it.fileName == baseName } ?: continue
                        require(type !in found) { "Gói ZIP có tệp trùng: ${type.fileName}" }
                        copyZipEntryBounded(zip, staged.getValue(type), type.maxBytes)
                        found += type
                    }
                }
            }

            require(found == PackType.entries.toSet()) {
                "Gói ZIP phải chứa ${PackType.entries.joinToString { it.fileName }}"
            }
            val manifest = parseManifest(requireNotNull(manifestText) { "Thiếu $MANIFEST_NAME" })
            PackType.entries.forEach { type -> verifyManifest(staged.getValue(type), type, manifest.getValue(type)) }

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

    private fun parseManifest(text: String): Map<PackType, ManifestEntry> {
        val root = JSONObject(text)
        require(root.optInt("manifestVersion", -1) == 1) { "Manifest version không được hỗ trợ" }
        require(root.optString("bundle") in ACCEPTED_BUNDLE_NAMES) { "Tên scientific bundle không hợp lệ" }
        val files = root.optJSONArray("files") ?: error("Manifest thiếu danh sách files")
        val entries = mutableMapOf<PackType, ManifestEntry>()
        for (index in 0 until files.length()) {
            val item = files.getJSONObject(index)
            val type = PackType.entries.firstOrNull { it.fileName == item.optString("name") } ?: continue
            require(type !in entries) { "Manifest lặp ${type.fileName}" }
            val sha = item.optString("sha256").lowercase()
            require(SHA256_REGEX.matches(sha)) { "SHA-256 không hợp lệ cho ${type.fileName}" }
            entries[type] = ManifestEntry(
                sha256 = sha,
                sizeBytes = item.optLong("sizeBytes", -1L),
                schemaVersion = item.optInt("schemaVersion", -1),
                scope = item.optString("scope")
            )
        }
        require(entries.keys == PackType.entries.toSet()) { "Manifest không mô tả đủ hai SQLite bắt buộc" }
        return entries
    }

    private fun verifyManifest(file: File, type: PackType, manifest: ManifestEntry) {
        require(manifest.sizeBytes in 1..type.maxBytes) { "Kích thước manifest không hợp lệ cho ${type.fileName}" }
        require(file.length() == manifest.sizeBytes) {
            "Sai kích thước ${type.fileName}: ${file.length()} != ${manifest.sizeBytes}"
        }
        require(manifest.schemaVersion == type.expectedSchemaVersion) { "Manifest schema không khớp ${type.fileName}" }
        require(manifest.scope == type.expectedScope) { "Manifest scope không khớp ${type.fileName}" }
        require(file.sha256() == manifest.sha256) { "SHA-256 không khớp ${type.fileName}" }
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
            message = "Đã cài thư viện khoa học: ${taxonomy.recordCount} taxonomy + ${evidence.recordCount} evidence • SHA-256 OK"
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

    private fun readZipTextBounded(zip: ZipInputStream, maxBytes: Int): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Manifest quá lớn" }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
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

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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

    companion object {
        private const val MANIFEST_NAME = "scientific-library.manifest.json"
        private val ACCEPTED_BUNDLE_NAMES = setOf(
            "FieldIntelligence-WFO-scientific-library",
            "FieldIntelligence-WFO-mobile-selected-genera"
        )
        private const val MAX_ZIP_ENTRIES = 32
        private const val MAX_MANIFEST_BYTES = 64 * 1024
        private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
