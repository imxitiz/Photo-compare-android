package com.example.prismdiff

import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.MasterViewportTransform
import com.example.prismdiff.core.model.RelativeSlotOffset
import com.example.prismdiff.core.model.ScaleSyncMode
import com.example.prismdiff.core.model.SyncFlags
import com.example.prismdiff.domain.viewport.ViewportSynchronizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportSynchronizerTest {

    private val img12Mp = ImageAsset("a", "file://a.jpg", "a.jpg", width = 4000, height = 3000)
    private val img48Mp = ImageAsset("b", "file://b.jpg", "b.jpg", width = 8000, height = 6000)

    @Test
    fun `fit normalized keeps identical viewport multiplier across mixed resolutions`() {
        val master = MasterViewportTransform(zoomFactor = 2.0f, scaleSyncMode = ScaleSyncMode.FIT_NORMALIZED)
        val tA = ViewportSynchronizer.computeEffectiveTransform(master, RelativeSlotOffset(), SyncFlags(), img12Mp, img12Mp, 1000f, 1000f)
        val tB = ViewportSynchronizer.computeEffectiveTransform(master, RelativeSlotOffset(), SyncFlags(), img48Mp, img12Mp, 1000f, 1000f)

        assertEquals(2.0f, tA.zoomFactor, 0.001f)
        assertEquals(2.0f, tB.zoomFactor, 0.001f)
    }

    @Test
    fun `physical 1 to 1 scales mixed resolution slot so pixel density matches`() {
        val master = MasterViewportTransform(zoomFactor = 1.0f, scaleSyncMode = ScaleSyncMode.PHYSICAL_1_TO_1)
        val tA = ViewportSynchronizer.computeEffectiveTransform(master, RelativeSlotOffset(), SyncFlags(), img12Mp, img12Mp, 1000f, 1000f)
        val tB = ViewportSynchronizer.computeEffectiveTransform(master, RelativeSlotOffset(), SyncFlags(), img48Mp, img12Mp, 1000f, 1000f)

        assertEquals(1.0f, tA.zoomFactor, 0.001f)
        assertEquals(2.0f, tB.zoomFactor, 0.001f)
    }

    @Test
    fun `anchor calibration offset preserves delta during master pan`() {
        val master = MasterViewportTransform(centerU = 0.5f, centerV = 0.5f, zoomFactor = 2.0f)
        val calibratedOffset = RelativeSlotOffset(deltaU = 0.05f, deltaV = -0.04f, deltaZoom = 1.1f)

        val pannedMaster = ViewportSynchronizer.panMasterByScreenDelta(master, dxPx = 100f, dyPx = -50f, 4000, 3000, 1000f, 1000f)
        val tB = ViewportSynchronizer.computeEffectiveTransform(pannedMaster, calibratedOffset, SyncFlags(), img12Mp, img12Mp, 1000f, 1000f)

        assertEquals(pannedMaster.centerU + 0.05f, tB.centerU, 0.0001f)
        assertEquals(pannedMaster.centerV - 0.04f, tB.centerV, 0.0001f)
        assertEquals(2.2f, tB.zoomFactor, 0.001f)
    }

    @Test
    fun `unlocked pan and zoom isolate slot from master updates`() {
        val master = MasterViewportTransform(centerU = 0.25f, centerV = 0.75f, zoomFactor = 8.0f)
        val offset = RelativeSlotOffset(independentCenterU = 0.6f, independentCenterV = 0.4f, independentZoom = 1.5f)
        val flags = SyncFlags(syncZoom = false, syncPan = false, syncRotation = false)

        val t = ViewportSynchronizer.computeEffectiveTransform(master, offset, flags, img12Mp, img12Mp, 1000f, 1000f)
        assertEquals(0.6f, t.centerU, 0.001f)
        assertEquals(0.4f, t.centerV, 0.001f)
        assertEquals(1.5f, t.zoomFactor, 0.001f)
    }

    @Test
    fun `normalizedToScreen and screenToNormalized round trip cleanly with rotation and flip`() {
        val master = MasterViewportTransform(centerU = 0.45f, centerV = 0.55f, zoomFactor = 3.0f, rotationDeg = 35f)
        val offset = RelativeSlotOffset(flipHorizontal = true)
        val transform = ViewportSynchronizer.computeEffectiveTransform(master, offset, SyncFlags(), img12Mp, img12Mp, 1080f, 1920f)

        val (sx, sy) = ViewportSynchronizer.normalizedToScreen(0.42f, 0.52f, transform, 4000, 3000, 1080f, 1920f)
        val (u, v) = ViewportSynchronizer.screenToNormalized(sx, sy, transform, 4000, 3000, 1080f, 1920f)

        assertEquals(0.42f, u, 0.002f)
        assertEquals(0.52f, v, 0.002f)
    }

    @Test
    fun `screenPixelsPerImagePixel detects 400 percent nearest-neighbor threshold`() {
        val oneToOneZoom = ViewportSynchronizer.zoomForPhysicalOneToOne(1000, 1000, 500f, 500f)
        assertEquals(2.0f, oneToOneZoom, 0.001f)

        val pxRatioAt400 = ViewportSynchronizer.screenPixelsPerImagePixel(oneToOneZoom * 4f, 1000, 1000, 500f, 500f)
        assertTrue(pxRatioAt400 >= ViewportSynchronizer.PIXEL_GRID_THRESHOLD_SCALE)
    }
}
