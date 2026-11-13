# ADR-002: Tiered AGSL RuntimeShader (API 33+) with Deterministic Kotlin Tile Fallback (API 24+)

## Context
PrismDiff targets `minSdk = 24` through `targetSdk = 36`. AGSL `RuntimeShader` delivers hardware-accelerated pixel-level comparison and custom shader formulas on Android 13+ (API 33+), but referencing `android.graphics.RuntimeShader` unconditionally crashes on API < 33.

## Decision
Isolate all `RuntimeShader` creation and uniform binding behind `@RequiresApi(Build.VERSION_CODES.TIRAMISU)` guards in `core.shader.AgslShaderLibrary`, and implement an identical mathematical `TileComparisonRenderer` fallback in pure Kotlin/Canvas for API 24–32 and JVM unit tests.

## Trade-offs
- **Pros**: Zero runtime crashes on older Android phones, identical deterministic formulas across GPU and CPU paths, and full JVM testability without requiring an emulator GPU.
- **Cons**: Real-time custom formula updates on API < 33 run on subsampled working buffers via `Dispatchers.Default` instead of GPU fragments.
