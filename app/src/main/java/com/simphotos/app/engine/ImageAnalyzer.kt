package com.simphotos.app.engine

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Size
import com.simphotos.app.data.Photo
import com.simphotos.app.data.PhotoFeatures
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Computes perceptual hashes (pHash + dHash), a coarse colour signature and quality metrics
 * (sharpness, exposure) from a downscaled thumbnail of the photo.
 */
class ImageAnalyzer(private val resolver: ContentResolver) {

    fun analyze(photo: Photo): PhotoFeatures? {
        val thumb = runCatching {
            resolver.loadThumbnail(photo.uri, Size(THUMB, THUMB), null)
        }.getOrNull() ?: return null
        return try {
            val src = if (thumb.config == Bitmap.Config.ARGB_8888) thumb
            else thumb.copy(Bitmap.Config.ARGB_8888, false)
            val features = compute(src)
            if (src !== thumb) src.recycle()
            features
        } finally {
            thumb.recycle()
        }
    }

    private fun compute(bmp: Bitmap): PhotoFeatures {
        val pHash = pHash(scaled(bmp, 32, 32))
        val dHash = dHash(scaled(bmp, 9, 8))
        val color = colorGrid(scaled(bmp, 4, 4))
        val (sharpness, exposure) = quality(bmp)
        return PhotoFeatures(pHash, dHash, color, sharpness, exposure)
    }

    private fun scaled(bmp: Bitmap, w: Int, h: Int): IntArray {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            bmp,
            null,
            android.graphics.Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        out.recycle()
        return px
    }

    private fun luma(c: Int): Double =
        0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)

    /** DCT-based perceptual hash: robust to small changes in exposure, compression and scale. */
    private fun pHash(px: IntArray): Long {
        val n = 32
        val g = DoubleArray(n * n) { luma(px[it]) }
        // Separable 2D DCT-II, keeping only the 8x8 low-frequency block.
        val rows = DoubleArray(n * 8)
        for (y in 0 until n) for (u in 0 until 8) {
            var s = 0.0
            for (x in 0 until n) s += g[y * n + x] * COS[u * n + x]
            rows[y * 8 + u] = s
        }
        val dct = DoubleArray(64)
        for (v in 0 until 8) for (u in 0 until 8) {
            var s = 0.0
            for (y in 0 until n) s += rows[y * 8 + u] * COS[v * n + y]
            dct[v * 8 + u] = s
        }
        val median = dct.copyOfRange(1, 64).sorted().let { (it[30] + it[31]) / 2 }
        var hash = 0L
        for (i in 0 until 64) if (dct[i] > median) hash = hash or (1L shl i)
        return hash
    }

    /** Gradient hash: captures the structure / layout of the image. */
    private fun dHash(px: IntArray): Long {
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (luma(px[y * 9 + x]) > luma(px[y * 9 + x + 1])) hash = hash or (1L shl bit)
            bit++
        }
        return hash
    }

    private fun colorGrid(px: IntArray): ByteArray {
        val out = ByteArray(48)
        for (i in 0 until 16) {
            val c = px[i]
            out[i * 3] = ((c shr 16) and 0xFF).toByte()
            out[i * 3 + 1] = ((c shr 8) and 0xFF).toByte()
            out[i * 3 + 2] = (c and 0xFF).toByte()
        }
        return out
    }

    /** Returns (variance of Laplacian, exposure score 0..1). */
    private fun quality(bmp: Bitmap): Pair<Float, Float> {
        // Normalise to a fixed working size so sharpness is comparable between photos.
        val longSide = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
        val scale = WORK.toFloat() / longSide
        val w = (bmp.width * scale).toInt().coerceAtLeast(3)
        val h = (bmp.height * scale).toInt().coerceAtLeast(3)
        val px = scaled(bmp, w, h)
        val g = DoubleArray(w * h) { luma(px[it]) }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = 4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]
            sum += lap
            sumSq += lap * lap
            count++
        }
        val mean = sum / count
        val variance = (sumSq / count - mean * mean).toFloat()

        var bright = 0.0
        var dark = 0
        var blown = 0
        for (v in g) {
            bright += v
            if (v < 12) dark++ else if (v > 245) blown++
        }
        val avg = bright / g.size
        val clipped = (dark + blown).toDouble() / g.size
        val balance = 1.0 - abs(avg - 120.0) / 120.0
        val exposure = (0.6 * balance + 0.4 * (1.0 - (clipped * 3).coerceAtMost(1.0)))
            .coerceIn(0.0, 1.0).toFloat()
        return variance to exposure
    }

    companion object {
        private const val THUMB = 512
        private const val WORK = 384
        private val COS = DoubleArray(8 * 32).also { t ->
            for (u in 0 until 8) for (x in 0 until 32) {
                t[u * 32 + x] = cos((2 * x + 1) * u * Math.PI / 64)
            }
        }

        /** Overall visual similarity between two photos in 0..1. */
        fun similarity(a: PhotoFeatures, b: PhotoFeatures): Float {
            val p = 1f - java.lang.Long.bitCount(a.pHash xor b.pHash) / 64f
            val d = 1f - java.lang.Long.bitCount(a.dHash xor b.dHash) / 64f
            var sq = 0.0
            for (i in 0 until 48) {
                val delta = abs((a.colorGrid[i].toInt() and 0xFF) - (b.colorGrid[i].toInt() and 0xFF))
                sq += delta * delta
            }
            // RMS colour difference: 0 = identical, ~60+ = very different palette.
            val rms = sqrt(sq / 48)
            val c = (1.0 - rms / 64.0).coerceIn(0.0, 1.0).toFloat()
            return 0.5f * p + 0.3f * d + 0.2f * c
        }
    }
}
