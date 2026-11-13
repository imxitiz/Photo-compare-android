package com.example.prismdiff.core.vision

import com.example.prismdiff.core.model.AnalysisMetricReport
import com.example.prismdiff.core.model.ComparisonMetricsBundle
import com.example.prismdiff.core.model.HistogramData
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic on-device image quality and difference analyzer.
 * Every report discloses the exact algorithm, scope, alignment/normalization context,
 * and mathematical limitations so results are never misrepresented as universal truth.
 */
object ImageMetricsEngine {

    fun computeHistogram(
        pixels: IntArray,
        regionLabel: String = "Full Frame",
    ): HistogramData {
        val luma = IntArray(256)
        val red = IntArray(256)
        val green = IntArray(256)
        val blue = IntArray(256)
        for (px in pixels) {
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val y = ((0.2126 * r + 0.7152 * g + 0.0722 * b) + 0.5).toInt().coerceIn(0, 255)
            red[r]++
            green[g]++
            blue[b]++
            luma[y]++
        }
        return HistogramData(
            lumaBins = luma,
            redBins = red,
            greenBins = green,
            blueBins = blue,
            sampleCount = pixels.size,
            regionLabel = regionLabel,
        )
    }

    /**
     * Estimates local focus/sharpness using Laplacian variance + Sobel gradient energy.
     * Higher scores indicate stronger high-frequency edge contrast, though heavy sensor
     * noise or artificial sharpening also elevate raw scores.
     */
    fun computeSharpnessIndex(pixels: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || pixels.size < width * height) return 0.0
        val luma = DoubleArray(width * height) { i -> ColorMath.linearLuminance(pixels[i]) * 255.0 }

        var sumLap = 0.0
        var sumLapSq = 0.0
        var sumGrad = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val idx = row + x
                val c = luma[idx]
                val lap = luma[idx - width] + luma[idx + width] + luma[idx - 1] + luma[idx + 1] - 4.0 * c
                sumLap += lap
                sumLapSq += lap * lap

                val gx = -luma[idx - width - 1] + luma[idx - width + 1] -
                    2.0 * luma[idx - 1] + 2.0 * luma[idx + 1] -
                    luma[idx + width - 1] + luma[idx + width + 1]
                val gy = -luma[idx - width - 1] - 2.0 * luma[idx - width] - luma[idx - width + 1] +
                    luma[idx + width - 1] + 2.0 * luma[idx + width] + luma[idx + width + 1]
                sumGrad += sqrt(gx * gx + gy * gy)
                count++
            }
        }
        if (count == 0) return 0.0
        val meanLap = sumLap / count
        val lapVar = max(0.0, (sumLapSq / count) - meanLap * meanLap)
        val meanGrad = sumGrad / count
        return sqrt(lapVar) * 0.65 + meanGrad * 0.35
    }

    /**
     * Estimates high-frequency luminance noise floor using Immerkaer's 3x3 noise operator:
     * N = [1 -2 1; -2 4 -2; 1 -2 1], normalized over non-edge regions.
     */
    fun estimateNoiseFloor(pixels: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || pixels.size < width * height) return 0.0
        val luma = DoubleArray(width * height) { i -> ColorMath.luma255(pixels[i]).toDouble() }
        var sumAbs = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val rPrev = (y - 1) * width
            val rCurr = y * width
            val rNext = (y + 1) * width
            for (x in 1 until width - 1) {
                val resp = abs(
                    luma[rPrev + x - 1] - 2.0 * luma[rPrev + x] + luma[rPrev + x + 1] -
                        2.0 * luma[rCurr + x - 1] + 4.0 * luma[rCurr + x] - 2.0 * luma[rCurr + x + 1] +
                        luma[rNext + x - 1] - 2.0 * luma[rNext + x] + luma[rNext + x + 1],
                )
                sumAbs += resp
                count++
            }
        }
        if (count == 0) return 0.0
        // Immerkaer scale factor sqrt(pi/2) / (6 * (W-2)*(H-2))
        return (sumAbs * 1.253314) / (6.0 * count)
    }

    fun compareBuffers(
        pixelsA: IntArray,
        pixelsB: IntArray,
        width: Int,
        height: Int,
        thresholdFraction: Float = 0.03f,
        scopeLabel: String = "Active Viewport",
        alignmentApplied: Boolean = false,
        colorNormalized: Boolean = false,
    ): ComparisonMetricsBundle {
        val count = min(pixelsA.size, min(pixelsB.size, width * height))
        if (count <= 0 || width <= 0 || height <= 0) {
            return ComparisonMetricsBundle(
                ssim = 1.0, psnrDb = 99.0, mae = 0.0, rmse = 0.0, maxDelta = 0,
                changedPixelCount = 0, changedPixelPercent = 0.0,
                meanDeltaE2000 = 0.0, maxDeltaE2000 = 0.0,
                sharpnessA = 0.0, sharpnessB = 0.0, noiseFloorA = 0.0, noiseFloorB = 0.0,
                clippingHighPercentA = 0.0, clippingShadowPercentA = 0.0,
                clippingHighPercentB = 0.0, clippingShadowPercentB = 0.0,
                scopeLabel = scopeLabel, alignmentApplied = alignmentApplied,
                colorNormalized = colorNormalized, reports = emptyList(),
            )
        }

        val threshold255 = (thresholdFraction.coerceIn(0f, 1f) * 255f)
        var sumAbsDiff = 0.0
        var sumSqDiff = 0.0
        var maxDelta = 0
        var changedCount = 0

        var clipHighA = 0
        var clipLowA = 0
        var clipHighB = 0
        var clipLowB = 0

        val stride = max(1, count / 4096)
        var deltaESum = 0.0
        var deltaEMax = 0.0
        var deltaECount = 0

        for (i in 0 until count) {
            val a = pixelsA[i]
            val b = pixelsB[i]
            val rA = (a ushr 16) and 0xFF
            val gA = (a ushr 8) and 0xFF
            val bA = a and 0xFF
            val rB = (b ushr 16) and 0xFF
            val gB = (b ushr 8) and 0xFF
            val bB = b and 0xFF

            if (rA >= 253 || gA >= 253 || bA >= 253) clipHighA++
            if (rA <= 2 && gA <= 2 && bA <= 2) clipLowA++
            if (rB >= 253 || gB >= 253 || bB >= 253) clipHighB++
            if (rB <= 2 && gB <= 2 && bB <= 2) clipLowB++

            val dr = abs(rA - rB)
            val dg = abs(gA - gB)
            val db = abs(bA - bB)
            val pxMax = max(dr, max(dg, db))
            if (pxMax > maxDelta) maxDelta = pxMax
            val pxMean = (dr + dg + db) / 3.0
            sumAbsDiff += pxMean
            sumSqDiff += (dr * dr + dg * dg + db * db) / 3.0
            if (pxMax > threshold255) changedCount++

            if (i % stride == 0) {
                val de = ColorMath.ciede2000(a, b)
                deltaESum += de
                if (de > deltaEMax) deltaEMax = de
                deltaECount++
            }
        }

        val mae = sumAbsDiff / count
        val mse = sumSqDiff / count
        val rmse = sqrt(mse)
        val psnrDb = if (mse <= 1e-9) 99.0 else 10.0 * log10((255.0 * 255.0) / mse)
        val changedPct = (changedCount.toDouble() / count) * 100.0
        val meanDe = if (deltaECount > 0) deltaESum / deltaECount else 0.0
        val ssim = computeSsim(pixelsA, pixelsB, width, height)

        val sharpA = computeSharpnessIndex(pixelsA, width, height)
        val sharpB = computeSharpnessIndex(pixelsB, width, height)
        val noiseA = estimateNoiseFloor(pixelsA, width, height)
        val noiseB = estimateNoiseFloor(pixelsB, width, height)

        val highPctA = (clipHighA.toDouble() / count) * 100.0
        val lowPctA = (clipLowA.toDouble() / count) * 100.0
        val highPctB = (clipHighB.toDouble() / count) * 100.0
        val lowPctB = (clipLowB.toDouble() / count) * 100.0

        val reports = listOf(
            AnalysisMetricReport(
                id = "ssim",
                title = "Structural Similarity (SSIM)",
                algorithmName = "8x8 Block Linearized Luminance SSIM",
                valueFormatted = String.format(Locale.US, "%.1f%%", ssim * 100.0),
                numericValue = ssim,
                unit = "index [0..1]",
                regionScope = scopeLabel,
                alignmentApplied = alignmentApplied,
                colorNormalized = colorNormalized,
                explanation = "Evaluates local luminance, contrast, and structural correlation across 8x8 windows.",
                limitationNote = "Sensitive to spatial misalignment and geometric crops; high SSIM does not guarantee color fidelity.",
            ),
            AnalysisMetricReport(
                id = "psnr",
                title = "Peak Signal-to-Noise Ratio",
                algorithmName = "RGB Mean Squared Error PSNR",
                valueFormatted = String.format(Locale.US, "%.2f dB", psnrDb),
                numericValue = psnrDb,
                unit = "dB",
                regionScope = scopeLabel,
                alignmentApplied = alignmentApplied,
                colorNormalized = colorNormalized,
                explanation = "Logarithmic fidelity metric comparing pixel MSE against 8-bit peak intensity (255).",
                limitationNote = "Best for compression checks on aligned images; unreliable when exposure or white balance differ.",
            ),
            AnalysisMetricReport(
                id = "delta_e",
                title = "Chromatic Shift (CIEDE2000)",
                algorithmName = "CIE L*a*b* D65 ΔE00",
                valueFormatted = String.format(Locale.US, "%.2f ΔE", meanDe),
                numericValue = meanDe,
                unit = "ΔE00",
                regionScope = scopeLabel,
                alignmentApplied = alignmentApplied,
                colorNormalized = colorNormalized,
                explanation = "Perceptual color distance (<1.0 imperceptible, 1-2 subtle, >5 clear shift).",
                limitationNote = "Assumes sRGB transfer characteristics unless color spaces are explicitly matched.",
            ),
            AnalysisMetricReport(
                id = "changed_px",
                title = "Changed Pixels (> ${(thresholdFraction * 100).toInt()}%)",
                algorithmName = "Channel-Max Threshold Mask",
                valueFormatted = String.format(Locale.US, "%.2f%%", changedPct),
                numericValue = changedPct,
                unit = "%",
                regionScope = scopeLabel,
                alignmentApplied = alignmentApplied,
                colorNormalized = colorNormalized,
                explanation = "$changedCount of $count sampled pixels exceed the channel tolerance threshold.",
                limitationNote = "Reflects numerical pixel differences, which include benign camera shake or sensor noise.",
            ),
            AnalysisMetricReport(
                id = "sharpness",
                title = "Focus / Edge Energy (A vs B)",
                algorithmName = "Laplacian Variance + Sobel Magnitude",
                valueFormatted = String.format(Locale.US, "%.1f vs %.1f", sharpA, sharpB),
                numericValue = sharpA - sharpB,
                unit = "index",
                regionScope = scopeLabel,
                alignmentApplied = alignmentApplied,
                colorNormalized = colorNormalized,
                explanation = "Quantifies micro-contrast and edge transitions inside the inspected region.",
                limitationNote = "Heavy noise or aggressive software sharpening can inflate scores; verify with Noise Residual.",
            ),
            AnalysisMetricReport(
                id = "noise",
                title = "Noise Floor (A vs B)",
                algorithmName = "Immerkaer 3x3 High-Pass Noise Operator",
                valueFormatted = String.format(Locale.US, "%.2f vs %.2f", noiseA, noiseB),
                numericValue = noiseA - noiseB,
                unit = "σ-est",
                regionScope = scopeLabel,
                alignmentApplied = alignmentApplied,
                colorNormalized = colorNormalized,
                explanation = "Estimates high-frequency luminance grain or denoising smoothing strength.",
                limitationNote = "Dense fine textures (foliage/hair) contribute residual energy alongside sensor grain.",
            ),
        )

        return ComparisonMetricsBundle(
            ssim = ssim,
            psnrDb = psnrDb,
            mae = mae,
            rmse = rmse,
            maxDelta = maxDelta,
            changedPixelCount = changedCount,
            changedPixelPercent = changedPct,
            meanDeltaE2000 = meanDe,
            maxDeltaE2000 = deltaEMax,
            sharpnessA = sharpA,
            sharpnessB = sharpB,
            noiseFloorA = noiseA,
            noiseFloorB = noiseB,
            clippingHighPercentA = highPctA,
            clippingShadowPercentA = lowPctA,
            clippingHighPercentB = highPctB,
            clippingShadowPercentB = lowPctB,
            scopeLabel = scopeLabel,
            alignmentApplied = alignmentApplied,
            colorNormalized = colorNormalized,
            reports = reports,
        )
    }

    /**
     * Computes 8x8 block SSIM over linearized Rec.709 luminance scaled to [0, 255].
     */
    fun computeSsim(pixelsA: IntArray, pixelsB: IntArray, width: Int, height: Int): Double {
        if (width < 8 || height < 8 || pixelsA.size < width * height || pixelsB.size < width * height) {
            return 1.0
        }
        val lumaA = DoubleArray(width * height) { i -> ColorMath.linearLuminance(pixelsA[i]) * 255.0 }
        val lumaB = DoubleArray(width * height) { i -> ColorMath.linearLuminance(pixelsB[i]) * 255.0 }

        val c1 = (0.01 * 255.0) * (0.01 * 255.0)
        val c2 = (0.03 * 255.0) * (0.03 * 255.0)
        var ssimSum = 0.0
        var blocks = 0
        val step = 4

        var y = 0
        while (y + 8 <= height) {
            var x = 0
            while (x + 8 <= width) {
                var meanA = 0.0
                var meanB = 0.0
                for (wy in 0 until 8) {
                    val row = (y + wy) * width + x
                    for (wx in 0 until 8) {
                        meanA += lumaA[row + wx]
                        meanB += lumaB[row + wx]
                    }
                }
                meanA /= 64.0
                meanB /= 64.0

                var varA = 0.0
                var varB = 0.0
                var covAB = 0.0
                for (wy in 0 until 8) {
                    val row = (y + wy) * width + x
                    for (wx in 0 until 8) {
                        val da = lumaA[row + wx] - meanA
                        val db = lumaB[row + wx] - meanB
                        varA += da * da
                        varB += db * db
                        covAB += da * db
                    }
                }
                varA /= 63.0
                varB /= 63.0
                covAB /= 63.0

                val num = (2.0 * meanA * meanB + c1) * (2.0 * covAB + c2)
                val den = (meanA * meanA + meanB * meanB + c1) * (varA + varB + c2)
                if (den > 1e-9) {
                    ssimSum += (num / den).coerceIn(-1.0, 1.0)
                    blocks++
                }
                x += step
            }
            y += step
        }
        return if (blocks > 0) (ssimSum / blocks).coerceIn(0.0, 1.0) else 1.0
    }
}
