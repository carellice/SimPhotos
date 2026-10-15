package com.simphotos.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class PhotoRepository(private val context: Context) {

    fun loadPhotos(): List<Photo> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.IS_FAVORITE,
            MediaStore.Images.Media.ORIENTATION,
        )
        val photos = ArrayList<Photo>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val favCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)
            val orientCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val bucket = c.getString(bucketCol).orEmpty()
                val path = c.getString(pathCol).orEmpty()
                val rotated = c.getInt(orientCol) % 180 != 0
                val w = c.getInt(wCol)
                val h = c.getInt(hCol)
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)
                photos += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    dateTaken = taken,
                    dateModified = c.getLong(modCol),
                    width = if (rotated) h else w,
                    height = if (rotated) w else h,
                    sizeBytes = c.getLong(sizeCol),
                    bucket = bucket,
                    isFavorite = c.getInt(favCol) == 1,
                    isScreenshot = bucket.contains("screenshot", ignoreCase = true) ||
                        path.contains("screenshot", ignoreCase = true),
                )
            }
        }
        return photos.sortedBy { it.dateTaken }
    }
}
