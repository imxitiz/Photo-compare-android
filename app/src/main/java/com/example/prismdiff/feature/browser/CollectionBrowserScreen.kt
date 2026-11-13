package com.example.prismdiff.feature.browser

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.prismdiff.core.decoder.DecodeOutcome
import com.example.prismdiff.core.decoder.TilePyramidDecoder
import com.example.prismdiff.core.designsystem.PrismDiffColors
import com.example.prismdiff.core.designsystem.PrismDiffTokens
import com.example.prismdiff.core.designsystem.StudioBadgePill
import com.example.prismdiff.core.model.CullingDecision
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.SortOrder
import com.example.prismdiff.feature.studio.PrismDiffUiState

private enum class BrowserDecisionFilter(val label: String) {
    ALL("All"),
    KEPT("Keep/Fav"),
    REJECTED("Rejected"),
    UNDECIDED("Undecided"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionBrowserScreen(
    state: PrismDiffUiState,
    decoder: TilePyramidDecoder,
    onPickPhotos: () -> Unit,
    onOpenSafFolder: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleSelect: (String) -> Unit,
    onInvertSelection: () -> Unit,
    onOpenPairInStudio: (ImageAsset) -> Unit,
    onCompareSelected: () -> Unit,
    onStartTournament: () -> Unit,
    onTrashUnselected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: (List<ImageAsset>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf(BrowserDecisionFilter.ALL) }

    val visibleAssets = remember(state.filteredAssets, state.decisions, filterMode) {
        when (filterMode) {
            BrowserDecisionFilter.ALL -> state.filteredAssets
            BrowserDecisionFilter.KEPT -> state.filteredAssets.filter {
                val d = state.decisions[it.id]?.decision
                d == CullingDecision.KEEP || d == CullingDecision.FAVORITE
            }
            BrowserDecisionFilter.REJECTED -> state.filteredAssets.filter {
                state.decisions[it.id]?.decision == CullingDecision.REJECT
            }
            BrowserDecisionFilter.UNDECIDED -> state.filteredAssets.filter {
                val d = state.decisions[it.id]?.decision
                d == null || d == CullingDecision.UNDECIDED
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header Bar + Source Pickers + Sort Menu
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.browser_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${visibleAssets.size} image(s) • ${state.burstClusters.size} burst group(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPickPhotos, modifier = Modifier.testTag("browser_pick_button")) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.action_pick_photos))
                }
                IconButton(onClick = onOpenSafFolder, modifier = Modifier.testTag("browser_folder_button")) {
                    Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.action_open_folder))
                }
                Box {
                    IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.testTag("browser_sort_button")) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort_label))
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (order == state.sortOrder) "✓ ${order.name.replace('_', ' ')}" else order.name.replace('_', ' '),
                                    )
                                },
                                onClick = {
                                    onSortChange(order)
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Search Field + Filter Chips
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_photos_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("browser_search_input"),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrowserDecisionFilter.entries.forEach { f ->
                FilterChip(
                    selected = filterMode == f,
                    onClick = { filterMode = f },
                    label = { Text(f.label) },
                )
            }
            TextButton(onClick = onInvertSelection, modifier = Modifier.testTag("browser_invert_button")) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_invert_selection))
            }
            TextButton(onClick = onStartTournament, modifier = Modifier.testTag("browser_elo_button")) {
                Text(stringResource(R.string.action_tournament_cull))
            }
        }

        // Grid of Images
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visibleAssets, key = { it.id }) { asset ->
                val isSelected = asset.id in state.browserMultiSelectedIds
                val decision = state.decisions[asset.id]?.decision ?: CullingDecision.UNDECIDED
                BrowserPhotoTile(
                    asset = asset,
                    decoder = decoder,
                    selected = isSelected,
                    decision = decision,
                    onClick = {
                        if (state.browserMultiSelectedIds.isNotEmpty()) {
                            onToggleSelect(asset.id)
                        } else {
                            onOpenPairInStudio(asset)
                        }
                    },
                    onLongClick = { onToggleSelect(asset.id) },
                )
            }
        }

        // Bottom Batch Action Bar
        val selectedAssets = visibleAssets.filter { it.id in state.browserMultiSelectedIds }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onCompareSelected,
                    modifier = Modifier.testTag("browser_compare_selected_button"),
                ) {
                    Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_compare_selected, selectedAssets.size.coerceAtLeast(2)))
                }
                if (selectedAssets.isNotEmpty()) {
                    OutlinedButton(onClick = { onShareSelected(selectedAssets) }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share (${selectedAssets.size})")
                    }
                    OutlinedButton(onClick = onDeleteSelected) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_delete_selected))
                    }
                }
                OutlinedButton(
                    onClick = onTrashUnselected,
                    modifier = Modifier.testTag("browser_trash_unselected_button"),
                ) {
                    Text(stringResource(R.string.action_trash_unselected))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserPhotoTile(
    asset: ImageAsset,
    decoder: TilePyramidDecoder,
    selected: Boolean,
    decision: CullingDecision,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var thumb by remember(asset.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(asset.id) {
        (decoder.decodeThumbnail(asset, 260) as? DecodeOutcome.Success)?.let { thumb = it.data }
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        decision == CullingDecision.FAVORITE -> PrismDiffColors.PrismAmber
        decision == CullingDecision.KEEP -> PrismDiffColors.PrismGreen
        decision == CullingDecision.REJECT -> PrismDiffColors.PrismRed
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("browser_tile_${asset.id}"),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(126.dp).background(Color.Black)) {
                thumb?.let { b ->
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = asset.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (decision != CullingDecision.UNDECIDED) {
                    val icon = when (decision) {
                        CullingDecision.FAVORITE -> Icons.Default.Star
                        CullingDecision.KEEP -> Icons.Default.CheckCircle
                        CullingDecision.REJECT -> Icons.Default.Close
                        else -> Icons.Default.CheckCircle
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = decision.name,
                        tint = borderColor,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                    )
                }
                if (selected) {
                    StudioBadgePill("Selected", modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = asset.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${asset.width}×${asset.height} • ${String.format("%.1f", asset.fileSizeBytes / (1024f * 1024f))} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
