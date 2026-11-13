package com.example.prismdiff.feature.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.prismdiff.core.designsystem.PrismDiffColors
import com.example.prismdiff.core.designsystem.PrismDiffTokens
import com.example.prismdiff.core.designsystem.StudioBadgePill
import com.example.prismdiff.core.designsystem.StudioSectionHeader
import com.example.prismdiff.core.model.AnalysisMetricReport
import com.example.prismdiff.core.model.AnnotationItem
import com.example.prismdiff.core.model.AnnotationType
import com.example.prismdiff.core.model.BlendOperator
import com.example.prismdiff.core.model.ColorChannelIsolation
import com.example.prismdiff.core.model.DiagnosticOverlay
import com.example.prismdiff.core.model.DiffPalette
import com.example.prismdiff.core.model.DifferenceVisualMode
import com.example.prismdiff.core.model.NormalizationAdjustments
import kotlin.math.roundToInt

/**
 * 4-tab Pro Inspector & Forensics panel usable either inside a Material 3 ModalBottomSheet
 * (phone portrait) or a docked side pane (tablets / landscape).
 */
@Composable
fun InspectorPanelContent(
    state: PrismDiffUiState,
    onSelectRegion: (String?) -> Unit,
    onUpdateDiff: (DifferenceVisualMode?, DiffPalette?, Float?, Float?) -> Unit,
    onUpdateDiagnostics: (DiagnosticOverlay?, ColorChannelIsolation?, Int?, Float?, Int?) -> Unit,
    onUpdateNormB: (NormalizationAdjustments) -> Unit,
    onUndoNorm: () -> Unit,
    onResetNorm: () -> Unit,
    onUpdateBlend: (Float?, BlendOperator?) -> Unit,
    onUpdateBlink: (Float?, Boolean?) -> Unit,
    onUpdateCurtainAngle: (Float) -> Unit,
    onToggleThirds: () -> Unit,
    onToggleAlignmentOverlay: () -> Unit,
    onToggleMaskGps: (Boolean) -> Unit,
    onAddAnnotation: (AnnotationItem) -> Unit,
    onClearAnnotations: () -> Unit,
    onExportPngCollage: () -> Unit,
    onExportAnimatedGif: () -> Unit,
    onExportJsonReport: () -> Unit,
    onRequestRemoveUnselected: (permanent: Boolean) -> Unit,
    onStartTournament: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Scopes", "Forensics", "EXIF Diff", "Actions")

    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, maxLines = 1) },
                    modifier = Modifier.testTag("inspector_tab_$idx"),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (selectedTab) {
                0 -> ScopesAndMetricsTab(state, onSelectRegion)
                1 -> ForensicsAndNormalizeTab(
                    state = state,
                    onUpdateDiff = onUpdateDiff,
                    onUpdateDiagnostics = onUpdateDiagnostics,
                    onUpdateNormB = onUpdateNormB,
                    onUndoNorm = onUndoNorm,
                    onResetNorm = onResetNorm,
                    onUpdateBlend = onUpdateBlend,
                    onUpdateBlink = onUpdateBlink,
                    onUpdateCurtainAngle = onUpdateCurtainAngle,
                    onToggleThirds = onToggleThirds,
                    onToggleAlignmentOverlay = onToggleAlignmentOverlay,
                )
                2 -> ExifDiffTab(state, onToggleMaskGps)
                3 -> ActionsAndExportTab(
                    state = state,
                    onAddAnnotation = onAddAnnotation,
                    onClearAnnotations = onClearAnnotations,
                    onExportPngCollage = onExportPngCollage,
                    onExportAnimatedGif = onExportAnimatedGif,
                    onExportJsonReport = onExportJsonReport,
                    onRequestRemoveUnselected = onRequestRemoveUnselected,
                    onStartTournament = onStartTournament,
                )
            }
        }
    }
}

@Composable
private fun ScopesAndMetricsTab(
    state: PrismDiffUiState,
    onSelectRegion: (String?) -> Unit,
) {
    var logScale by remember { mutableStateOf(false) }
    var overlaid by remember { mutableStateOf(false) }

    StudioSectionHeader("RGB + Luminance Histograms", "Live 256-bin analysis of current scope")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = logScale, onClick = { logScale = !logScale }, label = { Text("Log Scale") })
        FilterChip(selected = overlaid, onClick = { overlaid = !overlaid }, label = { Text("Overlay A/B") })
    }
    DualHistogramScope(
        histogramA = state.histogramA,
        histogramB = state.histogramB,
        isLogScale = logScale,
        overlaid = overlaid,
    )

    StudioSectionHeader("Analysis Region Scope", "Measure full frame or isolated ROI patch")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.activeRegionId == null,
            onClick = { onSelectRegion(null) },
            label = { Text("Full Frame") },
        )
        state.regions.forEach { region ->
            FilterChip(
                selected = state.activeRegionId == region.id,
                onClick = { onSelectRegion(region.id) },
                label = { Text(region.name) },
            )
        }
    }

    val bundle = state.metricsBundle
    if (bundle != null) {
        StudioSectionHeader(
            title = "Quantitative Similarity & Quality",
            subtitle = "Scope: ${bundle.scopeLabel} • Aligned: ${bundle.alignmentApplied} • Color Norm: ${bundle.colorNormalized}",
        )
        bundle.reports.forEach { report ->
            MetricDisclosureCard(report)
        }
    } else {
        Text("Analyzing frames…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetricDisclosureCard(report: AnalysisMetricReport) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.title, style = MaterialTheme.typography.titleSmall)
                    Text(report.algorithmName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = "${report.valueFormatted} ${report.unit}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(report.explanation, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Caveat: ${report.limitationNote}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun ForensicsAndNormalizeTab(
    state: PrismDiffUiState,
    onUpdateDiff: (DifferenceVisualMode?, DiffPalette?, Float?, Float?) -> Unit,
    onUpdateDiagnostics: (DiagnosticOverlay?, ColorChannelIsolation?, Int?, Float?, Int?) -> Unit,
    onUpdateNormB: (NormalizationAdjustments) -> Unit,
    onUndoNorm: () -> Unit,
    onResetNorm: () -> Unit,
    onUpdateBlend: (Float?, BlendOperator?) -> Unit,
    onUpdateBlink: (Float?, Boolean?) -> Unit,
    onUpdateCurtainAngle: (Float) -> Unit,
    onToggleThirds: () -> Unit,
    onToggleAlignmentOverlay: () -> Unit,
) {
    StudioSectionHeader("Diagnostic & Forensic Overlays")
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiagnosticOverlay.entries.forEach { ov ->
            FilterChip(
                selected = state.diagnosticOverlay == ov,
                onClick = { onUpdateDiagnostics(ov, null, null, null, null) },
                label = { Text(ov.name.replace('_', ' ')) },
            )
        }
    }

    if (state.diagnosticOverlay == DiagnosticOverlay.CHANNEL_ISOLATION) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorChannelIsolation.entries.forEach { ch ->
                FilterChip(
                    selected = state.channelIsolation == ch,
                    onClick = { onUpdateDiagnostics(null, ch, null, null, null) },
                    label = { Text(ch.name) },
                )
            }
        }
    }

    if (state.diagnosticOverlay == DiagnosticOverlay.ELA_FORENSICS) {
        Text("ELA JPEG Recompression Quality: ${state.elaQuality}%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = state.elaQuality.toFloat(),
            onValueChange = { onUpdateDiagnostics(null, null, it.roundToInt(), null, null) },
            valueRange = 50f..98f,
        )
        Text("ELA Residual Scale: ${state.elaScale.roundToInt()}×", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = state.elaScale,
            onValueChange = { onUpdateDiagnostics(null, null, null, it, null) },
            valueRange = 4f..60f,
        )
    }

    if (state.diagnosticOverlay == DiagnosticOverlay.BIT_PLANE_SLICE) {
        Text("Bit Plane Index: ${state.bitPlaneIndex} (0 = LSB, 7 = MSB)", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = state.bitPlaneIndex.toFloat(),
            onValueChange = { onUpdateDiagnostics(null, null, null, null, it.roundToInt()) },
            valueRange = 0f..7f,
            steps = 6,
        )
    }

    StudioSectionHeader("Difference Map & Blend Engine")
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DifferenceVisualMode.entries.forEach { dm ->
            FilterChip(
                selected = state.differenceMode == dm,
                onClick = { onUpdateDiff(dm, null, null, null) },
                label = { Text(dm.name.replace('_', ' ')) },
            )
        }
    }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiffPalette.entries.forEach { p ->
            FilterChip(
                selected = state.diffPalette == p,
                onClick = { onUpdateDiff(null, p, null, null) },
                label = { Text(p.name) },
            )
        }
    }
    Text("Diff Gain: ${state.diffBoost.roundToInt()}× • Noise Gate: ${(state.diffThreshold * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
    Slider(value = state.diffBoost, onValueChange = { onUpdateDiff(null, null, it, null) }, valueRange = 1f..40f)
    Slider(value = state.diffThreshold, onValueChange = { onUpdateDiff(null, null, null, it) }, valueRange = 0f..0.4f)

    Text("Blend Operator (${state.blendOperator.name})", style = MaterialTheme.typography.labelMedium)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BlendOperator.entries.forEach { op ->
            FilterChip(
                selected = state.blendOperator == op,
                onClick = { onUpdateBlend(null, op) },
                label = { Text(op.name) },
            )
        }
    }

    Text("Curtain Wipe Cut Angle: ${state.curtainAngleDeg.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
    Slider(value = state.curtainAngleDeg, onValueChange = onUpdateCurtainAngle, valueRange = 0f..180f)

    Text("Blink Strobe Rate: ${String.format("%.1f", state.blinkFrequencyHz)} Hz", style = MaterialTheme.typography.labelMedium)
    Slider(value = state.blinkFrequencyHz, onValueChange = { onUpdateBlink(it, null) }, valueRange = 0.5f..8f)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = state.showRuleOfThirds, onClick = onToggleThirds, label = { Text("Rule of Thirds") })
        FilterChip(selected = state.showAlignmentOverlay, onClick = onToggleAlignmentOverlay, label = { Text("Keypoints") })
    }

    HorizontalDivider()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        StudioSectionHeader("Non-Destructive Slot B Fairness Normalization", "Previews exposure/WB parity without touching originals")
        Row {
            TextButton(onClick = onUndoNorm) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo"); Text("Undo") }
            TextButton(onClick = onResetNorm) { Text("Reset") }
        }
    }
    val normB = state.normalizationB
    if (!normB.isIdentity) {
        StudioBadgePill("Normalized Preview Active on Slot B", accent = MaterialTheme.colorScheme.secondary)
    }
    Text("Exposure EV: ${String.format("%+.2f", normB.exposureEv)}", style = MaterialTheme.typography.labelMedium)
    Slider(value = normB.exposureEv, onValueChange = { onUpdateNormB(normB.copy(enabled = true, exposureEv = it)) }, valueRange = -2.5f..2.5f)
    Text("Contrast: ${String.format("%.2f", normB.contrast)}×", style = MaterialTheme.typography.labelMedium)
    Slider(value = normB.contrast, onValueChange = { onUpdateNormB(normB.copy(enabled = true, contrast = it)) }, valueRange = 0.5f..2.0f)
    Text("Saturation: ${String.format("%.2f", normB.saturation)}×", style = MaterialTheme.typography.labelMedium)
    Slider(value = normB.saturation, onValueChange = { onUpdateNormB(normB.copy(enabled = true, saturation = it)) }, valueRange = 0f..2.0f)
    Text("Warmth / Temp Shift: ${String.format("%+.2f", normB.temperatureShift)}", style = MaterialTheme.typography.labelMedium)
    Slider(value = normB.temperatureShift, onValueChange = { onUpdateNormB(normB.copy(enabled = true, temperatureShift = it)) }, valueRange = -1f..1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Luminance-Only Grayscale Check", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = normB.grayscale, onCheckedChange = { onUpdateNormB(normB.copy(enabled = true, grayscale = it)) })
    }
}

@Composable
private fun ExifDiffTab(
    state: PrismDiffUiState,
    onToggleMaskGps: (Boolean) -> Unit,
) {
    var diffOnly by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        StudioSectionHeader("EXIF & Encoding Comparison")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = diffOnly, onClick = { diffOnly = !diffOnly }, label = { Text("Diff Only") })
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
            Text("Mask GPS Coordinates", style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = state.preferences.maskGpsByDefault, onCheckedChange = onToggleMaskGps)
    }

    val rows = if (diffOnly) state.exifDiffRows.filter { it.differs } else state.exifDiffRows
    rows.forEach { entry ->
        val bg = if (entry.differs) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .border(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (!entry.deltaSummary.isNullOrBlank()) {
                    Text(entry.deltaSummary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A: ${entry.valueA}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("B: ${entry.valueB}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionsAndExportTab(
    state: PrismDiffUiState,
    onAddAnnotation: (AnnotationItem) -> Unit,
    onClearAnnotations: () -> Unit,
    onExportPngCollage: () -> Unit,
    onExportAnimatedGif: () -> Unit,
    onExportJsonReport: () -> Unit,
    onRequestRemoveUnselected: (permanent: Boolean) -> Unit,
    onStartTournament: () -> Unit,
) {
    StudioSectionHeader("Vector Callout Annotations", "Non-destructive overlays baked into exported collages")
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            onAddAnnotation(AnnotationItem("ann_${System.currentTimeMillis()}", AnnotationType.RECTANGLE, 0.28f, 0.28f, 0.72f, 0.72f))
        }) { Text("+ Focus Box") }
        OutlinedButton(onClick = {
            onAddAnnotation(AnnotationItem("ann_${System.currentTimeMillis()}", AnnotationType.CIRCLE, 0.5f, 0.5f, 0.68f, 0.5f, colorArgb = 0xFFFFB74DL))
        }) { Text("+ Target Circle") }
        OutlinedButton(onClick = {
            onAddAnnotation(AnnotationItem("ann_${System.currentTimeMillis()}", AnnotationType.ARROW, 0.2f, 0.2f, 0.45f, 0.45f, colorArgb = 0xFFFF5C8AL))
        }) { Text("+ Pointer Arrow") }
        TextButton(onClick = onClearAnnotations) { Text("Clear (${state.annotations.size})") }
    }

    HorizontalDivider()
    StudioSectionHeader("Burst Culling & Export Studio")
    Button(onClick = onStartTournament, modifier = Modifier.fillMaxWidth().testTag("start_elo_button")) {
        Text("Start / Next Elo Pairwise Burst Match")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExportPngCollage, modifier = Modifier.weight(1f).testTag("export_png_button")) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.padding(3.dp))
            Text("PNG Collage")
        }
        OutlinedButton(onClick = onExportAnimatedGif, modifier = Modifier.weight(1f).testTag("export_gif_button")) {
            Icon(Icons.Default.GifBox, contentDescription = null)
            Spacer(Modifier.padding(3.dp))
            Text("Wipe GIF")
        }
    }
    OutlinedButton(onClick = onExportJsonReport, modifier = Modifier.fillMaxWidth().testTag("export_json_button")) {
        Icon(Icons.Default.Share, contentDescription = null)
        Spacer(Modifier.padding(4.dp))
        Text("Export Analytical JSON Report")
    }

    HorizontalDivider()
    StudioSectionHeader("Space Cleanup (Safe Two-Stage Gate)")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onRequestRemoveUnselected(false) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
            modifier = Modifier.weight(1f).testTag("trash_unselected_button"),
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null)
            Spacer(Modifier.padding(3.dp))
            Text("Trash Unselected")
        }
        OutlinedButton(
            onClick = { onRequestRemoveUnselected(true) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrismDiffColors.PrismRed),
            modifier = Modifier.weight(1f).testTag("delete_unselected_button"),
        ) {
            Text("Permanent Delete")
        }
    }
}
