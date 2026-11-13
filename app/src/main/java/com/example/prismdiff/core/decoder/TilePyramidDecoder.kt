package com.example.prismdiff.core.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.example.prismdiff.core.model.EffectiveSlotTransform
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.ImageLoadError
import com.example.prismdiff.core.model.PixelSample
import com.example.prismdiff.core.vision.ColorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface DecodeOutcome<out T> {
    data class Success<T>(val data: T) : DecodeOutcome<T>
    data class Failure(val error: ImageLoadError) : DecodeOutcome<Nothing>
}

/**
 * Memory-budgeted Subsampled Tile-Pyramid Decoder powered by [BitmapRegionDecoder].
 *
 * - Never decodes 50MP–200MP images at full size into heap memory.
 * - Uses resolution-aware inSampleSize for thumbnails and working buffers.
 * - Extracts exact cropped viewport regions at dynamic mip levels based on zoom.
 * - Caches decoded thumbnails and viewport tiles inside a strict byte-budgeted LruCache.
 */
class TilePyramidDecoder(private val context: Context) {

    private val maxCacheBytes = ((Runtime.getRuntime().maxMemory() / 1024L).toInt() / 6)
        .coerceIn(16 * 1024, 96 * 1024) // KiB

    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }

    suspend fun decodeThumbnail(
        asset: ImageAsset,
        maxDimensionPx: Int = 360,
    ): DecodeOutcome<Bitmap> = withContext(Dispatchers.IO) {
        val cacheKey = "thumb_${asset.id}_$maxDimensionPx"
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext DecodeOutcome.Success(cached)
        }
        decodeSampledBitmap(asset.uriString, maxDimensionPx, asset.orientationDegrees)?.let { bmp ->
            bitmapCache.put(cacheKey, bmp)
            DecodeOutcome.Success(bmp)
        } ?: DecodeOutcome.Failure(ImageLoadError.CorruptedFile("Unable to decode thumbnail for ${asset.displayName}"))
    }

    /**
     * Decodes a resolution-capped working buffer (e.g. max 512px) for fast deterministic CV
     * metrics, histograms, alignment, and real-time preview overlays.
     */
    suspend fun decodeWorkingBuffer(
        asset: ImageAsset,
        maxDimensionPx: Int = 512,
    ): DecodeOutcome<Bitmap> = withContext(Dispatchers.IO) {
        val cacheKey = "work_${asset.id}_$maxDimensionPx"
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext DecodeOutcome.Success(cached)
        }
        decodeSampledBitmap(asset.uriString, maxDimensionPx, asset.orientationDegrees)?.let { bmp ->
            bitmapCache.put(cacheKey, bmp)
            DecodeOutcome.Success(bmp)
        } ?: DecodeOutcome.Failure(ImageLoadError.UnsupportedFormat("Unable to decode ${asset.displayName}"))
    }

    /**
     * Region-decodes the visible tile pyramid slice matching [transform] at target screen dimensions.
     * Falls back seamlessly to sampled decoding for formats without [BitmapRegionDecoder] support (e.g., GIF/SVG).
     */
    suspend fun decodeViewportTile(
        asset: ImageAsset,
        transform: EffectiveSlotTransform,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): DecodeOutcome<Bitmap> = withContext(Dispatchers.IO) {
        val safeTargetW = targetWidthPx.coerceIn(64, 1440)
        val safeTargetH = targetHeightPx.coerceIn(64, 1440)

        // When unzoomed or nearly fit-to-screen, a cached base mipmap is faster & avoids region I/O churn
        if (transform.zoomFactor <= 1.35f) {
            return@withContext decodeWorkingBuffer(asset, max(safeTargetW, safeTargetH).coerceAtMost(1280))
        }

        val uri = Uri.parse(asset.uriString)
        try {
            openInputStream(uri)?.use { stream ->
                val regionDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(stream)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(stream, false)
                }
                if (regionDecoder == null) {
                    return@withContext decodeWorkingBuffer(asset, 1024)
                }
                try {
                    val rawW = regionDecoder.width
                    val rawH = regionDecoder.height
                    if (rawW <= 0 || rawH <= 0) {
                        return@withContext DecodeOutcome.Failure(ImageLoadError.CorruptedFile("Empty image dimensions"))
                    }

                    // Convert normalized display center to unrotated source coordinates
                    val (srcU, srcV) = displayNormToRawNorm(
                        transform.centerU,
                        transform.centerV,
                        asset.orientationDegrees,
                    )
                    val spanFrac = (1.15f / transform.zoomFactor.coerceAtLeast(1f)).coerceIn(0.015f, 1f)
                    val halfW = (rawW * spanFrac * 0.5f).roundToInt().coerceAtLeast(16)
                    val halfH = (rawH * spanFrac * 0.5f).roundToInt().coerceAtLeast(16)
                    val cx = (srcU * rawW).roundToInt()
                    val cy = (srcV * rawH).roundToInt()

                    val rect = Rect(
                        (cx - halfW).coerceIn(0, rawW - 1),
                        (cy - halfH).coerceIn(0, rawH - 1),
                        (cx + halfW).coerceIn(1, rawW),
                        (cy + halfH).coerceIn(1, rawH),
                    )
                    if (rect.width() <= 0 || rect.height() <= 0) {
                        return@withContext decodeWorkingBuffer(asset, 1024)
                    }

                    val sampleSize = computeInSampleSize(rect.width(), rect.height(), safeTargetW, safeTargetH)
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val tile = regionDecoder.decodeRegion(rect, opts)
                        ?: return@withContext decodeWorkingBuffer(asset, 1024)
                    val oriented = rotateIfNeeded(tile, asset.orientationDegrees)
                    DecodeOutcome.Success(oriented)
                } finally {
                    regionDecoder.recycle()
                }
            } ?: DecodeOutcome.Failure(ImageLoadError.FileNotFound("Cannot open ${asset.displayName}"))
        } catch (oom: OutOfMemoryError) {
            clearCache()
            DecodeOutcome.Failure(ImageLoadError.OutOfMemory("Memory budget exceeded decoding ${asset.displayName}"))
        } catch (sec: SecurityException) {
            DecodeOutcome.Failure(ImageLoadError.PermissionDenied("Permission revoked for ${asset.displayName}"))
        } catch (t: Throwable) {
            // Fallback for formats not supported by BitmapRegionDecoder (AVIF/GIF/etc.)
            decodeWorkingBuffer(asset, 1024)
        }
    }

    /**
     * Extracts exact pixel color metrics `(R, G, B, HEX, HSL, CIE L*a*b*)` at normalized coordinates `(u, v)`.
     */
    suspend fun samplePixelAt(
        asset: ImageAsset,
        normU: Float,
        normV: Float,
    ): PixelSample? = withContext(Dispatchers.IO) {
        val outcome = decodeWorkingBuffer(asset, 1024)
        if (outcome !is DecodeOutcome.Success) return@withContext null
        val bmp = outcome.data
        val px = (normU.coerceIn(0f, 1f) * (bmp.width - 1)).roundToInt().coerceIn(0, bmp.width - 1)
        val py = (normV.coerceIn(0f, 1f) * (bmp.height - 1)).roundToInt().coerceIn(0, bmp.height - 1)
        val argb = bmp.getPixel(px, py)
        val realX = if (asset.width > 0) (normU.coerceIn(0f, 1f) * (asset.width - 1)).roundToInt() else px
        val realY = if (asset.height > 0) (normV.coerceIn(0f, 1f) * (asset.height - 1)).roundToInt() else py
        ColorMath.samplePixel(argb, realX, realY)
    }

    private fun decodeSampledBitmap(
        uriString: String,
        maxDimensionPx: Int,
        orientationDegrees: Int,
    ): Bitmap? {
        val uri = Uri.parse(uriString)
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx, maxDimensionPx)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            rotateIfNeeded(raw, orientationDegrees)
        } catch (_: Throwable) {
            null
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { java.io.File(it).takeIf { f -> f.exists() }?.inputStream() }
            else -> context.contentResolver.openInputStream(uri)
        }
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientationDegrees: Int): Bitmap {
        val deg = ((orientationDegrees % 360) + 360) % 360
        if (deg == 0) return bitmap
        val matrix = Matrix().apply { postRotate(deg.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun displayNormToRawNorm(u: Float, v: Float, orientationDeg: Int): Pair<Float, Float> {
        return when (((orientationDeg % 360) + 360) % 360) {
            90 -> v to (1f - u)
            180 -> (1f - u) to (1f - v)
            270 -> (1f - v) to u
            else -> u to v
        }
    }

    companion object {
        fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
            var inSampleSize = 1
            if (srcH > reqH || srcW > reqW) {
                val halfH = srcH / 2
                val halfW = srcW / 2
                while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize.coerceAtLeast(1)
        }
    }
}
