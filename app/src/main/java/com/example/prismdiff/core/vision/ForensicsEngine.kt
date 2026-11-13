package com.example.prismdiff.core.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.prismdiff.core.model.ColorChannelIsolation
import com.example.prismdiff.core.model.DiagnosticOverlay
import com.example.prismdiff.core.model.DiffPalette
import com.example.prismdiff.core.model.DifferenceVisualMode
import com.example.prismdiff.core.model.NormalizationAdjustments
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Deterministic visual diagnostics & digital forensics buffer processors.
 * Works identically on JVM/Robolectric tests and API 24..36 fallback render paths.
 */
object ForensicsEngine {

    fun generateDifferenceBuffer(
        pixelsA: IntArray,
        pixelsB: IntArray,
        width: Int,
        height: Int,
        mode: DifferenceVisualMode,
        palette: DiffPalette,
        boostFactor: Float,
        thresholdFraction: Float,
    ): IntArray {
        val len = min(pixelsA.size, min(pixelsB.size, width * height))
        val out = IntArray(len)
        val boost = boostFactor.coerceIn(1f, 50f)
        val thresh255 = (thresholdFraction.coerceIn(0f, 1f) * 255f)

        if (mode == DifferenceVisualMode.EDGE_DIFF) {
            val edgeA = sobelEdgeMap(pixelsA, width, height)
            val edgeB = sobelEdgeMap(pixelsB, width, height)
            for (i in 0 until len) {
                val eA = edgeA[i]
                val eB = edgeB[i]
                val r = ((eA * boost).toInt()).coerceIn(0, 255)
                val b = ((eB * boost).toInt()).coerceIn(0, 255)
                val g = (min(r, b) * 0.6f).toInt().coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return out
        }

        if (mode == DifferenceVisualMode.SSIM_HEATMAP) {
            return generateLocalSsimMap(pixelsA, pixelsB, width, height)
        }

        for (i in 0 until len) {
            val a = pixelsA[i]
            val b = pixelsB[i]
            val rA = (a ushr 16) and 0xFF
            val gA = (a ushr 8) and 0xFF
            val bA = a and 0xFF
            val rB = (b ushr 16) and 0xFF
            val gB = (b ushr 8) and 0xFF
            val bB = b and 0xFF

            out[i] = when (mode) {
                DifferenceVisualMode.ABSOLUTE_BOOST -> {
                    val dr = (abs(rA - rB) * boost).toInt().coerceIn(0, 255)
                    val dg = (abs(gA - gB) * boost).toInt().coerceIn(0, 255)
                    val db = (abs(bA - bB) * boost).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (dr shl 16) or (dg shl 8) or db
                }
                DifferenceVisualMode.GRAYSCALE_DIFF -> {
                    val diff = (max(abs(rA - rB), max(abs(gA - gB), abs(bA - bB))) * boost).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (diff shl 16) or (diff shl 8) or diff
                }
                DifferenceVisualMode.THRESHOLD_MASK -> {
                    val pxMax = max(abs(rA - rB), max(abs(gA - gB), abs(bA - bB))).toFloat()
                    val gray = (ColorMath.luma255(a) * 0.35f).toInt().coerceIn(0, 255)
                    if (pxMax > thresh255) {
                        val intensity = ((pxMax - thresh255) / max(1f, 255f - thresh255) * boost * 0.4f).coerceIn(0.35f, 1f)
                        paletteColor(palette, intensity)
                    } else {
                        (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    }
                }
                DifferenceVisualMode.DELTA_E_2000 -> {
                    val de = ColorMath.ciede2000(a, b)
                    val norm = ((de / 25.0) * (boost / 4.0)).coerceIn(0.0, 1.0).toFloat()
                    thermalColor(norm)
                }
                DifferenceVisualMode.SSIM_HEATMAP, DifferenceVisualMode.EDGE_DIFF -> a
            }
        }
        return out
    }

    fun applyDiagnosticOverlay(
        pixels: IntArray,
        width: Int,
        height: Int,
        overlay: DiagnosticOverlay,
        channelIsolation: ColorChannelIsolation = ColorChannelIsolation.ALL_RGB,
        elaQuality: Int = 85,
        elaScale: Float = 18f,
        bitPlaneIndex: Int = 0,
    ): IntArray {
        val len = min(pixels.size, width * height)
        if (len <= 0) return pixels
        return when (overlay) {
            DiagnosticOverlay.NONE -> pixels
            DiagnosticOverlay.FOCUS_PEAKING -> focusPeakingBuffer(pixels, width, height)
            DiagnosticOverlay.ZEBRA_CLIPPING -> zebraClippingBuffer(pixels, width, height)
            DiagnosticOverlay.FALSE_COLOR_IRE -> falseColorIreBuffer(pixels, len)
            DiagnosticOverlay.CHANNEL_ISOLATION -> channelIsolationBuffer(pixels, len, channelIsolation)
            DiagnosticOverlay.NOISE_RESIDUAL -> highPassNoiseResidualBuffer(pixels, width, height)
            DiagnosticOverlay.BIT_PLANE_SLICE -> bitPlaneSliceBuffer(pixels, len, bitPlaneIndex.coerceIn(0, 7))
            DiagnosticOverlay.ELA_FORENSICS -> computeElaResidualBuffer(pixels, width, height, elaQuality, elaScale)
        }
    }

    fun applyNormalization(
        pixels: IntArray,
        adj: NormalizationAdjustments,
        width: Int,
        height: Int,
    ): IntArray {
        if (adj.isIdentity) return pixels
        val out = IntArray(pixels.size)
        val evMul = 2.0.pow(adj.exposureEv.toDouble()).toFloat()
        val gammaInv = 1f / adj.gamma.coerceIn(0.25f, 3.0f)

        for (i in pixels.indices) {
            val px = pixels[i]
            var r = ((px ushr 16) and 0xFF) / 255f
            var g = ((px ushr 8) and 0xFF) / 255f
            var b = (px and 0xFF) / 255f

            r = r * evMul + adj.brightness
            g = g * evMul + adj.brightness
            b = b * evMul + adj.brightness

            r += adj.temperatureShift * 0.15f
            b -= adj.temperatureShift * 0.15f
            g -= adj.tintShift * 0.15f

            r = ((r - 0.5f) * adj.contrast + 0.5f).coerceIn(0f, 1f)
            g = ((g - 0.5f) * adj.contrast + 0.5f).coerceIn(0f, 1f)
            b = ((b - 0.5f) * adj.contrast + 0.5f).coerceIn(0f, 1f)

            if (gammaInv != 1f) {
                r = r.pow(gammaInv)
                g = g.pow(gammaInv)
                b = b.pow(gammaInv)
            }

            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (adj.grayscale) {
                r = luma; g = luma; b = luma
            } else if (adj.saturation != 1f) {
                r = (luma + (r - luma) * adj.saturation).coerceIn(0f, 1f)
                g = (luma + (g - luma) * adj.saturation).coerceIn(0f, 1f)
                b = (luma + (b - luma) * adj.saturation).coerceIn(0f, 1f)
            }

            val r8 = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
            val g8 = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
            val b8 = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
        }

        if (adj.sharpenAmount > 0.01f && width > 2 && height > 2) {
            val sharpened = out.copyOf()
            val amount = adj.sharpenAmount.coerceIn(0f, 2f)
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    val c = out[idx]
                    val n = out[idx - width]
                    val s = out[idx + width]
                    val w = out[idx - 1]
                    val e = out[idx + 1]
                    val rr = unsharpChan((c ushr 16) and 0xFF, (n ushr 16) and 0xFF, (s ushr 16) and 0xFF, (w ushr 16) and 0xFF, (e ushr 16) and 0xFF, amount)
                    val gg = unsharpChan((c ushr 8) and 0xFF, (n ushr 8) and 0xFF, (s ushr 8) and 0xFF, (w ushr 8) and 0xFF, (e ushr 8) and 0xFF, amount)
                    val bb = unsharpChan(c and 0xFF, n and 0xFF, s and 0xFF, w and 0xFF, e and 0xFF, amount)
                    sharpened[idx] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                }
            }
            return sharpened
        }
        return out
    }

    /**
     * Error Level Analysis (ELA): re-compresses working buffer as JPEG at [quality] and
     * amplifies DCT residual differences by [scale].
     */
    fun computeElaResidualBuffer(
        pixels: IntArray,
        width: Int,
        height: Int,
        quality: Int = 85,
        scale: Float = 18f,
    ): IntArray {
        val len = min(pixels.size, width * height)
        if (len <= 0 || width <= 0 || height <= 0) return pixels
        val srcBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        srcBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), baos)
        val bytes = baos.toByteArray()
        val recomp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return pixels
        val recompPixels = IntArray(len)
        recomp.getPixels(recompPixels, 0, width, 0, 0, width, height)
        srcBitmap.recycle()
        recomp.recycle()

        val out = IntArray(len)
        val s = scale.coerceIn(1f, 100f)
        for (i in 0 until len) {
            val a = pixels[i]
            val b = recompPixels[i]
            val dr = (abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)) * s).toInt().coerceIn(0, 255)
            val dg = (abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)) * s).toInt().coerceIn(0, 255)
            val db = (abs((a and 0xFF) - (b and 0xFF)) * s).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (dr shl 16) or (dg shl 8) or db
        }
        return out
    }

    private fun focusPeakingBuffer(pixels: IntArray, width: Int, height: Int): IntArray {
        val out = pixels.copyOf()
        val edges = sobelEdgeMap(pixels, width, height)
        for (i in edges.indices) {
            val e = edges[i]
            if (e > 34f) {
                val alpha = ((e - 34f) / 80f).coerceIn(0.45f, 1f)
                val base = pixels[i]
                val br = ((base ushr 16) and 0xFF) * (1f - alpha) + 92f * alpha
                val bg = ((base ushr 8) and 0xFF) * (1f - alpha) + 230f * alpha
                val bb = (base and 0xFF) * (1f - alpha) + 154f * alpha
                out[i] = (0xFF shl 24) or (br.toInt() shl 16) or (bg.toInt() shl 8) or bb.toInt()
            }
        }
        return out
    }

    private fun zebraClippingBuffer(pixels: IntArray, width: Int, height: Int): IntArray {
        val out = pixels.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val px = pixels[idx]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val stripe = ((x + y) / 4) % 2 == 0
                if ((r >= 253 || g >= 253 || b >= 253) && stripe) {
                    out[idx] = 0xFFFF3B30.toInt()
                } else if ((r <= 2 && g <= 2 && b <= 2) && stripe) {
                    out[idx] = 0xFF2D7FF9.toInt()
                }
            }
        }
        return out
    }

    private fun falseColorIreBuffer(pixels: IntArray, len: Int): IntArray {
        val out = IntArray(len)
        for (i in 0 until len) {
            val ire = ColorMath.luma255(pixels[i]) / 255f * 100f
            out[i] = when {
                ire < 5f -> 0xFF4B0082.toInt() // Crushed purple
                ire < 15f -> 0xFF1E5BFF.toInt() // Deep shadows blue
                ire < 35f -> 0xFF00C2D1.toInt() // Shadows cyan
                ire < 52f -> 0xFF39C96E.toInt() // 18% middle gray / foliage green
                ire < 68f -> 0xFFF3A6B8.toInt() // Skin tone reference pink
                ire < 85f -> 0xFFE6E6E6.toInt() // High midtones neutral
                ire < 95f -> 0xFFFFD426.toInt() // Near-clipping yellow
                else -> 0xFFFF2E2E.toInt() // Highlight clipping red
            }
        }
        return out
    }

    private fun channelIsolationBuffer(
        pixels: IntArray,
        len: Int,
        channel: ColorChannelIsolation,
    ): IntArray {
        if (channel == ColorChannelIsolation.ALL_RGB) return pixels
        val out = IntArray(len)
        for (i in 0 until len) {
            val px = pixels[i]
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            out[i] = when (channel) {
                ColorChannelIsolation.ALL_RGB -> px
                ColorChannelIsolation.RED -> (0xFF shl 24) or (r shl 16) or (r shl 8) or r
                ColorChannelIsolation.GREEN -> (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                ColorChannelIsolation.BLUE -> (0xFF shl 24) or (b shl 16) or (b shl 8) or b
                ColorChannelIsolation.LUMA_Y -> {
                    val y = ColorMath.luma255(px)
                    (0xFF shl 24) or (y shl 16) or (y shl 8) or y
                }
                ColorChannelIsolation.CHROMA_UV -> {
                    val y = ColorMath.luma255(px)
                    val cr = (128 + (r - y)).coerceIn(0, 255)
                    val cb = (128 + (b - y)).coerceIn(0, 255)
                    (0xFF shl 24) or (cr shl 16) or (128 shl 8) or cb
                }
                ColorChannelIsolation.HUE -> {
                    val (h, _, _) = ColorMath.rgbToHsl(r, g, b)
                    val v = ((h / 360f) * 255f).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
                ColorChannelIsolation.SATURATION -> {
                    val (_, s, _) = ColorMath.rgbToHsl(r, g, b)
                    val v = ((s / 100f) * 255f).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
        }
        return out
    }

    private fun highPassNoiseResidualBuffer(pixels: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    out[idx] = 0xFF808080.toInt()
                    continue
                }
                val avgR = (((pixels[idx - 1] ushr 16) and 0xFF) + ((pixels[idx + 1] ushr 16) and 0xFF) +
                    ((pixels[idx - width] ushr 16) and 0xFF) + ((pixels[idx + width] ushr 16) and 0xFF)) / 4
                val avgG = (((pixels[idx - 1] ushr 8) and 0xFF) + ((pixels[idx + 1] ushr 8) and 0xFF) +
                    ((pixels[idx - width] ushr 8) and 0xFF) + ((pixels[idx + width] ushr 8) and 0xFF)) / 4
                val avgB = ((pixels[idx - 1] and 0xFF) + (pixels[idx + 1] and 0xFF) +
                    (pixels[idx - width] and 0xFF) + (pixels[idx + width] and 0xFF)) / 4
                val px = pixels[idx]
                val r = (128 + (((px ushr 16) and 0xFF) - avgR) * 5).coerceIn(0, 255)
                val g = (128 + (((px ushr 8) and 0xFF) - avgG) * 5).coerceIn(0, 255)
                val b = (128 + ((px and 0xFF) - avgB) * 5).coerceIn(0, 255)
                out[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    private fun bitPlaneSliceBuffer(pixels: IntArray, len: Int, bit: Int): IntArray {
        val out = IntArray(len)
        val mask = 1 shl bit
        for (i in 0 until len) {
            val luma = ColorMath.luma255(pixels[i])
            val on = (luma and mask) != 0
            out[i] = if (on) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        return out
    }

    private fun generateLocalSsimMap(
        pixelsA: IntArray,
        pixelsB: IntArray,
        width: Int,
        height: Int,
    ): IntArray {
        val len = width * height
        val out = IntArray(len)
        val lumaA = DoubleArray(len) { ColorMath.linearLuminance(pixelsA[it]) * 255.0 }
        val lumaB = DoubleArray(len) { ColorMath.linearLuminance(pixelsB[it]) * 255.0 }
        val c1 = (0.01 * 255.0) * (0.01 * 255.0)
        val c2 = (0.03 * 255.0) * (0.03 * 255.0)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var meanA = 0.0
                var meanB = 0.0
                var count = 0
                for (dy in -2..2) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -2..2) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        val idx = yy * width + xx
                        meanA += lumaA[idx]
                        meanB += lumaB[idx]
                        count++
                    }
                }
                meanA /= count
                meanB /= count
                var varA = 0.0
                var varB = 0.0
                var cov = 0.0
                for (dy in -2..2) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -2..2) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        val idx = yy * width + xx
                        val da = lumaA[idx] - meanA
                        val db = lumaB[idx] - meanB
                        varA += da * da
                        varB += db * db
                        cov += da * db
                    }
                }
                val ssim = ((2.0 * meanA * meanB + c1) * (2.0 * cov / count + c2)) /
                    max(1e-6, (meanA * meanA + meanB * meanB + c1) * (varA / count + varB / count + c2))
                val diffSeverity = (1.0 - ssim.coerceIn(0.0, 1.0)).toFloat()
                out[y * width + x] = thermalColor(diffSeverity)
            }
        }
        return out
    }

    private fun sobelEdgeMap(pixels: IntArray, width: Int, height: Int): FloatArray {
        val len = width * height
        val out = FloatArray(len)
        val luma = FloatArray(len) { ColorMath.luma255(pixels[it]).toFloat() }
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val gx = -luma[i - width - 1] + luma[i - width + 1] -
                    2f * luma[i - 1] + 2f * luma[i + 1] -
                    luma[i + width - 1] + luma[i + width + 1]
                val gy = -luma[i - width - 1] - 2f * luma[i - width] - luma[i - width + 1] +
                    luma[i + width - 1] + 2f * luma[i + width] + luma[i + width + 1]
                out[i] = sqrt(gx * gx + gy * gy) * 0.25f
            }
        }
        return out
    }

    private fun unsharpChan(c: Int, n: Int, s: Int, w: Int, e: Int, amount: Float): Int {
        val blur = (n + s + w + e) * 0.25f
        return (c + (c - blur) * amount).toInt().coerceIn(0, 255)
    }

    private fun paletteColor(palette: DiffPalette, intensity: Float): Int {
        val t = intensity.coerceIn(0f, 1f)
        return when (palette) {
            DiffPalette.MAGENTA -> argb(255, (255 * t).toInt(), (70 * t).toInt(), (180 * t).toInt())
            DiffPalette.CYAN -> argb(255, (60 * t).toInt(), (230 * t).toInt(), (240 * t).toInt())
            DiffPalette.ACID_GREEN -> argb(255, (110 * t).toInt(), (255 * t).toInt(), (85 * t).toInt())
            DiffPalette.THERMAL_INFERNO -> thermalColor(t)
        }
    }

    private fun thermalColor(t: Float): Int {
        val x = t.coerceIn(0f, 1f)
        val r = (min(1f, x * 2.2f) * 255f).toInt()
        val g = (max(0f, min(1f, (x - 0.35f) * 2.2f)) * 255f).toInt()
        val b = ((if (x < 0.35f) x * 2.0f else max(0f, 1f - (x - 0.35f) * 2.5f)) * 210f).toInt()
        return argb(255, r, g, b)
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }
}
