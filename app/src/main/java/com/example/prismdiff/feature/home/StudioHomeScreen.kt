package com.example.prismdiff.feature.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.prismdiff.core.designsystem.StudioSectionHeader
import com.example.prismdiff.core.model.BurstCluster
import com.example.prismdiff.core.model.WorkspacePreset
import com.example.prismdiff.core.storage.CullingSessionEntity
import com.example.prismdiff.feature.studio.PrismDiffUiState

@Composable
fun StudioHomeScreen(
    state: PrismDiffUiState,
    decoder: TilePyramidDecoder,
    onPickPhotos: () -> Unit,
    onOpenSafFolder: () -> Unit,
    onOpenDirectCanvas: () -> Unit,
    onResumeSession: (CullingSessionEntity) -> Unit,
    onOpenCluster: (BurstCluster) -> Unit,
    onApplyPresetAndCompare: (WorkspacePreset) -> Unit,
    onOpenSamplePair: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Editorial Optical Hero Card (distinctive OLED darkroom aesthetic)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 820.dp)
                    .clip(RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0A141D),
                                Color(0xFF0B0D10),
                                Color(0xFF1A120A),
                            ),
                        ),
                    )
                    .border(
                        PrismDiffTokens.HairlineDivider,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
                    )
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StudioBadgePill("100% OFFLINE STUDIO", accent = PrismDiffColors.PrismCyan)
                        StudioBadgePill("ZERO CLOUD • ZERO READ_MEDIA", accent = PrismDiffColors.PrismAmber)
                    }
                    Text(
                        text = stringResource(R.string.home_headline),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.home_subheadline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onPickPhotos,
                            modifier = Modifier.weight(1f).testTag("home_pick_photos_button"),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_pick_photos))
                        }
                        OutlinedButton(
                            onClick = onOpenSafFolder,
                            modifier = Modifier.weight(1f).testTag("home_open_folder_button"),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_open_folder))
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenDirectCanvas,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().testTag("home_direct_canvas_button"),
                    ) {
                        Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_direct_canvas))
                    }
                }
            }
        }

        // Continue Previous Session
        if (state.sessions.isNotEmpty()) {
            item {
                val latest = state.sessions.first()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioSectionHeader(stringResource(R.string.section_continue_session))
                    Surface(
                        shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clickable { onResumeSession(latest) }.testTag("resume_session_card"),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(latest.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Mode: ${latest.comparisonModeName.replace('_', ' ')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StudioBadgePill("Resume", accent = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Smart Scene & Burst Clusters
        if (state.burstClusters.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioSectionHeader(
                        title = stringResource(R.string.section_smart_clusters),
                        subtitle = "Detected offline via dHash Hamming similarity + capture cadence",
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.burstClusters, key = { it.id }) { cluster ->
                            BurstClusterCard(
                                cluster = cluster,
                                decoder = decoder,
                                onClick = { onOpenCluster(cluster) },
                            )
                        }
                    }
                }
            }
        }

        // Workspace Presets Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioSectionHeader(
                    title = stringResource(R.string.section_workspace_presets),
                    subtitle = "11 data-driven studio configurations & your custom JSON presets",
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.presets, key = { it.id }) { preset ->
                        Surface(
                            shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
                            color = if (preset.id == state.preferences.activePresetId) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else MaterialTheme.colorScheme.surfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                PrismDiffTokens.HairlineDivider,
                                if (preset.id == state.preferences.activePresetId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .width(204.dp)
                                .clickable { onApplyPresetAndCompare(preset) }
                                .testTag("home_preset_${preset.id}"),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(preset.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Built-in Offline Calibration Lab Test Pairs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioSectionHeader(
                    title = stringResource(R.string.section_calibration_lab),
                    subtitle = "Synthetic 100% offline test fixtures to verify alignment, sharpness & ELA immediately",
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CalibrationFixtureCard(
                        title = "Sharpness Burst Pair",
                        subtitle = "ISO 100 Crisp vs Motion Blur",
                        onClick = { onOpenSamplePair("calibration_burst_sharp.jpg", "calibration_burst_soft.jpg") },
                    )
                    CalibrationFixtureCard(
                        title = "Handheld Alignment + WB",
                        subtitle = "Translated 3° shifted & warm tone",
                        onClick = { onOpenSamplePair("calibration_burst_sharp.jpg", "calibration_shifted_warm.jpg") },
                    )
                    CalibrationFixtureCard(
                        title = "Forensic ELA Splice",
                        subtitle = "Recompressed 58% patch detection",
                        onClick = { onOpenSamplePair("calibration_ela_clean.jpg", "calibration_ela_spliced.jpg") },
                    )
                }
            }
        }
    }
}

@Composable
private fun BurstClusterCard(
    cluster: BurstCluster,
    decoder: TilePyramidDecoder,
    onClick: () -> Unit,
) {
    val first = cluster.assets.firstOrNull()
    var thumb by remember(first?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(first?.id) {
        if (first != null) {
            (decoder.decodeThumbnail(first, 240) as? DecodeOutcome.Success)?.let { thumb = it.data }
        }
    }
    Surface(
        shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(220.dp).clickable(onClick = onClick),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(112.dp).background(Color.Black)) {
                thumb?.let { b ->
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = cluster.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                StudioBadgePill(
                    text = "${cluster.assets.size} shots",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Text(cluster.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = cluster.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CalibrationFixtureCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(210.dp).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
