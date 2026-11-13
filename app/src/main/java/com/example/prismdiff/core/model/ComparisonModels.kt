package com.example.prismdiff.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class ComparisonMode {
    SIDE_BY_SIDE,
    VERTICAL_SPLIT,
    CURTAIN_WIPE,
    ONION_SKIN,
    LAYER_STACK,
    BLINK_STROBE,
    DIFFERENCE_MAP,
    MULTI_LOUPE,
    ROI_MATRIX,
    CONTACT_SHEET,
    CUSTOM_SHADER,
}

@Serializable
enum class SplitOrientation {
    AUTO,
    HORIZONTAL_LEFT_RIGHT,
    VERTICAL_TOP_BOTTOM,
}

@Serializable
enum class MultiSlotLayout {
    AUTO,
    COLUMNS,
    ROWS,
    GRID,
    FOCUS_PAIR,
}

@Serializable
enum class StudioDockVisibility {
    COMPACT,
    EXPANDED,
    HIDDEN,
}

@Serializable
enum class BlendOperator {
    NORMAL,
    DIFFERENCE,
    SUBTRACT,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    LUMINOSITY,
    COLOR,
}

@Serializable
enum class DifferenceVisualMode {
    ABSOLUTE_BOOST,
    THRESHOLD_MASK,
    SSIM_HEATMAP,
    DELTA_E_2000,
    EDGE_DIFF,
    GRAYSCALE_DIFF,
}

@Serializable
enum class DiffPalette {
    MAGENTA,
    CYAN,
    ACID_GREEN,
    THERMAL_INFERNO,
}

@Serializable
enum class DiagnosticOverlay {
    NONE,
    FOCUS_PEAKING,
    ZEBRA_CLIPPING,
    FALSE_COLOR_IRE,
    CHANNEL_ISOLATION,
    ELA_FORENSICS,
    NOISE_RESIDUAL,
    BIT_PLANE_SLICE,
}

@Serializable
enum class ColorChannelIsolation {
    ALL_RGB,
    RED,
    GREEN,
    BLUE,
    LUMA_Y,
    CHROMA_UV,
    HUE,
    SATURATION,
}

@Immutable
@Serializable
data class NormalizationAdjustments(
    val enabled: Boolean = false,
    val exposureEv: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperatureShift: Float = 0f,
    val tintShift: Float = 0f,
    val grayscale: Boolean = false,
    val gamma: Float = 1f,
    val sharpenAmount: Float = 0f,
) {
    val isIdentity: Boolean
        get() = !enabled || (
            exposureEv == 0f &&
                brightness == 0f &&
                contrast == 1f &&
                saturation == 1f &&
                temperatureShift == 0f &&
                tintShift == 0f &&
                !grayscale &&
                gamma == 1f &&
                sharpenAmount == 0f
            )
}

@Serializable
enum class RegionShape {
    RECTANGLE,
    CIRCLE,
    FREEFORM,
}

@Immutable
@Serializable
data class NormPoint(
    val x: Float,
    val y: Float,
)

@Immutable
@Serializable
data class ComparisonRegion(
    val id: String,
    val name: String,
    val shape: RegionShape = RegionShape.RECTANGLE,
    val normLeft: Float = 0.25f,
    val normTop: Float = 0.25f,
    val normRight: Float = 0.75f,
    val normBottom: Float = 0.75f,
    val points: List<NormPoint> = emptyList(),
)

@Serializable
enum class AnnotationType {
    TEXT,
    ARROW,
    RECTANGLE,
    CIRCLE,
    FREEHAND,
}

@Immutable
@Serializable
data class AnnotationItem(
    val id: String,
    val type: AnnotationType,
    val normStartX: Float,
    val normStartY: Float,
    val normEndX: Float,
    val normEndY: Float,
    val text: String = "",
    val colorArgb: Long = 0xFF5CE1E6L,
    val points: List<NormPoint> = emptyList(),
)

@Immutable
@Serializable
data class LayerSlotConfig(
    val slotIndex: Int,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blendOperator: BlendOperator = BlendOperator.NORMAL,
    val locked: Boolean = false,
)

@Immutable
@Serializable
data class LoupeState(
    val normX: Float = 0.5f,
    val normY: Float = 0.5f,
    val magnification: Float = 8f,
    val isDocked: Boolean = true,
)
