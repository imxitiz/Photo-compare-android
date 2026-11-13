# System Overview

PrismDiff separates image presentation, viewport math, decoding, computer vision, shader compositing, and storage behind explicit unidirectional contracts:

1. **Presentation (`feature.*`)**: Pure Compose Material 3 UI consuming `@Immutable` `StateFlow` states. No business logic or direct bitmap decoding inside Composables.
2. **Viewport SSOT (`domain.viewport`)**: Pure Kotlin math (`ViewportSynchronizer`) mapping normalized master coordinates + per-slot alignment offsets into screen transforms.
3. **Decoding & Metadata (`core.decoder`)**: `BitmapRegionDecoder` tile pyramid + LRU `BitmapPool` + `ExifInterface` + JPEG SOF/DQT quantization parser.
4. **Deterministic CV (`core.vision`)**: Pure Kotlin sRGB linearization, CIEDE2000, SSIM, PSNR, MAE/RMSE, Laplacian sharpness, ELA, phase correlation + RANSAC alignment, `dHash` burst clustering, and Elo tournament culling.
5. **Hardware & Fallback Shaders (`core.shader`)**: API 33+ AGSL `RuntimeShader` pipelines paired with an exact API 24+ Kotlin/Canvas processor and sandboxed AST formula evaluator.
6. **Storage & Safety (`core.storage`)**: Room (`KSP`) session/culling persistence, DataStore preferences, Photo Picker + SAF ingestion, `.prismdiff.json` v1 presets, PNG/GIF/JSON exporters, and system-confirmed Trash/Delete.
