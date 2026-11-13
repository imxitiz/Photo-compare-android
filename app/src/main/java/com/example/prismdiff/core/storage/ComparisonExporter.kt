package com.example.prismdiff.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.prismdiff.core.decoder.ExifDiffEngine
import com.example.prismdiff.core.model.AnnotationItem
import com.example.prismdiff.core.model.AnnotationType
import com.example.prismdiff.core.model.ComparisonMetricsBundle
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.WorkspacePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.min

/**
 * Generates non-destructive derivative exports:
 * - Side-by-Side Before/After PNG collage with optional EXIF badges & annotations
 * - Diff Map PNG
 * - Animated Curtain-Wipe GIF89a
 * - Versioned JSON Comparison Report
 */
class ComparisonExporter(private val context: Context) {

    @Serializable
    data class ComparisonJsonReport(
        val schemaVersion: Int = 1,
        val generatedAtMs: Long,
        val assetA: ImageAsset,
        val assetB: ImageAsset,
        val ssim: Double?,
        val psnrDb: Double?,
        val meanDeltaE2000: Double?,
        val mae: Double?,
        val sharpnessA: Double?,
        val sharpnessB: Double?,
        val scopeLabel: String?,
        val alignmentApplied: Boolean?,
        val preset: WorkspacePreset?,
    )

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportSideBySideCollagePng(
        bitmapA: Bitmap,
        bitmapB: Bitmap,
        assetA: ImageAsset,
        assetB: ImageAsset,
        hudTemplate: String,
        annotations: List<AnnotationItem> = emptyList(),
    ): Uri = withContext(Dispatchers.IO) {
        val paneW = 800
        val paneH = 680
        val headerH = 58
        val footerH = 44
        val gap = 6
        val totalW = paneW * 2 + gap
        val totalH = headerH + paneH + footerH

        val out = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        drawFitted(canvas, bitmapA, Rect(0, headerH, paneW, headerH + paneH), paint)
        drawFitted(canvas, bitmapB, Rect(paneW + gap, headerH, totalW, headerH + paneH), paint)

        // Hairline center divider
        paint.color = Color.rgb(44, 52, 66)
        canvas.drawRect(paneW.toFloat(), 0f, (paneW + gap).toFloat(), totalH.toFloat(), paint)

        // Draw non-destructive annotations on left pane
        drawAnnotations(canvas, annotations, Rect(0, headerH, paneW, headerH + paneH))

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(92, 225, 230)
            textSize = 20f
        }
        canvas.drawText("A • ${assetA.displayName}", 20f, 36f, textPaint)
        canvas.drawText("B • ${assetB.displayName}", (paneW + gap + 20).toFloat(), 36f, textPaint)
        canvas.drawText(ExifDiffEngine.formatHudTemplate(hudTemplate, assetA), 20f, (headerH + paneH + 30).toFloat(), subPaint)
        canvas.drawText(ExifDiffEngine.formatHudTemplate(hudTemplate, assetB), (paneW + gap + 20).toFloat(), (headerH + paneH + 30).toFloat(), subPaint)

        saveBitmapPng(out, "prismdiff_collage_${System.currentTimeMillis()}.png")
    }

    suspend fun exportAnimatedWipeGif(
        bitmapA: Bitmap,
        bitmapB: Bitmap,
    ): Uri = withContext(Dispatchers.IO) {
        val w = 360
        val h = 240
        val scaledA = Bitmap.createScaledBitmap(bitmapA, w, h, true)
        val scaledB = Bitmap.createScaledBitmap(bitmapB, w, h, true)
        val pxA = IntArray(w * h)
        val pxB = IntArray(w * h)
        scaledA.getPixels(pxA, 0, w, 0, 0, w, h)
        scaledB.getPixels(pxB, 0, w, 0, 0, w, h)
        if (scaledA !== bitmapA) scaledA.recycle()
        if (scaledB !== bitmapB) scaledB.recycle()

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "prismdiff_wipe_${System.currentTimeMillis()}.gif")
        FileOutputStream(file).use { fos ->
            val encoder = SimpleGifEncoder(w, h, delayCs = 12)
            encoder.start(fos)
            val steps = listOf(0.1f, 0.25f, 0.45f, 0.65f, 0.85f, 0.92f, 0.75f, 0.55f, 0.35f, 0.15f)
            val frameBuf = IntArray(w * h)
            for (split in steps) {
                val splitX = (w * split).toInt()
                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) {
                        frameBuf[row + x] = when {
                            x == splitX || x == splitX + 1 -> Color.rgb(92, 225, 230)
                            x < splitX -> pxA[row + x]
                            else -> pxB[row + x]
                        }
                    }
                }
                encoder.addFrame(fos, frameBuf)
            }
            encoder.finish(fos)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun exportJsonReport(
        assetA: ImageAsset,
        assetB: ImageAsset,
        metrics: ComparisonMetricsBundle?,
        preset: WorkspacePreset?,
    ): Uri = withContext(Dispatchers.IO) {
        val report = ComparisonJsonReport(
            generatedAtMs = System.currentTimeMillis(),
            assetA = assetA,
            assetB = assetB,
            ssim = metrics?.ssim,
            psnrDb = metrics?.psnrDb,
            meanDeltaE2000 = metrics?.meanDeltaE2000,
            mae = metrics?.mae,
            sharpnessA = metrics?.sharpnessA,
            sharpnessB = metrics?.sharpnessB,
            scopeLabel = metrics?.scopeLabel,
            alignmentApplied = metrics?.alignmentApplied,
            preset = preset,
        )
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "prismdiff_report_${System.currentTimeMillis()}.json")
        file.writeText(json.encodeToString(report))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun saveBitmapPng(bitmap: Bitmap, fileName: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun drawFitted(canvas: Canvas, bmp: Bitmap, target: Rect, paint: Paint) {
        val scale = min(target.width().toFloat() / bmp.width, target.height().toFloat() / bmp.height)
        val dw = (bmp.width * scale).toInt()
        val dh = (bmp.height * scale).toInt()
        val left = target.left + (target.width() - dw) / 2
        val top = target.top + (target.height() - dh) / 2
        canvas.drawBitmap(bmp, null, Rect(left, top, left + dw, top + dh), paint)
    }

    private fun drawAnnotations(canvas: Canvas, annotations: List<AnnotationItem>, bounds: Rect) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f }
        for (ann in annotations) {
            p.color = ann.colorArgb.toInt()
            val sx = bounds.left + ann.normStartX * bounds.width()
            val sy = bounds.top + ann.normStartY * bounds.height()
            val ex = bounds.left + ann.normEndX * bounds.width()
            val ey = bounds.top + ann.normEndY * bounds.height()
            when (ann.type) {
                AnnotationType.RECTANGLE -> {
                    p.style = Paint.Style.STROKE
                    canvas.drawRect(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey), p)
                }
                AnnotationType.CIRCLE -> {
                    p.style = Paint.Style.STROKE
                    val r = kotlin.math.hypot(ex - sx, ey - sy)
                    canvas.drawCircle(sx, sy, r, p)
                }
                AnnotationType.ARROW -> {
                    p.style = Paint.Style.STROKE
                    canvas.drawLine(sx, sy, ex, ey, p)
                }
                AnnotationType.TEXT -> {
                    p.style = Paint.Style.FILL
                    p.textSize = 24f
                    canvas.drawText(ann.text, sx, sy, p)
                }
                AnnotationType.FREEHAND -> {
                    p.style = Paint.Style.STROKE
                    for (i in 1 until ann.points.size) {
                        val p0 = ann.points[i - 1]
                        val p1 = ann.points[i]
                        canvas.drawLine(
                            bounds.left + p0.x * bounds.width(),
                            bounds.top + p0.y * bounds.height(),
                            bounds.left + p1.x * bounds.width(),
                            bounds.top + p1.y * bounds.height(),
                            p,
                        )
                    }
                }
            }
        }
    }

    /**
     * Compact pure-Kotlin GIF89a animated encoder using a 216-color web-safe uniform palette
     * + uncompressed LZW blocks so animated before/after wipes export deterministically offline.
     */
    private class SimpleGifEncoder(
        private val width: Int,
        private val height: Int,
        private val delayCs: Int,
    ) {
        private val palette = ByteArray(256 * 3)

        init {
            var idx = 0
            for (r in 0..5) {
                for (g in 0..5) {
                    for (b in 0..5) {
                        palette[idx++] = (r * 51).toByte()
                        palette[idx++] = (g * 51).toByte()
                        palette[idx++] = (b * 51).toByte()
                    }
                }
            }
        }

        fun start(out: OutputStream) {
            out.write("GIF89a".toByteArray(Charsets.US_ASCII))
            writeShort(out, width)
            writeShort(out, height)
            out.write(0xF7) // Global Color Table, 256 colors
            out.write(0)
            out.write(0)
            out.write(palette)
            // Netscape 2.0 loop forever extension
            out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
        }

        fun addFrame(out: OutputStream, argb: IntArray) {
            // Graphics Control Extension
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x00))
            writeShort(out, delayCs)
            out.write(byteArrayOf(0x00, 0x00))

            // Image Descriptor
            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, width)
            writeShort(out, height)
            out.write(0x00)

            // Quantize to 6x6x6 palette and emit uncompressed LZW stream with periodic Clear codes
            val minCodeSize = 8
            out.write(minCodeSize)
            val lzwOut = ByteArrayOutputStream()
            val bitBuf = BitWriter(lzwOut)
            val clearCode = 256
            val eoiCode = 257
            bitBuf.writeBits(clearCode, 9)
            var countSinceClear = 0
            for (px in argb) {
                val r = (((px ushr 16) and 0xFF) * 5 + 127) / 255
                val g = (((px ushr 8) and 0xFF) * 5 + 127) / 255
                val b = ((px and 0xFF) * 5 + 127) / 255
                val palIdx = r * 36 + g * 6 + b
                bitBuf.writeBits(palIdx, 9)
                countSinceClear++
                if (countSinceClear >= 240) {
                    bitBuf.writeBits(clearCode, 9)
                    countSinceClear = 0
                }
            }
            bitBuf.writeBits(eoiCode, 9)
            bitBuf.flush()

            val bytes = lzwOut.toByteArray()
            var offset = 0
            while (offset < bytes.size) {
                val chunk = min(255, bytes.size - offset)
                out.write(chunk)
                out.write(bytes, offset, chunk)
                offset += chunk
            }
            out.write(0x00)
        }

        fun finish(out: OutputStream) {
            out.write(0x3B)
        }

        private fun writeShort(out: OutputStream, v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }

        private class BitWriter(private val out: ByteArrayOutputStream) {
            private var acc = 0
            private var bits = 0
            fun writeBits(code: Int, width: Int) {
                acc = acc or (code shl bits)
                bits += width
                while (bits >= 8) {
                    out.write(acc and 0xFF)
                    acc = acc ushr 8
                    bits -= 8
                }
            }
            fun flush() {
                if (bits > 0) out.write(acc and 0xFF)
            }
        }
    }
}
