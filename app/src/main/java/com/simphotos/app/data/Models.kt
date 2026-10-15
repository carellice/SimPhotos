package com.simphotos.app.data

import android.net.Uri

/** A photo from the device gallery, as read from MediaStore. */
data class Photo(
    val id: Long,
    val uri: Uri,
    val dateTaken: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val bucket: String,
    val isFavorite: Boolean,
    val isScreenshot: Boolean,
) {
    val megapixels: Double get() = width.toDouble() * height / 1_000_000.0
}

/** Visual fingerprint + quality metrics computed for a photo. */
data class PhotoFeatures(
    val pHash: Long,
    val dHash: Long,
    /** 4x4 grid of average RGB values (48 bytes). */
    val colorGrid: ByteArray,
    /** Variance of the Laplacian: higher means sharper. */
    val sharpness: Float,
    /** 0..1, 1 = well exposed (no heavy clipping, balanced brightness). */
    val exposure: Float,
)

data class AnalyzedPhoto(
    val photo: Photo,
    val features: PhotoFeatures,
)

data class GroupMember(
    val photo: Photo,
    val features: PhotoFeatures,
    /** Group-relative quality score in 0..100. */
    val score: Int,
    /** Similarity (0..1) to the best photo in the group. */
    val similarityToBest: Float,
    val reasons: List<String>,
)

data class SimilarGroup(
    val id: Long,
    val members: List<GroupMember>,
    val bestId: Long,
    val isExactDuplicate: Boolean,
) {
    val best: GroupMember get() = members.first { it.photo.id == bestId }
}

enum class TimeWindow(val label: String, val millis: Long) {
    MIN_1("1 min", 60_000L),
    MIN_10("10 min", 10 * 60_000L),
    HOUR_1("1 ora", 60 * 60_000L),
    DAY_1("1 giorno", 24 * 60 * 60_000L),
    UNLIMITED("Sempre", Long.MAX_VALUE),
}

enum class DeleteMode { TRASH, PERMANENT }

data class AppSettings(
    /** Minimum similarity (0.70..0.99) for two photos to be grouped. */
    val threshold: Float = 0.88f,
    val timeWindow: TimeWindow = TimeWindow.MIN_10,
    val findExactDuplicatesEverywhere: Boolean = true,
    val includeScreenshots: Boolean = false,
    val protectFavorites: Boolean = true,
    val deleteMode: DeleteMode = DeleteMode.TRASH,
)
