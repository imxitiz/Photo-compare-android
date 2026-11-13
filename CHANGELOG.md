# Changelog

## [1.0.0] - 2026-11-12
### Added
- Multi-entry configurable launch architecture (Studio Home, Collection Browser, Direct Comparison Canvas).
- Studio OLED Dark + Material You Dynamic Color theme with Studio Light, 18% Neutral Gray, and Alpha Checkerboard canvas backdrops.
- SSOT `ViewportSynchronizer` with Fit-Normalized and Physical 1:1 Pixel Sync, per-slot `RelativeTransformOffset`, Anchor Calibration mode, and independent Zoom/Pan/Rotation/Crop sync toggles.
- `BitmapRegionDecoder` Tile-Pyramid decoder with LRU `BitmapPool` and 64× nearest-neighbor pixel grid + live RGB/HEX/HSL/CIE-L*a*b* pixel probe.
- Composable comparison modes: 2-Up Adaptive Split, Vertical Split, 3/4-Up Quad & Contact Sheet, Rotatable Curtain Wipe, Onion Skin & Blend Stack, Temporal Blink/Strobe with >3 Hz photosensitive guard, Synchronized Multi-Loupe PiP, and ROI Patch Matrix.
- Deterministic offline CV suite: Phase Correlation + Feature RANSAC Auto-Alignment, SSIM, PSNR, MAE/RMSE, CIEDE2000, Laplacian/Sobel Focus Peaking, Zebra Clipping, False Color IRE, RGB/Luminance/Parade/Vectorscope Histograms, Error Level Analysis (ELA), Noise Residual, Bit-Plane Slicer, JPEG DQT estimator, `dHash` burst grouping, and Elo/Swiss burst culling tournament.
- Non-destructive Fairness Normalization, Vector Annotations, Ken Burns inspection slideshow, PNG/GIF/JSON Export, safe MediaStore Trash/Delete requests, `.prismdiff.json` preset import/export, and Custom Pixel-Math Formula Sandbox.
