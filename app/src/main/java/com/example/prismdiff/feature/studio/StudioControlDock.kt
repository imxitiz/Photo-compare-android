package com.example.prismdiff.feature.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.prismdiff.core.designsystem.PrismDiffColors
import com.example.prismdiff.core.designsystem.PrismDiffTokens
import com.example.prismdiff.core.designsystem.StudioActionChip
import com.example.prismdiff.core.designsystem.StudioBadgePill
import com.example.prismdiff.core.designsystem.StudioTouchIconButton
import com.example.prismdiff.core.model.CanvasBackdropMode
import com.example.prismdiff.core.model.ComparisonMode
import com.example.prismdiff.core.model.CullingDecision
import com.example.prismdiff.core.model.MultiSlotLayout
import com.example.prismdiff.core.model.ScaleSyncMode
import com.example.prismdiff.core.model.StudioDockVisibility
import com.example.prismdiff.core.vision.ColorMath
import kotlin.math.abs
import kotlin.math.roundToInt

private val OpticalDarkDockScheme = darkColorScheme(
    primary = PrismDiffColors.PrismCyan,
    onPrimary = Color(0xFF002022),
    primaryContainer = PrismDiffColors.PrismCyanContainer,
    onPrimaryContainer = Color(0xFFA7F7FA),
    secondary = PrismDiffColors.PrismAmber,
    onSecondary = Color(0xFF281700),
    secondaryContainer = PrismDiffColors.PrismAmberContainer,
    onSecondaryContainer = Color(0xFFFFDDB3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFEDEFF4),
    surfaceVariant = Color(0xFF171D26),
    onSurfaceVariant = Color(0xFFB3BDCA),
    surfaceContainerLowest = Color(0xFF07090D),
    surfaceContainerLow = Color(0xE80D1117),
    surfaceContainer = Color(0xFF161C25),
    surfaceContainerHigh = Color(0xFF1F2733),
    outline = Color(0xFF3E4859),
    outlineVariant = Color(0xFF2B3442),
    error = PrismDiffColors.PrismRed,
)

/**
 * 3-State Collapsible & Hideable Comparison Studio Dock:
 * - [StudioDockVisibility.COMPACT]: Single sleek floating bar leaving >85% of the canvas unobstructed.
 * - [StudioDockVisibility.EXPANDED]: Full slot placement, 11-mode switcher, alignment, transform & culling drawer.
 * - [StudioDockVisibility.HIDDEN]: Pure 100% edge-to-edge empty canvas with only a subtle Show pill.
 */
@Composable
fun StudioControlDock(
    state: PrismDiffUiState,
    bottomNavVisible: Boolean,
    onSetDockVisibility: (StudioDockVisibility) -> Unit,
    onSelectMode: (ComparisonMode) -> Unit,
    onSetMultiSlotLayout: (MultiSlotLayout) -> Unit,
    onCycleMultiSlotLayout: () -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onMoveSlot: (Int, Int) -> Unit,
    onToggleSyncAll: () -> Unit,
    onToggleScaleMode: () -> Unit,
    onToggleAnchorCalibrate: () -> Unit,
    onAutoAlign: () -> Unit,
    onUndoAlign: () -> Unit,
    onResetViewport: () -> Unit,
    onZoomOneToOne: () -> Unit,
    onSwapSlots: () -> Unit,
    onRotate90: () -> Unit,
    onFlipHoriz: () -> Unit,
    onPinReferenceA: () -> Unit,
    onPromoteBToA: () -> Unit,
    onStepB: (Int) -> Unit,
    onSetDecisionB: (CullingDecision) -> Unit,
    onOpenInspector: () -> Unit,
    onOpenSlotPicker: (Int) -> Unit,
    onToggleSlideshow: () -> Unit,
    onUpdateOnionAlpha: (Float) -> Unit,
    onUpdateCurtainAngle: (Float) -> Unit,
    onResetCurtain: () -> Unit,
    onUpdateBlink: (hz: Float?, playing: Boolean?) -> Unit,
    onClearProbe: () -> Unit,
    onAdvanceTournament: (winnerId: String, loserId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val useDarkOpticalDock = state.preferences.canvasBackdrop != CanvasBackdropMode.STUDIO_WHITE
    val scheme = if (useDarkOpticalDock) OpticalDarkDockScheme else MaterialTheme.colorScheme

    MaterialTheme(colorScheme = scheme) {
        if (state.dockVisibility == StudioDockVisibility.HIDDEN || state.lightsOutImmersive) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(14.dp),
            ) {
                Surface(
                    onClick = { onSetDockVisibility(StudioDockVisibility.COMPACT) },
                    shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusPill),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f),
                    border = BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .defaultMinSize(minHeight = PrismDiffTokens.MinTouchTarget)
                        .testTag("dock_show_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.cd_dock_show),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Controls",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            return@MaterialTheme
        }

        val insetMod = if (bottomNavVisible) Modifier else Modifier.navigationBarsPadding()
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(insetMod)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Optional pixel probe readout pill with quick dismiss
            val probeA = state.pixelProbeA
            val probeB = state.pixelProbeB
            if (probeA != null && probeB != null) {
                val argbA = (0xFF shl 24) or (probeA.r shl 16) or (probeA.g shl 8) or probeA.b
                val argbB = (0xFF shl 24) or (probeB.r shl 16) or (probeB.g shl 8) or probeB.b
                val deltaE = ColorMath.ciede2000(argbA, argbB)
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StudioBadgePill(
                        text = "Probe (${probeA.x},${probeA.y}) ${probeA.hex} vs ${probeB.hex} • ΔE ${String.format("%.1f", deltaE)}",
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xD90B0E14),
                        modifier = Modifier.clickable(onClick = onClearProbe),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear pixel probe",
                            tint = Color(0xFFF2F5FA),
                            modifier = Modifier.padding(4.dp).size(14.dp),
                        )
                    }
                }
            }

            // Optional Elo Tournament active banner
            val tournament = state.tournamentPair
            val slotA = state.slotA
            val slotB = state.slotB
            if (tournament != null && slotA != null && slotB != null) {
                Surface(
                    shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Elo Match",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onAdvanceTournament(slotA.id, slotB.id) }) { Text("Pick A") }
                            TextButton(onClick = { onAdvanceTournament(slotB.id, slotA.id) }) { Text("Pick B") }
                        }
                    }
                }
            }

            // Contextual Quick Strip when Compact (when Expanded it lives inside the scrollable drawer)
            if (state.dockVisibility == StudioDockVisibility.COMPACT) {
                ContextualModeBar(
                    state = state,
                    onUpdateOnionAlpha = onUpdateOnionAlpha,
                    onUpdateCurtainAngle = onUpdateCurtainAngle,
                    onResetCurtain = onResetCurtain,
                    onUpdateBlink = onUpdateBlink,
                )
            }

            // EXPANDED Collapsible Studio Panel
            AnimatedVisibility(
                visible = state.dockVisibility == StudioDockVisibility.EXPANDED,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ExpandedStudioDrawer(
                    state = state,
                    onCollapse = { onSetDockVisibility(StudioDockVisibility.COMPACT) },
                    onHide = { onSetDockVisibility(StudioDockVisibility.HIDDEN) },
                    onResetViewport = onResetViewport,
                    onZoomOneToOne = onZoomOneToOne,
                    onOpenSlotPicker = onOpenSlotPicker,
                    onAddSlot = onAddSlot,
                    onRemoveSlot = onRemoveSlot,
                    onMoveSlot = onMoveSlot,
                    onSetMultiSlotLayout = onSetMultiSlotLayout,
                    onSelectMode = onSelectMode,
                    onToggleSyncAll = onToggleSyncAll,
                    onToggleScaleMode = onToggleScaleMode,
                    onToggleAnchorCalibrate = onToggleAnchorCalibrate,
                    onAutoAlign = onAutoAlign,
                    onUndoAlign = onUndoAlign,
                    onRotate90 = onRotate90,
                    onFlipHoriz = onFlipHoriz,
                    onPinReferenceA = onPinReferenceA,
                    onPromoteBToA = onPromoteBToA,
                    onStepB = onStepB,
                    onSetDecisionB = onSetDecisionB,
                    onSwapSlots = onSwapSlots,
                    onToggleSlideshow = onToggleSlideshow,
                    onUpdateOnionAlpha = onUpdateOnionAlpha,
                    onUpdateCurtainAngle = onUpdateCurtainAngle,
                    onResetCurtain = onResetCurtain,
                    onUpdateBlink = onUpdateBlink,
                )
            }

            // COMPACT Always-Accessible Primary Pill Bar (guaranteed fit across 320dp+ phones)
            Surface(
                shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusPill),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp,
                border = BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val dockWidth = maxWidth
                    val showSwapInCompact = dockWidth >= 364.dp
                    val showLayoutInCompact = dockWidth >= 400.dp
                    val isExpanded = state.dockVisibility == StudioDockVisibility.EXPANDED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StudioTouchIconButton(
                            icon = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(
                                if (isExpanded) R.string.cd_dock_collapse else R.string.cd_dock_expand,
                            ),
                            onClick = {
                                onSetDockVisibility(
                                    if (isExpanded) StudioDockVisibility.COMPACT else StudioDockVisibility.EXPANDED,
                                )
                            },
                            active = isExpanded,
                            testTag = if (isExpanded) "dock_collapse_button" else "dock_expand_button",
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .defaultMinSize(minHeight = PrismDiffTokens.MinTouchTarget)
                                .clip(RoundedCornerShape(PrismDiffTokens.StudioRadiusPill))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    onSetDockVisibility(
                                        if (isExpanded) StudioDockVisibility.COMPACT else StudioDockVisibility.EXPANDED,
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                .testTag("compact_mode_pill"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = modeShortLabel(state.comparisonMode),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (showSwapInCompact) {
                            StudioTouchIconButton(
                                icon = Icons.Default.SwapHoriz,
                                contentDescription = stringResource(R.string.cd_swap_slots),
                                onClick = onSwapSlots,
                                testTag = "swap_slots_button",
                            )
                        }

                        if (state.activeSlotIds.size >= 3 && showLayoutInCompact) {
                            StudioActionChip(
                                label = "${state.activeSlotIds.size}•${layoutShortLabel(state.multiSlotLayout)}",
                                icon = Icons.Default.GridView,
                                selected = state.multiSlotLayout != MultiSlotLayout.AUTO,
                                onClick = onCycleMultiSlotLayout,
                                testTag = "compact_layout_button",
                            )
                        }

                        StudioTouchIconButton(
                            icon = if (state.syncFlags.anySynced) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = stringResource(R.string.cd_toggle_sync),
                            onClick = onToggleSyncAll,
                            active = state.syncFlags.anySynced,
                            testTag = "toggle_sync_button",
                        )

                        StudioTouchIconButton(
                            icon = Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.cd_reset_viewport),
                            onClick = onResetViewport,
                            testTag = "reset_viewport_button",
                        )

                        StudioTouchIconButton(
                            icon = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.cd_inspector),
                            onClick = onOpenInspector,
                            testTag = "open_inspector_button",
                        )

                        StudioTouchIconButton(
                            icon = Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.cd_dock_hide),
                            onClick = { onSetDockVisibility(StudioDockVisibility.HIDDEN) },
                            testTag = "dock_hide_button",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextualModeBar(
    state: PrismDiffUiState,
    onUpdateOnionAlpha: (Float) -> Unit,
    onUpdateCurtainAngle: (Float) -> Unit,
    onResetCurtain: () -> Unit,
    onUpdateBlink: (hz: Float?, playing: Boolean?) -> Unit,
) {
    when (state.comparisonMode) {
        ComparisonMode.CURTAIN_WIPE -> {
            Surface(
                shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Wipe ${(state.curtainRatio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    listOf(0f to "Vert 0°", 90f to "Horiz 90°", 45f to "Diag 45°", 135f to "Diag 135°").forEach { (deg, label) ->
                        val active = abs(state.curtainAngleDeg - deg) < 2f
                        StudioActionChip(
                            label = label,
                            icon = null,
                            selected = active,
                            onClick = { onUpdateCurtainAngle(deg) },
                            testTag = "curtain_angle_${deg.toInt()}",
                        )
                    }
                    StudioActionChip(
                        label = "50%",
                        icon = Icons.Default.RestartAlt,
                        selected = false,
                        onClick = onResetCurtain,
                        testTag = "curtain_reset_button",
                    )
                }
            }
        }

        ComparisonMode.ONION_SKIN, ComparisonMode.LAYER_STACK -> {
            Surface(
                shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Blend ${(state.onionAlpha * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(74.dp),
                    )
                    Slider(
                        value = state.onionAlpha,
                        onValueChange = onUpdateOnionAlpha,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        ComparisonMode.BLINK_STROBE -> {
            Surface(
                shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StudioTouchIconButton(
                        icon = if (state.blinkPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or pause blink strobe",
                        onClick = { onUpdateBlink(null, !state.blinkPlaying) },
                        active = state.blinkPlaying,
                        testTag = "blink_play_pause_button",
                    )
                    listOf(1f, 2f, 3f).forEach { hz ->
                        StudioActionChip(
                            label = "${hz.toInt()} Hz",
                            icon = null,
                            selected = abs(state.blinkFrequencyHz - hz) < 0.25f,
                            onClick = { onUpdateBlink(hz, null) },
                            testTag = "blink_hz_${hz.toInt()}",
                        )
                    }
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun ExpandedStudioDrawer(
    state: PrismDiffUiState,
    onCollapse: () -> Unit,
    onHide: () -> Unit,
    onResetViewport: () -> Unit,
    onZoomOneToOne: () -> Unit,
    onOpenSlotPicker: (Int) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onMoveSlot: (Int, Int) -> Unit,
    onSetMultiSlotLayout: (MultiSlotLayout) -> Unit,
    onSelectMode: (ComparisonMode) -> Unit,
    onToggleSyncAll: () -> Unit,
    onToggleScaleMode: () -> Unit,
    onToggleAnchorCalibrate: () -> Unit,
    onAutoAlign: () -> Unit,
    onUndoAlign: () -> Unit,
    onRotate90: () -> Unit,
    onFlipHoriz: () -> Unit,
    onPinReferenceA: () -> Unit,
    onPromoteBToA: () -> Unit,
    onStepB: (Int) -> Unit,
    onSetDecisionB: (CullingDecision) -> Unit,
    onSwapSlots: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onUpdateOnionAlpha: (Float) -> Unit,
    onUpdateCurtainAngle: (Float) -> Unit,
    onResetCurtain: () -> Unit,
    onUpdateBlink: (hz: Float?, playing: Boolean?) -> Unit,
) {
    val activeAssets = state.activeSlotAssets
    val slotA = state.slotA
    val slotB = state.slotB

    Surface(
        shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
        border = BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 164.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ContextualModeBar(
                state = state,
                onUpdateOnionAlpha = onUpdateOnionAlpha,
                onUpdateCurtainAngle = onUpdateCurtainAngle,
                onResetCurtain = onResetCurtain,
                onUpdateBlink = onUpdateBlink,
            )

            // Row 1: Dynamic Slots (A, B, C...) with Reorder Placement, Remove, and + Slot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                activeAssets.forEachIndexed { idx, asset ->
                    SlotPlacementCard(
                        slotLabel = ('A' + idx).toString(),
                        name = asset.displayName,
                        pinned = idx == 0 && state.pinnedReferenceId == asset.id,
                        canMoveLeft = idx > 0,
                        canMoveRight = idx < activeAssets.lastIndex,
                        canRemove = activeAssets.size > 2,
                        onPick = { onOpenSlotPicker(idx) },
                        onMoveLeft = { onMoveSlot(idx, idx - 1) },
                        onMoveRight = { onMoveSlot(idx, idx + 1) },
                        onRemove = { onRemoveSlot(idx) },
                        testTag = "slot_${('a' + idx)}_pill",
                    )
                }
                if (activeAssets.size < 6) {
                    StudioActionChip(
                        label = stringResource(R.string.action_add_slot),
                        icon = Icons.Default.Add,
                        selected = false,
                        onClick = onAddSlot,
                        testTag = "add_slot_button",
                    )
                }
            }

            // Placement selector whenever 3+ images are loaded (or in Split modes)
            if (activeAssets.size >= 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Placement:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val layouts = listOf(
                        Triple(MultiSlotLayout.AUTO, stringResource(R.string.layout_auto), Icons.Default.GridView),
                        Triple(MultiSlotLayout.COLUMNS, stringResource(R.string.layout_columns), Icons.Default.ViewColumn),
                        Triple(MultiSlotLayout.ROWS, stringResource(R.string.layout_rows), Icons.Default.ViewStream),
                        Triple(MultiSlotLayout.GRID, stringResource(R.string.layout_grid), Icons.Default.GridView),
                        Triple(MultiSlotLayout.FOCUS_PAIR, stringResource(R.string.layout_focus_pair), Icons.Default.Compare),
                    )
                    layouts.forEach { (layout, label, icon) ->
                        StudioActionChip(
                            label = label,
                            icon = icon,
                            selected = state.multiSlotLayout == layout,
                            onClick = { onSetMultiSlotLayout(layout) },
                            testTag = "layout_chip_${layout.name.lowercase()}",
                        )
                    }
                }
            }

            // Row 2: All 11 Comparison Modes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val modes = listOf(
                    Triple(ComparisonMode.SIDE_BY_SIDE, stringResource(R.string.mode_side_by_side), Icons.Default.Compare),
                    Triple(ComparisonMode.CURTAIN_WIPE, stringResource(R.string.mode_curtain_wipe), Icons.AutoMirrored.Filled.CompareArrows),
                    Triple(ComparisonMode.VERTICAL_SPLIT, stringResource(R.string.mode_vertical_split), Icons.Default.ViewStream),
                    Triple(ComparisonMode.ONION_SKIN, stringResource(R.string.mode_onion_skin), Icons.Default.Layers),
                    Triple(ComparisonMode.BLINK_STROBE, stringResource(R.string.mode_blink_strobe), Icons.Default.Visibility),
                    Triple(ComparisonMode.DIFFERENCE_MAP, stringResource(R.string.mode_difference_map), Icons.Default.Analytics),
                    Triple(ComparisonMode.MULTI_LOUPE, stringResource(R.string.mode_multi_loupe), Icons.Default.Search),
                    Triple(ComparisonMode.ROI_MATRIX, stringResource(R.string.mode_roi_matrix), Icons.Default.GridView),
                    Triple(ComparisonMode.CONTACT_SHEET, stringResource(R.string.mode_contact_sheet), Icons.Default.GridView),
                    Triple(ComparisonMode.LAYER_STACK, stringResource(R.string.mode_layer_stack), Icons.Default.Layers),
                    Triple(ComparisonMode.CUSTOM_SHADER, stringResource(R.string.mode_custom_shader), Icons.Default.Science),
                )
                modes.forEach { (mode, label, icon) ->
                    StudioActionChip(
                        label = label,
                        icon = icon,
                        selected = state.comparisonMode == mode,
                        onClick = { onSelectMode(mode) },
                        testTag = "mode_chip_${mode.name.lowercase()}",
                    )
                }
            }

            // Row 3: Viewport Sync, Zoom, Swap, Scale, Alignment, Rotation/Flip & Culling
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StudioActionChip(
                    label = "${(state.masterViewport.zoomFactor * 100).roundToInt()}%",
                    icon = null,
                    selected = state.masterViewport.zoomFactor > 1.05f,
                    onClick = onZoomOneToOne,
                    testTag = "zoom_1to1_chip",
                )
                StudioTouchIconButton(
                    icon = Icons.Default.SwapHoriz,
                    contentDescription = stringResource(R.string.cd_swap_slots),
                    onClick = onSwapSlots,
                    testTag = "drawer_swap_slots_button",
                )
                StudioActionChip(
                    label = if (state.syncFlags.anySynced) stringResource(R.string.sync_locked) else stringResource(R.string.sync_unlocked),
                    icon = if (state.syncFlags.anySynced) Icons.Default.Link else Icons.Default.LinkOff,
                    selected = state.syncFlags.anySynced,
                    onClick = onToggleSyncAll,
                    testTag = "drawer_toggle_sync_button",
                )
                StudioActionChip(
                    label = if (state.masterViewport.scaleSyncMode == ScaleSyncMode.PHYSICAL_1_TO_1) "1:1 Px" else "Fit",
                    icon = Icons.Default.CenterFocusStrong,
                    selected = state.masterViewport.scaleSyncMode == ScaleSyncMode.PHYSICAL_1_TO_1,
                    onClick = onToggleScaleMode,
                    testTag = "scale_mode_chip",
                )
                StudioActionChip(
                    label = stringResource(R.string.calibrate_anchor),
                    icon = Icons.Default.LockOpen,
                    selected = state.anchorCalibrationSlot != null,
                    onClick = onToggleAnchorCalibrate,
                    testTag = "calibrate_anchor_chip",
                )
                StudioActionChip(
                    label = stringResource(R.string.auto_align),
                    icon = Icons.Default.AutoFixHigh,
                    selected = state.alignmentResult != null,
                    onClick = if (state.alignmentResult == null) onAutoAlign else onUndoAlign,
                    testTag = "auto_align_chip",
                )
                StudioTouchIconButton(
                    icon = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "Rotate 90 degrees",
                    onClick = onRotate90,
                    testTag = "rotate_90_button",
                )
                StudioTouchIconButton(
                    icon = Icons.Default.Flip,
                    contentDescription = "Flip Slot B horizontally",
                    onClick = onFlipHoriz,
                    testTag = "flip_b_button",
                )
                StudioTouchIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous candidate for Slot B",
                    onClick = { onStepB(-1) },
                    testTag = "prev_b_button",
                )
                StudioTouchIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next candidate for Slot B",
                    onClick = { onStepB(1) },
                    testTag = "next_b_button",
                )
                StudioActionChip(
                    label = stringResource(R.string.pin_reference),
                    icon = Icons.Default.PushPin,
                    selected = slotA != null && state.pinnedReferenceId == slotA.id,
                    onClick = onPinReferenceA,
                    testTag = "pin_ref_button",
                )
                StudioActionChip(
                    label = stringResource(R.string.promote_challenger),
                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                    selected = false,
                    onClick = onPromoteBToA,
                    testTag = "promote_b_button",
                )
                val decB = slotB?.let { state.decisions[it.id]?.decision } ?: CullingDecision.UNDECIDED
                StudioTouchIconButton(
                    icon = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.cd_keep_photo),
                    onClick = { onSetDecisionB(CullingDecision.KEEP) },
                    active = decB == CullingDecision.KEEP,
                    tint = PrismDiffColors.PrismGreen,
                    testTag = "cull_keep_b",
                )
                StudioTouchIconButton(
                    icon = Icons.Default.Star,
                    contentDescription = stringResource(R.string.cd_favorite_photo),
                    onClick = { onSetDecisionB(CullingDecision.FAVORITE) },
                    active = decB == CullingDecision.FAVORITE,
                    tint = PrismDiffColors.PrismAmber,
                    testTag = "cull_fav_b",
                )
                StudioTouchIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_reject_photo),
                    onClick = { onSetDecisionB(CullingDecision.REJECT) },
                    active = decB == CullingDecision.REJECT,
                    tint = PrismDiffColors.PrismRed,
                    testTag = "cull_reject_b",
                )
                StudioTouchIconButton(
                    icon = if (state.slideshowPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = "Toggle Ken Burns slideshow",
                    onClick = onToggleSlideshow,
                    active = state.slideshowPlaying,
                    testTag = "slideshow_button",
                )
            }
        }
    }
}

@Composable
private fun SlotPlacementCard(
    slotLabel: String,
    name: String,
    pinned: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canRemove: Boolean,
    onPick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = PrismDiffTokens.MinTouchTarget)
            .clip(RoundedCornerShape(PrismDiffTokens.StudioRadiusPill))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                PrismDiffTokens.HairlineDivider,
                if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(PrismDiffTokens.StudioRadiusPill),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (canMoveLeft) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onMoveLeft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Move slot $slotLabel earlier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onPick)
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = if (pinned) "$slotLabel*" else slotLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(78.dp),
            )
        }

        if (canMoveRight) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onMoveRight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Move slot $slotLabel later",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (canRemove) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onRemove)
                    .testTag("remove_slot_${slotLabel.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove slot $slotLabel",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun modeShortLabel(mode: ComparisonMode): String = when (mode) {
    ComparisonMode.SIDE_BY_SIDE -> "Split"
    ComparisonMode.VERTICAL_SPLIT -> "Vertical"
    ComparisonMode.CURTAIN_WIPE -> "Wipe"
    ComparisonMode.ONION_SKIN -> "Onion"
    ComparisonMode.LAYER_STACK -> "Layers"
    ComparisonMode.BLINK_STROBE -> "Blink"
    ComparisonMode.DIFFERENCE_MAP -> "Diff Map"
    ComparisonMode.MULTI_LOUPE -> "Loupe"
    ComparisonMode.ROI_MATRIX -> "ROI Grid"
    ComparisonMode.CONTACT_SHEET -> "Contact"
    ComparisonMode.CUSTOM_SHADER -> "Shader"
}

private fun layoutShortLabel(layout: MultiSlotLayout): String = when (layout) {
    MultiSlotLayout.AUTO -> "Auto"
    MultiSlotLayout.COLUMNS -> "Cols"
    MultiSlotLayout.ROWS -> "Rows"
    MultiSlotLayout.GRID -> "Grid"
    MultiSlotLayout.FOCUS_PAIR -> "2-Up"
}
