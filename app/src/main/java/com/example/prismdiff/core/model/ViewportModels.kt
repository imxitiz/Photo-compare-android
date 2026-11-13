package com.example.prismdiff.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class ScaleSyncMode {
    FIT_NORMALIZED,
    PHYSICAL_1_TO_1,
    VIEWPORT_PERCENT,
    REFERENCE_SCALE,
}

@Immutable
@Serializable
data class SyncFlags(
    val syncZoom: Boolean = true,
    val syncPan: Boolean = true,
    val syncRotation: Boolean = true,
    val syncCrop: Boolean = true,
) {
    val anySynced: Boolean
        get() = syncZoom || syncPan || syncRotation || syncCrop
}

@Immutable
@Serializable
data class MasterViewportTransform(
    val centerU: Float = 0.5f,
    val centerV: Float = 0.5f,
    val zoomFactor: Float = 1f,
    val rotationDeg: Float = 0f,
    val scaleSyncMode: ScaleSyncMode = ScaleSyncMode.FIT_NORMALIZED,
)

@Immutable
@Serializable
data class RelativeSlotOffset(
    val deltaU: Float = 0f,
    val deltaV: Float = 0f,
    val deltaZoom: Float = 1f,
    val deltaRotationDeg: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val independentCenterU: Float = 0.5f,
    val independentCenterV: Float = 0.5f,
    val independentZoom: Float = 1f,
    val independentRotationDeg: Float = 0f,
)

@Immutable
data class EffectiveSlotTransform(
    val centerU: Float,
    val centerV: Float,
    val zoomFactor: Float,
    val rotationDeg: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
)

@Immutable
data class PixelSample(
    val x: Int,
    val y: Int,
    val r: Int,
    val g: Int,
    val b: Int,
    val hex: String,
    val hueDeg: Float,
    val satPercent: Float,
    val lightPercent: Float,
    val cieL: Double,
    val cieA: Double,
    val cieB: Double,
)
