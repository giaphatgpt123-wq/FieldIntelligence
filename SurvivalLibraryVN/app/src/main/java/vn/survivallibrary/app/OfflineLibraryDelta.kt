package vn.survivallibrary.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * Applies a verified delta without deleting the rest of the logical package.
 * The whole operation is atomic: additions/changes, explicit removals and the
 * package state are committed together or rolled back together.
 */
fun OfflineLibraryDb.installVerifiedDeltaPackage(
    manifest: LibraryPackageManifest,
    records: List<LibraryPackageRecord>,
    removedRecordIds: List<String>
): Int {
    require(manifest.updateMode == PackageUpdateMode.DELTA) { "Gói không ở chế độ DELTA" }

    val manifestDecision = LibraryDataPackages.validate(manifest)
    require(manifestDecision.valid) { manifestDecision.blockers.joinToString("; ") }
    val recordsDecision = LibraryDataPackages.validateRecords(manifest, records)
    require(recordsDecision.valid) { recordsDecision.blockers.joinToString("; ") }

    val removed = removedRecordIds.map { it.trim() }.filter { it.isNotEmpty() }
    require(removed.size == removedRecordIds.size) { "Danh sách thu hồi có ID rỗng" }
    require(removed.distinct().size == removed.size) { "Danh sách thu hồi có ID trùng" }
    require(removed.none { id -> records.any { it.id == id } }) {
        "Một hồ sơ không thể vừa cập nhật vừa bị thu hồi trong cùng gói"
    }

    val database = writableDatabase
    val installedAt = System.currentTimeMillis()
    database.beginTransaction()
    try {
        removed.forEach { recordId ->
            val args = arrayOf(recordId)
            database.delete("record_sources", "record_id = ?", args)
            database.delete("field_verification", "record_id = ?", args)
            database.delete("record_media", "record_id = ?", args)
            database.delete("favorites", "record_id = ?", args)
            database.delete(
                "library_records",
                "id = ? AND package_id = ?",
                arrayOf(recordId, manifest.packageId)
            )
        }

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

        val cumulativeRecordCount = packageCount(database, manifest.packageId, verifiedOnly = false)
        val cumulativeVerifiedCount = packageCount(database, manifest.packageId, verifiedOnly = true)

        val packageValues = ContentValues().apply {
            put("package_id", manifest.packageId)
            put("version", manifest.version)
            put("status", PackageInstallStatus.INSTALLED.name)
            put("record_count", cumulativeRecordCount)
            put("verified_count", cumulativeVerifiedCount)
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

private fun packageCount(database: SQLiteDatabase, packageId: String, verifiedOnly: Boolean): Int {
    val where = if (verifiedOnly) {
        "package_id = ? AND published = 1 AND verification_state IN ('DA_KIEM_CHUNG','DA_PHAT_HANH')"
    } else {
        "package_id = ? AND published = 1"
    }
    return database.rawQuery(
        "SELECT COUNT(*) FROM library_records WHERE $where",
        arrayOf(packageId)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
}
