package com.example.prismdiff.core.shader

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.prismdiff.core.model.ColorChannelIsolation
import com.example.prismdiff.core.model.DiagnosticOverlay
import com.example.prismdiff.core.model.DiffPalette
import com.example.prismdiff.core.model.DifferenceVisualMode
import com.example.prismdiff.core.model.NormalizationAdjustments
import com.example.prismdiff.core.vision.ForensicsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardware-accelerated AGSL RuntimeShader library for Android 13+ (API 33+).
 * Always paired with [TileComparisonRenderer] so API 24..32 devices run identical math.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object AgslShaderLibrary {

    private const val DIFF_BOOST_AGSL = """
        uniform shader imgA;
        uniform shader imgB;
        uniform float boost;
        uniform float threshold;
        half4 main(float2 coord) {
            half4 a = imgA.eval(coord);
            half4 b = imgB.eval(coord);
            half3 d = abs(a.rgb - b.rgb);
            float m = max(d.r, max(d.g, d.b));
            if (m < threshold) {
                float luma = dot(a.rgb, half3(0.2126, 0.7152, 0.0722)) * 0.3;
                return half4(luma, luma, luma, 1.0);
            }
            return half4(clamp(d * boost, 0.0, 1.0), 1.0);
        }
    """

    fun createDiffShader(bitmapA: Bitmap, bitmapB: Bitmap, boost: Float, threshold: Float): RuntimeShader {
        return RuntimeShader(DIFF_BOOST_AGSL).apply {
            setInputShader("imgA", BitmapShader(bitmapA, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            setInputShader("imgB", BitmapShader(bitmapB, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            setFloatUniform("boost", boost)
            setFloatUniform("threshold", threshold)
        }
    }
}

/**
 * Unified comparison & diagnostic buffer renderer supporting API 24..36 deterministically.
 */
object TileComparisonRenderer {

    suspend fun renderDifferenceBitmap(
        bitmapA: Bitmap,
        bitmapB: Bitmap,
        mode: DifferenceVisualMode,
        palette: DiffPalette,
        boostFactor: Float,
        thresholdFraction: Float,
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = minOf(bitmapA.width, bitmapB.width, 640)
        val h = minOf(bitmapA.height, bitmapB.height, 640)
        val scaledA = if (bitmapA.width == w && bitmapA.height == h) bitmapA else Bitmap.createScaledBitmap(bitmapA, w, h, true)
        val scaledB = if (bitmapB.width == w && bitmapB.height == h) bitmapB else Bitmap.createScaledBitmap(bitmapB, w, h, true)

        val pxA = IntArray(w * h)
        val pxB = IntArray(w * h)
        scaledA.getPixels(pxA, 0, w, 0, 0, w, h)
        scaledB.getPixels(pxB, 0, w, 0, 0, w, h)
        if (scaledA !== bitmapA) scaledA.recycle()
        if (scaledB !== bitmapB) scaledB.recycle()

        val diffPx = ForensicsEngine.generateDifferenceBuffer(
            pixelsA = pxA,
            pixelsB = pxB,
            width = w,
            height = h,
            mode = mode,
            palette = palette,
            boostFactor = boostFactor,
            thresholdFraction = thresholdFraction,
        )
        Bitmap.createBitmap(diffPx, w, h, Bitmap.Config.ARGB_8888)
    }

    suspend fun renderCustomFormulaBitmap(
        bitmapA: Bitmap,
        bitmapB: Bitmap,
        expression: String,
        p1: Float,
        p2: Float,
        p3: Float,
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        val w = minOf(bitmapA.width, bitmapB.width, 512)
        val h = minOf(bitmapA.height, bitmapB.height, 512)
        val scaledA = if (bitmapA.width == w && bitmapA.height == h) bitmapA else Bitmap.createScaledBitmap(bitmapA, w, h, true)
        val scaledB = if (bitmapB.width == w && bitmapB.height == h) bitmapB else Bitmap.createScaledBitmap(bitmapB, w, h, true)

        val pxA = IntArray(w * h)
        val pxB = IntArray(w * h)
        scaledA.getPixels(pxA, 0, w, 0, 0, w, h)
        scaledB.getPixels(pxB, 0, w, 0, 0, w, h)
        if (scaledA !== bitmapA) scaledA.recycle()
        if (scaledB !== bitmapB) scaledB.recycle()

        PixelMathFormulaEvaluator.evaluateBuffer(expression, pxA, pxB, p1, p2, p3).map { outPx ->
            Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
        }
    }

    suspend fun renderDiagnosticBitmap(
        source: Bitmap,
        overlay: DiagnosticOverlay,
        normalization: NormalizationAdjustments,
        channelIsolation: ColorChannelIsolation,
        elaQuality: Int,
        elaScale: Float,
        bitPlaneIndex: Int,
    ): Bitmap = withContext(Dispatchers.Default) {
        if (overlay == DiagnosticOverlay.NONE && normalization.isIdentity) return@withContext source
        val w = source.width
        val h = source.height
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val normalized = if (normalization.isIdentity) px else ForensicsEngine.applyNormalization(px, normalization, w, h)
        val processed = ForensicsEngine.applyDiagnosticOverlay(
            pixels = normalized,
            width = w,
            height = h,
            overlay = overlay,
            channelIsolation = channelIsolation,
            elaQuality = elaQuality,
            elaScale = elaScale,
            bitPlaneIndex = bitPlaneIndex,
        )
        Bitmap.createBitmap(processed, w, h, Bitmap.Config.ARGB_8888)
    }
}
