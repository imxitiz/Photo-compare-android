package com.example.prismdiff.core.storage

import com.example.prismdiff.core.model.BlendOperator
import com.example.prismdiff.core.model.CanvasBackdropMode
import com.example.prismdiff.core.model.ColorChannelIsolation
import com.example.prismdiff.core.model.ComparisonMode
import com.example.prismdiff.core.model.DiagnosticOverlay
import com.example.prismdiff.core.model.DiffPalette
import com.example.prismdiff.core.model.DifferenceVisualMode
import com.example.prismdiff.core.model.InfoDensity
import com.example.prismdiff.core.model.ScaleSyncMode
import com.example.prismdiff.core.model.SplitOrientation
import com.example.prismdiff.core.model.SyncFlags
import com.example.prismdiff.core.model.WorkspacePreset
import com.example.prismdiff.core.shader.PixelMathFormulaEvaluator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages the 11 built-in studio presets plus strict schema-validated JSON import/export
 * (`.prismdiff.json`, schemaVersion = 1).
 */
object WorkspacePresetManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val builtInPresets: List<WorkspacePreset> = listOf(
        WorkspacePreset(
            id = "quick_compare",
            name = "Quick Compare",
            description = "Clean 2-Up adaptive split with synchronized zoom & pan and crisp OLED framing.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.SIDE_BY_SIDE,
            backdropMode = CanvasBackdropMode.OLED_BLACK,
        ),
        WorkspacePreset(
            id = "photo_culling",
            name = "Photo Culling",
            description = "Fast burst comparison with Focus Peaking & King-of-the-Hill reference pinning.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.SIDE_BY_SIDE,
            diagnosticOverlay = DiagnosticOverlay.FOCUS_PEAKING,
            hudTemplate = "{FileName} • {Shutter} ISO{ISO} • {Quality}",
        ),
        WorkspacePreset(
            id = "before_after",
            name = "Before & After",
            description = "Interactive Curtain Wipe slider for editing or camera comparisons.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.CURTAIN_WIPE,
            backdropMode = CanvasBackdropMode.NEUTRAL_18_GRAY,
        ),
        WorkspacePreset(
            id = "photographer",
            name = "Photographer",
            description = "18% neutral gray surroundings, live RGB histograms, and EV/ISO metadata pills.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.SIDE_BY_SIDE,
            backdropMode = CanvasBackdropMode.NEUTRAL_18_GRAY,
            showHistogramHud = true,
            infoDensity = InfoDensity.PRO_STUDIO,
            hudTemplate = "{Camera} • {FocalLength} {Aperture} {Shutter} ISO{ISO}",
        ),
        WorkspacePreset(
            id = "color_analysis",
            name = "Color Analysis",
            description = "18% neutral backdrop, ΔE2000 chromatic shift heatmap, and Luminosity blend inspection.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.DIFFERENCE_MAP,
            differenceMode = DifferenceVisualMode.DELTA_E_2000,
            backdropMode = CanvasBackdropMode.NEUTRAL_18_GRAY,
            showHistogramHud = true,
        ),
        WorkspacePreset(
            id = "image_quality",
            name = "Image Quality",
            description = "Physical 1:1 pixel scaling with SSIM structural similarity heatmap.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.DIFFERENCE_MAP,
            scaleSyncMode = ScaleSyncMode.PHYSICAL_1_TO_1,
            differenceMode = DifferenceVisualMode.SSIM_HEATMAP,
            infoDensity = InfoDensity.PRO_STUDIO,
        ),
        WorkspacePreset(
            id = "pixel_inspection",
            name = "Pixel Inspection",
            description = "Synchronized Multi-Loupe magnifier with nearest-neighbor pixel grid readiness.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.MULTI_LOUPE,
            scaleSyncMode = ScaleSyncMode.PHYSICAL_1_TO_1,
            infoDensity = InfoDensity.PRO_STUDIO,
        ),
        WorkspacePreset(
            id = "forensic_inspection",
            name = "Forensic Inspection",
            description = "Error Level Analysis (ELA) residual amplification + JPEG quality/BPP HUD.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.SIDE_BY_SIDE,
            diagnosticOverlay = DiagnosticOverlay.ELA_FORENSICS,
            hudTemplate = "{FileName} • {Quality} • {BPP} • {SizeMB}",
            infoDensity = InfoDensity.PRO_STUDIO,
        ),
        WorkspacePreset(
            id = "studio_review",
            name = "Studio Review",
            description = "Onion Skin blend stack with Difference operator and False Color IRE scopes.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.ONION_SKIN,
            blendOperator = BlendOperator.DIFFERENCE,
            diagnosticOverlay = DiagnosticOverlay.FALSE_COLOR_IRE,
            backdropMode = CanvasBackdropMode.NEUTRAL_18_GRAY,
        ),
        WorkspacePreset(
            id = "multi_image_selection",
            name = "Multi-Image Selection",
            description = "Synchronized 4-Up / Contact Sheet matrix for multi-shot camera shootouts.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.CONTACT_SHEET,
            infoDensity = InfoDensity.STANDARD,
        ),
        WorkspacePreset(
            id = "minimal_distraction",
            name = "Minimal Distraction",
            description = "Pure pixel-first OLED canvas with minimal HUD clutter and subtle 1dp divider.",
            isBuiltIn = true,
            comparisonMode = ComparisonMode.SIDE_BY_SIDE,
            backdropMode = CanvasBackdropMode.OLED_BLACK,
            infoDensity = InfoDensity.MINIMAL,
            showHudPills = false,
            showHistogramHud = false,
        ),
    )

    fun exportToJson(preset: WorkspacePreset): String = json.encodeToString(preset)

    fun importFromJson(rawJson: String): Result<WorkspacePreset> = runCatching {
        val decoded = json.decodeFromString<WorkspacePreset>(rawJson)
        require(decoded.schemaVersion in 1..WorkspacePreset.CURRENT_SCHEMA_VERSION) {
            "Unsupported preset schemaVersion: ${decoded.schemaVersion}"
        }
        require(decoded.name.isNotBlank()) { "Preset name cannot be blank" }
        decoded.customShader?.let { shader ->
            PixelMathFormulaEvaluator.validate(shader.expression).getOrThrow()
        }
        decoded.copy(
            isBuiltIn = false,
            strobeFrequencyHz = decoded.strobeFrequencyHz.coerceIn(0.5f, 10f),
            dividerThicknessDp = decoded.dividerThicknessDp.coerceIn(0.5f, 6f),
        )
    }
}
