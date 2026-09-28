package vn.survivallibrary.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local-first storage for the new app. Production library rows are intentionally
 * not seeded here: only records that pass LibraryRules may later be published.
 */
class OfflineLibraryDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE library_records (
                id TEXT PRIMARY KEY,
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
        db.execSQL("CREATE INDEX idx_records_category ON library_records(category_id)")
        db.execSQL("CREATE INDEX idx_records_published ON library_records(published)")
        db.execSQL(
            """
            CREATE TABLE favorites (
                record_id TEXT PRIMARY KEY,
                saved_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE library_meta (
                meta_key TEXT PRIMARY KEY,
                meta_value TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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

    fun publishedCount(): Int = scalarCount("SELECT COUNT(*) FROM library_records WHERE published = 1")

    fun verifiedCount(): Int = scalarCount(
        "SELECT COUNT(*) FROM library_records WHERE verification_state IN ('DA_KIEM_CHUNG','DA_PHAT_HANH')"
    )

    private fun scalarCount(sql: String): Int = readableDatabase.rawQuery(sql, null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    companion object {
        private const val DB_NAME = "survival_library_vn.db"
        private const val DB_VERSION = 1
    }
}
