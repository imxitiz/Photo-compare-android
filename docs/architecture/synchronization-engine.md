# Synchronization Engine Mathematical Specification

## Single Source of Truth Model

All viewports observe a single immutable `MasterViewportTransform`:

$$M = (u_c, v_c, z, \theta, \text{syncMode})$$

where:
- $(u_c, v_c) \in [0, 1]^2$ is the normalized image center currently aligned with the viewport center.
- $z \in [0.25, 64.0]$ is the zoom multiplier relative to the chosen `ScaleSyncMode`.
- $\theta \in [0, 360)$ is the master rotation in degrees.

Each image slot $i$ maintains an independent `RelativeTransformOffset`:

$$\Delta_i = (\Delta u_i, \Delta v_i, \Delta z_i, \Delta \theta_i, \text{flipH}_i, \text{flipV}_i)$$

The effective transform for slot $i$ is computed purely as:

$$T_i = \begin{cases}
(u_c + \Delta u_i, \; v_c + \Delta v_i, \; z \cdot \Delta z_i, \; (\theta + \Delta \theta_i) \bmod 360) & \text{if sync active} \\
\text{independent slot state} & \text{if sync unlocked for channel}
\end{cases}$$

## Scale Mapping Modes
1. **Fit-Normalized (`FIT_NORMALIZED`)**: $z = 1.0$ fits each image inside its container bounds regardless of megapixel count (ideal for 12MP binned vs 50MP full-res comparison).
2. **Physical 1:1 Pixel (`PHYSICAL_1_TO_1`)**: Scale derives from `imageWidth * z / containerWidth`, matching physical pixel density across sensors.
3. **Viewport Percentage (`VIEWPORT_PERCENT`)**: Matches normalized horizontal/vertical frame coverage.
4. **Reference Scale (`REFERENCE_SCALE`)**: Locks scaling relative to the pinned Reference slot (Slot A).

## Pixel-Peeping Threshold
When effective screen-pixels-per-image-pixel $\ge 4.0$ ($400\%$ zoom), the renderer switches automatically from bilinear filtering to **`FilterQuality.None` (Nearest-Neighbor)** and enables optional subpixel grid hairlines and the `(x, y) -> RGB / HEX / HSL / Lab` probe.

## Curtain Wipe Geometry & Stationary Drag (`CurtainGeometry`)
- **Any-Angle Slicing**: `CurtainGeometry.computeSlice` intersects the viewport rectangle with the half-plane $(x - x_0)\cos\theta + (y - y_0)\sin\theta \le 0$ via Sutherland-Hodgman polygon clipping, cleanly supporting vertical (`0°`), horizontal (`90°`), and diagonal (`45°`/`135°`) wipes plus automatic RTL mirroring.
- **Stationary Pointer Interceptor**: The parent stage captures `PointerEventPass.Initial` drag deltas once movement exceeds `0.35 * touchSlop` near the cut segment or handle pill, feeding projected `dragDeltaToRatio` updates into `StateFlow` without moving underlying image viewports or causing handle oscillation.

## Multi-Slot Isolation & Layout Placement (`MultiSlotLayout`)
Every comparison pane enforces strict `.clipToBounds()` isolation so zoomed or rotated images never bleed into neighboring slots. Up to 6 simultaneous slots can be reordered, removed, or arranged via `AUTO`, `COLUMNS`, `ROWS`, `GRID`, or `FOCUS_PAIR`, while 2-up splits include a draggable `splitRatio` divider.

