package com.example.prismdiff.feature.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.prismdiff.core.model.ComparisonMode
import com.example.prismdiff.core.model.CullingDecision
import com.example.prismdiff.core.model.ScaleSyncMode
import kotlin.math.roundToInt

/**
 * Floating thumb-zone dock for the Comparison Studio.
 * Designed around progressive disclosure: clean mode rail + primary precision bar,
 * expanding into Inspector / Forensics / EXIF / Culling sheets on demand.
 */
@Composable
fun StudioControlDock(
    state: PrismDiffUiState,
    onSelectMode: (ComparisonMode) -> Unit,
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
    onToggleLightsOut: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onUpdateOnionAlpha: (Float) -> Unit,
    onAdvanceTournament: (winnerId: String, loserId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.lightsOutImmersive) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusPill),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clickable(onClick = onToggleLightsOut)
                    .testTag("exit_lights_out_pill"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Show studio controls", modifier = Modifier.size(18.dp))
                    Text("Controls", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Optional pixel probe readout pill
        val probeA = state.pixelProbeA
        val probeB = state.pixelProbeB
        if (probeA != null && probeB != null) {
            val argbA = (0xFF shl 24) or (probeA.r shl 16) or (probeA.g shl 8) or probeA.b
            val argbB = (0xFF shl 24) or (probeB.r shl 16) or (probeB.g shl 8) or probeB.b
            val deltaE = com.example.prismdiff.core.vision.ColorMath.ciede2000(argbA, argbB)
            StudioBadgePill(
                text = "Probe (${probeA.x},${probeA.y}) A ${probeA.hex} vs B ${probeB.hex} • ΔE00 ${String.format("%.2f", deltaE)}",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Elo Match",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onAdvanceTournament(slotA.id, slotB.id) }) {
                            Text("Pick A")
                        }
                        TextButton(onClick = { onAdvanceTournament(slotB.id, slotA.id) }) {
                            Text("Pick B")
                        }
                    }
                }
            }
        }

        // Inline Onion/Blend opacity slider when in Onion Skin or Layer Stack
        AnimatedVisibility(
            visible = state.comparisonMode == ComparisonMode.ONION_SKIN || state.comparisonMode == ComparisonMode.LAYER_STACK,
        ) {
            Surface(
                shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Blend ${(state.onionAlpha * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
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

        // Main floating translucent darkroom control surface
        Surface(
            shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.93f),
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(
                PrismDiffTokens.HairlineDivider,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Row 1: Slot assignment + Quick Burst Stepping + King-of-the-Hill & Culling
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SlotSelectorPill(
                        tag = "A",
                        name = slotA?.displayName ?: "Slot A",
                        pinned = slotA != null && state.pinnedReferenceId == slotA.id,
                        onClick = { onOpenSlotPicker(0) },
                        testTag = "slot_a_pill",
                    )
                    StudioTouchIconButton(
                        icon = Icons.Default.SwapHoriz,
                        contentDescription = stringResource(R.string.cd_swap_slots),
                        onClick = onSwapSlots,
                        testTag = "swap_slots_button",
                    )
                    SlotSelectorPill(
                        tag = "B",
                        name = slotB?.displayName ?: "Slot B",
                        pinned = false,
                        onClick = { onOpenSlotPicker(1) },
                        testTag = "slot_b_pill",
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
                    // Quick Culling buttons for B
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
                }

                // Row 2: 11 Comparison Mode Selector Chips
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
                        Triple(ComparisonMode.VERTICAL_SPLIT, stringResource(R.string.mode_vertical_split), Icons.Default.Compare),
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

                // Row 3: Viewport Sync, Alignment, Transform, 1:1, Lights-Out & Inspector Trigger
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StudioActionChip(
                        label = if (state.syncFlags.anySynced) stringResource(R.string.sync_locked) else stringResource(R.string.sync_unlocked),
                        icon = if (state.syncFlags.anySynced) Icons.Default.Link else Icons.Default.LinkOff,
                        selected = state.syncFlags.anySynced,
                        onClick = onToggleSyncAll,
                        testTag = "toggle_sync_button",
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
                    StudioActionChip(
                        label = "${(state.masterViewport.zoomFactor * 100).roundToInt()}%",
                        icon = null,
                        selected = state.masterViewport.zoomFactor > 1.05f,
                        onClick = onZoomOneToOne,
                        testTag = "zoom_1to1_chip",
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
                        icon = Icons.Default.CenterFocusStrong,
                        contentDescription = stringResource(R.string.cd_reset_viewport),
                        onClick = onResetViewport,
                        testTag = "reset_viewport_button",
                    )
                    StudioTouchIconButton(
                        icon = if (state.slideshowPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Ken Burns slideshow",
                        onClick = onToggleSlideshow,
                        active = state.slideshowPlaying,
                        testTag = "slideshow_button",
                    )
                    StudioTouchIconButton(
                        icon = Icons.Default.DarkMode,
                        contentDescription = stringResource(R.string.lights_out_enter),
                        onClick = onToggleLightsOut,
                        testTag = "lights_out_button",
                    )
                    StudioActionChip(
                        label = "Inspector",
                        icon = Icons.Default.Tune,
                        selected = false,
                        onClick = onOpenInspector,
                        testTag = "open_inspector_button",
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotSelectorPill(
    tag: String,
    name: String,
    pinned: Boolean,
    onClick: () -> Unit,
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (pinned) "$tag*" else tag,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(88.dp),
        )
    }
}
