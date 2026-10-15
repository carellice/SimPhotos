package com.simphotos.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Persists computed [PhotoFeatures] so that subsequent scans only analyze new or changed photos.
 */
class FeatureCache(context: Context) :
    SQLiteOpenHelper(context, "features.db", null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE features (
                id INTEGER PRIMARY KEY,
                date_modified INTEGER NOT NULL,
                size INTEGER NOT NULL,
                phash INTEGER NOT NULL,
                dhash INTEGER NOT NULL,
                color BLOB NOT NULL,
                sharpness REAL NOT NULL,
                exposure REAL NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS features")
        onCreate(db)
    }

    /** Returns cached features keyed by photo id, only for entries that still match the photo. */
    fun loadValid(photos: List<Photo>): Map<Long, PhotoFeatures> {
        val byId = photos.associateBy { it.id }
        val result = HashMap<Long, PhotoFeatures>(photos.size)
        readableDatabase.rawQuery(
            "SELECT id, date_modified, size, phash, dhash, color, sharpness, exposure FROM features",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val p = byId[id] ?: continue
                if (p.dateModified != c.getLong(1) || p.sizeBytes != c.getLong(2)) continue
                result[id] = PhotoFeatures(
                    pHash = c.getLong(3),
                    dHash = c.getLong(4),
                    colorGrid = c.getBlob(5),
                    sharpness = c.getFloat(6),
                    exposure = c.getFloat(7),
                )
            }
        }
        return result
    }

    fun putAll(entries: List<AnalyzedPhoto>) {
        if (entries.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues()
            for ((p, f) in entries) {
                values.clear()
                values.put("id", p.id)
                values.put("date_modified", p.dateModified)
                values.put("size", p.sizeBytes)
                values.put("phash", f.pHash)
                values.put("dhash", f.dHash)
                values.put("color", f.colorGrid)
                values.put("sharpness", f.sharpness)
                values.put("exposure", f.exposure)
                db.insertWithOnConflict("features", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.delete("features", "id IN (${ids.joinToString(",")})", null)
    }

    fun clear() {
        writableDatabase.delete("features", null, null)
    }

    private companion object {
        const val VERSION = 1
    }
}
