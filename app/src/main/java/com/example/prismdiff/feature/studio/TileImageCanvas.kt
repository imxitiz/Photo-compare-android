package com.example.prismdiff.feature.studio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.prismdiff.core.decoder.DecodeOutcome
import com.example.prismdiff.core.decoder.DecodedTile
import com.example.prismdiff.core.decoder.TilePyramidDecoder
import com.example.prismdiff.core.designsystem.PrismDiffColors
import com.example.prismdiff.core.designsystem.StudioBadgePill
import com.example.prismdiff.core.model.AlignmentResult
import com.example.prismdiff.core.model.AnnotationItem
import com.example.prismdiff.core.model.AnnotationType
import com.example.prismdiff.core.model.ColorChannelIsolation
import com.example.prismdiff.core.model.DiagnosticOverlay
import com.example.prismdiff.core.model.EffectiveSlotTransform
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.NormalizationAdjustments
import com.example.prismdiff.core.shader.TileComparisonRenderer
import com.example.prismdiff.domain.viewport.ViewportSynchronizer
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hardware-accelerated subsampled tile viewport renderer.
 *
 * - Strictly clipped to pane bounds so zoomed/rotated slots never paint over adjacent panes.
 * - Decodes only visible viewport pyramid slices via [TilePyramidDecoder] and renders [DecodedTile]
 *   at its exact normalized subregion coordinates.
 * - Switches automatically to [FilterQuality.None] nearest-neighbor sampling at >=400% zoom
 *   with crisp pixel-grid hairlines.
 */
@Composable
fun SubsampledTilePane(
    asset: ImageAsset?,
    decoder: TilePyramidDecoder,
    transform: EffectiveSlotTransform,
    diagnosticOverlay: DiagnosticOverlay,
    normalization: NormalizationAdjustments,
    channelIsolation: ColorChannelIsolation,
    elaQuality: Int,
    elaScale: Float,
    bitPlaneIndex: Int,
    showPixelGridAbove400: Boolean,
    showRuleOfThirds: Boolean,
    alignmentResult: AlignmentResult?,
    showAlignmentOverlay: Boolean,
    annotations: List<AnnotationItem>,
    hudLabel: String?,
    onPanDelta: (dxPx: Float, dyPx: Float, containerW: Float, containerH: Float) -> Unit,
    onZoomFactor: (zoomRatio: Float) -> Unit,
    onTapNormalized: (u: Float, v: Float) -> Unit,
    onDoubleTapResetOrZoom: (containerW: Float, containerH: Float) -> Unit,
    modifier: Modifier = Modifier,
    hudApplyStatusBarPadding: Boolean = false,
) {
    if (asset == null) {
        Box(modifier = modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
            Text(
                text = "Tap to choose image",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val containerW = with(density) { maxWidth.toPx() }.coerceAtLeast(64f)
        val containerH = with(density) { maxHeight.toPx() }.coerceAtLeast(64f)

        var renderedTile by remember(asset.id) { mutableStateOf<DecodedTile?>(null) }
        var errorText by remember(asset.id) { mutableStateOf<String?>(null) }

        val zoomBucket = (transform.zoomFactor * 2f).roundToInt()
        val uBucket = (transform.centerU * 12f).roundToInt()
        val vBucket = (transform.centerV * 12f).roundToInt()

        LaunchedEffect(
            asset.id,
            zoomBucket,
            uBucket,
            vBucket,
            diagnosticOverlay,
            normalization,
            channelIsolation,
            elaQuality,
            elaScale,
            bitPlaneIndex,
        ) {
            val outcome = decoder.decodeViewportTile(
                asset = asset,
                transform = transform,
                targetWidthPx = containerW.roundToInt(),
                targetHeightPx = containerH.roundToInt(),
            )
            when (outcome) {
                is DecodeOutcome.Success -> {
                    val tile = outcome.data
                    val processed = TileComparisonRenderer.renderDiagnosticBitmap(
                        source = tile.bitmap,
                        overlay = diagnosticOverlay,
                        normalization = normalization,
                        channelIsolation = channelIsolation,
                        elaQuality = elaQuality,
                        elaScale = elaScale,
                        bitPlaneIndex = bitPlaneIndex,
                    )
                    renderedTile = tile.copy(bitmap = processed)
                    errorText = null
                }
                is DecodeOutcome.Failure -> {
                    errorText = outcome.error.message
                }
            }
        }

        val imgW = asset.width.takeIf { it > 0 } ?: (renderedTile?.bitmap?.width ?: 100)
        val imgH = asset.height.takeIf { it > 0 } ?: (renderedTile?.bitmap?.height ?: 100)
        val screenPxPerImgPx = ViewportSynchronizer.screenPixelsPerImagePixel(
            effectiveZoom = transform.zoomFactor,
            imageWidth = imgW,
            imageHeight = imgH,
            containerWidthPx = containerW,
            containerHeightPx = containerH,
        )
        val isNearestNeighbor = screenPxPerImgPx >= ViewportSynchronizer.PIXEL_GRID_THRESHOLD_SCALE

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(asset.id, containerW, containerH) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (pan != Offset.Zero) onPanDelta(pan.x, pan.y, containerW, containerH)
                        if (zoom != 1f) onZoomFactor(zoom)
                    }
                }
                .pointerInput(asset.id, transform, containerW, containerH) {
                    detectTapGestures(
                        onTap = { pos ->
                            val (u, v) = ViewportSynchronizer.screenToNormalized(
                                screenX = pos.x,
                                screenY = pos.y,
                                transform = transform,
                                imageWidth = imgW,
                                imageHeight = imgH,
                                containerWidthPx = containerW,
                                containerHeightPx = containerH,
                            )
                            onTapNormalized(u, v)
                        },
                        onDoubleTap = {
                            onDoubleTapResetOrZoom(containerW, containerH)
                        },
                    )
                },
        ) {
            val tile = renderedTile ?: return@Canvas
            val imageBitmap = tile.bitmap.asImageBitmap()
            val fitScale = min(size.width / imgW, size.height / imgH)
            val drawnW = imgW * fitScale * transform.zoomFactor
            val drawnH = imgH * fitScale * transform.zoomFactor

            withTransform({
                translate(size.width * 0.5f, size.height * 0.5f)
                rotate(transform.rotationDeg, Offset.Zero)
                scale(
                    scaleX = if (transform.flipHorizontal) -1f else 1f,
                    scaleY = if (transform.flipVertical) -1f else 1f,
                    pivot = Offset.Zero,
                )
            }) {
                val left = -transform.centerU * drawnW
                val top = -transform.centerV * drawnH
                val tileLeft = left + tile.regionLeftNorm * drawnW
                val tileTop = top + tile.regionTopNorm * drawnH
                val tileW = ((tile.regionRightNorm - tile.regionLeftNorm) * drawnW).roundToInt().coerceAtLeast(1)
                val tileH = ((tile.regionBottomNorm - tile.regionTopNorm) * drawnH).roundToInt().coerceAtLeast(1)
                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(imageBitmap.width, imageBitmap.height),
                    dstOffset = IntOffset(tileLeft.roundToInt(), tileTop.roundToInt()),
                    dstSize = IntSize(tileW, tileH),
                    filterQuality = if (isNearestNeighbor) FilterQuality.None else FilterQuality.Medium,
                )

                // Crisp pixel-grid hairlines when zoomed >=400% (up to 64x)
                if (showPixelGridAbove400 && isNearestNeighbor && screenPxPerImgPx >= 8f) {
                    val step = screenPxPerImgPx
                    val visLeft = (-size.width * 0.6f - left).coerceAtLeast(0f)
                    val visRight = (size.width * 0.6f - left).coerceAtMost(drawnW)
                    val visTop = (-size.height * 0.6f - top).coerceAtLeast(0f)
                    val visBottom = (size.height * 0.6f - top).coerceAtMost(drawnH)

                    var gx = (visLeft / step).toInt() * step
                    while (gx <= visRight) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.22f),
                            start = Offset(left + gx, top + visTop),
                            end = Offset(left + gx, top + visBottom),
                            strokeWidth = 1f,
                        )
                        gx += step
                    }
                    var gy = (visTop / step).toInt() * step
                    while (gy <= visBottom) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.22f),
                            start = Offset(left + visLeft, top + gy),
                            end = Offset(left + visRight, top + gy),
                            strokeWidth = 1f,
                        )
                        gy += step
                    }
                }

                // Non-destructive vector annotations in normalized image space
                for (ann in annotations) {
                    val c = Color(ann.colorArgb)
                    val sx = left + ann.normStartX * drawnW
                    val sy = top + ann.normStartY * drawnH
                    val ex = left + ann.normEndX * drawnW
                    val ey = top + ann.normEndY * drawnH
                    when (ann.type) {
                        AnnotationType.RECTANGLE -> drawRect(
                            color = c,
                            topLeft = Offset(min(sx, ex), min(sy, ey)),
                            size = Size(kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)),
                            style = Stroke(width = 3f),
                        )
                        AnnotationType.CIRCLE -> drawCircle(
                            color = c,
                            radius = hypot(ex - sx, ey - sy),
                            center = Offset(sx, sy),
                            style = Stroke(width = 3f),
                        )
                        AnnotationType.ARROW -> drawLine(c, Offset(sx, sy), Offset(ex, ey), strokeWidth = 4f)
                        AnnotationType.FREEHAND -> {
                            for (i in 1 until ann.points.size) {
                                val p0 = ann.points[i - 1]
                                val p1 = ann.points[i]
                                drawLine(
                                    color = c,
                                    start = Offset(left + p0.x * drawnW, top + p0.y * drawnH),
                                    end = Offset(left + p1.x * drawnW, top + p1.y * drawnH),
                                    strokeWidth = 3f,
                                )
                            }
                        }
                        AnnotationType.TEXT -> drawCircle(c, radius = 8f, center = Offset(sx, sy))
                    }
                }

                // Optional alignment feature keypoints visualization
                if (showAlignmentOverlay && alignmentResult != null) {
                    for (pair in alignmentResult.featurePairs) {
                        val pt = Offset(left + pair.normAx * drawnW, top + pair.normAy * drawnH)
                        drawCircle(
                            color = if (pair.isInlier) PrismDiffColors.PrismGreen else PrismDiffColors.PrismRed,
                            radius = 6f,
                            center = pt,
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }

            if (showRuleOfThirds) {
                val thirdW = size.width / 3f
                val thirdH = size.height / 3f
                val guide = Color.White.copy(alpha = 0.25f)
                drawLine(guide, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = 1f)
                drawLine(guide, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = 1f)
                drawLine(guide, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = 1f)
                drawLine(guide, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = 1f)
            }
        }

        if (renderedTile == null && errorText == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (errorText != null) {
            StudioBadgePill(
                text = errorText!!,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
        }
        if (!hudLabel.isNullOrBlank()) {
            StudioBadgePill(
                text = hudLabel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .then(if (hudApplyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
