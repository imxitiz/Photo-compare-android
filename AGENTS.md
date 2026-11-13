# AGENTS.md — PrismDiff Architecture & Extension Guide

This file is the authoritative entry point for AI coding agents and human engineers working on **PrismDiff**.

## Non-Negotiable Engineering Invariants

1. **Zero Full-Resolution Heap Bloat**: Never load full-resolution images with `BitmapFactory.decodeFile()` or `SIZE_ORIGINAL`. Use `TilePyramidDecoder` (`BitmapRegionDecoder` + `BitmapPool`) for the comparison canvas and resolution-capped thumbnails for lists/grids.
2. **Unidirectional Viewport State**: Never attach mutual imperative pan/zoom listeners between image panes. All viewport math MUST flow through `ViewportSynchronizer` (`MasterViewportTransform` + `RelativeSlotOffset`).
3. **Never Mutate Original Files**: All alignment, normalization, cropping, annotations, and shader filters are non-destructive previews. Exports write new derivative files via `ComparisonExporter`. Deletions require explicit user confirmation via `MediaStore.createTrashRequest`, `createDeleteRequest`, or SAF `DocumentsContract`.
4. **Play Policy & Privacy**: Do not add `READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES` permissions. Use Android Photo Picker, SAF (`OpenDocumentTree`/`OpenMultipleDocuments`), and `ACTION_SEND`/`ACTION_SEND_MULTIPLE`. Keep GPS coordinates masked in UI/exports unless explicitly revealed.
5. **Explainable Metrics**: Every `AnalysisMetricReport` MUST include `algorithmName`, `scope`, `alignmentApplied`, `colorNormalized`, and `limitationNote`.
6. **UI & i18n Hygiene**: Never hardcode user-facing strings inside Composables; externalize all strings to `res/values/strings.xml`. Respect `LayoutDirection.Rtl` and `48.dp` minimum touch targets. Annotate state classes `@Immutable` / `@Stable`.

## Package Boundaries (`com.example.prismdiff.*`)

- `core.model` — Pure immutable domain models, configuration schemas (`WorkspacePreset` v1), and enums. Zero Android UI imports.
- `core.designsystem` — Material 3 Expressive OLED/Dynamic Color theme, Studio fonts, centralized spacing/elevation/motion tokens, and reusable Studio atoms.
- `core.decoder` — `TilePyramidDecoder`, `BitmapPool`, `MetadataExtractor`, JPEG DQT quality estimator, and `CalibrationSampleGenerator`.
- `core.vision` — Pure Kotlin deterministic CV: `ColorMath` (linear sRGB, CIE Lab, CIEDE2000), `ImageMetricsEngine` (SSIM, PSNR, MAE/RMSE, Histograms, Sharpness), `ForensicsEngine` (ELA, Noise Residual, Bit-Plane Slicer, Focus Peaking, Zebra, False Color), `AlignmentEngine` (Phase Correlation + Block Matching + RANSAC), `BurstClusteringEngine` (`dHash`), and `EloTournamentEngine`.
- `core.shader` — AGSL `RuntimeShader` library (API 33+), unified `TileComparisonRenderer` (API 24+ fallback), and sandboxed `PixelMathFormulaEvaluator`.
- `core.storage` — Room database (`PrismDiffDatabase`), DataStore (`UserPreferencesRepository`), `WorkspacePresetManager`, `MediaIngestionRepository`, and `ComparisonExporter` (PNG collage, diff map, LZW animated GIF, JSON report).
- `domain.viewport` — `ViewportSynchronizer` (SSOT normalized coordinate math and matrix projections).
- `feature.home`, `feature.browser`, `feature.studio`, `feature.settings` — Presentation screens, ViewModels (`StateFlow`), and adaptive Compose components.

## How to Add a New Comparison Mode

1. Add the enum/sealed entry in `core.model.ComparisonModels.kt`.
2. Add its localized label and description in `res/values/strings.xml`.
3. Implement its rendering strategy in `feature.studio` (or a new AGSL/tile pass in `core.shader`) consuming `PrismDiffUiState` and `ViewportSynchronizer` outputs.
4. Register its parameters in `WorkspacePreset` if configurable, and add unit/Robolectric tests.

## How to Run Verification

- Compile APK: `gradle :app:assembleDebug`
- Run Unit & Robolectric Tests: `gradle :app:testDebugUnitTest`
