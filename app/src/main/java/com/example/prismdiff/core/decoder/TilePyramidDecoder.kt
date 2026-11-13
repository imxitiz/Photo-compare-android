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

data class DecodedTile(
    val bitmap: Bitmap,
    val regionLeftNorm: Float = 0f,
    val regionTopNorm: Float = 0f,
    val regionRightNorm: Float = 1f,
    val regionBottomNorm: Float = 1f,
)

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
    ): DecodeOutcome<DecodedTile> = withContext(Dispatchers.IO) {
        val safeTargetW = targetWidthPx.coerceIn(64, 1440)
        val safeTargetH = targetHeightPx.coerceIn(64, 1440)

        fun fullFrameFallback(outcome: DecodeOutcome<Bitmap>): DecodeOutcome<DecodedTile> {
            return when (outcome) {
                is DecodeOutcome.Success -> DecodeOutcome.Success(DecodedTile(outcome.data, 0f, 0f, 1f, 1f))
                is DecodeOutcome.Failure -> outcome
            }
        }

        // When unzoomed or nearly fit-to-screen, a cached base mipmap is faster & avoids region I/O churn
        if (transform.zoomFactor <= 1.35f) {
            return@withContext fullFrameFallback(
                decodeWorkingBuffer(asset, max(safeTargetW, safeTargetH).coerceAtMost(1280)),
            )
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
                    return@withContext fullFrameFallback(decodeWorkingBuffer(asset, 1024))
                }
                try {
                    val rawW = regionDecoder.width
                    val rawH = regionDecoder.height
                    if (rawW <= 0 || rawH <= 0) {
                        return@withContext DecodeOutcome.Failure(ImageLoadError.CorruptedFile("Empty image dimensions"))
                    }

                    val halfSpan = (0.75f / transform.zoomFactor.coerceAtLeast(1f)).coerceIn(0.015f, 0.5f)
                    val dispLeft = (transform.centerU - halfSpan).coerceIn(0f, 1f)
                    val dispTop = (transform.centerV - halfSpan).coerceIn(0f, 1f)
                    val dispRight = (transform.centerU + halfSpan).coerceIn(0f, 1f)
                    val dispBottom = (transform.centerV + halfSpan).coerceIn(0f, 1f)

                    val (rawU0, rawV0) = displayNormToRawNorm(dispLeft, dispTop, asset.orientationDegrees)
                    val (rawU1, rawV1) = displayNormToRawNorm(dispRight, dispBottom, asset.orientationDegrees)
                    val minRawU = min(rawU0, rawU1).coerceIn(0f, 1f)
                    val maxRawU = max(rawU0, rawU1).coerceIn(0f, 1f)
                    val minRawV = min(rawV0, rawV1).coerceIn(0f, 1f)
                    val maxRawV = max(rawV0, rawV1).coerceIn(0f, 1f)

                    val rect = Rect(
                        (minRawU * rawW).toInt().coerceIn(0, rawW - 1),
                        (minRawV * rawH).toInt().coerceIn(0, rawH - 1),
                        (maxRawU * rawW).roundToInt().coerceIn(1, rawW),
                        (maxRawV * rawH).roundToInt().coerceIn(1, rawH),
                    )
                    if (rect.width() < 8 || rect.height() < 8) {
                        return@withContext fullFrameFallback(decodeWorkingBuffer(asset, 1024))
                    }

                    val sampleSize = computeInSampleSize(rect.width(), rect.height(), safeTargetW, safeTargetH)
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val tile = regionDecoder.decodeRegion(rect, opts)
                        ?: return@withContext fullFrameFallback(decodeWorkingBuffer(asset, 1024))
                    val oriented = rotateIfNeeded(tile, asset.orientationDegrees)

                    val qRawL = rect.left.toFloat() / rawW
                    val qRawT = rect.top.toFloat() / rawH
                    val qRawR = rect.right.toFloat() / rawW
                    val qRawB = rect.bottom.toFloat() / rawH
                    val (dU0, dV0) = rawNormToDisplayNorm(qRawL, qRawT, asset.orientationDegrees)
                    val (dU1, dV1) = rawNormToDisplayNorm(qRawR, qRawB, asset.orientationDegrees)

                    DecodeOutcome.Success(
                        DecodedTile(
                            bitmap = oriented,
                            regionLeftNorm = min(dU0, dU1).coerceIn(0f, 1f),
                            regionTopNorm = min(dV0, dV1).coerceIn(0f, 1f),
                            regionRightNorm = max(dU0, dU1).coerceIn(0f, 1f),
                            regionBottomNorm = max(dV0, dV1).coerceIn(0f, 1f),
                        ),
                    )
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
            fullFrameFallback(decodeWorkingBuffer(asset, 1024))
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

    private fun rawNormToDisplayNorm(u: Float, v: Float, orientationDeg: Int): Pair<Float, Float> {
        return when (((orientationDeg % 360) + 360) % 360) {
            90 -> (1f - v) to u
            180 -> (1f - u) to (1f - v)
            270 -> v to (1f - u)
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
