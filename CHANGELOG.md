# Changelog

## [1.0.1] - 2026-11-12
### Fixed & Improved
- **Curtain Wipe Geometry & Drag**: Replaced stale-closure drag math with `CurtainGeometry` (Sutherland-Hodgman half-plane clipping for 0°/45°/90°/135°/360° cuts), two-tone optical divider line, double-tap 50% reset, and atomic `updateCurtainByDelta` so the wiper moves smoothly and never snaps back to 50%.
- **Pane Isolation & Zoomed Subsampling**: Added strict `.clipToBounds()` on every slot container and `DecodedTile` normalized subregion mapping so zoomed/rotated images never bleed across adjacent panes. Fixed double-tap zoom so low-res calibration images zoom in cleanly to >=300% instead of shrinking.
- **Full Edge-to-Edge + 3-State Collapsible/Hideable Studio Dock**: `StudioRoute` now renders full-bleed edge-to-edge behind system bars while keeping top HUD badges clear of the status bar. Redesigned `StudioControlDock` into `COMPACT` (with 1-tap `Reset View`), `EXPANDED`, and `HIDDEN` modes.
- **Multi-Slot Placement (`MultiSlotLayout`)**: Added `AUTO`, `COLUMNS`, `ROWS`, `GRID`, and `FOCUS_PAIR` placement switching plus per-slot add/remove/reorder controls for 3–6 images, and an interactive draggable split ratio divider for 2-Up comparisons.
- **Theme Contrast**: Fixed Hero Card gradient contrast in Light Mode and calibrated the Comparison Studio dock with an optical darkroom palette when viewing on dark/gray backdrops.

## [1.0.0] - 2026-11-12
### Added
- Multi-entry configurable launch architecture (Studio Home, Collection Browser, Direct Comparison Canvas).
- Studio OLED Dark + Material You Dynamic Color theme with Studio Light, 18% Neutral Gray, and Alpha Checkerboard canvas backdrops.
- SSOT `ViewportSynchronizer` with Fit-Normalized and Physical 1:1 Pixel Sync, per-slot `RelativeTransformOffset`, Anchor Calibration mode, and independent Zoom/Pan/Rotation/Crop sync toggles.
- `BitmapRegionDecoder` Tile-Pyramid decoder with LRU `BitmapPool` and 64× nearest-neighbor pixel grid + live RGB/HEX/HSL/CIE-L*a*b* pixel probe.
- Composable comparison modes: 2-Up Adaptive Split, Vertical Split, 3/4-Up Quad & Contact Sheet, Rotatable Curtain Wipe, Onion Skin & Blend Stack, Temporal Blink/Strobe with >3 Hz photosensitive guard, Synchronized Multi-Loupe PiP, and ROI Patch Matrix.
- Deterministic offline CV suite: Phase Correlation + Feature RANSAC Auto-Alignment, SSIM, PSNR, MAE/RMSE, CIEDE2000, Laplacian/Sobel Focus Peaking, Zebra Clipping, False Color IRE, RGB/Luminance/Parade/Vectorscope Histograms, Error Level Analysis (ELA), Noise Residual, Bit-Plane Slicer, JPEG DQT estimator, `dHash` burst grouping, and Elo/Swiss burst culling tournament.
- Non-destructive Fairness Normalization, Vector Annotations, Ken Burns inspection slideshow, PNG/GIF/JSON Export, safe MediaStore Trash/Delete requests, `.prismdiff.json` preset import/export, and Custom Pixel-Math Formula Sandbox.
