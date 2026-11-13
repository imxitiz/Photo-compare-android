# PrismDiff

**PrismDiff** is a 100% offline, native Kotlin and Jetpack Compose visual comparison, burst culling, and forensic analysis studio for Android.

## Core Principles

1. **95% Pixels, Progressive Depth**: Distraction-free OLED pitch-black or 18% neutral gray canvas by default, revealing forensic scopes, EXIF diffs, and custom shaders only when invoked.
2. **Single Source of Truth (SSOT) Viewport Sync**: All pan, zoom, and rotation flow from an immutable `MasterViewportTransform` combined with per-slot `RelativeTransformOffset` (`T_i = Master ⊕ Δ_i`), eliminating imperative listener-loop desyncs.
3. **Subsampled Tile Pyramid**: Powered by `BitmapRegionDecoder` + an LRU `BitmapPool` and automatic nearest-neighbor (`FilterQuality.None`) pixel-grid rendering above 400% zoom up to 64×.
4. **Explainable On-Device Computer Vision**: Every metric (SSIM, PSNR, CIEDE2000, Laplacian Focus Index, ELA, `dHash` clustering) computes locally and discloses what it measures, alignment/normalization flags, and limitations.
5. **User-Configurable Everything**: Configurable start screen (Studio Home, Collection Browser, or Direct Comparison Canvas), 11 built-in presets, importable/exportable `.prismdiff.json` workspaces, and an interactive sandboxed Pixel-Math Shader Lab.

## Quick Build & Test

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

See [AGENTS.md](AGENTS.md) and `docs/` for architecture blueprints, math specifications, and extension contracts.
