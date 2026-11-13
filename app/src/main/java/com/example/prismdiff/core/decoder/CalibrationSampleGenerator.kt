package com.example.prismdiff.core.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.prismdiff.core.model.BurstCluster
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.vision.BurstClusteringEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates deterministic, realistic offline calibration image pairs inside app cache
 * (with genuine EXIF headers, controlled sharpness, compression, exposure, and alignment shifts)
 * so every user and manual reviewer can test every PrismDiff capability immediately.
 */
object CalibrationSampleGenerator {

    data class SamplePack(
        val assets: List<ImageAsset>,
        val clusters: List<BurstCluster>,
    )

    suspend fun ensureCalibrationSamples(context: Context): SamplePack = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "calibration").apply { mkdirs() }
        val specs = listOf(
            SampleSpec("calib_burst_sharp.jpg", "Burst_01_Sharp_ISO400.jpg", 96, 400, "1/1000", "2.8", "2026:11:12 08:15:10", Variant.SHARP_STUDIO),
            SampleSpec("calib_burst_soft.jpg", "Burst_02_Soft_ISO800.jpg", 94, 800, "1/500", "2.8", "2026:11:12 08:15:11", Variant.SOFT_MOTION),
            SampleSpec("calib_comp_ref.jpg", "Export_Q96_Master.jpg", 96, 100, "1/250", "1.8", "2026:11:12 09:30:00", Variant.SHARP_STUDIO),
            SampleSpec("calib_comp_recomp.jpg", "Export_Q58_Social_Edit.jpg", 58, 100, "1/250", "1.8", "2026:11:12 09:32:10", Variant.COMPRESSED_SPLICE),
            SampleSpec("calib_align_shift.jpg", "Handheld_Shifted_Warm.jpg", 92, 640, "1/640", "2.0", "2026:11:12 08:15:12", Variant.SHIFTED_WARM),
        )

        val assets = specs.map { spec ->
            val file = File(dir, spec.fileName)
            if (!file.exists() || file.length() < 1024L) {
                renderSampleFile(file, spec)
            }
            val asset = MetadataExtractor.inspectAsset(
                context = context,
                uri = Uri.fromFile(file),
                displayName = spec.displayName,
                folderName = "Calibration Lab",
                fileSizeBytes = file.length(),
                mimeType = "image/jpeg",
                dateModifiedMs = file.lastModified(),
                isSampleAsset = true,
            )
            // Compute dHash from working buffer
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            val hash = if (bmp != null) {
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val h = BurstClusteringEngine.computeDHash(px, bmp.width, bmp.height)
                bmp.recycle()
                h
            } else null
            asset.copy(dHash = hash)
        }

        val clusters = listOf(
            BurstCluster(
                id = "calib_burst",
                title = "Calibration: Sharpness & Handheld Shift (3 shots)",
                reason = "Grouped by: visual dHash scene match + captured within 2s",
                assets = listOf(assets[0], assets[1], assets[4]),
                confidence = 0.96f,
            ),
            BurstCluster(
                id = "calib_forensic",
                title = "Calibration: JPEG Compression & ELA Forensics (2 shots)",
                reason = "Grouped by: identical resolution & scene geometry (Q96 vs Q58)",
                assets = listOf(assets[2], assets[3]),
                confidence = 0.98f,
            ),
        )
        SamplePack(assets = assets, clusters = clusters)
    }

    private enum class Variant {
        SHARP_STUDIO,
        SOFT_MOTION,
        COMPRESSED_SPLICE,
        SHIFTED_WARM,
    }

    private data class SampleSpec(
        val fileName: String,
        val displayName: String,
        val jpegQuality: Int,
        val iso: Int,
        val exposureTime: String,
        val fNumber: String,
        val dateTaken: String,
        val variant: Variant,
    )

    private fun renderSampleFile(target: File, spec: SampleSpec) {
        val w = 960
        val h = 640
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (spec.variant == Variant.SHIFTED_WARM) {
            canvas.translate(22f, -14f)
            canvas.rotate(1.6f, w * 0.5f, h * 0.5f)
        }

        val skyColorTop = if (spec.variant == Variant.SHIFTED_WARM) Color.rgb(24, 48, 72) else Color.rgb(16, 40, 68)
        val skyColorBot = if (spec.variant == Variant.SHIFTED_WARM) Color.rgb(242, 165, 95) else Color.rgb(198, 138, 92)
        val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), skyColorTop, skyColorBot, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(-40f, -40f, w + 40f, h + 40f, skyPaint)

        // Mountain ridges
        val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (spec.variant == Variant.SHIFTED_WARM) Color.rgb(45, 58, 74) else Color.rgb(34, 48, 66)
        }
        val ridgePath = Path().apply {
            moveTo(-40f, h * 0.72f)
            lineTo(w * 0.25f, h * 0.42f)
            lineTo(w * 0.52f, h * 0.60f)
            lineTo(w * 0.78f, h * 0.35f)
            lineTo(w + 40f, h * 0.65f)
            lineTo(w + 40f, h + 40f)
            lineTo(-40f, h + 40f)
            close()
        }
        canvas.drawPath(ridgePath, ridgePaint)

        // High-frequency optical resolution chart & plumage-like fine lines
        val chartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(235, 245, 250)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val cx = w * 0.5f
        val cy = h * 0.52f
        for (i in 0 until 24) {
            val angle = i * (Math.PI * 2.0 / 24.0)
            val rIn = 18.0
            val rOut = 128.0
            canvas.drawLine(
                (cx + cos(angle) * rIn).toFloat(),
                (cy + sin(angle) * rIn).toFloat(),
                (cx + cos(angle) * rOut).toFloat(),
                (cy + sin(angle) * rOut).toFloat(),
                chartPaint,
            )
        }
        for (r in listOf(36f, 68f, 102f, 134f)) {
            canvas.drawCircle(cx, cy, r, chartPaint)
        }

        // Color checker patches on lower edge for ΔE2000 & vectorscope verification
        val patchColors = listOf(
            Color.rgb(220, 68, 75),
            Color.rgb(65, 195, 125),
            Color.rgb(65, 145, 235),
            Color.rgb(240, 192, 65),
            Color.rgb(185, 85, 210),
            Color.rgb(92, 225, 230),
        )
        val patchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        patchColors.forEachIndexed { idx, col ->
            patchPaint.color = if (spec.variant == Variant.SHIFTED_WARM) {
                Color.rgb(
                    (Color.red(col) + 18).coerceAtMost(255),
                    Color.green(col),
                    (Color.blue(col) - 16).coerceAtLeast(0),
                )
            } else col
            val left = 160f + idx * 108f
            canvas.drawRect(left, h - 96f, left + 92f, h - 36f, patchPaint)
        }

        // Simulated localized edit/splice for ELA forensics
        if (spec.variant == Variant.COMPRESSED_SPLICE) {
            val splicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 245, 248)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(w * 0.78f, h * 0.35f, 34f, splicePaint)
        }

        val finalBmp = if (spec.variant == Variant.SOFT_MOTION) {
            // Downscale & upscale to simulate motion/focus softness
            val small = Bitmap.createScaledBitmap(bmp, w / 4, h / 4, true)
            val blurred = Bitmap.createScaledBitmap(small, w, h, true)
            small.recycle()
            bmp.recycle()
            blurred
        } else {
            bmp
        }

        FileOutputStream(target).use { out ->
            finalBmp.compress(Bitmap.CompressFormat.JPEG, spec.jpegQuality, out)
        }
        finalBmp.recycle()

        // Embed rich EXIF metadata
        val exif = ExifInterface(target.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, "PrismLab")
        exif.setAttribute(ExifInterface.TAG_MODEL, "PrismCam Pro-1")
        exif.setAttribute(ExifInterface.TAG_LENS_MODEL, "50mm f/1.8 Prime")
        exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "50/1")
        exif.setAttribute(ExifInterface.TAG_F_NUMBER, spec.fNumber)
        exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, if (spec.exposureTime.contains("/")) {
            val parts = spec.exposureTime.split("/")
            (parts[0].toDouble() / parts[1].toDouble()).toString()
        } else spec.exposureTime)
        exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, spec.iso.toString())
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, spec.dateTaken)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "PrismDiff Calibration Engine")
        exif.setAttribute(ExifInterface.TAG_COLOR_SPACE, "1")
        exif.saveAttributes()
    }
}
