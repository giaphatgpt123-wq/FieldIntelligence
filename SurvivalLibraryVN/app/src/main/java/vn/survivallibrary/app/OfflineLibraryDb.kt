package vn.survivallibrary.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local-first storage for the app. Production rows are never seeded as verified
 * content by the APK. They are installed only through a validated data package.
 */
class OfflineLibraryDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        createCoreTables(db)
        createPackageTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createPackageTables(db)
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE library_records ADD COLUMN package_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_package ON library_records(package_id)")
        }
    }

    private fun createCoreTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS library_records (
                id TEXT PRIMARY KEY,
                package_id TEXT NOT NULL DEFAULT '',
                vietnamese_name TEXT NOT NULL,
                category_id TEXT NOT NULL,
                usage_level TEXT NOT NULL,
                verification_state TEXT NOT NULL,
                summary TEXT NOT NULL DEFAULT '',
                published INTEGER NOT NULL DEFAULT 0,
                high_risk INTEGER NOT NULL DEFAULT 0,
                source_count INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_category ON library_records(category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_published ON library_records(published)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_package ON library_records(package_id)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorites (
                record_id TEXT PRIMARY KEY,
                saved_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS library_meta (
                meta_key TEXT PRIMARY KEY,
                meta_value TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createPackageTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS content_packages (
                package_id TEXT PRIMARY KEY,
                version INTEGER NOT NULL,
                status TEXT NOT NULL,
                record_count INTEGER NOT NULL DEFAULT 0,
                verified_count INTEGER NOT NULL DEFAULT 0,
                installed_at INTEGER NOT NULL DEFAULT 0,
                checksum TEXT NOT NULL DEFAULT '',
                source_uri TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS record_sources (
                record_id TEXT NOT NULL,
                source_key TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                publisher TEXT NOT NULL DEFAULT '',
                uri TEXT NOT NULL DEFAULT '',
                checked_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(record_id, source_key)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS field_verification (
                record_id TEXT NOT NULL,
                field_key TEXT NOT NULL,
                status TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(record_id, field_key)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS record_media (
                media_id TEXT PRIMARY KEY,
                record_id TEXT NOT NULL,
                local_path TEXT NOT NULL DEFAULT '',
                source_uri TEXT NOT NULL DEFAULT '',
                verified INTEGER NOT NULL DEFAULT 0,
                angle_label TEXT NOT NULL DEFAULT '',
                checksum TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sources_record ON record_sources(record_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_record ON record_media(record_id)")
    }

    fun favoriteIds(): Set<String> {
        val result = linkedSetOf<String>()
        readableDatabase.query("favorites", arrayOf("record_id"), null, null, null, null, "saved_at DESC").use { cursor ->
            val index = cursor.getColumnIndexOrThrow("record_id")
            while (cursor.moveToNext()) result += cursor.getString(index)
        }
        return result
    }

    fun setFavorite(recordId: String, favorite: Boolean) {
        if (favorite) {
            val values = ContentValues().apply {
                put("record_id", recordId)
                put("saved_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            writableDatabase.delete("favorites", "record_id = ?", arrayOf(recordId))
        }
    }

    fun installedPackageStates(): List<InstalledPackageState> {
        val result = mutableListOf<InstalledPackageState>()
        readableDatabase.query(
            "content_packages",
            arrayOf("package_id", "version", "status", "record_count", "verified_count", "installed_at", "checksum", "source_uri"),
            null,
            null,
            null,
            null,
            "package_id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val rawStatus = cursor.getString(2)
                val status = PackageInstallStatus.entries.firstOrNull { it.name == rawStatus } ?: PackageInstallStatus.BLOCKED
                result += InstalledPackageState(
                    packageId = cursor.getString(0),
                    version = cursor.getInt(1),
                    status = status,
                    recordCount = cursor.getInt(3),
                    verifiedCount = cursor.getInt(4),
                    installedAt = cursor.getLong(5),
                    checksum = cursor.getString(6),
                    sourceUri = cursor.getString(7)
                )
            }
        }
        return result
    }

    fun installVerifiedPackage(manifest: LibraryPackageManifest, records: List<LibraryPackageRecord>): Int {
        val manifestDecision = LibraryDataPackages.validate(manifest)
        require(manifestDecision.valid) { manifestDecision.blockers.joinToString("; ") }
        val recordsDecision = LibraryDataPackages.validateRecords(manifest, records)
        require(recordsDecision.valid) { recordsDecision.blockers.joinToString("; ") }

        val database = writableDatabase
        val installedAt = System.currentTimeMillis()
        database.beginTransaction()
        try {
            database.delete("library_records", "package_id = ?", arrayOf(manifest.packageId))
            records.forEach { record ->
                val values = ContentValues().apply {
                    put("id", record.id)
                    put("package_id", manifest.packageId)
                    put("vietnamese_name", record.vietnameseName)
                    put("category_id", record.categoryId)
                    put("usage_level", record.usageLevel.name)
                    put("verification_state", record.verificationState.name)
                    put("summary", record.summary)
                    put("published", 1)
                    put("high_risk", if (record.highRisk) 1 else 0)
                    put("source_count", record.sourceCount)
                    put("updated_at", installedAt)
                }
                val rowId = database.insertWithOnConflict(
                    "library_records",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                require(rowId != -1L) { "Không thể ghi hồ sơ ${record.id}" }
            }

            val packageValues = ContentValues().apply {
                put("package_id", manifest.packageId)
                put("version", manifest.version)
                put("status", PackageInstallStatus.INSTALLED.name)
                put("record_count", manifest.recordCount)
                put("verified_count", manifest.verifiedCount)
                put("installed_at", installedAt)
                put("checksum", manifest.sha256.lowercase())
                put("source_uri", manifest.sourceUri)
            }
            val packageRow = database.insertWithOnConflict(
                "content_packages",
                null,
                packageValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            require(packageRow != -1L) { "Không thể ghi trạng thái gói ${manifest.packageId}" }

            val meta = ContentValues().apply {
                put("meta_key", "last_data_update_at")
                put("meta_value", installedAt.toString())
            }
            database.insertWithOnConflict("library_meta", null, meta, SQLiteDatabase.CONFLICT_REPLACE)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return records.size
    }

    fun upsertPackageState(state: InstalledPackageState) {
        val values = ContentValues().apply {
            put("package_id", state.packageId)
            put("version", state.version)
            put("status", state.status.name)
            put("record_count", state.recordCount)
            put("verified_count", state.verifiedCount)
            put("installed_at", state.installedAt)
            put("checksum", state.checksum)
            put("source_uri", state.sourceUri)
        }
        writableDatabase.insertWithOnConflict("content_packages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setMeta(key: String, value: String) {
        val values = ContentValues().apply {
            put("meta_key", key)
            put("meta_value", value)
        }
        writableDatabase.insertWithOnConflict("library_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMeta(key: String): String? = readableDatabase.query(
        "library_meta",
        arrayOf("meta_value"),
        "meta_key = ?",
        arrayOf(key),
        null,
        null,
        null,
        "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun publishedCount(): Int = scalarCount("SELECT COUNT(*) FROM library_records WHERE published = 1")

    fun verifiedCount(): Int = scalarCount(
        "SELECT COUNT(*) FROM library_records WHERE verification_state IN ('DA_KIEM_CHUNG','DA_PHAT_HANH')"
    )

    private fun scalarCount(sql: String): Int = readableDatabase.rawQuery(sql, null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    companion object {
        private const val DB_NAME = "survival_library_vn.db"
        private const val DB_VERSION = 3
    }
}
