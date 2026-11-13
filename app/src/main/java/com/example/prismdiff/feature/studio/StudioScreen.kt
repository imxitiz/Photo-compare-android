package com.example.prismdiff.feature.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.prismdiff.core.model.ComparisonMode
import com.example.prismdiff.core.model.ScaleSyncMode
import com.example.prismdiff.core.model.StudioDockVisibility

/**
 * Adaptive Studio Screen supporting compact phones (3-state collapsible/hideable dock + ModalBottomSheet)
 * and wide tablets/foldables/landscape (persistent side Inspector pane).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    state: PrismDiffUiState,
    viewModel: PrismDiffViewModel,
    derivedBitmap: android.graphics.Bitmap?,
    bottomNavVisible: Boolean,
    onLaunchPhotoPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInspectorSheet by remember { mutableStateOf(false) }
    var slotPickerTarget by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = state.dockVisibility != StudioDockVisibility.COMPACT) {
        viewModel.setDockVisibility(StudioDockVisibility.COMPACT)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useDockedSidePane = maxWidth >= 840.dp &&
            !state.lightsOutImmersive &&
            state.dockVisibility != StudioDockVisibility.HIDDEN

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ComparisonCanvasStage(
                    state = state,
                    decoder = viewModel.decoder,
                    derivedBitmap = derivedBitmap,
                    onPanDelta = viewModel::panByScreenDelta,
                    onZoomFactor = viewModel::zoomByFactor,
                    onDoubleTapZoom = viewModel::handleDoubleTapZoom,
                    onTapNormalized = viewModel::samplePixelAt,
                    onCurtainDragDelta = viewModel::updateCurtainByDelta,
                    onCurtainReset = { viewModel.updateCurtain(ratio = 0.5f) },
                    onSplitRatioUpdate = viewModel::updateSplitRatio,
                    onHoldPeekA = { hold -> viewModel.updateBlinkStrobe(holdPeekA = hold) },
                    onLoupeMove = { x, y -> viewModel.updateLoupe(normX = x, normY = y) },
                    onCycleSlotAsset = { idx ->
                        val chosen = state.filteredAssets.getOrNull(idx) ?: return@ComparisonCanvasStage
                        viewModel.assignAssetToSlot(1, chosen.id)
                        viewModel.setComparisonMode(ComparisonMode.SIDE_BY_SIDE)
                    },
                    onSetDecision = viewModel::setCullingDecision,
                    modifier = Modifier.fillMaxSize(),
                )

                StudioControlDock(
                    state = state,
                    bottomNavVisible = bottomNavVisible,
                    onSetDockVisibility = viewModel::setDockVisibility,
                    onSelectMode = viewModel::setComparisonMode,
                    onSetMultiSlotLayout = viewModel::setMultiSlotLayout,
                    onCycleMultiSlotLayout = viewModel::cycleMultiSlotLayout,
                    onAddSlot = { viewModel.addSlotAsset() },
                    onRemoveSlot = viewModel::removeSlotAt,
                    onMoveSlot = viewModel::moveSlot,
                    onToggleSyncAll = {
                        val next = !state.syncFlags.anySynced
                        viewModel.toggleSyncFlag(zoom = next, pan = next, rotation = next, crop = next)
                    },
                    onToggleScaleMode = {
                        val next = if (state.masterViewport.scaleSyncMode == ScaleSyncMode.PHYSICAL_1_TO_1) {
                            ScaleSyncMode.FIT_NORMALIZED
                        } else ScaleSyncMode.PHYSICAL_1_TO_1
                        viewModel.setScaleSyncMode(next)
                    },
                    onToggleAnchorCalibrate = { viewModel.toggleAnchorCalibration(1) },
                    onAutoAlign = viewModel::runAutoAlignScene,
                    onUndoAlign = viewModel::undoAlignment,
                    onResetViewport = viewModel::resetViewportAndOffsets,
                    onZoomOneToOne = {
                        val nextZ = if (state.masterViewport.zoomFactor < 2f) 4f else 1f
                        viewModel.setMasterZoomExact(nextZ)
                    },
                    onSwapSlots = viewModel::swapSlotsAB,
                    onRotate90 = { viewModel.rotateSlotOrMaster(1, 90f) },
                    onFlipHoriz = { viewModel.flipSlot(1, horizontal = true) },
                    onPinReferenceA = viewModel::togglePinReferenceSlotA,
                    onPromoteBToA = viewModel::promoteSlotBToReference,
                    onStepB = viewModel::stepSlotB,
                    onSetDecisionB = { dec ->
                        state.slotB?.let { viewModel.setCullingDecision(it.id, dec) }
                    },
                    onOpenInspector = { showInspectorSheet = true },
                    onOpenSlotPicker = { slot -> slotPickerTarget = slot },
                    onToggleSlideshow = { viewModel.toggleSlideshow(kenBurns = true) },
                    onUpdateOnionAlpha = { alpha -> viewModel.updateOnionAndBlend(alpha = alpha) },
                    onUpdateCurtainAngle = { deg -> viewModel.updateCurtain(angleDeg = deg) },
                    onResetCurtain = { viewModel.updateCurtain(ratio = 0.5f) },
                    onUpdateBlink = { hz, play -> viewModel.updateBlinkStrobe(hz, play) },
                    onClearProbe = viewModel::clearPixelProbe,
                    onAdvanceTournament = { w, l -> viewModel.startOrAdvanceTournament(w, l) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (useDockedSidePane) {
                Surface(
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                ) {
                    InspectorPanelContent(
                        state = state,
                        onSelectRegion = viewModel::selectRegion,
                        onUpdateDiff = viewModel::updateDifferenceSettings,
                        onUpdateDiagnostics = viewModel::updateDiagnostics,
                        onUpdateNormB = { norm -> viewModel.updateNormalization(slotB = norm) },
                        onUndoNorm = viewModel::undoNormalization,
                        onResetNorm = viewModel::resetNormalization,
                        onUpdateBlend = viewModel::updateOnionAndBlend,
                        onUpdateBlink = { hz, play -> viewModel.updateBlinkStrobe(hz, play) },
                        onUpdateCurtainAngle = { ang -> viewModel.updateCurtain(angleDeg = ang) },
                        onToggleThirds = viewModel::toggleRuleOfThirds,
                        onToggleAlignmentOverlay = viewModel::toggleAlignmentOverlay,
                        onToggleMaskGps = viewModel::setMaskGps,
                        onAddAnnotation = viewModel::addAnnotation,
                        onClearAnnotations = viewModel::clearAnnotations,
                        onExportPngCollage = viewModel::exportComparisonCollage,
                        onExportAnimatedGif = viewModel::exportAnimatedWipeGif,
                        onExportJsonReport = viewModel::exportJsonReport,
                        onRequestRemoveUnselected = viewModel::requestTrashOrDeleteUnselected,
                        onStartTournament = { viewModel.startOrAdvanceTournament() },
                    )
                }
            }
        }
    }

    if (showInspectorSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showInspectorSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            InspectorPanelContent(
                state = state,
                onSelectRegion = viewModel::selectRegion,
                onUpdateDiff = viewModel::updateDifferenceSettings,
                onUpdateDiagnostics = viewModel::updateDiagnostics,
                onUpdateNormB = { norm -> viewModel.updateNormalization(slotB = norm) },
                onUndoNorm = viewModel::undoNormalization,
                onResetNorm = viewModel::resetNormalization,
                onUpdateBlend = viewModel::updateOnionAndBlend,
                onUpdateBlink = { hz, play -> viewModel.updateBlinkStrobe(hz, play) },
                onUpdateCurtainAngle = { ang -> viewModel.updateCurtain(angleDeg = ang) },
                onToggleThirds = viewModel::toggleRuleOfThirds,
                onToggleAlignmentOverlay = viewModel::toggleAlignmentOverlay,
                onToggleMaskGps = viewModel::setMaskGps,
                onAddAnnotation = viewModel::addAnnotation,
                onClearAnnotations = viewModel::clearAnnotations,
                onExportPngCollage = viewModel::exportComparisonCollage,
                onExportAnimatedGif = viewModel::exportAnimatedWipeGif,
                onExportJsonReport = viewModel::exportJsonReport,
                onRequestRemoveUnselected = viewModel::requestTrashOrDeleteUnselected,
                onStartTournament = { viewModel.startOrAdvanceTournament() },
            )
        }
    }

    slotPickerTarget?.let { targetSlot ->
        AlertDialog(
            onDismissRequest = { slotPickerTarget = null },
            title = { Text("Assign Slot ${'A' + targetSlot}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            slotPickerTarget = null
                            onLaunchPhotoPicker()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Pick from Device Photo Picker")
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        items(state.filteredAssets.take(40), key = { it.id }) { asset ->
                            ListItem(
                                headlineContent = { Text(asset.displayName) },
                                supportingContent = { Text("${asset.width}×${asset.height} • ${asset.folderName}") },
                                modifier = Modifier.clickable {
                                    viewModel.assignAssetToSlot(targetSlot, asset.id)
                                    slotPickerTarget = null
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { slotPickerTarget = null }) { Text("Close") }
            },
        )
    }
}
