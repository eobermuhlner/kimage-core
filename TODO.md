# TODO - Gaps in Astro Processing Solution

This document outlines the significant gaps between the current implementation and a complete astrophotography solution.

## Priority 1 - Essential Gaps

### 1.1 Output: FITS Writing
- **Status**: Reads FITS but cannot write FITS format
- **Impact**: Intermediate steps lose metadata; users must use external formats
- **Files**: `core.image.io.ImageWriter` - no FITS writer implementation

### 1.2 Deconvolution
- **Status**: No deconvolution algorithm implemented
- **Impact**: Cannot restore resolution degraded by seeing/optics
- **Needed**: Richardson-Lucy or Wiener filter deconvolution
- **Files**: Would need new `EnhanceDeconvolve.kt`

### 1.3 Field Flattening / DBE
- **Status**: `removeBackground` uses naive interpolation, not true DBE
- **Impact**: Cannot handle complex light pollution gradients
- **Files**: `astro.background.Interpolate.kt` - needs DBE implementation

### 1.4 Hot Pixel / Cosmetic Correction
- **Status**: Implemented in enhance pipeline
- **Impact**: Hot/cold pixels automatically removed
- **Files**: `astro/cosmetic/CosmeticCorrection.kt`

## Priority 2 - Important Features

### 2.1 Advanced Stacking
- **Local Rejection**: No cosmic ray/clatellite rejection per-pixel
- **Drizzle**: No drizzle integration for better resolution
- **Star Protection**: No star vs. nebula region-aware integration

### 2.2 Lens Distortion Correction
- **Status**: Only affine transformation (translation/rotation/scale)
- **Impact**: Cannot correct for optical distortion in wide-field
- **Files**: `astro.align.AlignStars.kt` - would need distortion model

### 2.3 Sub-pixel Alignment Refinement
- **Status**: Integer pixel alignment
- **Impact**: Limited to ~0.5 pixel precision even with good stars
- **Needed**: Cross-correlation refinement

### 2.4 Plate Solver Options
- **Status**: Only ASTAP integration
- **Impact**: Requires external dependency
- **Needed**: Additional solvers (ASTAP is good but limited)
- **Files**: `astro.process.AstroProcess.kt` - platesolve config

### 2.5 Star Catalog Integration
- **Status**: Only NGC catalog
- **Impact**: Missing fainter objects
- **Needed**: Gaia, SDSS integration for better annotate

## Priority 3 - Nice to Have

### 3.1 Mosaic Building
- **Status**: No mosaic support
- **Impact**: Cannot assemble multi-panel mosaics

### 3.2 Blink / Image Comparison
- **Status**: No blink functionality
- **Impact**: Cannot quickly compare frames for quality
- **CLI**: Could add `kimage-astro-process blink` command

### 3.3 Focus Analysis
- **Status**: No built-in focus quality analysis
- **Impact**: Must use external tools
- **CLI**: Could enhance `stars` command with FWHM trends

### 3.4 Report Generation
- **Status**: No HTML/PDF reporting
- **Impact**: No shareable processing summary
- **Output**: Could generate `processing-report.html`

### 3.5 GPU Acceleration
- **Status**: Pure Java/Kotlin CPU processing
- **Impact**: Slow for large datasets
- **Needed**: OpenCL or CUDA backends

## Priority 4 - Minor Gaps

### 4.1 Star Detection Improvements
- Image-based star detection (background from noise)
- Variable star detection (compare frames)

### 4.2 HDR Blend Options
- More algorithms beyond current HDR combine
- Tone mapping options

### 4.3 Batch Processing
- Multiple targets in single run
- Queue system

### 4.4 Integration
- No API/server mode
- No plugin system

---

## Summary

| Category | Implementations | Gaps |
|----------|-----------------|------|
| Calibration | bias, dark, flat, darkflat | cosmetic, DBE, flat illumination |
| Alignment | affine, triangle stars | distortion, sub-pixel, field rotation |
| Stacking | 8 algorithms |drizzle, local rejection, star mask |
| Enhance | 12 steps | deconvolution, DBE, artifact repair |
| Annotate | NGC, WCS, markers | more catalogs, blink |
| Output | TIF, JPG, PNG | FITS writer |
| Pipeline | complete | batch, queue, API |

**Biggest gaps to complete solution:**
1. FITS writing (interoperability)
2. Deconvolution (image quality)
3. DBE/Field flattening (light pollution)
4. Cosmetic correction (hot pixels)
5. Advanced stacking (cosmic rays, drizzle)