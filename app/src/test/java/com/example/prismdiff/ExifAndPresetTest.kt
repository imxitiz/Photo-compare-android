package com.example.prismdiff

import com.example.prismdiff.core.decoder.ExifDiffEngine
import com.example.prismdiff.core.model.ExifSnapshot
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.shader.PixelMathFormulaEvaluator
import com.example.prismdiff.core.storage.MediaIngestionRepository
import com.example.prismdiff.core.storage.WorkspacePresetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifAndPresetTest {

    @Test
    fun `exif diff computes EV deltas and respects GPS privacy mask`() {
        val assetA = ImageAsset(
            id = "a",
            uriString = "file://a.jpg",
            displayName = "IMG_2.jpg",
            width = 4000,
            height = 3000,
            fileSizeBytes = 2_000_000L,
            exifSnapshot = ExifSnapshot(
                cameraModel = "Pixel 9 Pro",
                iso = 100,
                apertureFNumber = 2.8,
                shutterSpeedSeconds = 0.01,
                shutterSpeedDisplay = "1/100s",
                gpsLatitude = 37.4220,
                gpsLongitude = -122.0841,
            ),
        )
        val assetB = ImageAsset(
            id = "b",
            uriString = "file://b.jpg",
            displayName = "IMG_10.jpg",
            width = 4000,
            height = 3000,
            fileSizeBytes = 4_000_000L,
            exifSnapshot = ExifSnapshot(
                cameraModel = "Pixel 9 Pro",
                iso = 200,
                apertureFNumber = 2.0,
                shutterSpeedSeconds = 0.005,
                shutterSpeedDisplay = "1/200s",
                gpsLatitude = 37.4220,
                gpsLongitude = -122.0841,
            ),
        )

        val maskedRows = ExifDiffEngine.compareMetadata(assetA, assetB, maskGps = true)
        val isoRow = maskedRows.first { it.key == "iso" }
        val gpsMasked = maskedRows.first { it.key == "gps" }
        assertEquals("+1.00 EV", isoRow.deltaSummary)
        assertTrue(gpsMasked.valueA.contains("Masked"))

        val unmaskedRows = ExifDiffEngine.compareMetadata(assetA, assetB, maskGps = false)
        val gpsUnmasked = unmaskedRows.first { it.key == "gps" }
        assertTrue(gpsUnmasked.valueA.contains("37.42200"))

        val hud = ExifDiffEngine.formatHudTemplate("{FileName} ISO{ISO} {Shutter}", assetA)
        assertEquals("IMG_2.jpg ISO100 1/100s", hud)
    }

    @Test
    fun `natural filename comparator places IMG_2 before IMG_10`() {
        val sorted = listOf("IMG_10.jpg", "IMG_2.jpg", "IMG_1.jpg").sortedWith(MediaIngestionRepository::naturalCompare)
        assertEquals(listOf("IMG_1.jpg", "IMG_2.jpg", "IMG_10.jpg"), sorted)
    }

    @Test
    fun `workspace preset JSON export import round trip and validation`() {
        val builtIn = WorkspacePresetManager.builtInPresets.first()
        val jsonText = WorkspacePresetManager.exportToJson(builtIn)
        val reimported = WorkspacePresetManager.importFromJson(jsonText)

        assertTrue(reimported.isSuccess)
        assertEquals(builtIn.name, reimported.getOrThrow().name)
        assertFalse(reimported.getOrThrow().isBuiltIn)

        val badSchema = jsonText.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        assertTrue(WorkspacePresetManager.importFromJson(badSchema).isFailure)
    }

    @Test
    fun `pixel math formula AST evaluator executes built-in expressions and rejects unsafe tokens`() {
        val expr = PixelMathFormulaEvaluator.builtInTemplates.first().expression
        assertTrue(PixelMathFormulaEvaluator.validate(expr).isSuccess)
        assertTrue(PixelMathFormulaEvaluator.validate("Runtime.exec('bad')").isFailure)

        val pxA = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val pxB = intArrayOf(0xFF0000FF.toInt(), 0xFF00FF00.toInt())
        val evaluated = PixelMathFormulaEvaluator.evaluateBuffer(expr, pxA, pxB, p1 = 6f, p2 = 0.02f, p3 = 1f)
        assertTrue(evaluated.isSuccess)
        assertEquals(2, evaluated.getOrThrow().size)
    }
}
