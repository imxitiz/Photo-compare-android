# Image Processing Fundamentals & Metric Limitations

## 1. Color Spaces & Linearization
- Standard Android bitmaps commonly decode into gamma-encoded **sRGB** (IEC 61966-2-1) or wide-gamut **Display P3**.
- Computing luminance, SSIM, or Euclidean RGB differences directly on gamma-encoded integers compresses shadow errors and exaggerates midtone shifts.
- PrismDiff linearizes sRGB components ($c_{lin} = c/12.92$ if $c \le 0.04045$, else $((c + 0.055)/1.055)^{2.4}$) prior to **CIE XYZ (D65) -> CIE $L^*a^*b^*$** and **CIEDE2000 ($\Delta E_{00}$)** calculations.

## 2. Explicit Limitations of Every Analytical Metric
- **Absolute Pixel Difference / MAE / RMSE**: Measures numerical sample distance, NOT perceptual quality. A 1-pixel camera shake or slight exposure shift yields high MAE despite identical sharpness.
- **PSNR (Peak Signal-to-Noise Ratio)**: Logarithmic MSE ratio useful for comparing compression loss (`Q95` vs `Q75`) on *aligned* frames, meaningless across unaligned or exposure-shifted shots.
- **SSIM (Structural Similarity Index)**: Evaluates local luminance, contrast, and structure windows ($8\times 8$). Sensitive to spatial misalignment; requires registration first.
- **CIEDE2000 ($\Delta E_{00}$)**: Measures perceptual color shift ($\Delta E < 1.0$ imperceptible; $1–2$ subtle; $>5$ clear color divergence), assuming accurate color-profile decoding.
- **Laplacian Variance / Sobel Edge Energy**: Estimates local high-frequency contrast. High sensor noise or artificial oversharpening increases raw Laplacian energy without true optical detail—always pair visual inspection with the Noise Residual view.
- **Error Level Analysis (ELA)**: Re-saves at target JPEG quality $Q$ and amplifies DCT quantization residuals. Highlights regions with differing compression histories; uniform flat skies naturally show low ELA values even in authentic photos, and multiple re-saves flatten ELA contrast.
