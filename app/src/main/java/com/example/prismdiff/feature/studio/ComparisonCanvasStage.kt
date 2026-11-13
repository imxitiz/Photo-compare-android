package com.example.prismdiff.feature.studio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.example.prismdiff.core.designsystem.PrismDiffTokens
import com.example.prismdiff.core.designsystem.StudioBadgePill
import com.example.prismdiff.core.designsystem.StudioCanvasBackdrop
import com.example.prismdiff.core.model.BlendOperator
import com.example.prismdiff.core.model.ComparisonMode
import com.example.prismdiff.core.model.CullingDecision
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.RelativeSlotOffset
import com.example.prismdiff.core.model.SplitOrientation
import com.example.prismdiff.domain.viewport.ViewportSynchronizer
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Main composable Comparison Canvas Stage supporting all 11 comparison modes cleanly
 * via independent strategy branches powered by SSOT [ViewportSynchronizer].
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
    onCurtainUpdate: (ratio: Float?, angleDeg: Float?) -> Unit,
    onHoldPeekA: (Boolean) -> Unit,
    onLoupeMove: (normX: Float, normY: Float) -> Unit,
    onCycleSlotAsset: (slotIndex: Int) -> Unit,
    onSetDecision: (assetId: String, decision: CullingDecision) -> Unit,
    onToggleLightsOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeAssets = state.activeSlotAssets
    val assetA = state.slotA
    val assetB = state.slotB
    val showHud = !state.lightsOutImmersive && state.preferences.hudTemplate.isNotBlank()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
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
                val useVerticalStack = when {
                    state.comparisonMode == ComparisonMode.VERTICAL_SPLIT -> true
                    state.splitOrientation == SplitOrientation.VERTICAL_TOP_BOTTOM -> true
                    state.splitOrientation == SplitOrientation.HORIZONTAL_LEFT_RIGHT -> false
                    else -> !isLandscape
                }

                if (activeAssets.size >= 3 && state.comparisonMode == ComparisonMode.SIDE_BY_SIDE) {
                    // 3-Up / 4-Up synchronized quad layout
                    val gridAssets = activeAssets.take(4)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            gridAssets.take(2).forEachIndexed { idx, item ->
                                val t = ViewportSynchronizer.computeEffectiveTransform(
                                    state.masterViewport,
                                    state.slotOffsets[idx] ?: RelativeSlotOffset(),
                                    state.syncFlags,
                                    item,
                                    assetA,
                                    stageW * 0.5f,
                                    stageH * 0.5f,
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                                        alignmentResult = state.alignmentResult,
                                        showAlignmentOverlay = state.showAlignmentOverlay,
                                        annotations = if (idx == 0) state.annotations else emptyList(),
                                        hudLabel = if (showHud) "${('A' + idx)}: ${item.displayName}" else null,
                                        onPanDelta = { dx, dy, w, h -> onPanDelta(idx, dx, dy, w, h) },
                                        onZoomFactor = { z -> onZoomFactor(idx, z) },
                                        onTapNormalized = onTapNormalized,
                                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                                    )
                                }
                                if (idx == 0) {
                                    Box(Modifier.width(1.dp).fillMaxHeight().background(PrismDiffColors.StudioHairline))
                                }
                            }
                        }
                        Box(Modifier.height(1.dp).fillMaxWidth().background(PrismDiffColors.StudioHairline))
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            gridAssets.drop(2).forEachIndexed { subIdx, item ->
                                val idx = subIdx + 2
                                val t = ViewportSynchronizer.computeEffectiveTransform(
                                    state.masterViewport,
                                    state.slotOffsets[idx] ?: RelativeSlotOffset(),
                                    state.syncFlags,
                                    item,
                                    assetA,
                                    stageW * 0.5f,
                                    stageH * 0.5f,
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    SubsampledTilePane(
                                        asset = item,
                                        decoder = decoder,
                                        transform = t,
                                        diagnosticOverlay = state.diagnosticOverlay,
                                        normalization = state.normalizationB,
                                        channelIsolation = state.channelIsolation,
                                        elaQuality = state.elaQuality,
                                        elaScale = state.elaScale,
                                        bitPlaneIndex = state.bitPlaneIndex,
                                        showPixelGridAbove400 = state.preferences.showPixelGridAbove400,
                                        showRuleOfThirds = state.showRuleOfThirds,
                                        alignmentResult = null,
                                        showAlignmentOverlay = false,
                                        annotations = emptyList(),
                                        hudLabel = if (showHud) "${('A' + idx)}: ${item.displayName}" else null,
                                        onPanDelta = { dx, dy, w, h -> onPanDelta(idx, dx, dy, w, h) },
                                        onZoomFactor = { z -> onZoomFactor(idx, z) },
                                        onTapNormalized = onTapNormalized,
                                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                                    )
                                }
                                if (subIdx == 0 && gridAssets.size > 3) {
                                    Box(Modifier.width(1.dp).fillMaxHeight().background(PrismDiffColors.StudioHairline))
                                }
                            }
                        }
                    }
                } else if (useVerticalStack) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(state.splitRatio).fillMaxWidth()) {
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
                                onPanDelta = { dx, dy, w, h -> onPanDelta(0, dx, dy, w, h) },
                                onZoomFactor = { z -> onZoomFactor(0, z) },
                                onTapNormalized = onTapNormalized,
                                onDoubleTapResetOrZoom = onDoubleTapZoom,
                            )
                        }
                        Box(Modifier.height(1.dp).fillMaxWidth().background(PrismDiffColors.StudioHairline))
                        Box(modifier = Modifier.weight(1f - state.splitRatio).fillMaxWidth()) {
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
                                onPanDelta = { dx, dy, w, h -> onPanDelta(1, dx, dy, w, h) },
                                onZoomFactor = { z -> onZoomFactor(1, z) },
                                onTapNormalized = onTapNormalized,
                                onDoubleTapResetOrZoom = onDoubleTapZoom,
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(state.splitRatio).fillMaxHeight()) {
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
                                onPanDelta = { dx, dy, w, h -> onPanDelta(0, dx, dy, w, h) },
                                onZoomFactor = { z -> onZoomFactor(0, z) },
                                onTapNormalized = onTapNormalized,
                                onDoubleTapResetOrZoom = onDoubleTapZoom,
                            )
                        }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(PrismDiffColors.StudioHairline))
                        Box(modifier = Modifier.weight(1f - state.splitRatio).fillMaxHeight()) {
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
                                onPanDelta = { dx, dy, w, h -> onPanDelta(1, dx, dy, w, h) },
                                onZoomFactor = { z -> onZoomFactor(1, z) },
                                onTapNormalized = onTapNormalized,
                                onDoubleTapResetOrZoom = onDoubleTapZoom,
                            )
                        }
                    }
                }
            }

            ComparisonMode.CURTAIN_WIPE -> {
                val effectiveSplit = if (isRtl) 1f - state.curtainRatio else state.curtainRatio
                val angleRad = Math.toRadians(state.curtainAngleDeg.toDouble())
                val nx = cos(angleRad).toFloat()
                val ny = sin(angleRad).toFloat()

                Box(modifier = Modifier.fillMaxSize()) {
                    // Base layer: Slot B
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

                    // Clipped top layer: Slot A using custom rotatable curtain shape
                    val curtainShape = remember(effectiveSplit, state.curtainAngleDeg) {
                        androidx.compose.foundation.shape.GenericShape { size, _ ->
                            val cx = size.width * effectiveSplit
                            val cy = size.height * effectiveSplit
                            val isMostlyVertical = kotlin.math.abs(sin(angleRad)) > 0.707
                            if (kotlin.math.abs(state.curtainAngleDeg - 90f) < 2f || kotlin.math.abs(state.curtainAngleDeg - 270f) < 2f) {
                                moveTo(0f, 0f)
                                lineTo(cx, 0f)
                                lineTo(cx, size.height)
                                lineTo(0f, size.height)
                                close()
                            } else if (kotlin.math.abs(state.curtainAngleDeg) < 2f || kotlin.math.abs(state.curtainAngleDeg - 180f) < 2f) {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width, cy)
                                lineTo(0f, cy)
                                close()
                            } else {
                                // Diagonal half-plane clip passing through (size.width * effectiveSplit, size.height * 0.5f)
                                val anchorX = size.width * effectiveSplit
                                val anchorY = size.height * 0.5f
                                val slope = if (kotlin.math.abs(ny) < 1e-3f) 0f else -nx / ny
                                if (isMostlyVertical) {
                                    val topX = anchorX + slope * (-anchorY)
                                    val botX = anchorX + slope * (size.height - anchorY)
                                    moveTo(0f, 0f)
                                    lineTo(topX, 0f)
                                    lineTo(botX, size.height)
                                    lineTo(0f, size.height)
                                    close()
                                } else {
                                    val leftY = anchorY - (anchorX) * (if (kotlin.math.abs(nx) < 1e-3f) 0f else ny / nx)
                                    val rightY = anchorY + (size.width - anchorX) * (if (kotlin.math.abs(nx) < 1e-3f) 0f else ny / nx)
                                    moveTo(0f, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(size.width, rightY)
                                    lineTo(0f, leftY)
                                    close()
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().clip(curtainShape)) {
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

                    // Draggable curtain line & handle
                    val handleX = with(density) { (stageW * effectiveSplit).toDp() }
                    val handleY = with(density) { (stageH * 0.5f).toDp() }
                    Box(
                        modifier = Modifier
                            .offset(x = handleX - 24.dp, y = handleY - 24.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .pointerInput(isRtl, stageW, stageH, state.curtainAngleDeg) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val isHorizCut = kotlin.math.abs(state.curtainAngleDeg) < 20f || kotlin.math.abs(state.curtainAngleDeg - 180f) < 20f
                                    val delta = if (isHorizCut) dragAmount.y / stageH else (if (isRtl) -dragAmount.x else dragAmount.x) / stageW
                                    onCurtainUpdate(state.curtainRatio + delta, null)
                                }
                            }
                            .testTag("curtain_handle"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${(state.curtainRatio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (showHud) {
                        StudioBadgePill("A: ${assetA?.displayName ?: "—"}", Modifier.align(Alignment.TopStart).padding(10.dp))
                        StudioBadgePill("B: ${assetB?.displayName ?: "—"}", Modifier.align(Alignment.TopEnd).padding(10.dp), accent = MaterialTheme.colorScheme.secondary)
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
                Box(modifier = Modifier.fillMaxSize()) {
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
                        Canvas(modifier = Modifier.fillMaxSize().alpha(state.onionAlpha)) {
                            val fitScale = kotlin.math.min(size.width / imgBmp.width, size.height / imgBmp.height)
                            val dw = imgBmp.width * fitScale * transformB.zoomFactor
                            val dh = imgBmp.height * fitScale * transformB.zoomFactor
                            val left = size.width * 0.5f - transformB.centerU * dw
                            val top = size.height * 0.5f - transformB.centerV * dh
                            drawImage(
                                image = imgBmp,
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                                blendMode = blend,
                            )
                        }
                    }

                    if (showHud) {
                        StudioBadgePill(
                            text = "${state.blendOperator.name} • ${(state.onionAlpha * 100).roundToInt()}% B over A",
                            modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
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
                            "$tag • ${activeAsset?.displayName ?: ""} • Hold anywhere to peek A"
                        } else null,
                        onPanDelta = { dx, dy, w, h -> onPanDelta(if (showA) 0 else 1, dx, dy, w, h) },
                        onZoomFactor = { z -> onZoomFactor(if (showA) 0 else 1, z) },
                        onTapNormalized = onTapNormalized,
                        onDoubleTapResetOrZoom = onDoubleTapZoom,
                    )
                    if (state.preferences.photosensitiveGuardEnabled && state.blinkFrequencyHz > 3f) {
                        StudioBadgePill(
                            text = stringResource(R.string.photosensitive_warning),
                            accent = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp),
                        )
                    }
                }
            }

            ComparisonMode.DIFFERENCE_MAP, ComparisonMode.CUSTOM_SHADER -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (derivedBitmap != null) {
                        Image(
                            bitmap = derivedBitmap.asImageBitmap(),
                            contentDescription = "Computed visual difference map",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { onToggleLightsOut() })
                                },
                        )
                    } else {
                        Text("Computing mathematical difference buffer…", style = MaterialTheme.typography.bodyMedium)
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
                            modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
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
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ComparisonMode.ROI_MATRIX -> {
                RoiPatchMatrixStage(
                    assets = activeAssets.ifEmpty { listOfNotNull(assetA, assetB) },
                    regions = state.regions,
                    decoder = decoder,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ComparisonMode.CONTACT_SHEET -> {
                ContactSheetStage(
                    assets = state.filteredAssets.take(24),
                    decisions = state.decisions,
                    decoder = decoder,
                    onSetDecision = onSetDecision,
                    onPickAsset = { idx ->
                        val chosen = state.filteredAssets.getOrNull(idx) ?: return@ContactSheetStage
                        onCycleSlotAsset(idx)
                    },
                    modifier = Modifier.fillMaxSize(),
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
        // Overview composition with magnetic crosshair
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
                drawCircle(PrismDiffColors.PrismCyan, radius = 26.dp.toPx(), center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawLine(PrismDiffColors.PrismCyan, Offset(cx - 34.dp.toPx(), cy), Offset(cx + 34.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())
                drawLine(PrismDiffColors.PrismCyan, Offset(cx, cy - 34.dp.toPx()), Offset(cx, cy + 34.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            }
            StudioBadgePill(
                text = "Drag crosshair • ${magnification.roundToInt()}× Loupe (${(loupeNormX * 100).roundToInt()}%, ${(loupeNormY * 100).roundToInt()}%)",
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            )
        }

        // Docked synchronized split magnifier callouts
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
    regions: List<com.example.prismdiff.core.model.ComparisonRegion>,
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
                            .border(1.dp, PrismDiffColors.StudioHairline, RoundedCornerShape(10.dp)),
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
    decisions: Map<String, com.example.prismdiff.core.model.ImageCullingState>,
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
