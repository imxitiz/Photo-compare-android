package com.example.prismdiff.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class AppLaunchScreen {
    STUDIO_HOME,
    COLLECTION_BROWSER,
    DIRECT_CANVAS,
}

@Serializable
enum class AppThemeMode {
    OLED_DARK,
    SYSTEM,
    LIGHT,
}

@Serializable
enum class CanvasBackdropMode {
    OLED_BLACK,
    NEUTRAL_18_GRAY,
    STUDIO_WHITE,
    CHECKERBOARD,
    CUSTOM,
}

@Serializable
enum class SortOrder {
    EXIF_DATE_DESC,
    EXIF_DATE_ASC,
    FILENAME_NATURAL_ASC,
    FILENAME_NATURAL_DESC,
    MODIFIED_DESC,
    FILE_SIZE_DESC,
    RESOLUTION_DESC,
    SHARPNESS_DESC,
}

@Serializable
enum class InfoDensity {
    MINIMAL,
    STANDARD,
    PRO_STUDIO,
}

@Immutable
@Serializable
data class CustomShaderDefinition(
    val id: String,
    val name: String,
    val description: String,
    val expression: String,
    val param1Label: String = "Boost",
    val param1Default: Float = 8f,
    val param2Label: String = "Threshold",
    val param2Default: Float = 0.05f,
    val param3Label: String = "Mix",
    val param3Default: Float = 1f,
)

@Immutable
@Serializable
data class WorkspacePreset(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean = false,
    val comparisonMode: ComparisonMode = ComparisonMode.SIDE_BY_SIDE,
    val splitOrientation: SplitOrientation = SplitOrientation.AUTO,
    val scaleSyncMode: ScaleSyncMode = ScaleSyncMode.FIT_NORMALIZED,
    val syncFlags: SyncFlags = SyncFlags(),
    val backdropMode: CanvasBackdropMode = CanvasBackdropMode.OLED_BLACK,
    val blendOperator: BlendOperator = BlendOperator.NORMAL,
    val differenceMode: DifferenceVisualMode = DifferenceVisualMode.ABSOLUTE_BOOST,
    val diffPalette: DiffPalette = DiffPalette.MAGENTA,
    val diagnosticOverlay: DiagnosticOverlay = DiagnosticOverlay.NONE,
    val channelIsolation: ColorChannelIsolation = ColorChannelIsolation.ALL_RGB,
    val dividerThicknessDp: Float = 1f,
    val strobeFrequencyHz: Float = 2f,
    val infoDensity: InfoDensity = InfoDensity.STANDARD,
    val showHudPills: Boolean = true,
    val showHistogramHud: Boolean = false,
    val hudTemplate: String = "{FileName} • {Aperture} {Shutter} ISO{ISO} • {SizeMB}",
    val customShader: CustomShaderDefinition? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Immutable
data class AppPreferences(
    val launchScreen: AppLaunchScreen = AppLaunchScreen.STUDIO_HOME,
    val themeMode: AppThemeMode = AppThemeMode.OLED_DARK,
    val useDynamicColor: Boolean = true,
    val canvasBackdrop: CanvasBackdropMode = CanvasBackdropMode.OLED_BLACK,
    val customBackdropArgb: Long = 0xFF1A1D24L,
    val maskGpsByDefault: Boolean = true,
    val volumeKeyNavigation: Boolean = false,
    val keepScreenOnInStudio: Boolean = true,
    val photosensitiveGuardEnabled: Boolean = true,
    val showPixelGridAbove400: Boolean = true,
    val hudTemplate: String = "{FileName} • {Aperture} {Shutter} ISO{ISO} • {SizeMB}",
    val activePresetId: String = "quick_compare",
    val defaultSortOrder: SortOrder = SortOrder.EXIF_DATE_DESC,
)
