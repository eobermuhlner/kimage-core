# kimage-astro-process: Improvement Analysis

Comparison against best-of-breed tools: **PixInsight**, **Siril**, **AstroPixelProcessor (APP)**, **DeepSkyStacker (DSS)**, **Startools**.

Ratings:
- **Impact**: how much typical astrophotographers benefit (High / Medium / Low)
- **Feasibility**: implementation effort relative to current architecture (High = easy / Medium = moderate / Low = hard)

---

## Current Capabilities Summary

| Category | kimage Status |
|---|---|
| Calibration (bias/dark/flat/darkflat) | ✓ Full pipeline |
| Dark frame scaling | ✓ Configurable multiplier |
| Hot/cold pixel cosmetic correction | ✓ |
| Debayering (10+ algorithms incl. AHD, AMaZE) | ✓ |
| Background level/color normalization | ✓ |
| Background gradient removal | ✓ RBF-based (basic) |
| Star detection + quad alignment | ✓ Affine transforms |
| Stacking: median, average, sigma-clip, winsorized | ✓ |
| Drizzle integration | ✓ With rejection |
| AutoSTF stretch | ✓ PixInsight-compatible |
| Linear percentile stretch | ✓ |
| Sigmoid stretch | ✓ |
| Noise reduction (multi-scale median) | ✓ Basic |
| Richardson-Lucy deconvolution | ✓ |
| Wiener filter deconvolution | ✓ |
| Unsharp mask / sharpening | ✓ |
| White balance (global/local/custom) | ✓ |
| Plate solving (ASTAP + internal Gaia DR3) | ✓ |
| WCS annotation (NGC catalog) | ✓ |
| Named sources / multi-stream pipeline | ✓ |
| Narrowband channel combination | ✓ |
| Star extraction branching | ✓ Basic |
| Star removal (simple subtraction) | ✓ `removeStars` step |
| LRGB/RGB/HSB decompose branching | ✓ |
| HDR combination (multi-exposure blend) | ✓ Basic |
| Quantize, edge enhance, rotate, crop | ✓ |

---

## Improvement Areas

### 1. Stretching / Tone Mapping

#### 1.1 ArcSinh Stretch
**Impact: High | Feasibility: High**

Hyperbolic arcsine stretch is standard in Siril and PixInsight. Unlike linear stretch, it preserves star color and shape while pulling up faint nebulosity. The formula is straightforward: `asinh(β·x) / asinh(β)`. It is the single most requested stretch mode after AutoSTF.

Status: DONE

#### 1.2 Generalized Hyperbolic Stretch (GHS)
**Impact: High | Feasibility: High**

GHS is the current state-of-the-art parametric stretch, available in both PixInsight (plugin) and Siril (native). Parameters D (intensity), b (stretch factor), LP/SP/HP (local/symmetry/highlight protection points) allow precise control over where the stretch is applied in the histogram. An open-source reference implementation exists (Python). Replaces trial-and-error sigmoid tuning.

Status: DONE

#### 1.3 Masked Stretch
**Impact: Medium | Feasibility: High**

PixInsight's MaskedStretch protects highlights (bright stars) from blowout during aggressive stretching by applying a luminance mask. Equivalent to: stretch only where pixel < threshold, blend elsewhere. Prevents the star core whitening that sigmoid stretch can cause.

Status: DONE

---

### 2. Noise Reduction

#### 2.1 TGV Denoising (Total Generalized Variation)
**Impact: High | Feasibility: Medium**

TGVDenoise is PixInsight's highest-quality noise reduction and widely considered the best algorithm available in any astrophotography tool. It is an edge-preserving variational method that removes noise while keeping fine detail and avoiding the "plastic" look of wavelet methods. Algorithm is published in academic literature (Bredies et al.). Requires implementing a PDE solver with iterative minimization — non-trivial but well-documented.

Status: DONE

#### 2.2 Improved Wavelet Noise Reduction
**Impact: Medium | Feasibility: High**

Current multi-scale median transform could be extended with:
- À trous wavelet decomposition (already related to MMT but with Gaussian kernels)
- Per-layer strength curves (not just threshold)
- Luminance-only mode with chrominance protection
- Support for applying only to background regions (star mask inversion)

These would bring the noise reduction much closer to PixInsight's MLT process without requiring TGV.

---

### 3. Background Extraction / Gradient Removal

#### 3.1 Polynomial Background Extraction (DBE-style)
**Impact: High | Feasibility: Medium**

The current RBF-based background removal works but lacks the precision of PixInsight's DBE (Dynamic Background Extraction). DBE fits a polynomial surface (degree 1–8) through user-placed sample points, enabling precise gradient removal across complex light pollution gradients. Key additions needed:
- Configurable polynomial degree
- Automatic sample grid generation (ABE-style) as a fast alternative
- Division mode (in addition to subtraction) for flat-field-like gradients
- Sigma-clipped samples to reject stars from fitting

#### 3.2 Large-Scale Gradient (Vignetting) Correction
**Impact: Medium | Feasibility: High**

Vignetting from optical systems is a radially symmetric gradient not fully handled by flat frames (sensor tilt, filter non-uniformity). A simple radial polynomial model fit to the background would cover the majority of cases and is a fast, robust complement to per-sample DBE.

---

### 4. Color Calibration

#### 4.1 Photometric Color Calibration (PCC)
**Impact: High | Feasibility: Medium**

PixInsight's PCC and Siril's photometric calibration use catalog star photometry (Gaia, APASS) combined with the plate-solved WCS to compute accurate R/G/B scale factors based on measured stellar colors. This gives physically meaningful white balance rather than heuristic median-based correction. Since kimage already has Gaia DR3 plate solving, the catalog infrastructure is in place. The additional step is matching detected stars to catalog entries and computing per-channel photometric ratios.

#### 4.2 Background Neutralization (Pre-calibration)
**Impact: Medium | Feasibility: High**

A dedicated step that sets the sky background to a neutral gray (equal R/G/B) using a user-specified or auto-detected background region. Currently, color neutralization is done during the normalize stage before stacking but is not available as an explicit enhancement step post-stack. Exposing it as an enhance step allows applying it after background extraction, which is the standard workflow order.

#### 4.3 Green Channel Equalization (SCNR)
**Impact: Medium | Feasibility: High**

One-shot-color (OSC/DSLR) cameras produce a persistent green cast after debayering due to the 2:1:1 Bayer green ratio. PixInsight's SCNR (Selective Color Noise Reduction) and Siril's green equalization correct this. Implementation: subtract the average of R and B from G where G > (R+B)/2, with configurable strength. Very simple, very useful for OSC workflows.

---

### 5. Calibration Improvements

#### 5.1 Dark Frame Optimization (Temperature/Exposure Scaling)
**Impact: Medium | Feasibility: High**

Current dark scaling uses a single configurable multiplier. True dark optimization scales darks by the ratio of light-to-dark exposure times and can further apply a temperature coefficient if sensor temperature metadata is available in FITS headers. PixInsight and APP both do this automatically. Implementation: extract `EXPTIME` and optionally `CCD-TEMP` from FITS headers, compute scale factor, apply before subtraction. The exposure-time part is straightforward; temperature scaling is optional.

#### 5.2 Bad Pixel Map Support
**Impact: Medium | Feasibility: High**

Currently hot/cold pixel correction is per-frame and threshold-based (cosmetic correction enhance step). A persistent bad pixel map generated from master dark/bias analysis (pixels that are consistently hot across all frames) would be more accurate and faster than per-frame detection. The map can be applied to every frame before calibration, similar to PixInsight's CosmeticCorrection process.

#### 5.3 Frame Quality Weighting for Stacking
**Impact: High | Feasibility: Medium**

All frames currently receive equal weight in stacking. PixInsight, Siril, and APP weight frames by measured quality metrics: FWHM, star eccentricity, background noise level, SNR. Frames with worse seeing or tracking errors contribute less. kimage already calculates FWHM per-star during alignment — this data could directly drive per-frame weights fed into the stacking algorithms (weighted average / weighted sigma-clip). High impact for unguided or variable-seeing sessions.

---

### 6. Stacking Improvements

#### 6.1 Linear Fit Clipping
**Impact: Medium | Feasibility: High**

A rejection algorithm available in PixInsight and Siril. Instead of comparing each pixel to the stack median, it fits a linear model of pixel values across frames and clips those that deviate significantly from the fitted trend. Outperforms sigma-clip for gradients or frame-to-frame brightness variations. Well-defined algorithm, straightforward to add alongside existing sigma-clip variants.

#### 6.2 CCDClip / Noise-Model-Aware Rejection
**Impact: Medium | Feasibility: Medium**

PixInsight's CCDClip uses the camera's read noise and gain to compute expected pixel variance, then clips statistical outliers based on that model rather than a fixed kappa. Requires gain/read noise metadata (can be extracted from FITS headers or configured). More precise than sigma-clip for low-frame-count stacks where the sample estimate of sigma is noisy.

#### 6.3 ESD Rejection (Generalized Extreme Studentized Deviate)
**Impact: Low | Feasibility: Medium**

A robust statistical test for outliers that handles masking of multiple outliers simultaneously. Used in PixInsight's ImageIntegration. Useful for stacks with few frames where sigma-clip breaks down, but lower priority than frame weighting or linear fit clipping.

---

### 7. Alignment Improvements

#### 7.1 Polynomial / Surface Spline Distortion Correction
**Impact: Medium | Feasibility: Medium**

Current alignment applies only affine transforms (translation, rotation, scale, shear). For wide-field imaging with significant optical distortion, affine registration leaves residual misalignment at the field edges. PixInsight and APP support polynomial warp (2nd–4th order) and thin-plate splines for field-distortion-corrected alignment. Requires storing more control points and a polynomial/RBF warp function during image resampling.

#### 7.2 Comet Registration
**Impact: Medium | Feasibility: Medium**

PixInsight, Siril, and DSS all support comet-mode alignment where the comet nucleus is tracked independently from the stars. This produces two stacks from the same frames: one star-aligned, one comet-aligned. The two are then combined. Implementation requires detecting and tracking a non-stellar moving target across frames.

---

### 8. Star Processing

#### 8.1 Star Reduction (Morphological Erosion)
**Impact: Medium | Feasibility: High**

A simple morphological erosion-based star size reduction step. Stars are identified (kimage already has this from alignment data), and a configurable reduction kernel shrinks them without affecting the nebula background. PixInsight uses its MorphologicalTransformation process for this; Startools has a dedicated Shrink module. Implementation: erosion followed by luminance mask blend to restrict application to stars only.

#### 8.2 Star Halo Reduction
**Impact: Medium | Feasibility: High**

Bloomed star halos can be reduced by a targeted unsharp mask or curve adjustment applied only inside a star mask with a feathered edge. This is a common intermediate step and could be exposed as an enhance step option within the existing masked processing branching framework.

#### 8.3 Star Removal Inpainting
**Impact: Medium | Feasibility: High→Low depending on algorithm**

The current `removeStars` step subtracts star pixels and leaves near-zero (black) holes in their place. For a standalone starless image this is visually problematic. Inpainting fills those holes with an estimate of the underlying background. Several algorithms exist at different quality/effort trade-offs:

| Algorithm | Quality | Effort | Notes |
|---|---|---|---|
| **Median Annulus Fill** | Good | Easy (~30 lines) | Samples a thin ring just outside the star ellipse; fills the hole with the ring median. Per-star, not iterative. Fast; visible flat patch on large stars. |
| **Iterative Erosion** | Good | Moderate (~80 lines) | Expands unmasked pixels inward pass-by-pass. Converges to a bilinear interpolant. Smooth, no seams. Iterations ≈ max star radius. |
| **Polynomial Surface Fit** | Very good | Moderate (~80 lines + least-squares) | Fits a 2D polynomial (plane or quadratic) through boundary pixels of each hole. Excellent on smooth sky gradients; cannot follow nebulosity curvature. |
| **RBF Interpolation** | Very good | Hard (~120 lines + matrix inversion) | Thin-plate spline through all boundary samples. Handles curved gradients well; one matrix solve per star. |
| **Fast Marching Method (Telea 2004)** | Excellent | Hard (~150 lines + priority queue) | Fills inward in wavefront order. Each pixel weighted average of filled neighbors by distance and gradient alignment. General-purpose; basis of OpenCV `INPAINT_TELEA`. |
| **Navier-Stokes / Isophote Propagation (Bertalmio 2000)** | Excellent | Very hard (~200 lines, PDE solver) | Propagates isophotes (equal-intensity lines) into the hole like fluid flow. Best when stars sit on top of visible structure (galaxy arm, dense nebula). |
| **Total Variation Inpainting** | Excellent | Very hard (proximal gradient / ADMM solver) | Edge-preserving fill via TV-norm minimization. Good for structured backgrounds; harder to implement than Telea. |
| **Exemplar-based / PatchMatch** | Best | Very hard (400+ lines) | Copies best-matching texture patches from elsewhere in the image. Overkill for most stars but useful for very large star cores over textured backgrounds. |

**Practical recommendation:** Median Annulus or Iterative Erosion cover 90% of astrophotography use cases. Telea FMM is the first step that would be noticeably better on difficult images and is near-professional quality.

The algorithm could be selected via a `removeStars.inpaint` parameter:
```yaml
- removeStars:
    factor: 2.0
    inpaint: erosion     # none | annulus | erosion | polynomial | rbf | telea
```

#### 8.4 AI Star Removal (StarNet-style)
**Impact: High | Feasibility: Low**

StarNet2 and APP's starless separation use trained convolutional neural networks to separate stars from background in a single pass, without requiring alignment star data. The output (starless + star layer) is then processed independently — a workflow already supported by kimage's extract-stars branching. However, implementing a neural network inference engine in Kotlin/JVM is a significant undertaking (requires ONNX Runtime or DL4J, plus a trained model). The trained StarNet2 model weights are open source.

---

### 9. Narrowband / Multi-Channel Processing

#### 9.1 PixelMath / Formula-Based Channel Arithmetic
**Impact: Medium | Feasibility: Medium**

PixInsight's PixelMath allows arbitrary per-pixel formulas like `0.7*Ha + 0.3*OIII` or `iif(R > 0.5, Ha, OIII)`. This is how Hubble palette (SHO), HOO, and Foraxx palettes are composed. kimage's composite channels step handles fixed R/G/B source assignment but not arbitrary weighting or blending. Adding a simple expression evaluator (parsing `Ha`, `OIII`, `SII`, `R`, `G`, `B`, `L` as named source references with standard arithmetic) would cover 95% of narrowband palette use cases.

#### 9.2 LRGB Combination Improvements
**Impact: Medium | Feasibility: High**

The current LRGB decompose step can separate and recombine luminance and color, but it lacks:
- Chrominance noise reduction before recombination (CCD color data has worse SNR than luminance)
- Configurable luminance blending weight (how much of the L replaces vs. blends with the RGB luminance)
- Saturation boost compensation after luminance injection (LRGB typically desaturates)

These are the standard adjustments made in PixInsight's LRGBCombination process.

---

### 10. Advanced / High-Effort Features

#### 10.1 HDR Multiscale Transform
**Impact: Medium | Feasibility: Medium**

PixInsight's HDRMultiscaleTransform compresses dynamic range on a per-scale basis using wavelet layers, preserving fine structure at all brightness levels. Useful for galaxies with a bright core adjacent to faint outer arms. The current HDR combination step blends entire exposure stacks, which is coarser. A multiscale approach requires wavelet decomposition per-layer compression and reconstruction — non-trivial but well-documented.

#### 10.2 Banding / Column Pattern Noise Removal
**Impact: Medium | Feasibility: Medium**

CMOS and some CCD cameras exhibit periodic horizontal or vertical banding (fixed-pattern noise not removed by bias/dark subtraction). Startools' Band module and Siril's banding correction address this with frequency-domain analysis (FFT) to identify and subtract periodic patterns. Useful for Canon DSLRs and many cooled CMOS cameras. Implementation requires a 1D FFT per row/column, identifying dominant frequencies, and subtracting the fitted pattern.

#### 10.3 Satellite Trail / Plane Trail Auto-Rejection
**Impact: Medium | Feasibility: Medium**

Currently, satellite trails are removed by stacking rejection if enough frames are available. Dedicated trail detection (Hough transform for line detection) could flag trails per-frame before stacking, making rejection more reliable even with few frames. APP does automatic trail detection during integration.

#### 10.4 GPU Acceleration
**Impact: High | Feasibility: Low**

All processing is CPU-based. For stacking hundreds of large FITS frames (30+ MP), GPU acceleration via OpenCL or CUDA would dramatically reduce processing time. The JVM ecosystem has TornadoVM and Aparapi for GPU offloading, but retrofitting the Matrix/Image abstraction layer is a major architectural effort. Most impactful for drizzle integration and convolution operations.

#### 10.5 Python / External Scripting API
**Impact: Medium | Feasibility: Medium**

Siril's siril-py Python bindings allow automation and scripting beyond what YAML configuration provides. kimage could expose a gRPC or socket-based API to allow scripting the pipeline from Python or other languages. Alternatively, exposing the core library as a Kotlin/Java API with clear entry points (without the CLI layer) would enable integration into larger automated processing workflows.

#### 10.6 Mosaic / Panel Stitching Support
**Impact: Low | Feasibility: Low**

PixInsight and APP support multi-panel mosaics: plate solving each panel, computing the relative WCS offsets, and stitching with gradient-corrected seams. This requires a projective alignment model rather than simple affine transforms, and a gradient-aware blending step at panel boundaries. Complex but increasingly common as astrophotographers shoot large sky areas.

---

## Priority Matrix

| Improvement | Impact | Feasibility | Priority |
|---|---|---|---|
| GHS (Generalized Hyperbolic Stretch) | High | High | **P1** |
| ArcSinh Stretch | High | High | **P1** |
| Frame Quality Weighting for Stacking | High | Medium | **P1** |
| TGV Denoising | High | Medium | **P1** |
| Photometric Color Calibration (PCC) | High | Medium | **P1** |
| Polynomial Background Extraction (DBE-style) | High | Medium | **P1** |
| Dark Frame Optimization (exposure scaling) | Medium | High | **P2** |
| Bad Pixel Map Support | Medium | High | **P2** |
| Green Channel Equalization (SCNR) | Medium | High | **P2** |
| Linear Fit Clipping (stacking) | Medium | High | **P2** |
| Improved Wavelet Noise Reduction | Medium | High | **P2** |
| Masked Stretch | Medium | High | **P2** |
| Star Reduction (morphological) | Medium | High | **P2** |
| LRGB Combination Improvements | Medium | High | **P2** |
| Background Neutralization enhance step | Medium | High | **P2** |
| PixelMath / Formula Channel Arithmetic | Medium | Medium | **P3** |
| Surface Spline Distortion Correction | Medium | Medium | **P3** |
| Banding / Pattern Noise Removal | Medium | Medium | **P3** |
| CCDClip / Noise-Model Rejection | Medium | Medium | **P3** |
| Comet Registration | Medium | Medium | **P3** |
| Large-Scale Vignetting Correction | Medium | High | **P3** |
| HDR Multiscale Transform | Medium | Medium | **P3** |
| Star Halo Reduction | Medium | High | **P3** |
| Star Removal: Median Annulus Inpainting | Medium | High | **P3** |
| Star Removal: Iterative Erosion Inpainting | Medium | High | **P3** |
| Star Removal: Polynomial Surface Fit Inpainting | Medium | High | **P3** |
| Star Removal: RBF Inpainting | Medium | Medium | **P3** |
| Star Removal: Fast Marching Method (Telea) | Medium | Medium | **P3** |
| Star Removal: Navier-Stokes Inpainting | Medium | Low | **P4** |
| Star Removal: PatchMatch / Exemplar Inpainting | Medium | Low | **P4** |
| Satellite Trail Auto-Rejection | Medium | Medium | **P3** |
| Python / External Scripting API | Medium | Medium | **P3** |
| ESD Rejection | Low | Medium | **P4** |
| AI Star Removal (StarNet-style) | High | Low | **P4** |
| GPU Acceleration | High | Low | **P4** |
| Mosaic / Panel Stitching | Low | Low | **P4** |

---

## What kimage Does Well (vs. Peers)

- **Drizzle integration** — on par with PixInsight and APP; most tools don't include this
- **Flexible branching pipeline** — extract-stars, decompose, masked processing, named sources are more composable than any competing open-source tool
- **Multi-stream narrowband** — named sources + composite channels covers narrowband workflows that Siril and DSS lack
- **Plate solving** — both ASTAP and internal Gaia DR3 solver, better integrated than DSS/Startools
- **YAML-based reproducibility** — full pipeline state in a single config file; re-runs are exact; no GUI-click workflow to remember
- **Debayering algorithm breadth** — more debayer options than any peer (AHD, AMaZE, VNG, PPG, etc.)
- **Stacking memory management** — tile-based + memory-mapped large-stack support avoids OOM on big datasets
