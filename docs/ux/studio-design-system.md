# PrismDiff Studio Design System & UX Architecture

## 1. Perceptual Darkroom Aesthetic
- **True OLED Dark Default (`#000000`) + Material You Dynamic Color**: Minimizes stray display luminance around images while preserving Android 12+ user accent harmony (`PrismDiffTheme.kt`).
- **Calibrated Canvas Backdrops**: Independent per-canvas surrounds (`OLED_BLACK`, `NEUTRAL_18_GRAY` `#777777` for unbiased color/exposure evaluation, `STUDIO_WHITE`, `CHECKERBOARD`, and `CUSTOM`).
- **Typography**: Locally bundled `Space Grotesk` (display/headers), `Plus Jakarta Sans` (body/UI), and `JetBrains Mono` (tabular technical metrics, EXIF deltas, and pixel hex probes).

## 2. Progressive Disclosure & Thumb-Zone Ergonomics
- **Zero-Clutter Stage**: Full-bleed image stage with floating translucent bottom dock (`StudioControlDock`) keeping mode switching, Sync Lock, 1:1 pixel jump, King-of-the-Hill pinning (`Pin Ref A`, `Promote B→A`), and quick Keep/Favorite/Reject controls inside comfortable thumb reach.
- **Lights-Out Mode**: Single-tap immersive hide of all navigation chrome and HUD overlays with a subtle bottom-right recovery pill.
- **4-Tab Pro Inspector**: Deep analytics (*Scopes*, *Forensics*, *EXIF Diff*, *Actions*) live inside a swipeable Material 3 `ModalBottomSheet` on phones and automatically dock as a `360.dp` persistent side pane on `Expanded` screens (`>= 840.dp`).

## 3. Adaptive Navigation & Accessibility
- **Configurable Startup Destination**: Users choose between *Studio Home*, *Collection Browser*, or *Direct Canvas* in Settings—never locked into one flow.
- **Window Size Adaptation**: Compact viewports use bottom `NavigationBar`; medium/expanded tablets and landscape screens switch to `NavigationRail`.
- **RTL & Safety Guarantees**: `Curtain Wipe` automatically mirrors drag direction in RTL layouts (`LayoutDirection.Rtl`), `Blink Strobe` warns on rates `> 3 Hz`, all primary interactive controls honor `>= 48.dp` touch targets, and destructive deletions enforce a two-stage preview + system Trash/Delete confirmation gate.
