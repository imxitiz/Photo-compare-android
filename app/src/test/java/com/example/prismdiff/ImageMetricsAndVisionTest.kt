package com.example.prismdiff

import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.ImageCullingState
import com.example.prismdiff.core.vision.AlignmentEngine
import com.example.prismdiff.core.vision.BurstClusteringEngine
import com.example.prismdiff.core.vision.ColorMath
import com.example.prismdiff.core.vision.EloTournamentEngine
import com.example.prismdiff.core.vision.ImageMetricsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImageMetricsAndVisionTest {

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun makePattern(width: Int, height: Int, shiftX: Int = 0, shiftY: Int = 0, blur: Boolean = false): IntArray {
        val out = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sx = (x - shiftX).coerceIn(0, width - 1)
                val sy = (y - shiftY).coerceIn(0, height - 1)
                val checker = if (((sx / 8) + (sy / 8)) % 2 == 0) 210 else 45
                val ring = if ((sx - width / 2) * (sx - width / 2) + (sy - height / 2) * (sy - height / 2) < 196) 240 else checker
                val v = if (blur) (ring * 0.4 + 128 * 0.6).toInt() else ring
                out[y * width + x] = rgb(v, (v * 0.9).toInt(), (255 - v / 3).coerceIn(0, 255))
            }
        }
        return out
    }

    @Test
    fun `identical buffers yield exact SSIM 1 and zero MAE and DeltaE`() {
        val w = 64
        val h = 64
        val a = makePattern(w, h)
        val report = ImageMetricsEngine.compareBuffers(a, a.copyOf(), w, h)

        assertEquals(1.0, report.ssim, 0.0001)
        assertEquals(99.0, report.psnrDb, 0.01)
        assertEquals(0.0, report.mae, 0.0001)
        assertEquals(0.0, report.meanDeltaE2000, 0.0001)
        assertEquals(0.0, report.changedPixelPercent, 0.0001)
        assertTrue(report.reports.size >= 6)
    }

    @Test
    fun `sharp pattern scores higher sharpness than softened pattern`() {
        val w = 64
        val h = 64
        val sharp = makePattern(w, h, blur = false)
        val soft = makePattern(w, h, blur = true)
        val report = ImageMetricsEngine.compareBuffers(sharp, soft, w, h)

        assertTrue("Expected sharpnessA (${report.sharpnessA}) > sharpnessB (${report.sharpnessB})", report.sharpnessA > report.sharpnessB)
        assertTrue("Expected SSIM < 0.98 for contrast-softened buffer", report.ssim < 0.98)
    }

    @Test
    fun `histogram bin totals match sampled pixel count`() {
        val w = 48
        val h = 48
        val px = makePattern(w, h)
        val hist = ImageMetricsEngine.computeHistogram(px, "Test ROI")

        assertEquals(w * h, hist.sampleCount)
        assertEquals(w * h, hist.lumaBins.sum())
        assertEquals(w * h, hist.redBins.sum())
        assertEquals(w * h, hist.greenBins.sum())
        assertEquals(w * h, hist.blueBins.sum())
    }

    @Test
    fun `CIEDE2000 produces near zero for same color and large shift for opposing hues`() {
        assertEquals(0.0, ColorMath.ciede2000(rgb(120, 80, 200), rgb(120, 80, 200)), 0.0001)
        val dist = ColorMath.ciede2000(rgb(255, 0, 0), rgb(0, 0, 255))
        assertTrue("Expected strong perceptual shift > 20, got $dist", dist > 20.0)
    }

    @Test
    fun `alignment engine recovers translation shift on textured scene`() {
        val w = 72
        val h = 72
        val a = makePattern(w, h, shiftX = 0, shiftY = 0)
        val b = makePattern(w, h, shiftX = 6, shiftY = -4)
        val aligned = AlignmentEngine.autoAlign(a, b, w, h, allowRotationAndScale = false)

        assertTrue("Expected reliable alignment, got ${aligned.summaryNote}", aligned.isReliable)
        assertTrue("Expected positive deltaU near +6/72, got ${aligned.offset.deltaU}", abs(aligned.offset.deltaU - (6f / w)) < 0.04f)
        assertTrue("Expected negative deltaV near -4/72, got ${aligned.offset.deltaV}", abs(aligned.offset.deltaV - (-4f / h)) < 0.04f)
    }

    @Test
    fun `burst clustering dHash and Elo tournament behave deterministically`() {
        val w = 64
        val h = 64
        val scene1 = IntArray(w * h)
        val scene2 = IntArray(w * h)
        val opposite = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val base = ((x * 2 + y * 3) * 255 / (w * 2 + h * 3)).coerceIn(0, 255)
                val shifted = (((x - 1).coerceAtLeast(0) * 2 + y * 3) * 255 / (w * 2 + h * 3)).coerceIn(0, 255)
                scene1[y * w + x] = rgb(base, base, base)
                scene2[y * w + x] = rgb(shifted, shifted, shifted)
                opposite[y * w + x] = rgb(255 - base, 255 - base, 255 - base)
            }
        }
        val h1 = BurstClusteringEngine.computeDHash(scene1, w, h)
        val h2 = BurstClusteringEngine.computeDHash(scene2, w, h)
        val hOpp = BurstClusteringEngine.computeDHash(opposite, w, h)

        assertTrue("Similar scenes should be close in dHash", BurstClusteringEngine.hammingDistance(h1, h2) <= 10)
        assertTrue("Inverted scenes should be distant in dHash", BurstClusteringEngine.hammingDistance(h1, hOpp) >= 32)

        val assets = listOf(
            ImageAsset("1", "u1", "1.jpg", dateTakenMs = 1000L, dHash = h1),
            ImageAsset("2", "u2", "2.jpg", dateTakenMs = 2000L, dHash = h2),
        )
        val clusters = BurstClusteringEngine.clusterAssets(assets)
        assertEquals(1, clusters.size)
        assertEquals(2, clusters.first().assets.size)

        val (winner, loser) = EloTournamentEngine.recordMatch(ImageCullingState("1"), ImageCullingState("2"))
        assertTrue(winner.eloRating > 1200.0)
        assertTrue(loser.eloRating < 1200.0)
        val pair = EloTournamentEngine.suggestNextPair(listOf("1", "2"), mapOf("1" to winner, "2" to loser))
        assertEquals(setOf("1", "2"), setOf(pair?.first, pair?.second))
    }
}
