# Open Extensibility, Presets & Custom Pixel-Math Shader Lab

PrismDiff is designed as a configurable, open platform rather than a rigid viewer. Advanced workflows can be built, saved, exported, and shared without modifying Kotlin source code.

## 1. Versioned JSON Workspace Presets (`.prismdiff.json`)
`WorkspacePreset` (`schemaVersion = 1`) captures every dimension of a studio workspace:
- `comparisonMode`, `splitOrientation`, `scaleSyncMode`, `syncFlags`
- `backdropMode` (`OLED_BLACK`, `NEUTRAL_18_GRAY`, `STUDIO_WHITE`, `CHECKERBOARD`, `CUSTOM`)
- `blendOperator`, `differenceMode`, `diffPalette`, `diagnosticOverlay`, `channelIsolation`
- `dividerThicknessDp`, `strobeFrequencyHz`, `infoDensity`, `showHudPills`, `showHistogramHud`
- `hudTemplate` and optional embedded `customShader`

Validation (`WorkspacePresetManager.importFromJson`) enforces:
1. Supported `schemaVersion` range.
2. Non-blank preset name.
3. Safe clamping of strobe rate (`0.5f..10f`) and divider thickness (`0.5f..6f`).
4. Full AST validation of any embedded custom shader expression before saving to Room.

## 2. Sandboxed Custom Pixel-Math Shader Lab
`PixelMathFormulaEvaluator` parses user-entered mathematical expressions into a typed `AstNode` tree without reflection, scripting engines, or dynamic code loading:
- **Inputs**: `rA, gA, bA, lA` (Slot A channels + Rec.709 luminance in `[0..1]`), `rB, gB, bB, lB` (Slot B), and interactive sliders `p1, p2, p3`.
- **Operators**: `+`, `-`, `*`, `/`, `^`, unary `-`, parentheses.
- **Built-in Functions**: `abs`, `sqrt`, `sin`, `cos`, `min`, `max`, `clamp`, `step`, `smoothstep`.
- **Built-in Templates**: *Chroma Key Diff*, *Luma Denoise Evaluator*, *Highlight Recovery Inspector*, and *Midtone Contrast Delta*.

## 3. Custom Canvas HUD Templates
Users can customize canvas metadata pills via token substitution (`ExifDiffEngine.formatHudTemplate`):
`{FileName}`, `{Folder}`, `{Resolution}`, `{MP}`, `{SizeMB}`, `{Aperture}`, `{Shutter}`, `{ISO}`, `{FocalLength}`, `{Camera}`, `{Quality}`, `{BPP}`.
