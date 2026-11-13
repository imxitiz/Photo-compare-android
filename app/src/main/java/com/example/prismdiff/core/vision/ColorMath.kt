package com.example.prismdiff.core.vision

import com.example.prismdiff.core.model.PixelSample
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Color science primitives: IEC 61966-2-1 sRGB linearization, Rec.709 luminance,
 * HSL conversion, CIE L*a*b* (D65), and CIEDE2000 perceptual color difference.
 */
object ColorMath {

    fun srgbToLinear(c8: Int): Double {
        val c = (c8 and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun linearToSrgb8(linear: Double): Int {
        val clamped = linear.coerceIn(0.0, 1.0)
        val srgb = if (clamped <= 0.0031308) {
            12.92 * clamped
        } else {
            1.055 * clamped.pow(1.0 / 2.4) - 0.055
        }
        return (srgb * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    /** Linearized Rec.709 relative luminance in [0.0, 1.0]. */
    fun linearLuminance(argb: Int): Double {
        val r = srgbToLinear((argb ushr 16) and 0xFF)
        val g = srgbToLinear((argb ushr 8) and 0xFF)
        val b = srgbToLinear(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Gamma-encoded Rec.601/709 display luma in [0, 255]. */
    fun luma255(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return ((0.2126 * r + 0.7152 * g + 0.0722 * b) + 0.5).toInt().coerceIn(0, 255)
    }

    fun rgbToHsl(r8: Int, g8: Int, b8: Int): Triple<Float, Float, Float> {
        val r = (r8 and 0xFF) / 255f
        val g = (g8 and 0xFF) / 255f
        val b = (b8 and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val l = (maxC + minC) * 0.5f
        val d = maxC - minC
        if (d < 1e-6f) {
            return Triple(0f, 0f, l * 100f)
        }
        val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
        var h = when (maxC) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        h *= 60f
        return Triple(h, s * 100f, l * 100f)
    }

    fun rgbToCieLab(r8: Int, g8: Int, b8: Int): Triple<Double, Double, Double> {
        val r = srgbToLinear(r8)
        val g = srgbToLinear(g8)
        val b = srgbToLinear(b8)

        // sRGB to XYZ (D65)
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) / 1.00000
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883

        fun f(t: Double): Double = if (t > 0.008856) t.pow(1.0 / 3.0) else (7.787 * t) + (16.0 / 116.0)
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        val lStar = 116.0 * fy - 16.0
        val aStar = 500.0 * (fx - fy)
        val bStar = 200.0 * (fy - fz)
        return Triple(lStar, aStar, bStar)
    }

    /**
     * Exact CIEDE2000 perceptual color difference (ΔE00) with kL=kC=kH=1.
     */
    fun ciede2000(argb1: Int, argb2: Int): Double {
        val (l1, a1, b1) = rgbToCieLab((argb1 ushr 16) and 0xFF, (argb1 ushr 8) and 0xFF, argb1 and 0xFF)
        val (l2, a2, b2) = rgbToCieLab((argb2 ushr 16) and 0xFF, (argb2 ushr 8) and 0xFF, argb2 and 0xFF)

        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val cBar = 0.5 * (c1 + c2)
        val cBar7 = cBar.pow(7.0)
        val g = 0.5 * (1.0 - sqrt(cBar7 / (cBar7 + 25.0.pow(7.0))))

        val a1Prime = (1.0 + g) * a1
        val a2Prime = (1.0 + g) * a2
        val c1Prime = sqrt(a1Prime * a1Prime + b1 * b1)
        val c2Prime = sqrt(a2Prime * a2Prime + b2 * b2)

        fun hueAngle(b: Double, aPrime: Double): Double {
            if (b == 0.0 && aPrime == 0.0) return 0.0
            val deg = Math.toDegrees(atan2(b, aPrime))
            return if (deg >= 0.0) deg else deg + 360.0
        }

        val h1Prime = hueAngle(b1, a1Prime)
        val h2Prime = hueAngle(b2, a2Prime)

        val deltaLPrime = l2 - l1
        val deltaCPrime = c2Prime - c1Prime

        val deltahPrime = when {
            c1Prime * c2Prime == 0.0 -> 0.0
            kotlin.math.abs(h2Prime - h1Prime) <= 180.0 -> h2Prime - h1Prime
            h2Prime - h1Prime > 180.0 -> (h2Prime - h1Prime) - 360.0
            else -> (h2Prime - h1Prime) + 360.0
        }
        val deltaHPrime = 2.0 * sqrt(c1Prime * c2Prime) * sin(Math.toRadians(deltahPrime / 2.0))

        val lBarPrime = 0.5 * (l1 + l2)
        val cBarPrime = 0.5 * (c1Prime + c2Prime)

        val hBarPrime = when {
            c1Prime * c2Prime == 0.0 -> h1Prime + h2Prime
            kotlin.math.abs(h1Prime - h2Prime) <= 180.0 -> 0.5 * (h1Prime + h2Prime)
            h1Prime + h2Prime < 360.0 -> 0.5 * (h1Prime + h2Prime + 360.0)
            else -> 0.5 * (h1Prime + h2Prime - 360.0)
        }

        val t = 1.0 -
            0.17 * cos(Math.toRadians(hBarPrime - 30.0)) +
            0.24 * cos(Math.toRadians(2.0 * hBarPrime)) +
            0.32 * cos(Math.toRadians(3.0 * hBarPrime + 6.0)) -
            0.20 * cos(Math.toRadians(4.0 * hBarPrime - 63.0))

        val deltaTheta = 30.0 * exp(-(((hBarPrime - 275.0) / 25.0).pow(2.0)))
        val cBarPrime7 = cBarPrime.pow(7.0)
        val rC = 2.0 * sqrt(cBarPrime7 / (cBarPrime7 + 25.0.pow(7.0)))
        val lMinus50Sq = (lBarPrime - 50.0).pow(2.0)
        val sL = 1.0 + (0.015 * lMinus50Sq) / sqrt(20.0 + lMinus50Sq)
        val sC = 1.0 + 0.045 * cBarPrime
        val sH = 1.0 + 0.015 * cBarPrime * t
        val rT = -sin(Math.toRadians(2.0 * deltaTheta)) * rC

        val termL = deltaLPrime / sL
        val termC = deltaCPrime / sC
        val termH = deltaHPrime / sH
        return sqrt(termL * termL + termC * termC + termH * termH + rT * termC * termH)
    }

    fun samplePixel(argb: Int, x: Int, y: Int): PixelSample {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val (h, s, l) = rgbToHsl(r, g, b)
        val (cieL, cieA, cieB) = rgbToCieLab(r, g, b)
        val hex = String.format("#%02X%02X%02X", r, g, b)
        return PixelSample(
            x = x,
            y = y,
            r = r,
            g = g,
            b = b,
            hex = hex,
            hueDeg = h,
            satPercent = s,
            lightPercent = l,
            cieL = cieL,
            cieA = cieA,
            cieB = cieB,
        )
    }
}
