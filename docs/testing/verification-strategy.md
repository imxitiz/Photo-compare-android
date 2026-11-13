# Verification & Testing Strategy

Because PrismDiff performs quantitative image forensics, geometric alignment, and safe file management offline on-device, verification combines mathematical unit tests with Robolectric Jetpack Compose Critical User Journey (CUJ) tests.

## 1. Domain & CV Math Unit Suites (`app/src/test/java/com/example/prismdiff/`)
- `ViewportSynchronizerTest`: Verifies SSOT `FIT_NORMALIZED` vs `PHYSICAL_1_TO_1` mixed-resolution scaling (12MP vs 48MP), Anchor Calibration relative delta preservation, unlocked slot isolation, `normalizedToScreen` <-> `screenToNormalized` geometric invertibility under rotation and mirror flip, and the 400% nearest-neighbor pixel-grid threshold.
- `ImageMetricsAndVisionTest`: Verifies deterministic SSIM (`1.0` identical, `< 0.98` contrast-softened), PSNR, MAE, CIEDE2000 `ΔE00`, Laplacian/Sobel sharpness ordering, 256-bin histogram conservation (`sum == pixelCount`), `AlignmentEngine` translation shift recovery, `dHash` Hamming clustering, and `EloTournamentEngine` updates.
- `ExifAndPresetTest`: Verifies directional EV deltas (`+1.00 EV` ISO), GPS coordinate privacy masking, custom HUD token formatting, natural alphanumeric filename sorting (`IMG_2.jpg` before `IMG_10.jpg`), `.prismdiff.json` round-trip/schema rejection, and `PixelMathFormulaEvaluator` AST sandboxing.

## 2. End-to-End Robolectric Compose CUJs
- `ExampleRobolectricTest`: Validates `R.string.app_name == "PrismDiff"`.
- `PrismDiffRobolectricTest`: Exercises cold launch into `StudioHomeScreen`, navigating to `StudioScreen`, switching to `Curtain Wipe` + toggling Sync Lock, navigating to `SettingsAndShaderLabScreen` to configure startup screen + verify invalid/valid Pixel-Math AST errors, and browsing `CollectionBrowserScreen` search & selection inversion.

## 3. Google Play Policy & Zero-Permission Audit
- Manifest contains zero broad storage permissions (`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`).
- All media access relies exclusively on Android Photo Picker (`PickMultipleVisualMedia`), Storage Access Framework (`OpenDocumentTree`), `ACTION_SEND`/`ACTION_SEND_MULTIPLE`, and `MediaStore.createTrashRequest`/`createDeleteRequest`.
