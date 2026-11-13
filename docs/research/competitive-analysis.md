# Competitive & Platform Research Summary (Baseline: November 13, 2026)

## 1. Reference Baseline: `sniederb/photocompare` (Photo Compare)
- **Verified Architecture**: Uses two `SubsamplingScaleImageView` instances coordinated by `CompareImagesActivity`, `CrossViewEventHandler`, and `PhotoViewMediator`, alongside Glide `.override(Target.SIZE_ORIGINAL)` inside a `ViewPager`.
- **Strengths Retained**: Clean 4-step mental model (pick folder/date -> choose pair -> compare with sync toggle -> mark selection & share/delete unselected), filename sort fallback for corrupted EXIF timestamps.
- **Architectural Bottlenecks Solved by PrismDiff**:
  1. Bidirectional listener muting (`copyPanAndZoom` disabling listeners in `finally`) replaced by SSOT `MasterViewportTransform + RelativeSlotOffset`.
  2. Unbounded full-bitmap heap allocation (`largeHeap="true"`) replaced by `BitmapRegionDecoder` region-tile pyramid + LRU `BitmapPool`.
  3. White `#FFFFFF` backdrop & opaque corner arrows replaced by OLED Dark / 18% Neutral Gray canvas and thumb-zone edge ergonomics.
  4. Manual EXIF text scanning replaced by Diff-First delta highlighting ($\Delta\text{EV}$, $\Delta\text{ISO}$, JPEG DQT quality).

## 2. Adjacent Mobile & Desktop Tools Evaluated
- **VES Image Compare (Android, GPL-3.0)**: Demonstrates value of Overlay Tap, Overlay Slide, Touch-Reveal brushing, Differences highlight overlay, Share-to-app ingestion, and zero-network privacy.
- **Image Comparator / Diffchecker / Kaleidoscope / Beyond Compare**: Demonstrate stacked layer blends, flicker blink comparison, pixel-level tolerance thresholds, and metadata diff tables.
- **Darktable / Lightroom / Capture One / RawTherapee / Forensic Tools**: Establish 18% neutral gray surroundings, Focus Peaking, Zebra clipping, False Color IRE, RGB Parade/Vectorscope, ELA, and non-destructive sidecar/preset reproducibility.

## 3. Research Confidence & Limitations
- All competitive behaviors listed above reflect publicly documented architectures and open-source code inspections as of November 2026.
- PrismDiff implements 100% deterministic on-device algorithms in Kotlin + AGSL/Canvas without third-party proprietary SDKs or cloud dependencies.
