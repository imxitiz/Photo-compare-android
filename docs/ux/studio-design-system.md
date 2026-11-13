# PrismDiff Studio Design System & UX Architecture

## 1. Perceptual Darkroom Aesthetic
- **True OLED Dark Default (`#000000`) + Material You Dynamic Color**: Minimizes stray display luminance around images while preserving Android 12+ user accent harmony (`PrismDiffTheme.kt`).
- **Calibrated Canvas Backdrops**: Independent per-canvas surrounds (`OLED_BLACK`, `NEUTRAL_18_GRAY` `#777777` for unbiased color/exposure evaluation, `STUDIO_WHITE`, `CHECKERBOARD`, and `CUSTOM`).
- **Typography**: Locally bundled `Space Grotesk` (display/headers), `Plus Jakarta Sans` (body/UI), and `JetBrains Mono` (tabular technical metrics, EXIF deltas, and pixel hex probes).

## 2. Progressive Disclosure & Thumb-Zone Ergonomics
- **Edge-to-Edge Stage + 3-State Collapsible/Hideable Dock (`StudioDockVisibility`)**: Full-bleed edge-to-edge canvas with `WindowInsets(0)` on `StudioRoute` and top-row `.statusBarsPadding()` HUD clearance:
  - **Compact (`COMPACT`, default)**: Single slim bottom pill keeping expand/collapse, active mode, sync lock, one-tap **Reset View**, Inspector, and **Hide** directly reachable without scrolling, plus a contextual quick strip for Curtain Wipe angles (`0°`/`90°`/`45°`/`135°`/`50%`), Onion blend slider, or Blink Hz.
  - **Expanded (`EXPANDED`)**: Scroll-bounded drawer exposing dynamic 2–6 slot cards (`A`–`F`, reorder `<`/`>`, remove `×`, `+ Slot`), `MultiSlotLayout` placement chips (`Auto`, `Cols`, `Rows`, `Grid`, `2-Up`), all 11 comparison modes, alignment, transform, and King-of-the-Hill culling controls.
  - **Hidden (`HIDDEN` / Lights-Out)**: Hides both dock and bottom/rail navigation chrome for a pure 100% unobstructed canvas with only a subtle bottom-right `Controls` recovery pill.
- **Optical Dock Contrast**: The Compare dock automatically applies an optical dark surface palette whenever the canvas backdrop is dark (`OLED_BLACK`, `NEUTRAL_18_GRAY`, `CHECKERBOARD`, `CUSTOM`) so bright-white control cards never glare over dark photography even when the app theme is Light Mode.
- **4-Tab Pro Inspector**: Deep analytics (*Scopes*, *Forensics*, *EXIF Diff*, *Actions*) live inside a swipeable Material 3 `ModalBottomSheet` on phones and automatically dock as a `360.dp` persistent side pane on `Expanded` screens (`>= 840.dp`).

## 3. Adaptive Navigation & Accessibility
- **Configurable Startup Destination**: Users choose between *Studio Home*, *Collection Browser*, or *Direct Canvas* in Settings—never locked into one flow.
- **Window Size Adaptation**: Compact viewports use bottom `NavigationBar`; medium/expanded tablets and landscape screens switch to `NavigationRail`.
- **RTL & Safety Guarantees**: `Curtain Wipe` automatically mirrors drag direction in RTL layouts (`LayoutDirection.Rtl`), `Blink Strobe` warns on rates `> 3 Hz`, all primary interactive controls honor `>= 48.dp` touch targets, and destructive deletions enforce a two-stage preview + system Trash/Delete confirmation gate.
