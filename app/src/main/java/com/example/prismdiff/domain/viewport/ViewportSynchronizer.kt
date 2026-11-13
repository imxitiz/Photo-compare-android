package com.example.prismdiff.domain.viewport

import com.example.prismdiff.core.model.EffectiveSlotTransform
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.MasterViewportTransform
import com.example.prismdiff.core.model.RelativeSlotOffset
import com.example.prismdiff.core.model.ScaleSyncMode
import com.example.prismdiff.core.model.SyncFlags
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Single Source of Truth (SSOT) viewport synchronization math engine.
 *
 * Eliminates bidirectional listener-loop race conditions by deriving each image slot's
 * effective transform purely from an immutable [MasterViewportTransform], per-slot
 * [RelativeSlotOffset], and [SyncFlags].
 */
object ViewportSynchronizer {

    const val MIN_ZOOM = 0.25f
    const val MAX_ZOOM = 64.0f
    const val PIXEL_GRID_THRESHOLD_SCALE = 4.0f

    fun computeEffectiveTransform(
        master: MasterViewportTransform,
        slotOffset: RelativeSlotOffset,
        syncFlags: SyncFlags,
        slotAsset: ImageAsset?,
        referenceAsset: ImageAsset?,
        containerWidthPx: Float,
        containerHeightPx: Float,
    ): EffectiveSlotTransform {
        val u = if (syncFlags.syncPan) {
            (master.centerU + slotOffset.deltaU).coerceIn(0f, 1f)
        } else {
            slotOffset.independentCenterU.coerceIn(0f, 1f)
        }

        val v = if (syncFlags.syncPan) {
            (master.centerV + slotOffset.deltaV).coerceIn(0f, 1f)
        } else {
            slotOffset.independentCenterV.coerceIn(0f, 1f)
        }

        val baseZoom = if (syncFlags.syncZoom) {
            val modeMultiplier = computeScaleModeRatio(
                mode = master.scaleSyncMode,
                slotAsset = slotAsset,
                referenceAsset = referenceAsset,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
            )
            (master.zoomFactor * modeMultiplier * slotOffset.deltaZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        } else {
            slotOffset.independentZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        }

        val rotation = if (syncFlags.syncRotation) {
            normalizeAngle(master.rotationDeg + slotOffset.deltaRotationDeg)
        } else {
            normalizeAngle(slotOffset.independentRotationDeg)
        }

        return EffectiveSlotTransform(
            centerU = u,
            centerV = v,
            zoomFactor = baseZoom,
            rotationDeg = rotation,
            flipHorizontal = slotOffset.flipHorizontal,
            flipVertical = slotOffset.flipVertical,
        )
    }

    /**
     * Computes scale ratio relative to a slot's fit-to-container baseline so mixed-resolution
     * images (e.g., 12MP vs 50MP) map accurately under the chosen [ScaleSyncMode].
     */
    fun computeScaleModeRatio(
        mode: ScaleSyncMode,
        slotAsset: ImageAsset?,
        referenceAsset: ImageAsset?,
        containerWidthPx: Float,
        containerHeightPx: Float,
    ): Float {
        if (slotAsset == null || referenceAsset == null) return 1f
        if (slotAsset.width <= 0 || slotAsset.height <= 0 ||
            referenceAsset.width <= 0 || referenceAsset.height <= 0
        ) {
            return 1f
        }
        val safeW = max(1f, containerWidthPx)
        val safeH = max(1f, containerHeightPx)

        val fitScaleRef = min(safeW / referenceAsset.width, safeH / referenceAsset.height)
        val fitScaleSlot = min(safeW / slotAsset.width, safeH / slotAsset.height)
        if (fitScaleSlot <= 0f || fitScaleRef <= 0f) return 1f

        return when (mode) {
            ScaleSyncMode.FIT_NORMALIZED -> 1f
            ScaleSyncMode.PHYSICAL_1_TO_1 -> {
                // Keep physical screen-pixels-per-image-pixel equal across sensors.
                fitScaleRef / fitScaleSlot
            }
            ScaleSyncMode.VIEWPORT_PERCENT -> {
                (slotAsset.width.toFloat() / referenceAsset.width.toFloat()) * (fitScaleRef / fitScaleSlot)
            }
            ScaleSyncMode.REFERENCE_SCALE -> {
                fitScaleRef / fitScaleSlot
            }
        }
    }

    /**
     * Computes screen pixels per source image pixel. Once >= [PIXEL_GRID_THRESHOLD_SCALE] (400%),
     * renderers switch to FilterQuality.None (nearest-neighbor crisp pixel grid).
     */
    fun screenPixelsPerImagePixel(
        effectiveZoom: Float,
        imageWidth: Int,
        imageHeight: Int,
        containerWidthPx: Float,
        containerHeightPx: Float,
    ): Float {
        if (imageWidth <= 0 || imageHeight <= 0 || containerWidthPx <= 0f || containerHeightPx <= 0f) {
            return effectiveZoom
        }
        val fitScale = min(containerWidthPx / imageWidth, containerHeightPx / imageHeight)
        return fitScale * effectiveZoom
    }

    /**
     * Converts a zoom multiplier so that 1 screen pixel equals 1 image pixel (100% physical 1:1).
     */
    fun zoomForPhysicalOneToOne(
        imageWidth: Int,
        imageHeight: Int,
        containerWidthPx: Float,
        containerHeightPx: Float,
    ): Float {
        if (imageWidth <= 0 || imageHeight <= 0 || containerWidthPx <= 0f || containerHeightPx <= 0f) {
            return 1f
        }
        val fitScale = min(containerWidthPx / imageWidth, containerHeightPx / imageHeight)
        return (1f / fitScale).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /**
     * Applies a screen-space pan gesture delta `(dxPx, dyPx)` to [MasterViewportTransform],
     * accounting for current zoom and rotation so dragging feels locked to the finger.
     */
    fun panMasterByScreenDelta(
        master: MasterViewportTransform,
        dxPx: Float,
        dyPx: Float,
        referenceWidth: Int,
        referenceHeight: Int,
        containerWidthPx: Float,
        containerHeightPx: Float,
    ): MasterViewportTransform {
        if (referenceWidth <= 0 || referenceHeight <= 0 || containerWidthPx <= 0f || containerHeightPx <= 0f) {
            return master
        }
        val fitScale = min(containerWidthPx / referenceWidth, containerHeightPx / referenceHeight)
        val renderedW = referenceWidth * fitScale * master.zoomFactor
        val renderedH = referenceHeight * fitScale * master.zoomFactor

        val rad = Math.toRadians(-master.rotationDeg.toDouble())
        val rotDx = (dxPx * cos(rad) - dyPx * sin(rad)).toFloat()
        val rotDy = (dxPx * sin(rad) + dyPx * cos(rad)).toFloat()

        val du = -rotDx / max(1f, renderedW)
        val dv = -rotDy / max(1f, renderedH)

        return master.copy(
            centerU = (master.centerU + du).coerceIn(0f, 1f),
            centerV = (master.centerV + dv).coerceIn(0f, 1f),
        )
    }

    /**
     * Applies a screen-space pan gesture delta to an individual slot's [RelativeSlotOffset]
     * either as an Anchor Calibration delta (when [calibrateRelativeDelta] is true) or as
     * an independent unlocked position.
     */
    fun panSlotOffsetByScreenDelta(
        offset: RelativeSlotOffset,
        master: MasterViewportTransform,
        dxPx: Float,
        dyPx: Float,
        imageWidth: Int,
        imageHeight: Int,
        containerWidthPx: Float,
        containerHeightPx: Float,
        calibrateRelativeDelta: Boolean,
    ): RelativeSlotOffset {
        if (imageWidth <= 0 || imageHeight <= 0 || containerWidthPx <= 0f || containerHeightPx <= 0f) {
            return offset
        }
        val fitScale = min(containerWidthPx / imageWidth, containerHeightPx / imageHeight)
        val activeZoom = if (calibrateRelativeDelta) master.zoomFactor * offset.deltaZoom else offset.independentZoom
        val activeRot = if (calibrateRelativeDelta) master.rotationDeg + offset.deltaRotationDeg else offset.independentRotationDeg

        val renderedW = imageWidth * fitScale * activeZoom
        val renderedH = imageHeight * fitScale * activeZoom
        val rad = Math.toRadians(-activeRot.toDouble())
        val rotDx = (dxPx * cos(rad) - dyPx * sin(rad)).toFloat()
        val rotDy = (dxPx * sin(rad) + dyPx * cos(rad)).toFloat()
        val du = -rotDx / max(1f, renderedW)
        val dv = -rotDy / max(1f, renderedH)

        return if (calibrateRelativeDelta) {
            offset.copy(
                deltaU = (offset.deltaU + du).coerceIn(-0.8f, 0.8f),
                deltaV = (offset.deltaV + dv).coerceIn(-0.8f, 0.8f),
            )
        } else {
            offset.copy(
                independentCenterU = (offset.independentCenterU + du).coerceIn(0f, 1f),
                independentCenterV = (offset.independentCenterV + dv).coerceIn(0f, 1f),
            )
        }
    }

    fun zoomMaster(master: MasterViewportTransform, zoomRatio: Float): MasterViewportTransform {
        return master.copy(zoomFactor = (master.zoomFactor * zoomRatio).coerceIn(MIN_ZOOM, MAX_ZOOM))
    }

    fun resetMaster(): MasterViewportTransform = MasterViewportTransform()

    /**
     * Converts a normalized image coordinate `(u, v)` to screen coordinates `(x, y)` inside
     * a container of `(containerWidthPx, containerHeightPx)`.
     */
    fun normalizedToScreen(
        u: Float,
        v: Float,
        transform: EffectiveSlotTransform,
        imageWidth: Int,
        imageHeight: Int,
        containerWidthPx: Float,
        containerHeightPx: Float,
    ): Pair<Float, Float> {
        if (imageWidth <= 0 || imageHeight <= 0 || containerWidthPx <= 0f || containerHeightPx <= 0f) {
            return containerWidthPx * 0.5f to containerHeightPx * 0.5f
        }
        val fitScale = min(containerWidthPx / imageWidth, containerHeightPx / imageHeight)
        val renderedW = imageWidth * fitScale * transform.zoomFactor
        val renderedH = imageHeight * fitScale * transform.zoomFactor

        var localX = (u - transform.centerU) * renderedW
        var localY = (v - transform.centerV) * renderedH
        if (transform.flipHorizontal) localX = -localX
        if (transform.flipVertical) localY = -localY

        val rad = Math.toRadians(transform.rotationDeg.toDouble())
        val rotX = (localX * cos(rad) - localY * sin(rad)).toFloat()
        val rotY = (localX * sin(rad) + localY * cos(rad)).toFloat()

        return (containerWidthPx * 0.5f + rotX) to (containerHeightPx * 0.5f + rotY)
    }

    /**
     * Converts a screen tap `(screenX, screenY)` back to normalized image coordinates `(u, v)`.
     */
    fun screenToNormalized(
        screenX: Float,
        screenY: Float,
        transform: EffectiveSlotTransform,
        imageWidth: Int,
        imageHeight: Int,
        containerWidthPx: Float,
        containerHeightPx: Float,
    ): Pair<Float, Float> {
        if (imageWidth <= 0 || imageHeight <= 0 || containerWidthPx <= 0f || containerHeightPx <= 0f) {
            return 0.5f to 0.5f
        }
        val fitScale = min(containerWidthPx / imageWidth, containerHeightPx / imageHeight)
        val renderedW = max(1f, imageWidth * fitScale * transform.zoomFactor)
        val renderedH = max(1f, imageHeight * fitScale * transform.zoomFactor)

        val dx = screenX - containerWidthPx * 0.5f
        val dy = screenY - containerHeightPx * 0.5f
        val rad = Math.toRadians(-transform.rotationDeg.toDouble())
        var localX = (dx * cos(rad) - dy * sin(rad)).toFloat()
        var localY = (dx * sin(rad) + dy * cos(rad)).toFloat()
        if (transform.flipHorizontal) localX = -localX
        if (transform.flipVertical) localY = -localY

        val u = (transform.centerU + localX / renderedW).coerceIn(0f, 1f)
        val v = (transform.centerV + localY / renderedH).coerceIn(0f, 1f)
        return u to v
    }

    fun normalizeAngle(deg: Float): Float {
        val mod = deg % 360f
        return if (mod < 0f) mod + 360f else mod
    }
}
