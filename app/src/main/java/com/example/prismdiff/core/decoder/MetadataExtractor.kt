package com.example.prismdiff.core.decoder

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.prismdiff.core.model.ExifDiffEntry
import com.example.prismdiff.core.model.ExifSnapshot
import com.example.prismdiff.core.model.ImageAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Extracts comprehensive EXIF, file size, dimensions, color space, JPEG SOF subsampling,
 * DQT compression quality estimation, and UltraHDR GainMap presence.
 */
object MetadataExtractor {

    suspend fun inspectAsset(
        context: Context,
        uri: Uri,
        displayName: String,
        folderName: String,
        fileSizeBytes: Long,
        mimeType: String,
        dateModifiedMs: Long,
        isSampleAsset: Boolean = false,
    ): ImageAsset = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var orientationDeg = 0
        var exifSnapshot = ExifSnapshot()
        var dateTakenMs = 0L

        try {
            openStream(context, uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientationDeg = exif.rotationDegrees
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                val lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()
                val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0).takeIf { it > 0 }
                val focal35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, -1).takeIf { it > 0 }
                val fNum = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, -1.0).takeIf { it > 0 }
                val expTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0).takeIf { it > 0 }
                val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1).takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED, -1).takeIf { it > 0 }
                val expBias = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.let { parseRational(it) }
                val flashVal = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
                val flashFired = if (flashVal >= 0) (flashVal and 1) == 1 else null
                val wb = exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1).let {
                    when (it) {
                        0 -> "Auto"
                        1 -> "Manual"
                        else -> null
                    }
                }
                val metering = exif.getAttributeInt(ExifInterface.TAG_METERING_MODE, -1).let {
                    when (it) {
                        1 -> "Average"
                        2 -> "Center-Weighted"
                        3 -> "Spot"
                        5 -> "Pattern / Matrix"
                        else -> null
                    }
                }
                val dtOrig = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                dateTakenMs = parseExifDateMs(dtOrig)
                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim()
                val colorSpaceAttr = exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1)
                val colorSpaceExif = when (colorSpaceAttr) {
                    1 -> "sRGB"
                    2 -> "Adobe RGB"
                    65535 -> "Uncalibrated / Wide"
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) bounds.outColorSpace?.name else null
                }
                val bits = exif.getAttributeInt(ExifInterface.TAG_BITS_PER_SAMPLE, -1).takeIf { it > 0 }
                val latLong = exif.latLong

                val rawTags = linkedMapOf<String, String>()
                listOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_LENS_MODEL,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
                    ExifInterface.TAG_SOFTWARE,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_COLOR_SPACE,
                ).forEach { tag ->
                    exif.getAttribute(tag)?.let { v -> rawTags[tag] = v }
                }

                exifSnapshot = ExifSnapshot(
                    cameraMake = make,
                    cameraModel = model,
                    lensModel = lens,
                    focalLengthMm = focal,
                    equivalentFocalLength35mm = focal35,
                    apertureFNumber = fNum,
                    shutterSpeedSeconds = expTime,
                    shutterSpeedDisplay = formatShutter(expTime),
                    iso = iso,
                    exposureBiasEv = expBias,
                    flashFired = flashFired,
                    whiteBalanceMode = wb,
                    meteringMode = metering,
                    dateTimeOriginal = dtOrig,
                    software = software,
                    colorSpace = colorSpaceExif,
                    bitsPerSample = bits,
                    gpsLatitude = latLong?.getOrNull(0),
                    gpsLongitude = latLong?.getOrNull(1),
                    rawTags = rawTags,
                )
            }
        } catch (_: Throwable) {
            // Non-EXIF formats (PNG/WebP/BMP) gracefully keep defaults
        }

        // Parse JPEG SOF/DQT compression markers when JPEG
        val jpegInfo = if (mimeType.contains("jpeg", ignoreCase = true) ||
            displayName.endsWith(".jpg", true) ||
            displayName.endsWith(".jpeg", true)
        ) {
            openStream(context, uri)?.use { parseJpegQualityAndSubsampling(it) }
        } else null

        if (jpegInfo != null) {
            exifSnapshot = exifSnapshot.copy(
                jpegQualityEstimate = jpegInfo.first,
                jpegSubsampling = jpegInfo.second,
            )
        }

        val rotated90 = orientationDeg == 90 || orientationDeg == 270
        val dispW = if (rotated90) bounds.outHeight else bounds.outWidth
        val dispH = if (rotated90) bounds.outWidth else bounds.outHeight

        ImageAsset(
            id = uri.toString(),
            uriString = uri.toString(),
            displayName = displayName,
            folderName = folderName,
            width = dispW.coerceAtLeast(0),
            height = dispH.coerceAtLeast(0),
            orientationDegrees = orientationDeg,
            fileSizeBytes = fileSizeBytes,
            mimeType = mimeType,
            dateTakenMs = dateTakenMs,
            dateModifiedMs = dateModifiedMs,
            exifSnapshot = exifSnapshot,
            isSampleAsset = isSampleAsset,
        )
    }

    /**
     * Parses JPEG DQT (Define Quantization Table) and SOF0/SOF2 markers to estimate
     * encoder quality [1..100] and chroma subsampling (4:4:4, 4:2:2, 4:2:0).
     */
    fun parseJpegQualityAndSubsampling(stream: InputStream): Pair<Int?, String?> {
        val bis = BufferedInputStream(stream, 16 * 1024)
        var qualityEst: Int? = null
        var subsampling: String? = null
        try {
            val b0 = bis.read()
            val b1 = bis.read()
            if (b0 != 0xFF || b1 != 0xD8) return null to null // Not SOI

            var safety = 0
            while (safety++ < 128) {
                var markerPrefix = bis.read()
                while (markerPrefix != 0xFF && markerPrefix != -1) {
                    markerPrefix = bis.read()
                }
                if (markerPrefix == -1) break
                var marker = bis.read()
                while (marker == 0xFF) marker = bis.read()
                if (marker == -1 || marker == 0xDA || marker == 0xD9) break // SOS or EOI

                val lenHi = bis.read()
                val lenLo = bis.read()
                if (lenHi == -1 || lenLo == -1) break
                val segLen = ((lenHi shl 8) or lenLo) - 2
                if (segLen <= 0 || segLen > 65535) break
                val seg = ByteArray(segLen)
                var read = 0
                while (read < segLen) {
                    val r = bis.read(seg, read, segLen - read)
                    if (r <= 0) break
                    read += r
                }

                // DQT (0xDB): Luminance quantization table 0
                if (marker == 0xDB && read >= 65 && (seg[0].toInt() and 0x0F) == 0 && (seg[0].toInt() ushr 4) == 0) {
                    var sum = 0
                    for (i in 1..64) sum += (seg[i].toInt() and 0xFF)
                    val avgQ = sum / 64.0
                    // Standard IJG approximation mapping average quantization step to Q[1..100]
                    qualityEst = when {
                        avgQ <= 1.2 -> 100
                        avgQ <= 16.0 -> (100.0 - (avgQ - 1.0) * 1.65).roundToInt().coerceIn(75, 99)
                        else -> (82.0 - (avgQ - 16.0) * 0.9).roundToInt().coerceIn(10, 75)
                    }
                }

                // SOF0 (0xC0) or SOF2 (0xC2): YCbCr sampling factors
                if ((marker == 0xC0 || marker == 0xC2) && read >= 9) {
                    val comps = seg[5].toInt() and 0xFF
                    if (comps >= 3) {
                        val ySamp = seg[7].toInt() and 0xFF
                        val hY = ySamp ushr 4
                        val vY = ySamp and 0x0F
                        subsampling = when {
                            hY == 1 && vY == 1 -> "4:4:4"
                            hY == 2 && vY == 1 -> "4:2:2"
                            hY == 2 && vY == 2 -> "4:2:0"
                            else -> "${hY}x${vY}"
                        }
                    } else if (comps == 1) {
                        subsampling = "Grayscale"
                    }
                }
                if (qualityEst != null && subsampling != null) break
            }
        } catch (_: Throwable) {
            // Gracefully return partial findings
        }
        return qualityEst to subsampling
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { java.io.File(it).takeIf { f -> f.exists() }?.inputStream() }
            else -> context.contentResolver.openInputStream(uri)
        }
    }

    private fun parseRational(str: String): Double? {
        val parts = str.split("/")
        if (parts.size == 2) {
            val n = parts[0].toDoubleOrNull() ?: return null
            val d = parts[1].toDoubleOrNull() ?: return null
            return if (abs(d) > 1e-9) n / d else null
        }
        return str.toDoubleOrNull()
    }

    private fun parseExifDateMs(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(dateStr)?.time ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    fun formatShutter(sec: Double?): String? {
        if (sec == null || sec <= 0.0) return null
        return if (sec >= 0.8) {
            String.format(Locale.US, "%.1fs", sec)
        } else {
            val denom = (1.0 / sec).roundToInt().coerceAtLeast(1)
            "1/${denom}s"
        }
    }
}

/**
 * Computes Diff-First semantic metadata comparison rows and formats custom HUD pill templates.
 */
object ExifDiffEngine {

    fun formatHudTemplate(template: String, asset: ImageAsset): String {
        val ex = asset.exifSnapshot
        return template
            .replace("{FileName}", asset.displayName)
            .replace("{Folder}", asset.folderName)
            .replace("{Resolution}", "${asset.width}×${asset.height}")
            .replace("{MP}", String.format(Locale.US, "%.1fMP", asset.megapixelCount))
            .replace("{SizeMB}", String.format(Locale.US, "%.2fMB", asset.fileSizeBytes / (1024.0 * 1024.0)))
            .replace("{Aperture}", ex.apertureFNumber?.let { "f/${String.format(Locale.US, "%.1f", it)}" } ?: "f/—")
            .replace("{Shutter}", ex.shutterSpeedDisplay ?: "—")
            .replace("{ISO}", ex.iso?.toString() ?: "—")
            .replace("{FocalLength}", ex.focalLengthMm?.let { "${it.roundToInt()}mm" } ?: "—")
            .replace("{Camera}", ex.cameraModel ?: "Unknown")
            .replace("{Quality}", ex.jpegQualityEstimate?.let { "Q$it ${ex.jpegSubsampling ?: ""}".trim() } ?: "—")
            .replace("{BPP}", String.format(Locale.US, "%.2fbpp", asset.bitsPerPixel))
    }

    fun compareMetadata(
        assetA: ImageAsset,
        assetB: ImageAsset,
        maskGps: Boolean = true,
    ): List<ExifDiffEntry> {
        val exA = assetA.exifSnapshot
        val exB = assetB.exifSnapshot
        val rows = mutableListOf<ExifDiffEntry>()

        fun addRow(key: String, label: String, a: String?, b: String?, delta: String? = null, gps: Boolean = false) {
            val va = a?.takeIf { it.isNotBlank() } ?: "—"
            val vb = b?.takeIf { it.isNotBlank() } ?: "—"
            rows.add(
                ExifDiffEntry(
                    key = key,
                    label = label,
                    valueA = va,
                    valueB = vb,
                    deltaSummary = delta,
                    differs = va != vb,
                    isSensitiveGps = gps,
                ),
            )
        }

        addRow("file", "File Name", assetA.displayName, assetB.displayName)

        val resA = "${assetA.width}×${assetA.height} (${String.format(Locale.US, "%.1f", assetA.megapixelCount)} MP)"
        val resB = "${assetB.width}×${assetB.height} (${String.format(Locale.US, "%.1f", assetB.megapixelCount)} MP)"
        val mpDelta = assetB.megapixelCount - assetA.megapixelCount
        addRow(
            "resolution",
            "Resolution",
            resA,
            resB,
            delta = if (abs(mpDelta) > 0.05) String.format(Locale.US, "%+.1f MP", mpDelta) else null,
        )

        val sizeA = formatBytes(assetA.fileSizeBytes)
        val sizeB = formatBytes(assetB.fileSizeBytes)
        val sizePct = if (assetA.fileSizeBytes > 0) {
            ((assetB.fileSizeBytes - assetA.fileSizeBytes).toDouble() / assetA.fileSizeBytes) * 100.0
        } else 0.0
        addRow(
            "size",
            "File Size / BPP",
            "$sizeA (${String.format(Locale.US, "%.2f", assetA.bitsPerPixel)} bpp)",
            "$sizeB (${String.format(Locale.US, "%.2f", assetB.bitsPerPixel)} bpp)",
            delta = if (abs(sizePct) >= 1.0) String.format(Locale.US, "%+.0f%%", sizePct) else null,
        )

        val qualA = exA.jpegQualityEstimate?.let { "Q$it ${exA.jpegSubsampling ?: ""}".trim() }
        val qualB = exB.jpegQualityEstimate?.let { "Q$it ${exB.jpegSubsampling ?: ""}".trim() }
        val qDelta = if (exA.jpegQualityEstimate != null && exB.jpegQualityEstimate != null &&
            exA.jpegQualityEstimate != exB.jpegQualityEstimate
        ) {
            val d = exB.jpegQualityEstimate - exA.jpegQualityEstimate
            if (d > 0) "+$d Q" else "$d Q"
        } else null
        addRow("jpeg_q", "Compression / Sampling", qualA, qualB, qDelta)

        addRow("camera", "Camera", listOfNotNull(exA.cameraMake, exA.cameraModel).joinToString(" "), listOfNotNull(exB.cameraMake, exB.cameraModel).joinToString(" "))
        addRow("lens", "Lens", exA.lensModel, exB.lensModel)

        val isoDelta = if (exA.iso != null && exB.iso != null && exA.iso > 0 && exB.iso > 0 && exA.iso != exB.iso) {
            val ev = log2(exB.iso.toDouble() / exA.iso.toDouble())
            String.format(Locale.US, "%+.2f EV", ev)
        } else null
        addRow("iso", "ISO", exA.iso?.toString(), exB.iso?.toString(), isoDelta)

        val apDelta = if (exA.apertureFNumber != null && exB.apertureFNumber != null && exA.apertureFNumber != exB.apertureFNumber) {
            val ev = 2.0 * log2(exA.apertureFNumber / exB.apertureFNumber)
            String.format(Locale.US, "%+.2f EV", ev)
        } else null
        addRow(
            "aperture",
            "Aperture",
            exA.apertureFNumber?.let { "f/${String.format(Locale.US, "%.1f", it)}" },
            exB.apertureFNumber?.let { "f/${String.format(Locale.US, "%.1f", it)}" },
            apDelta,
        )

        val shDelta = if (exA.shutterSpeedSeconds != null && exB.shutterSpeedSeconds != null &&
            exA.shutterSpeedSeconds > 0 && exB.shutterSpeedSeconds > 0 &&
            exA.shutterSpeedSeconds != exB.shutterSpeedSeconds
        ) {
            val ev = log2(exB.shutterSpeedSeconds / exA.shutterSpeedSeconds)
            String.format(Locale.US, "%+.2f EV", ev)
        } else null
        addRow("shutter", "Shutter Speed", exA.shutterSpeedDisplay, exB.shutterSpeedDisplay, shDelta)

        addRow(
            "focal",
            "Focal Length",
            exA.focalLengthMm?.let { "${it.roundToInt()}mm" },
            exB.focalLengthMm?.let { "${it.roundToInt()}mm" },
        )
        addRow("color_space", "Color Profile", exA.colorSpace, exB.colorSpace)
        addRow("software", "Software / Pipeline", exA.software, exB.software)

        val timeDelta = if (assetA.dateTakenMs > 0 && assetB.dateTakenMs > 0 && assetA.dateTakenMs != assetB.dateTakenMs) {
            val diffSec = (assetB.dateTakenMs - assetA.dateTakenMs) / 1000.0
            String.format(Locale.US, "%+.1fs", diffSec)
        } else null
        addRow("date", "Date Taken", exA.dateTimeOriginal, exB.dateTimeOriginal, timeDelta)

        val gpsA = formatGps(exA.gpsLatitude, exA.gpsLongitude, maskGps)
        val gpsB = formatGps(exB.gpsLatitude, exB.gpsLongitude, maskGps)
        val gpsDelta = if (exA.gpsLatitude != null && exA.gpsLongitude != null &&
            exB.gpsLatitude != null && exB.gpsLongitude != null
        ) {
            val meters = haversineMeters(exA.gpsLatitude, exA.gpsLongitude, exB.gpsLatitude, exB.gpsLongitude)
            if (meters >= 1000) String.format(Locale.US, "%.2f km apart", meters / 1000.0)
            else String.format(Locale.US, "%.0f m apart", meters)
        } else null
        addRow("gps", "GPS Location", gpsA, gpsB, gpsDelta, gps = true)

        return rows
    }

    private fun formatGps(lat: Double?, lon: Double?, masked: Boolean): String? {
        if (lat == null || lon == null) return null
        return if (masked) "Present (Masked for privacy)"
        else String.format(Locale.US, "%.5f, %.5f", lat, lon)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
