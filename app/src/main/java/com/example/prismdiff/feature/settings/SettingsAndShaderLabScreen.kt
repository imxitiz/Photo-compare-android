package com.example.prismdiff.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.prismdiff.core.designsystem.PrismDiffColors
import com.example.prismdiff.core.designsystem.PrismDiffTokens
import com.example.prismdiff.core.designsystem.StudioSectionHeader
import com.example.prismdiff.core.model.AppLaunchScreen
import com.example.prismdiff.core.model.AppThemeMode
import com.example.prismdiff.core.model.CanvasBackdropMode
import com.example.prismdiff.core.model.ComparisonMode
import com.example.prismdiff.core.model.CustomShaderDefinition
import com.example.prismdiff.core.model.WorkspacePreset
import com.example.prismdiff.core.shader.PixelMathFormulaEvaluator
import com.example.prismdiff.feature.studio.PrismDiffUiState
import com.example.prismdiff.feature.studio.PrismDiffViewModel

@Composable
fun SettingsAndShaderLabScreen(
    state: PrismDiffUiState,
    viewModel: PrismDiffViewModel,
    onOpenShaderInStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    val clipboard = LocalClipboardManager.current

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showImportPresetDialog by remember { mutableStateOf(false) }
    var hudDraft by remember(prefs.hudTemplate) { mutableStateOf(prefs.hudTemplate) }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("settings_lazy_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        }

        // 1. Configurable Launch Screen (Zero hardcoding!)
        item {
            SettingsCard {
                StudioSectionHeader(
                    title = stringResource(R.string.settings_launch_screen),
                    subtitle = "Choose which workspace opens immediately on cold app launch",
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val options = listOf(
                        AppLaunchScreen.STUDIO_HOME to "Studio Home",
                        AppLaunchScreen.COLLECTION_BROWSER to "Folder / Picker",
                        AppLaunchScreen.DIRECT_CANVAS to "Direct Canvas",
                    )
                    options.forEach { (screen, label) ->
                        FilterChip(
                            selected = prefs.launchScreen == screen,
                            onClick = { viewModel.setLaunchScreen(screen) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("launch_screen_chip_${screen.name.lowercase()}"),
                        )
                    }
                }
            }
        }

        // 2. Theme, Dynamic Material You & Perceptual Backdrop
        item {
            SettingsCard {
                StudioSectionHeader(stringResource(R.string.settings_theme))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.name.replace('_', ' ')) },
                            modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}"),
                        )
                    }
                }
                SwitchRow(
                    label = "Material You Dynamic Color (Android 12+)",
                    checked = prefs.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                HorizontalDivider()
                StudioSectionHeader(stringResource(R.string.settings_backdrop), "Affects perceptual color & edge contrast")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasBackdropMode.entries.forEach { bg ->
                        FilterChip(
                            selected = prefs.canvasBackdrop == bg,
                            onClick = { viewModel.setCanvasBackdrop(bg) },
                            label = { Text(bg.name.replace('_', ' ')) },
                        )
                    }
                }
            }
        }

        // 3. Interactive Custom Pixel-Math Shader Lab
        item {
            SettingsCard {
                StudioSectionHeader(
                    title = stringResource(R.string.settings_shader_lab),
                    subtitle = "Sandboxed AST pixel math expression (rA,gA,bA,lA, rB,gB,bB,lB, p1,p2,p3)",
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelMathFormulaEvaluator.builtInTemplates.forEach { tpl ->
                        FilterChip(
                            selected = state.customShader.id == tpl.id,
                            onClick = { viewModel.updateCustomShader(definition = tpl) },
                            label = { Text(tpl.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.customShader.expression,
                    onValueChange = { viewModel.updateCustomShader(expression = it) },
                    label = { Text("Formula Expression") },
                    isError = state.customShaderError != null,
                    supportingText = {
                        Text(
                            text = state.customShaderError ?: "Valid AST formula • Variables: rA,gA,bA,lA,rB,gB,bB,lB,p1,p2,p3",
                            color = if (state.customShaderError != null) PrismDiffColors.PrismRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("shader_formula_input"),
                )
                Text("${state.customShader.param1Label} (p1): ${String.format("%.2f", state.customParam1)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = state.customParam1, onValueChange = { viewModel.updateCustomShader(p1 = it) }, valueRange = 0.5f..25f)
                Text("${state.customShader.param2Label} (p2): ${String.format("%.2f", state.customParam2)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = state.customParam2, onValueChange = { viewModel.updateCustomShader(p2 = it) }, valueRange = 0f..1f)
                Text("${state.customShader.param3Label} (p3): ${String.format("%.2f", state.customParam3)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = state.customParam3, onValueChange = { viewModel.updateCustomShader(p3 = it) }, valueRange = 0f..1f)

                Button(
                    onClick = {
                        viewModel.setComparisonMode(ComparisonMode.CUSTOM_SHADER)
                        onOpenShaderInStudio()
                    },
                    enabled = state.customShaderError == null,
                    modifier = Modifier.fillMaxWidth().testTag("test_shader_in_studio_button"),
                ) {
                    Text("Run Custom Shader in Studio")
                }
            }
        }

        // 4. Open Extensibility: JSON Workspace Presets & Custom HUD Template
        item {
            SettingsCard {
                StudioSectionHeader(
                    title = stringResource(R.string.settings_presets_json),
                    subtitle = "Save, export, or import versioned `.prismdiff.json` studio configurations",
                )
                OutlinedTextField(
                    value = hudDraft,
                    onValueChange = {
                        hudDraft = it
                        viewModel.setHudTemplate(it)
                    },
                    label = { Text("Custom Canvas HUD Template") },
                    supportingText = { Text("Tokens: {FileName} {Camera} {FocalLength} {Aperture} {Shutter} {ISO} {Quality} {BPP} {SizeMB}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showSavePresetDialog = true },
                        modifier = Modifier.weight(1f).testTag("save_preset_button"),
                    ) { Text("Save Current") }
                    OutlinedButton(
                        onClick = { showImportPresetDialog = true },
                        modifier = Modifier.weight(1f).testTag("import_preset_button"),
                    ) { Text("Import JSON") }
                }
                OutlinedButton(
                    onClick = {
                        val active = state.presets.find { it.id == prefs.activePresetId } ?: state.presets.first()
                        val raw = viewModel.exportPresetJson(active)
                        clipboard.setText(AnnotatedString(raw))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy Active Preset JSON to Clipboard")
                }
            }
        }

        // 5. Privacy, Accessibility & Ergonomics
        item {
            SettingsCard {
                StudioSectionHeader(stringResource(R.string.settings_privacy_ergonomics))
                SwitchRow("Mask GPS Coordinates by Default", prefs.maskGpsByDefault, viewModel::setMaskGps)
                SwitchRow("Volume Keys Step Burst Candidate", prefs.volumeKeyNavigation, viewModel::setVolumeKeyNavigation)
                SwitchRow("Keep Screen On in Studio", prefs.keepScreenOnInStudio, viewModel::setKeepScreenOn)
                SwitchRow("Photosensitive Blink >3 Hz Guard", prefs.photosensitiveGuardEnabled, viewModel::setPhotosensitiveGuard)
                SwitchRow("Pixel Grid & Nearest-Neighbor >=400%", prefs.showPixelGridAbove400, viewModel::setShowPixelGrid)
            }
        }
    }

    if (showSavePresetDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Studio Workspace Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveCurrentAsPreset(name, desc)
                    showSavePresetDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showImportPresetDialog) {
        var rawJson by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportPresetDialog = false },
            title = { Text("Import .prismdiff.json Preset") },
            text = {
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = { rawJson = it },
                    label = { Text("Paste schemaVersion 1 JSON") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.importPresetJson(rawJson)
                    showImportPresetDialog = false
                }) { Text("Validate & Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportPresetDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
