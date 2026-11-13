package com.example.prismdiff.feature.studio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import kotlin.math.hypot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.prismdiff.core.decoder.DecodeOutcome
import com.example.prismdiff.core.decoder.ExifDiffEngine
import com.example.prismdiff.core.decoder.TilePyramidDecoder
import com.example.prismdiff.core.designsystem.PrismDiffColors
import com.example.prismdiff.core.designsystem.StudioBadgePill
import com.example.prismdiff.core.designsystem.StudioCanvasBackdrop
import com.example.prismdiff.core.model.BlendOperator
import com.example.prismdiff.core.model.ComparisonMode
import com.example.prismdiff.core.model.ComparisonRegion
import com.example.prismdiff.core.model.CullingDecision
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.ImageCullingState
import com.example.prismdiff.core.model.MultiSlotLayout
import com.example.prismdiff.core.model.RelativeSlotOffset
import com.example.prismdiff.core.model.SplitOrientation
import com.example.prismdiff.domain.viewport.CurtainGeometry
import com.example.prismdiff.domain.viewport.ViewportSynchronizer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Main composable Comparison Canvas Stage supporting all 11 comparison modes cleanly
 * via independent strategy branches powered by SSOT [ViewportSynchronizer] and [CurtainGeometry].
 */
@Composable
fun ComparisonCanvasStage(
    state: PrismDiffUiState,
    decoder: TilePyramidDecoder,
    derivedBitmap: Bitmap?,
    onPanDelta: (slotIndex: Int, dx: Float, dy: Float, w: Float, h: Float) -> Unit,
    onZoomFactor: (slotIndex: Int, zoomRatio: Float) -> Unit,
    onDoubleTapZoom: (w: Float, h: Float) -> Unit,
    onTapNormalized: (u: Float, v: Float) -> Unit,
    onCurtainDragDelta: (deltaRatio: Float) -> Unit,
    onCurtainReset: () -> Unit,
    onSplitRatioUpdate: (Float) -> Unit,
    onHoldPeekA: (Boolean) -> Unit,
    onLoupeMove: (normX: Float, normY: Float) -> Unit,
    onCycleSlotAsset: (slotIndex: Int) -> Unit,
    onSetDecision: (assetId: String, decision: CullingDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeAssets = state.activeSlotAssets
    val assetA = state.slotA
    val assetB = state.slotB
    val showHud = !state.lightsOutImmersive && state.preferences.hudTemplate.isNotBlank()

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        StudioCanvasBackdrop(
            mode = state.preferences.canvasBackdrop,
            customArgb = state.preferences.customBackdropArgb,
        )

        val density = LocalDensity.current
        val stageW = with(density) { maxWidth.toPx() }.coerceAtLeast(64f)
        val stageH = with(density) { maxHeight.toPx() }.coerceAtLeast(64f)
        val isLandscape = maxWidth > maxHeight
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        val transformA = remember(state.masterViewport, state.slotOffsets[0], state.syncFlags, assetA, stageW, stageH) {
            ViewportSynchronizer.computeEffectiveTransform(
                master = state.masterViewport,
                slotOffset = state.slotOffsets[0] ?: RelativeSlotOffset(),
                syncFlags = state.syncFlags,
                slotAsset = assetA,
                referenceAsset = assetA,
                containerWidthPx = stageW,
                containerHeightPx = stageH,
            )
        }
        val transformB = remember(state.masterViewport, state.slotOffsets[1], state.syncFlags, assetB, assetA, stageW, stageH) {
            ViewportSynchronizer.computeEffectiveTransform(
                master = state.masterViewport,
                slotOffset = state.slotOffsets[1] ?: RelativeSlotOffset(),
                syncFlags = state.syncFlags,
                slotAsset = assetB,
                referenceAsset = assetA,
                containerWidthPx = stageW,
                containerHeightPx = stageH,
            )
        }

        val hudA = if (showHud && assetA != null) {
            val pinMark = if (state.pinnedReferenceId == assetA.id) "PIN • " else ""
            "A: $pinMark" + ExifDiffEngine.formatHudTemplate(state.preferences.hudTemplate, assetA)
        } else null

        val hudB = if (showHud && assetB != null) {
            "B: " + ExifDiffEngine.formatHudTemplate(state.preferences.hudTemplate, assetB)
        } else null

        when (state.comparisonMode) {
            ComparisonMode.SIDE_BY_SIDE, ComparisonMode.VERTICAL_SPLIT -> {
                val useVertical2Up = when {
                    state.comparisonMode == ComparisonMode.VERTICAL_SPLIT -> true
                    state.splitOrientation == SplitOrientation.VERTICAL_TOP_BOTTOM -> true
                    state.splitOrientation == SplitOrientation.HORIZONTAL_LEFT_RIGHT -> false
                    else -> !isLandscape
                }

                val multiLayout = when {
                    activeAssets.size < 3 || state.multiSlotLayout == MultiSlotLayout.FOCUS_PAIR -> null
                    state.multiSlotLayout == MultiSlotLayout.AUTO -> when {
                        state.comparisonMode == ComparisonMode.VERTICAL_SPLIT -> MultiSlotLayout.ROWS
                        activeAssets.size == 3 -> if (isLandscape) MultiSlotLayout.COLUMNS else MultiSlotLayout.ROWS
                        else -> MultiSlotLayout.GRID
                    }
                    else -> state.multiSlotLayout
                }

                when (multiLayout) {
                    MultiSlotLayout.COLUMNS -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            activeAssets.forEachIndexed { idx, item ->
                                val paneW = stageW / activeAssets.size
                                val t = ViewportSynchronizer.computeEffectiveTransform(
                                    state.masterViewport,
                                    state.slotOffsets[idx] ?: RelativeSlotOffset(),
                                    state.syncFlags,
                                    item,
                                    assetA,
                                    paneW,
                                    stageH,
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                                    SubsampledTilePane(
                                        asset = item,
                                        decoder = decoder,
                                        transform = t,
                                        diagnosticOverlay = state.diagnosticOverlay,
                                        normalization = if (idx == 0) state.normalizationA else state.normalizationB,
                                        channelIsolation = state.channelIsolation,
                                        elaQuality = state.elaQuality,
                                        elaScale = state.elaScale,
                                        bitPlaneIndex = state.bitPlaneIndex,
                                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                                        showRuleOfThirds = state.showRuleOfThirds,
                                        alignmentResult = if (idx <= 1) state.alignmentResult else null,
                                        showAlignmentOverlay = state.showAlignmentOverlay && idx <= 1,
                                        annotations = if (idx == 0) state.annotations else emptyList(),
                                        hudLabel = if (showHud) "${('A' + idx)}: ${item.displayName}" else null,
                                        hudApplyStatusBarPadding = true,
                                        onPanDelta = { dx, dy, w, h -> onPanDelta(idx, dx, dy, w, h) },
                                        onZoomFactor = { z -> onZoomFactor(idx, z) },
                                        onTapNormalized = onTapNormalized,
                                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                                    )
                                }
                                if (idx < activeAssets.lastIndex) {
                                    Box(Modifier.width(1.dp).fillMaxHeight().background(PrismDiffColors.StudioHairline))
                                }
                            }
                        }
                    }

                    MultiSlotLayout.ROWS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            activeAssets.forEachIndexed { idx, item ->
                                val paneH = stageH / activeAssets.size
                                val t = ViewportSynchronizer.computeEffectiveTransform(
                                    state.masterViewport,
                                    state.slotOffsets[idx] ?: RelativeSlotOffset(),
                                    state.syncFlags,
                                    item,
                                    assetA,
                                    stageW,
                                    paneH,
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                                    SubsampledTilePane(
                                        asset = item,
                                        decoder = decoder,
                                        transform = t,
                                        diagnosticOverlay = state.diagnosticOverlay,
                                        normalization = if (idx == 0) state.normalizationA else state.normalizationB,
                                        channelIsolation = state.channelIsolation,
                                        elaQuality = state.elaQuality,
                                        elaScale = state.elaScale,
                                        bitPlaneIndex = state.bitPlaneIndex,
                                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                                        showRuleOfThirds = state.showRuleOfThirds,
                                        alignmentResult = if (idx <= 1) state.alignmentResult else null,
                                        showAlignmentOverlay = state.showAlignmentOverlay && idx <= 1,
                                        annotations = if (idx == 0) state.annotations else emptyList(),
                                        hudLabel = if (showHud) "${('A' + idx)}: ${item.displayName}" else null,
                                        hudApplyStatusBarPadding = idx == 0,
                                        onPanDelta = { dx, dy, w, h -> onPanDelta(idx, dx, dy, w, h) },
                                        onZoomFactor = { z -> onZoomFactor(idx, z) },
                                        onTapNormalized = onTapNormalized,
                                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                                    )
                                }
                                if (idx < activeAssets.lastIndex) {
                                    Box(Modifier.height(1.dp).fillMaxWidth().background(PrismDiffColors.StudioHairline))
                                }
                            }
                        }
                    }

                    MultiSlotLayout.GRID -> {
                        val rows = activeAssets.chunked(2)
                        Column(modifier = Modifier.fillMaxSize()) {
                            rows.forEachIndexed { rowIdx, rowItems ->
                                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    rowItems.forEachIndexed { colIdx, item ->
                                        val idx = rowIdx * 2 + colIdx
                                        val t = ViewportSynchronizer.computeEffectiveTransform(
                                            state.masterViewport,
                                            state.slotOffsets[idx] ?: RelativeSlotOffset(),
                                            state.syncFlags,
                                            item,
                                            assetA,
                                            stageW / rowItems.size,
                                            stageH / rows.size,
                                        )
                                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                                            SubsampledTilePane(
                                                asset = item,
                                                decoder = decoder,
                                                transform = t,
                                                diagnosticOverlay = state.diagnosticOverlay,
                                                normalization = if (idx == 0) state.normalizationA else state.normalizationB,
                                                channelIsolation = state.channelIsolation,
                                                elaQuality = state.elaQuality,
                                                elaScale = state.elaScale,
                                                bitPlaneIndex = state.bitPlaneIndex,
                                                showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                                                showRuleOfThirds = state.showRuleOfThirds,
                                                alignmentResult = if (idx <= 1) state.alignmentResult else null,
                                                showAlignmentOverlay = state.showAlignmentOverlay && idx <= 1,
                                                annotations = if (idx == 0) state.annotations else emptyList(),
                                                hudLabel = if (showHud) "${('A' + idx)}: ${item.displayName}" else null,
                                                hudApplyStatusBarPadding = rowIdx == 0,
                                                onPanDelta = { dx, dy, w, h -> onPanDelta(idx, dx, dy, w, h) },
                                                onZoomFactor = { z -> onZoomFactor(idx, z) },
                                                onTapNormalized = onTapNormalized,
                                                onDoubleTapResetOrZoom = onDoubleTapZoom,
                                            )
                                        }
                                        if (colIdx < rowItems.lastIndex) {
                                            Box(Modifier.width(1.dp).fillMaxHeight().background(PrismDiffColors.StudioHairline))
                                        }
                                    }
                                }
                                if (rowIdx < rows.lastIndex) {
                                    Box(Modifier.height(1.dp).fillMaxWidth().background(PrismDiffColors.StudioHairline))
                                }
                            }
                        }
                    }

                    else -> {
                        // 2-Up Focus Split (with draggable split ratio divider)
                        val currentSplit by rememberUpdatedState(state.splitRatio)
                        val currentSplitCallback by rememberUpdatedState(onSplitRatioUpdate)

                        if (useVertical2Up) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(state.splitRatio).fillMaxWidth().clipToBounds()) {
                                    SubsampledTilePane(
                                        asset = assetA,
                                        decoder = decoder,
                                        transform = transformA,
                                        diagnosticOverlay = state.diagnosticOverlay,
                                        normalization = state.normalizationA,
                                        channelIsolation = state.channelIsolation,
                                        elaQuality = state.elaQuality,
                                        elaScale = state.elaScale,
                                        bitPlaneIndex = state.bitPlaneIndex,
                                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                                        showRuleOfThirds = state.showRuleOfThirds,
                                        alignmentResult = state.alignmentResult,
                                        showAlignmentOverlay = state.showAlignmentOverlay,
                                        annotations = state.annotations,
                                        hudLabel = hudA,
                                        hudApplyStatusBarPadding = true,
                                        onPanDelta = { dx, dy, w, h -> onPanDelta(0, dx, dy, w, h) },
                                        onZoomFactor = { z -> onZoomFactor(0, z) },
                                        onTapNormalized = onTapNormalized,
                                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .background(Color.Transparent)
                                        .pointerInput(stageH) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                currentSplitCallback(currentSplit + dragAmount.y / stageH)
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            detectTapGestures(onDoubleTap = { currentSplitCallback(0.5f) })
                                        }
                                        .testTag("split_ratio_divider"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(PrismDiffColors.StudioHairline))
                                    Box(
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(PrismDiffColors.StudioHairline.copy(alpha = 0.9f)),
                                    )
                                }
                                Box(modifier = Modifier.weight(1f - state.splitRatio).fillMaxWidth().clipToBounds()) {
                                    SubsampledTilePane(
                                        asset = assetB,
                                        decoder = decoder,
                                        transform = transformB,
                                        diagnosticOverlay = state.diagnosticOverlay,
                                        normalization = state.normalizationB,
                                        channelIsolation = state.channelIsolation,
                                        elaQuality = state.elaQuality,
                                        elaScale = state.elaScale,
                                        bitPlaneIndex = state.bitPlaneIndex,
                                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                                        showRuleOfThirds = state.showRuleOfThirds,
                                        alignmentResult = state.alignmentResult,
                                        showAlignmentOverlay = state.showAlignmentOverlay,
                                        annotations = emptyList(),
                                        hudLabel = hudB,
                                        hudApplyStatusBarPadding = false,
                                        onPanDelta = { dx, dy, w, h -> onPanDelta(1, dx, dy, w, h) },
                                        onZoomFactor = { z -> onZoomFactor(1, z) },
                                        onTapNormalized = onTapNormalized,
                                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                                    )
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(state.splitRatio).fillMaxHeight().clipToBounds()) {
                                    SubsampledTilePane(
                                        asset = assetA,
                                        decoder = decoder,
                                        transform = transformA,
                                        diagnosticOverlay = state.diagnosticOverlay,
                                        normalization = state.normalizationA,
                                        channelIsolation = state.channelIsolation,
                                        elaQuality = state.elaQuality,
                                        elaScale = state.elaScale,
                                        bitPlaneIndex = state.bitPlaneIndex,
                                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                                        showRuleOfThirds = state.showRuleOfThirds,
                                        alignmentResult = state.alignmentResult,
                                        showAlignmentOverlay = state.showAlignmentOverlay,
                                        annotations = state.annotations,
                                        hudLabel = hudA,
                                        hudApplyStatusBarPadding = true,
                                        onPanDelta = { dx, dy, w, h -> onPanDelta(0, dx, dy, w, h) },
                                        onZoomFactor = { z -> onZoomFactor(0, z) },
                                        onTapNormalized = onTapNormalized,
                                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(14.dp)
                                        .pointerInput(stageW) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                currentSplitCallback(currentSplit + dragAmount.x / stageW)
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            detectTapGestures(onDoubleTap = { currentSplitCallback(0.5f) })
                                        }
                                        .testTag("split_ratio_divider"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.fillMaxHeight().width(1.dp).background(PrismDiffColors.StudioHairline))
                                    Box(
                                        modifier = Modifier
                                            .size(width = 6.dp, height = 40.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(PrismDiffColors.StudioHairline.copy(alpha = 0.9f)),
                                    )
                                }
                                Box(modifier = Modifier.weight(1f - state.splitRatio).fillMaxHeight().clipToBounds()) {
                                    SubsampledTilePane(
                                        asset = assetB,
                                        decoder = decoder,
                                        transform = transformB,
                                        diagnosticOverlay = state.diagnosticOverlay,
                                        normalization = state.normalizationB,
                                        channelIsolation = state.channelIsolation,
                                        elaQuality = state.elaQuality,
                                        elaScale = state.elaScale,
                                        bitPlaneIndex = state.bitPlaneIndex,
                                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                                        showRuleOfThirds = state.showRuleOfThirds,
                                        alignmentResult = state.alignmentResult,
                                        showAlignmentOverlay = state.showAlignmentOverlay,
                                        annotations = emptyList(),
                                        hudLabel = hudB,
                                        hudApplyStatusBarPadding = true,
                                        onPanDelta = { dx, dy, w, h -> onPanDelta(1, dx, dy, w, h) },
                                        onZoomFactor = { z -> onZoomFactor(1, z) },
                                        onTapNormalized = onTapNormalized,
                                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ComparisonMode.CURTAIN_WIPE -> {
                val currentAngle by rememberUpdatedState(state.curtainAngleDeg)
                val currentDeltaCb by rememberUpdatedState(onCurtainDragDelta)
                val currentResetCb by rememberUpdatedState(onCurtainReset)

                val slice = remember(stageW, stageH, state.curtainRatio, state.curtainAngleDeg, isRtl) {
                    CurtainGeometry.computeSlice(stageW, stageH, state.curtainRatio, state.curtainAngleDeg, isRtl)
                }
                val currentSlice by rememberUpdatedState(slice)

                val handleW = 60.dp
                val handleH = 40.dp
                val halfHandleW = with(density) { (handleW / 2).toPx() }
                val halfHandleH = with(density) { (handleH / 2).toPx() }
                val topSafePx = halfHandleH + with(density) { if (showHud) 56.dp.toPx() else 14.dp.toPx() }
                val bottomSafePx = when (state.dockVisibility) {
                    com.example.prismdiff.core.model.StudioDockVisibility.HIDDEN ->
                        (stageH - halfHandleH - with(density) { 24.dp.toPx() }).coerceAtLeast(topSafePx)
                    com.example.prismdiff.core.model.StudioDockVisibility.COMPACT ->
                        (stageH - halfHandleH - with(density) { 126.dp.toPx() }).coerceAtLeast(topSafePx)
                    com.example.prismdiff.core.model.StudioDockVisibility.EXPANDED ->
                        (stageH * 0.28f).coerceAtLeast(topSafePx)
                }

                // Keep handle pill riding directly on the cut segment inside the unobstructed vertical corridor
                val segDy = slice.lineEnd.y - slice.lineStart.y
                val targetY = slice.handleCenter.y.coerceIn(topSafePx, bottomSafePx)
                val projectedHandle = if (kotlin.math.abs(segDy) > 1f) {
                    val t = ((targetY - slice.lineStart.y) / segDy).coerceIn(0.08f, 0.92f)
                    Offset(
                        x = slice.lineStart.x + (slice.lineEnd.x - slice.lineStart.x) * t,
                        y = slice.lineStart.y + segDy * t,
                    )
                } else {
                    Offset(slice.handleCenter.x, slice.handleCenter.y)
                }

                val clampedCenterX = projectedHandle.x.coerceIn(
                    halfHandleW + 8f,
                    (stageW - halfHandleW - 8f).coerceAtLeast(halfHandleW + 8f),
                )
                val clampedCenterY = projectedHandle.y.coerceIn(
                    topSafePx,
                    (stageH - halfHandleH - 12f).coerceAtLeast(topSafePx),
                )
                val currentHandleCenter by rememberUpdatedState(Offset(clampedCenterX, clampedCenterY))
                val grabRadiusPx = with(density) { 38.dp.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(stageW, stageH, isRtl) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val activeSlice = currentSlice
                                val distToLine = CurtainGeometry.distanceToSegment(
                                    px = down.position.x,
                                    py = down.position.y,
                                    ax = activeSlice.lineStart.x,
                                    ay = activeSlice.lineStart.y,
                                    bx = activeSlice.lineEnd.x,
                                    by = activeSlice.lineEnd.y,
                                )
                                val hc = currentHandleCenter
                                val distToHandle = hypot(down.position.x - hc.x, down.position.y - hc.y)
                                if (distToLine <= grabRadiusPx || distToHandle <= grabRadiusPx) {
                                    val pointerId = down.id
                                    val startPos = down.position
                                    var lastPos = down.position
                                    var dragging = false
                                    val dragStartSlop = (viewConfiguration.touchSlop * 0.35f).coerceAtLeast(3f)
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                        if (!change.pressed) {
                                            if (dragging) change.consume()
                                            break
                                        }
                                        val movedDist = hypot(change.position.x - startPos.x, change.position.y - startPos.y)
                                        if (!dragging && movedDist >= dragStartSlop) {
                                            dragging = true
                                        }
                                        if (dragging) {
                                            change.consume()
                                            val stepDelta = change.position - lastPos
                                            lastPos = change.position
                                            if (stepDelta != Offset.Zero) {
                                                val deltaRatio = CurtainGeometry.dragDeltaToRatio(
                                                    dx = stepDelta.x,
                                                    dy = stepDelta.y,
                                                    width = stageW,
                                                    height = stageH,
                                                    angleDeg = currentAngle,
                                                    isRtl = isRtl,
                                                )
                                                currentDeltaCb(deltaRatio)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    // Base layer: Slot B (right / bottom side)
                    SubsampledTilePane(
                        asset = assetB,
                        decoder = decoder,
                        transform = transformB,
                        diagnosticOverlay = state.diagnosticOverlay,
                        normalization = state.normalizationB,
                        channelIsolation = state.channelIsolation,
                        elaQuality = state.elaQuality,
                        elaScale = state.elaScale,
                        bitPlaneIndex = state.bitPlaneIndex,
                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                        showRuleOfThirds = state.showRuleOfThirds,
                        alignmentResult = state.alignmentResult,
                        showAlignmentOverlay = false,
                        annotations = emptyList(),
                        hudLabel = null,
                        onPanDelta = { dx, dy, w, h -> onPanDelta(1, dx, dy, w, h) },
                        onZoomFactor = { z -> onZoomFactor(1, z) },
                        onTapNormalized = onTapNormalized,
                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                    )

                    // Top clipped layer: Slot A using exact Sutherland-Hodgman polygon clip
                    val curtainShape = remember(state.curtainRatio, state.curtainAngleDeg, isRtl) {
                        GenericShape { size, _ ->
                            val pathSlice = CurtainGeometry.computeSlice(
                                size.width,
                                size.height,
                                state.curtainRatio,
                                state.curtainAngleDeg,
                                isRtl,
                            )
                            val poly = pathSlice.polygon
                            if (poly.isNotEmpty()) {
                                moveTo(poly[0].x, poly[0].y)
                                for (i in 1 until poly.size) {
                                    lineTo(poly[i].x, poly[i].y)
                                }
                                close()
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().clip(curtainShape).clipToBounds()) {
                        SubsampledTilePane(
                            asset = assetA,
                            decoder = decoder,
                            transform = transformA,
                            diagnosticOverlay = state.diagnosticOverlay,
                            normalization = state.normalizationA,
                            channelIsolation = state.channelIsolation,
                            elaQuality = state.elaQuality,
                            elaScale = state.elaScale,
                            bitPlaneIndex = state.bitPlaneIndex,
                            showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                            showRuleOfThirds = false,
                            alignmentResult = state.alignmentResult,
                            showAlignmentOverlay = state.showAlignmentOverlay,
                            annotations = state.annotations,
                            hudLabel = null,
                            onPanDelta = { dx, dy, w, h -> onPanDelta(0, dx, dy, w, h) },
                            onZoomFactor = { z -> onZoomFactor(0, z) },
                            onTapNormalized = onTapNormalized,
                            onDoubleTapResetOrZoom = onDoubleTapZoom,
                        )
                    }

                    // High-contrast two-tone curtain divider line along the actual cut plane
                    val lineAccent = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = Offset(slice.lineStart.x, slice.lineStart.y)
                        val e = Offset(slice.lineEnd.x, slice.lineEnd.y)
                        drawLine(Color(0xCC000000), start = s, end = e, strokeWidth = 3.5.dp.toPx())
                        drawLine(lineAccent, start = s, end = e, strokeWidth = 1.5.dp.toPx())
                    }

                    // Visual wiper pill centered along the curtain divider (all drags handled cleanly by stationary parent!)
                    val handleX = with(density) { clampedCenterX.toDp() }
                    val handleY = with(density) { clampedCenterY.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = handleX - handleW / 2, y = handleY - handleH / 2)
                            .size(width = handleW, height = handleH)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color(0xEA0E1218))
                            .border(1.5.dp, PrismDiffColors.PrismCyan, RoundedCornerShape(19.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { currentResetCb() })
                            }
                            .testTag("curtain_handle"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag curtain wipe",
                                tint = PrismDiffColors.PrismCyan,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${(state.curtainRatio * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF2F5FA),
                            )
                        }
                    }

                    if (showHud) {
                        val slotSuffix = if (activeAssets.size >= 3) " (${activeAssets.size})" else ""
                        StudioBadgePill(
                            text = "A: ${assetA?.displayName ?: "—"}",
                            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp),
                        )
                        StudioBadgePill(
                            text = "B$slotSuffix: ${assetB?.displayName ?: "—"}",
                            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(10.dp),
                            accent = PrismDiffColors.PrismAmber,
                        )
                    }
                }
            }

            ComparisonMode.ONION_SKIN, ComparisonMode.LAYER_STACK -> {
                val blend = when (state.blendOperator) {
                    BlendOperator.NORMAL -> BlendMode.SrcOver
                    BlendOperator.DIFFERENCE -> BlendMode.Difference
                    BlendOperator.SUBTRACT -> BlendMode.Exclusion
                    BlendOperator.MULTIPLY -> BlendMode.Multiply
                    BlendOperator.SCREEN -> BlendMode.Screen
                    BlendOperator.OVERLAY -> BlendMode.Overlay
                    BlendOperator.LUMINOSITY -> BlendMode.Luminosity
                    BlendOperator.COLOR -> BlendMode.Color
                }
                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    SubsampledTilePane(
                        asset = assetA,
                        decoder = decoder,
                        transform = transformA,
                        diagnosticOverlay = state.diagnosticOverlay,
                        normalization = state.normalizationA,
                        channelIsolation = state.channelIsolation,
                        elaQuality = state.elaQuality,
                        elaScale = state.elaScale,
                        bitPlaneIndex = state.bitPlaneIndex,
                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                        showRuleOfThirds = state.showRuleOfThirds,
                        alignmentResult = state.alignmentResult,
                        showAlignmentOverlay = state.showAlignmentOverlay,
                        annotations = state.annotations,
                        hudLabel = null,
                        onPanDelta = { dx, dy, w, h -> onPanDelta(0, dx, dy, w, h) },
                        onZoomFactor = { z -> onZoomFactor(0, z) },
                        onTapNormalized = onTapNormalized,
                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                    )

                    var overlayBmp by remember(assetB?.id, state.blendOperator) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(assetB?.id) {
                        if (assetB != null) {
                            val res = decoder.decodeWorkingBuffer(assetB, 1024)
                            if (res is DecodeOutcome.Success) overlayBmp = res.data
                        }
                    }
                    overlayBmp?.let { bmp ->
                        val imgBmp = bmp.asImageBitmap()
                        Canvas(modifier = Modifier.fillMaxSize().clipToBounds().alpha(state.onionAlpha)) {
                            val fitScale = min(size.width / imgBmp.width, size.height / imgBmp.height)
                            val dw = imgBmp.width * fitScale * transformB.zoomFactor
                            val dh = imgBmp.height * fitScale * transformB.zoomFactor
                            val left = size.width * 0.5f - transformB.centerU * dw
                            val top = size.height * 0.5f - transformB.centerV * dh
                            withTransform({
                                translate(size.width * 0.5f, size.height * 0.5f)
                                rotate(transformB.rotationDeg, Offset.Zero)
                                scale(
                                    scaleX = if (transformB.flipHorizontal) -1f else 1f,
                                    scaleY = if (transformB.flipVertical) -1f else 1f,
                                    pivot = Offset.Zero,
                                )
                            }) {
                                drawImage(
                                    image = imgBmp,
                                    dstOffset = IntOffset((left - size.width * 0.5f).roundToInt(), (top - size.height * 0.5f).roundToInt()),
                                    dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                                    blendMode = blend,
                                )
                            }
                        }
                    }

                    if (showHud) {
                        StudioBadgePill(
                            text = "${state.blendOperator.name} • ${(state.onionAlpha * 100).roundToInt()}% B over A",
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(10.dp),
                        )
                    }
                }
            }

            ComparisonMode.BLINK_STROBE -> {
                val showA = if (state.blinkHoldPeekA) true else state.blinkShowingA
                val activeAsset = if (showA) assetA else assetB
                val activeTransform = if (showA) transformA else transformB
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onHoldPeekA(true)
                                    tryAwaitRelease()
                                    onHoldPeekA(false)
                                },
                            )
                        },
                ) {
                    SubsampledTilePane(
                        asset = activeAsset,
                        decoder = decoder,
                        transform = activeTransform,
                        diagnosticOverlay = state.diagnosticOverlay,
                        normalization = if (showA) state.normalizationA else state.normalizationB,
                        channelIsolation = state.channelIsolation,
                        elaQuality = state.elaQuality,
                        elaScale = state.elaScale,
                        bitPlaneIndex = state.bitPlaneIndex,
                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                        showRuleOfThirds = state.showRuleOfThirds,
                        alignmentResult = state.alignmentResult,
                        showAlignmentOverlay = false,
                        annotations = state.annotations,
                        hudLabel = if (showHud) {
                            val tag = if (showA) "A (Ref)" else "B (Challenger)"
                            "$tag • ${activeAsset?.displayName ?: ""} • Hold to peek A"
                        } else null,
                        hudApplyStatusBarPadding = true,
                        onPanDelta = { dx, dy, w, h -> onPanDelta(if (showA) 0 else 1, dx, dy, w, h) },
                        onZoomFactor = { z -> onZoomFactor(if (showA) 0 else 1, z) },
                        onTapNormalized = onTapNormalized,
                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                    )
                    if (state.preferences.photosensitiveGuardEnabled && state.blinkFrequencyHz > 3f) {
                        StudioBadgePill(
                            text = stringResource(R.string.photosensitive_warning),
                            accent = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 42.dp),
                        )
                    }
                }
            }

            ComparisonMode.DIFFERENCE_MAP, ComparisonMode.CUSTOM_SHADER -> {
                Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                    if (derivedBitmap != null) {
                        val imgBmp = derivedBitmap.asImageBitmap()
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(stageW, stageH) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        if (pan != Offset.Zero) onPanDelta(0, pan.x, pan.y, stageW, stageH)
                                        if (zoom != 1f) onZoomFactor(0, zoom)
                                    }
                                }
                                .pointerInput(stageW, stageH) {
                                    detectTapGestures(
                                        onDoubleTap = { onDoubleTapZoom(stageW, stageH) },
                                    )
                                },
                        ) {
                            val fitScale = min(size.width / imgBmp.width, size.height / imgBmp.height)
                            val dw = imgBmp.width * fitScale * transformA.zoomFactor
                            val dh = imgBmp.height * fitScale * transformA.zoomFactor
                            val left = -transformA.centerU * dw
                            val top = -transformA.centerV * dh
                            withTransform({
                                translate(size.width * 0.5f, size.height * 0.5f)
                                rotate(transformA.rotationDeg, Offset.Zero)
                            }) {
                                drawImage(
                                    image = imgBmp,
                                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                    dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                                    filterQuality = if (transformA.zoomFactor >= 3f) FilterQuality.None else FilterQuality.Medium,
                                )
                            }
                        }
                    } else {
                        Text(
                            "Computing mathematical difference buffer…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val bundle = state.metricsBundle
                    if (showHud && bundle != null) {
                        val badgeText = if (state.comparisonMode == ComparisonMode.CUSTOM_SHADER) {
                            "${state.customShader.name} • SSIM ${(bundle.ssim * 100).roundToInt()}% • ΔE ${String.format("%.1f", bundle.meanDeltaE2000)}"
                        } else {
                            "${state.differenceMode.name} • SSIM ${(bundle.ssim * 100).roundToInt()}% • PSNR ${String.format("%.1f", bundle.psnrDb)}dB • Δ ${String.format("%.1f", bundle.changedPixelPercent)}%"
                        }
                        StudioBadgePill(
                            text = badgeText,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(10.dp),
                        )
                    }
                }
            }

            ComparisonMode.MULTI_LOUPE -> {
                MultiLoupeStage(
                    assetA = assetA,
                    assetB = assetB,
                    decoder = decoder,
                    loupeNormX = state.loupeState.normX,
                    loupeNormY = state.loupeState.normY,
                    magnification = state.loupeState.magnification,
                    onLoupeMove = onLoupeMove,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
            }

            ComparisonMode.ROI_MATRIX -> {
                RoiPatchMatrixStage(
                    assets = activeAssets.ifEmpty { listOfNotNull(assetA, assetB) },
                    regions = state.regions,
                    decoder = decoder,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
            }

            ComparisonMode.CONTACT_SHEET -> {
                ContactSheetStage(
                    assets = state.filteredAssets.take(24),
                    decisions = state.decisions,
                    decoder = decoder,
                    onSetDecision = onSetDecision,
                    onPickAsset = { idx -> onCycleSlotAsset(idx) },
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun MultiLoupeStage(
    assetA: ImageAsset?,
    assetB: ImageAsset?,
    decoder: TilePyramidDecoder,
    loupeNormX: Float,
    loupeNormY: Float,
    magnification: Float,
    onLoupeMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bmpA by remember(assetA?.id) { mutableStateOf<Bitmap?>(null) }
    var bmpB by remember(assetB?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(assetA?.id) {
        if (assetA != null) {
            (decoder.decodeWorkingBuffer(assetA, 1024) as? DecodeOutcome.Success)?.let { bmpA = it.data }
        }
    }
    LaunchedEffect(assetB?.id) {
        if (assetB != null) {
            (decoder.decodeWorkingBuffer(assetB, 1024) as? DecodeOutcome.Success)?.let { bmpB = it.data }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onLoupeMove(
                            (change.position.x / size.width).coerceIn(0.05f, 0.95f),
                            (change.position.y / size.height).coerceIn(0.05f, 0.95f),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        onLoupeMove(
                            (pos.x / size.width).coerceIn(0.05f, 0.95f),
                            (pos.y / size.height).coerceIn(0.05f, 0.95f),
                        )
                    }
                },
        ) {
            bmpA?.let { b ->
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = "Overview reference for multi-loupe crosshair",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * loupeNormX
                val cy = size.height * loupeNormY
                drawCircle(PrismDiffColors.PrismCyan, radius = 26.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                drawLine(PrismDiffColors.PrismCyan, Offset(cx - 34.dp.toPx(), cy), Offset(cx + 34.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())
                drawLine(PrismDiffColors.PrismCyan, Offset(cx, cy - 34.dp.toPx()), Offset(cx, cy + 34.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            }
            StudioBadgePill(
                text = "Drag crosshair • ${magnification.roundToInt()}× Loupe (${(loupeNormX * 100).roundToInt()}%, ${(loupeNormY * 100).roundToInt()}%)",
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            )
        }

        Row(
            modifier = Modifier.height(170.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoupeCalloutCard("Slot A (${magnification.roundToInt()}×)", bmpA, loupeNormX, loupeNormY, magnification, PrismDiffColors.PrismCyan, Modifier.weight(1f))
            LoupeCalloutCard("Slot B (${magnification.roundToInt()}×)", bmpB, loupeNormX, loupeNormY, magnification, PrismDiffColors.PrismAmber, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoupeCalloutCard(
    label: String,
    bitmap: Bitmap?,
    normX: Float,
    normY: Float,
    mag: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.5.dp, accent, RoundedCornerShape(12.dp))
            .clipToBounds(),
    ) {
        if (bitmap != null) {
            val img = bitmap.asImageBitmap()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val srcSpanW = (img.width / mag).roundToInt().coerceIn(8, img.width)
                val srcSpanH = (img.height / mag).roundToInt().coerceIn(8, img.height)
                val sx = (normX * img.width - srcSpanW * 0.5f).roundToInt().coerceIn(0, img.width - srcSpanW)
                val sy = (normY * img.height - srcSpanH * 0.5f).roundToInt().coerceIn(0, img.height - srcSpanH)
                drawImage(
                    image = img,
                    srcOffset = IntOffset(sx, sy),
                    srcSize = IntSize(srcSpanW, srcSpanH),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }
        }
        StudioBadgePill(label, Modifier.align(Alignment.TopStart).padding(6.dp), accent = accent)
    }
}

@Composable
private fun RoiPatchMatrixStage(
    assets: List<ImageAsset>,
    regions: List<ComparisonRegion>,
    decoder: TilePyramidDecoder,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "ROI Patch Matrix (${regions.size} regions × ${assets.size} frames)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        regions.forEach { region ->
            Text(region.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth().height(128.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                assets.take(4).forEachIndexed { idx, asset ->
                    var bmp by remember(asset.id) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(asset.id) {
                        (decoder.decodeWorkingBuffer(asset, 512) as? DecodeOutcome.Success)?.let { bmp = it.data }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .border(1.dp, PrismDiffColors.StudioHairline, RoundedCornerShape(10.dp))
                            .clipToBounds(),
                    ) {
                        bmp?.let { b ->
                            val img = b.asImageBitmap()
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sx = (region.normLeft * img.width).roundToInt().coerceIn(0, img.width - 8)
                                val sy = (region.normTop * img.height).roundToInt().coerceIn(0, img.height - 8)
                                val sw = ((region.normRight - region.normLeft) * img.width).roundToInt().coerceIn(8, img.width - sx)
                                val sh = ((region.normBottom - region.normTop) * img.height).roundToInt().coerceIn(8, img.height - sy)
                                drawImage(
                                    image = img,
                                    srcOffset = IntOffset(sx, sy),
                                    srcSize = IntSize(sw, sh),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                    filterQuality = FilterQuality.None,
                                )
                            }
                        }
                        StudioBadgePill("${('A' + idx)}: ${asset.displayName.take(12)}", Modifier.align(Alignment.BottomStart).padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSheetStage(
    assets: List<ImageAsset>,
    decisions: Map<String, ImageCullingState>,
    decoder: TilePyramidDecoder,
    onSetDecision: (String, CullingDecision) -> Unit,
    onPickAsset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        modifier = modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(assets, key = { _, item -> item.id }) { idx, asset ->
            var thumb by remember(asset.id) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(asset.id) {
                (decoder.decodeThumbnail(asset, 280) as? DecodeOutcome.Success)?.let { thumb = it.data }
            }
            val dec = decisions[asset.id]?.decision ?: CullingDecision.UNDECIDED
            val borderCol = when (dec) {
                CullingDecision.FAVORITE -> PrismDiffColors.PrismAmber
                CullingDecision.KEEP -> PrismDiffColors.PrismGreen
                CullingDecision.REJECT -> PrismDiffColors.PrismRed
                else -> PrismDiffColors.StudioHairline
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, borderCol),
                modifier = Modifier.clickable { onPickAsset(idx) },
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(110.dp).background(Color.Black)) {
                        thumb?.let { b ->
                            Image(
                                bitmap = b.asImageBitmap(),
                                contentDescription = asset.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = asset.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.cd_keep_photo),
                                tint = if (dec == CullingDecision.KEEP) PrismDiffColors.PrismGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).clickable { onSetDecision(asset.id, CullingDecision.KEEP) },
                            )
                            Icon(
                                Icons.Default.Star,
                                contentDescription = stringResource(R.string.cd_favorite_photo),
                                tint = if (dec == CullingDecision.FAVORITE) PrismDiffColors.PrismAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).clickable { onSetDecision(asset.id, CullingDecision.FAVORITE) },
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_reject_photo),
                                tint = if (dec == CullingDecision.REJECT) PrismDiffColors.PrismRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).clickable { onSetDecision(asset.id, CullingDecision.REJECT) },
                            )
                        }
                    }
                }
            }
        }
    }
}
