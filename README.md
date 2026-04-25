# kimage Astro Image Processing

Automated astrophotography processing: calibrate, align, stack, and enhance your images with one command.

## 🚀 Quick Start (5 minutes)

### Prerequisites
- Java installed on your system
- Astrophotography images (FITS, TIFF, etc.)
- Optional: calibration frames (bias, dark, flat)

### Get Your First Result
```sh
# 1. Navigate to your image directory
cd /path/to/your/images

# 2. Initialize configuration (scans directory automatically)
kimage-astro-process init

# 3. Process your images
kimage-astro-process process
```

`init` scans your directory and prints what it found before writing the config:
```
Smart init detected:
  Light frames : 42 *.fit in '.'
  Debayer      : enabled (pattern: RGGB)
  Calibration  : bias='bias', dark='dark', flat='flat', darkflat='darkflat'
  Sigmoid      : midpoint=0.0080
  Target       : M42
Created kimage-astro-process.yaml
```

Review the summary, then open `kimage-astro-process.yaml` and adjust anything that looks wrong before running `process`. Your processed images will appear in `astro-process/output/`.

### For Quick Testing
Enable quick mode to process only 3 images:
```yaml
# Edit kimage-astro-process.yaml
quick: true
quickCount: 3
```

## Commands

| Command | Purpose |
|---------|---------|
| `init` | Scan directory and create a tailored configuration file |
| `process` | Run the complete processing pipeline |
| `config` | Show current configuration |
| `stars` | Analyze star quality and focus |

## What `init` Detects

`init` inspects your working directory before writing `kimage-astro-process.yaml`:

| What | How it's detected | Result in config |
|---|---|---|
| **Input file extension** | Counts image files by extension; picks the most common | `format.inputImageExtension` |
| **Light frame subdirectory** | Looks for a `light/` or `lights/` folder (any case) | `format.inputDirectory` |
| **Calibration directories** | Checks for `bias/biases/`, `dark/darks/`, `flat/flats/`, `darkflat/darkflats/dark_flat/` (case-insensitive) | `calibrate.*Directory` |
| **Bayer pattern** | Reads `BAYERPAT` FITS header from the first light frame | `format.debayer.bayerPattern` |
| **Debayer on/off** | Disabled for TIFF/PNG/JPEG (already RGB); disabled for 3-channel FITS | `format.debayer.enabled` |
| **Sigmoid midpoint** | Samples up to 3 light frames, computes median background level, clamps to [0.001, 0.3] | First `sigmoid.midpoint` in enhance steps |
| **Target name** | Reads `OBJECT` FITS header from the first light frame | `annotate.decorate.title` |

Calibration is never explicitly disabled — the pipeline skips any calibration stage whose directory does not exist. If `init` writes a non-default directory name (e.g. `darkDirectory: darks`), it found that directory on disk; otherwise it writes the default name and the stage is silently skipped when that directory is absent.

If the detection is wrong, just edit `kimage-astro-process.yaml` before running `process`.

## Basic Workflow

The typical astrophotography processing follows these steps:

**1. Calibration** → Remove sensor noise and optical artifacts

**2. Alignment** → Align images using star positions

**3. Stacking** → Combine images to reduce noise

**4. Enhancement** → Stretch, balance, and enhance the final image

### Directory Structure
Organize your files like this:
```
your-project/
├── *.fit                       # Light frames (your main images)
├── bias/*.fit                  # Bias frames (optional)
├── dark/*.fit                  # Dark frames (optional)
├── darkflat/*.fit              # Dark flat frames (optional)
├── flat/*.fit                  # Flat frames (optional)
└── kimage-astro-process.yaml   # Configuration file (created by init)
```

`init` also recognises these common variants automatically:
- Light frames in a `light/` or `lights/` subdirectory (any capitalisation)
- Calibration directories named `biases/`, `darks/`, `flats/`, `darkflats/`, `dark_flat/`, `dark_flats/` (case-insensitive)

## Essential Configuration

The most important settings you might want to change:

### Input/Output Formats
```yaml
format:
  inputImageExtension: "fit"    # Your image format (fit, tif, jpg, etc.)
  inputDirectory: "."           # Where your images are located
```

### Calibration Frames
```yaml
calibrate:
  biasDirectory: "bias"         # Bias frames location
  darkDirectory: "dark"         # Dark frames location
  flatDirectory: "flat"         # Flat frames location
```

### Alignment Quality
```yaml
align:
  starThreshold: 0.2            # Lower = more stars detected (0.1-0.5)
  maxStars: 100                 # More stars = better alignment
  positionTolerance: 2.0        # Pixel tolerance for alignment
```

### Drizzle Stacking (Advanced)

Drizzle recovers resolution beyond the Nyquist limit when your frames have sub-pixel dithering between exposures. Instead of stacking pre-aligned images on a shared grid, each input pixel is forward-projected onto a supersampled output grid, distributing its flux proportionally across the output pixels it overlaps.

Enable drizzle by setting `algorithm: Drizzle` in the `stack` block:

```yaml
stack:
  algorithm: Drizzle
  drizzle:
    scale: 2.0          # Output grid scale (1× = native, 2× = double resolution)
    pixfrac: 0.7        # Drop size as a fraction of input pixel (0.0–1.0)
    kernel: Square      # Kernel shape: Square (top-hat) or Gaussian
    rejection: SigmaClip  # Bad-pixel rejection: None, SigmaClip, or Winsorize
    kappa: 2.0          # Rejection threshold in standard deviations (used by SigmaClip/Winsorize)
    iterations: 5       # Number of sigma-clip iterations (SigmaClip only)
    crop:               # Optional: drizzle only a sub-region of the output (reference-frame coords)
      enabled: true
      x: 100            # Left edge of the crop window in reference-frame pixels
      y: 100            # Top edge
      width: 800        # Width of the crop window
      height: 600       # Height of the crop window
```

**When to use it:**
- Your imaging session used dithering between frames (random sub-pixel offsets)
- You want a higher-resolution result than individual frames provide
- Your target has fine detail that is being lost to aliasing

**When NOT to use it:**
- Frames were not dithered — with identical offsets, drizzle offers no benefit over Median
- You have very few frames — at least 5–10 dithered frames are recommended

**Parameter guidance:**
- `scale: 2.0` — the most common choice; doubles linear resolution, quadruples file size
- `pixfrac: 0.7` — balances resolution gain against noise: lower values sharpen but increase noise, higher values blur slightly but lower noise
- `kernel: Square` — conserves flux exactly and is preferred for photometric work; `Gaussian` produces smoother blending

**Bad-pixel rejection:**

Without rejection (`rejection: None`, the default), every input pixel contributes to the output — hot pixels and cosmic rays from any single frame will bleed into the result. Enable rejection when your frames may contain outliers:

- `rejection: SigmaClip` — iteratively clips values more than `kappa` standard deviations from the per-pixel median; recommended for most sessions
- `rejection: Winsorize` — clamps outliers to the sigma boundary rather than removing them; preserves more data but is less aggressive
- `kappa: 2.0` — lower values reject more aggressively (try 1.5–2.5); higher values are more lenient
- `iterations: 5` — more iterations refine the clip boundary but rarely change results beyond 3–5

**Cropping the drizzle output:**

Use `crop` to drizzle only a sub-region instead of the full frame — useful for focusing on a specific object, reducing memory use, or avoiding noisy edges. Coordinates are in reference-frame pixels (the aligned coordinate system, before scale is applied). The output image size will be `width × scale` by `height × scale`.

> **Note:** Drizzle is only effective when frames are dithered. Without dithering, use Median or a sigma-clipping algorithm instead.

### Enhancement Steps (Advanced)
Enhancement uses a flexible step-by-step approach. Common steps include:

- **rotate** - Rotate the image
- **crop** - Remove unwanted edges
- **whitebalance** - Color correction
- **autoStretch** - Automatic STF stretch (luminance-based, PixInsight-compatible)
- **linearPercentile** - Histogram stretching
- **sigmoid** - S-curve contrast enhancement
- **reduceNoise** - Noise reduction
- **deconvolve** - Richardson-Lucy deconvolution (restores resolution)
- **highDynamicRange** - HDR processing

The default configuration includes a sophisticated pipeline that works well for most images.

### Output Formats
```yaml
output:
  outputImageExtensions:      # Choose your output formats
    - "tif"                   # Best quality
    - "jpg"                   # For sharing
    - "png"                   # For web use
```

## Common Issues & Solutions

### Image Format Problems

**Problem: Using TIFF files instead of FITS**
```yaml
format:
  inputImageExtension: "tif"
```

**Problem: Want different output formats**
```yaml
output:
  outputImageExtensions:
    - "tif"    # Highest quality for further processing
    - "jpg"    # For sharing/web
```

### Filename Token Problems

**Problem: Want to extract metadata from filenames for organized output**

If your image filenames contain structured information (like `M42_300s_ISO800_2024-01-15_001.fit`), you can extract this into tokens for use in output naming.

**Enable filename tokens and define the structure:**
```yaml
format:
  filenameTokens:
    enabled: true
    separator: "_"           # Character that separates tokens in your filenames
    names:                   # Token names in the same order as they appear in filenames
      - targetName           # M42
      - exposureTime         # 300s
      - iso                  # ISO800
      - dateTime             # 2024-01-15
      - sequenceNumber       # 001
```

**Use tokens in output naming:**
```yaml
output:
  outputName: "{targetName}_{stackedCount}x{exposureTime}_{iso}_processed"
  # This creates: M42_15x300s_ISO800_processed.tif
```

**Common filename token patterns:**
- **Basic:** `target_exposure_iso_sequence` → `M42_300s_ISO800_001.fit`
- **Detailed:** `target_exp_bin_camera_iso_date_temp_seq` → `M42_300s_1x1_ASI294MC_ISO800_20240115_-10C_001.fit`
- **Simple:** `target_sequence` → `M42_001.fit`

**Troubleshooting tokens:**
- **Error "Wrong number of tokens":** Make sure your `names` list has exactly the same number of items as tokens in your filename when split by the separator
- **Missing separator:** If filenames use different separators (like `-` or `.`), update the `separator` field
- **Inconsistent filenames:** All image files must follow the same naming pattern

### Alignment Issues

**Problem: No stars detected for alignment**
- Lower the `starThreshold` (try 0.1)
- Check if images need debayering first
- Make sure images aren't completely over/under exposed

**Problem: Images don't align properly**
```yaml
align:
  starThreshold: 0.1         # Detect more stars
  maxStars: 200              # Use more stars for alignment
  positionTolerance: 5.0     # Allow more tolerance
```
- Also check the stacked `max` file for elongated/streaky stars

### Final Image Brightness

`init` estimates a starting `midpoint` from the median background level of your frames. If the result is still too dark or too bright, tweak it manually:

**Problem: Final image too dark**
1. Make sure black pixel artifacts at borders are cropped away
2. Decrease the `midpoint` in the first `sigmoid` step:
```yaml
enhance:
  steps:
    # ... other steps ...
    - sigmoid:
        midpoint: 0.005      # Lower value = brighter
        strength: 1.1
```

**Problem: Final image too bright/blown out**
1. Increase the `midpoint` in the first `sigmoid` step:
```yaml
enhance:
  steps:
    # ... other steps ...
    - sigmoid:
        midpoint: 0.02       # Higher value = darker
        strength: 1.1
```

### Performance Issues

**Problem: Processing too slow**

- Enable quick mode: `quick: true, quickCount: 3`
- Reduce `maxStars` to 50

**Problem: Stacking runs out of disk space in system temp**

Stacking writes memory-mapped temp files. For large frame counts, these can be tens of gigabytes. Redirect them to a drive with more space:
```yaml
stack:
  tempDir: "/data/tmp"          # Linux/macOS — drizzle also uses this path
  # tempDir: "D:/tmp"           # Windows
```

**Problem: No disk space to spare — use tile-based stacking**

Set `maxDiskSpaceBytes: 0` to process row-by-row without any temp files. Each image is loaded from disk once per row strip, so it's slower but requires zero additional disk space:
```yaml
stack:
  maxDiskSpaceBytes: 0          # tile-based, no disk usage
```

Or set a specific limit (e.g. 4 GB) and let the pipeline decide automatically:
```yaml
stack:
  maxDiskSpaceBytes: 4000000000
```

**Problem: Need to redo processing steps**

- Delete intermediate files and rerun:
```sh
# To redo just enhancement onward (most common)
rm -rf astro-process/enhanced
kimage-astro-process process

# To redo everything from stacking onward
rm -rf astro-process/stacked kimage-astro-process process
kimage-astro-process process

# To redo everything from alignment onward
rm -rf astro-process/aligned 
kimage-astro-process process
```

The software automatically skips steps where output files already exist, so deleting specific directories forces those steps to be redone with your new settings.

### Quality Assessment & Tips

**Evaluate your images before processing:**
```sh
kimage-astro-process stars
```
This shows star count, FWHM (focus quality - lower is better), sharpness, and overall quality scores.

**Best Practices:**
- **Use Good Calibration Frames** - Quality bias, dark, and flat frames make a huge difference
- **Quick Mode for Testing** - Set `quick: true` to process just 3 images while experimenting
- **Check the Max File** - The stacked `max` file in `astro-process/stacked/` reveals alignment problems - look for elongated or streaky stars
- **Default Settings Work Well** - The included enhancement pipeline handles most cases automatically
- **Inspect Progress** - Each processing step saves intermediate files you can examine

## How It Works

The software automatically handles these steps:
1. **Calibration** - Subtracts bias, dark frames; divides by flats
2. **Alignment** - Uses star positions to align all images
3. **Stacking** - Combines aligned images (reduces noise)
4. **Enhancement** - Applies contrast stretching, noise reduction, color balance

## Pipeline Variants

The enhancement stage supports several advanced branching patterns that go beyond the single linear pipeline.

### Variant Processing — Compare Enhancement Settings

Run multiple enhancement configurations on the same stack and produce a separate output for each.

```yaml
enhance:
  branches:
    - name: "soft"
      steps:
        - sigmoid: { midpoint: 0.3, strength: 0.8 }
    - name: "aggressive"
      steps:
        - sigmoid: { midpoint: 0.2, strength: 1.5 }
        - sharpen: { strength: 0.4 }

output:
  outputName: "{firstInput}_{branchName}"   # {branchName} is filled per branch
```

Each branch writes its own output files. The `{branchName}` token inserts the branch name into the filename. When `branches` is present, `steps` is ignored.

---

### Per-Frame Mode — Timelapse / Animation

Skip stacking and process each aligned frame individually to produce a numbered image sequence.

```yaml
stack:
  perFrame: true

enhance:
  steps:
    - linearPercentile: { minPercentile: 0.0001, maxPercentile: 0.9999 }
    - sigmoid: { midpoint: 0.35, strength: 1.0 }

output:
  outputName: "frame_{frameIndex}"    # {frameIndex} is the 1-based frame number
  outputImageExtensions: ["png"]
```

---

### Star/Background Separation — `extractStars`

Separate stars from background nebulosity, process each independently, then recombine with soft masking.

```yaml
enhance:
  steps:
    - linearPercentile: { minPercentile: 0.0001, maxPercentile: 0.9999 }
    - extractStars:
        factor: 2.0                   # Star ellipse radius multiplier × FWHM
        softMaskBlurRadius: 5         # Gaussian feather radius in pixels
        starsBranch:
          steps:
            - reduceNoise: { thresholds: [0.00005] }
        backgroundBranch:
          steps:
            - reduceNoise: { thresholds: [0.0005] }
            - removeBackground: {}
    - sigmoid: { midpoint: 0.3, strength: 1.2 }
```

Star positions are read from `astro-process/aligned/stars.yaml` (written after alignment). If that file does not exist, `findStars` runs on the stacked image.

---

### Per-Channel / LRGB Processing — `decompose`

Decompose the image into components, process each independently, then recombine.

```yaml
enhance:
  steps:
    - decompose:
        mode: LRGB        # LRGB | RGB | HSB
        luminance:
          steps:
            - reduceNoise: { thresholds: [0.0002] }
            - sharpen: { strength: 0.3 }
        color:
          steps:
            - whitebalance: { type: Global }
    - sigmoid: { midpoint: 0.4, strength: 1.0 }
```

| Mode | Branches | Sub-image | Recombine |
|------|----------|-----------|-----------|
| `LRGB` | `luminance` (gray), `color` (RGB) | Y channel; full RGB | Brightness replacement |
| `RGB` | `red`, `green`, `blue` | Single-channel gray | `MatrixImage(r,g,b)` |
| `HSB` | `hue`, `saturation`, `brightness` | Single-channel gray | HSB → RGB conversion |

A null branch (omitted key) means pass-through — that component is unchanged.

---

### Masked/Regional Processing — `maskedProcess`

Apply different enhancement steps inside and outside a spatial mask, then blend.

```yaml
enhance:
  steps:
    - maskedProcess:
        mask:
          source: Luminance     # Stars | Luminance | File | Platesolve
          threshold: 0.3        # Luminance: pixel cutoff (0–1)
          blur: 10              # Feather radius in pixels
        insideMask:
          steps:
            - reduceNoise: { thresholds: [0.0001] }
        outsideMask:
          steps:
            - reduceNoise: { thresholds: [0.0008] }
            - removeBackground: {}
```

| Mask source | How the mask is built |
|-------------|----------------------|
| `Stars` | Star ellipses from `stars.yaml`, Gaussian-feathered |
| `Luminance` | Pixels above `threshold` → 1, below → 0; Gaussian-feathered |
| `File` | Load grayscale PNG from `maskFile` path (pixel values = weight) |
| `Platesolve` | Ellipse from `objectName` in the NGC catalog |

---

### Named Sources — Narrowband, HDR, Multi-Session

For workflows requiring multiple independent stacks (narrowband filters, exposure brackets, multi-night sessions), declare named sources. Each source runs its own calibrate/align/stack pipeline under `astro-process/<name>/`.

**Narrowband Palette Synthesis**
```yaml
sources:
  - name: "Ha"
    format: { inputDirectory: "ha_frames/", inputImageExtension: "fit" }
    stack: { algorithm: Median }
  - name: "OIII"
    format: { inputDirectory: "oiii_frames/" }
    stack: { algorithm: Median }

enhance:
  branches:
    - name: "SHO"
      steps:
        - compositeChannels: { red: "Ha", green: "OIII", blue: "OIII" }
        - sigmoid: { midpoint: 0.3, strength: 1.2 }
    - name: "HOO"
      steps:
        - compositeChannels: { red: "Ha", green: "OIII", blue: "OIII" }
        - sigmoid: { midpoint: 0.3, strength: 1.0 }

output:
  outputName: "{firstInput}_{branchName}"
```

`compositeChannels` picks the luminance (BT.709) from RGB sources or uses the Gray channel directly from monochrome sources.

**Exposure-Bracket HDR**
```yaml
sources:
  - name: "short"
    format: { maxExposureSeconds: 5 }
    stack: { algorithm: Median }
  - name: "long"
    format: { minExposureSeconds: 30 }
    stack: { algorithm: Median }

enhance:
  input: "long"         # which source to start enhancement from
  steps:
    - mergeWith: { image: "short", method: Hdr }
    - sigmoid: { midpoint: 0.3, strength: 1.0 }
```

Exposure time is read from the FITS `EXPTIME` header. Files without this header are always included.

**Multi-Session Merge**
```yaml
sources:
  - name: "night1"
    calibrate: { inputDir: "night1/" }
  - name: "night2"
    calibrate: { inputDir: "night2/" }

enhance:
  steps:
    - stackSources:
        images: ["night1", "night2"]
        algorithm: WeightedAverage
        weights: [0.6, 0.4]
    - sigmoid: { midpoint: 0.3, strength: 1.0 }
```

`stackSources` uses any existing stacking algorithm and optional per-source weights. If `outputName` is set, the result is also stored in the registry under that name for later reference by other steps.

---

## Advanced Features

For power users, the software also supports:
- **Custom Enhancement Steps** - Build your own processing pipeline
- **Pipeline Variants** - Branches, per-frame, star/background separation, per-channel
- **Named Sources** - Narrowband synthesis, HDR exposure blending, multi-session merge
- **Annotation System** - Add titles, markers, and graphics to final images
- **Token-Based Naming** - Extract metadata from filenames for organized outputs
- **HDR Processing** - Combine multiple enhancement steps for maximum detail
- **Background Removal** - Remove gradients and light pollution
- **Plate Solving** - Automatically determine sky coordinates using ASTAP and mark NGC objects
- **Stacking Algorithms** - Choose from Median, SigmaClip, Winsorize, SmartMax, and more
- **Drizzle Stacking** - Recover resolution beyond the native pixel scale from dithered frames

<details>
<summary>Click to see full configuration reference</summary>

## Complete Configuration Reference

### Top-Level Configuration
```yaml
quick: false                 # Enable quick mode (process limited images)
quickCount: 3               # Number of images to process in quick mode
```

### Target Configuration
```yaml
target:
  name: "M42"                   # Object name (used to look up RA/DEC from NGC catalog)
  ra: 83.82                     # Right ascension in degrees (optional, overrides name lookup)
  dec: -5.39                    # Declination in degrees (optional, overrides name lookup)
  angle: 0.0                    # Image rotation angle in degrees (optional)
  fov: 1.5                      # Field of view in degrees (optional)
```

### Platesolve Configuration
```yaml
platesolve:
  enabled: false                # Whether to run plate solving on the reference image
  platesolveType: "Astap"       # Solver type: Astap, Internal, Custom
  executable: "astap_cli"       # Path to Astap solver executable (required for type: Astap)
```

#### Solver Types
- **Astap**: Uses the external `astap_cli` tool (industry standard).
- **Internal**: A native Kotlin solver.
  - Automatically downloads star data from **Gaia DR3** via the VizieR API.
  - Caches star catalog data locally in `~/.kimage-astro-process/star-catalog/`.
  - Uses gnomonic projection and robust quad-based pattern matching.
- **Custom**: Placeholder for user-defined external solvers.


Plate solving determines the sky coordinates (RA/DEC) of the image. When enabled, the WCS result
is used to automatically mark deep sky objects from the NGC catalog in annotated output images.

### Format Configuration
```yaml
format:
  inputImageExtension: "fit"    # Input file extension (fit, tif, jpg, png, etc.)
  outputImageExtension: "tif"   # Intermediate file extension
  inputDirectory: "."           # Directory containing input images
  minExposureSeconds: null      # Include only FITS files with EXPTIME >= this value
  maxExposureSeconds: null      # Include only FITS files with EXPTIME <= this value
                                # (files without EXPTIME header are always included)
  filenameTokens:               # Extract metadata from filenames
    enabled: false              # Whether to parse filename tokens
    separator: "_"              # Character separating tokens in filenames
    names:                      # Token names in order of appearance
      - "targetType"            # e.g., "Light", "Dark", "Flat"
      - "targetName"            # e.g., "M42", "NGC7000"
      - "exposureTime"          # e.g., "300s", "120s"
      - "binLevel"              # e.g., "1x1", "2x2"
      - "camera"                # e.g., "ASI294MC", "Canon5D"
      - "iso"                   # e.g., "ISO800", "ISO1600"
      - "dateTime"              # e.g., "20240115", "2024-01-15"
      - "temperature"           # e.g., "-10C", "5C"
      - "sequenceNumber"        # e.g., "001", "042"
  debayer:                      # Debayering configuration
    enabled: true               # Whether to debayer raw camera files
    cleanupBadPixels: true      # Remove bad/hot pixels during debayering
    bayerPattern: "RGGB"        # Bayer pattern: RGGB, GRBG, GBRG, BGGR
    interpolation: "AHD"      # Debayer algorithm: None, SuperPixel, SuperPixelHalf, Monochrome, Nearest, Bilinear, AHD, GLI, AMaZE, VNG, PPG
```

### Calibration Configuration
```yaml
calibrate:
  enabled: true                # Enable/disable frame calibration (bias/dark/flat/darkflat)
  inputImageExtension: "fit"    # File extension for calibration frames
  debayer:                      # Debayering for calibration frames
    enabled: true
    bayerPattern: "RGGB"
    interpolation: "AHD"        # Debayer algorithm: None, SuperPixel, SuperPixelHalf, Monochrome, Nearest, Bilinear, AHD, GLI, AMaZE, VNG, PPG
  biasDirectory: "bias"         # Directory containing bias frames
  flatDirectory: "flat"         # Directory containing flat frames
  darkflatDirectory: "darkflat" # Directory containing dark flat frames
  darkDirectory: "dark"         # Directory containing dark frames
  searchParentDirectories: true # Search parent directories for calibration frames
  darkskip: false               # Skip dark subtraction from light frames (for short exposures)
  darkScalingFactor: 1.0        # Scale dark frame by (lightExp/darkExp) before subtraction
  normalizeBackground:          # Background normalization settings (independent of frame calibration)
    enabled: true               # Whether to normalize backgrounds across images
    offset: 0.01                # Offset value for normalization
  calibratedOutputDirectory: "astro-process/calibrated" # Output directory for calibrated images
```

### Alignment Configuration
```yaml
align:
  starThreshold: 0.2            # Star detection threshold (0.1-0.5, lower = more stars)
  maxStars: 100                 # Maximum stars to use for alignment (50-500)
  positionTolerance: 2.0        # Pixel tolerance for star position matching (1.0-10.0)
  alignedOutputDirectory: "astro-process/aligned" # Output directory for aligned images
```

### Stacking Configuration
```yaml
stack:
  stackedOutputDirectory: "astro-process/stacked" # Output directory for stacked images
  algorithm: "Median"           # Stacking algorithm:
                                # Median, Average, Max, Min
                                # SigmaClipMedian, SigmaClipAverage
                                # SigmaWinsorizeMedian, SigmaWinsorizeAverage
                                # WinsorizedSigmaClipMedian, WinsorizedSigmaClipAverage
                                # SigmaClipWeightedMedian, SmartMax
  perFrame: false               # Skip stacking; process each aligned frame individually
                                # Use with {frameIndex} token for timelapse/animation output
                                # Drizzle (see below)
  kappa: 2.0                    # Sigma-clip / winsorize rejection threshold in standard deviations
                                # (used by all SigmaClip*, SigmaWinsorize*, WinsorizedSigmaClip*, SmartMax)
                                # Lower = more aggressive rejection (try 1.5–3.0)
  iterations: 10                # Number of sigma-clip iterations (3–10 is usually enough)
  precision: Float              # Pixel storage precision during stacking:
                                #   Float  – 32-bit float (~7 significant digits); half the disk usage
                                #   Double – 64-bit double (~15 significant digits); use for 32-bit FITS
  tempDir: null                 # Directory for memory-mapped temp files (null = system temp dir)
                                # Set this if your system temp partition is small (e.g. a ramdisk)
  maxDiskSpaceBytes: 9223372036854775807  # Maximum bytes allowed for mmap temp files.
                                # The pipeline calculates the exact requirement upfront:
                                #   numImages × numChannels × numPixels × bytesPerElement
                                # When the requirement exceeds this limit, stacking automatically
                                # falls back to tile-based processing: images are loaded in full
                                # for each strip of pixels, using only in-memory buffers.
                                # Set to 0 to always use tile-based (row-by-row, zero disk usage).
                                # Example values:
                                #   500000000    – 500 MB
                                #   2000000000   – 2 GB
                                #   10000000000  – 10 GB
  drizzle:                      # Used only when algorithm: Drizzle
    scale: 2.0                  # Output grid scale factor (1.0 = native, 2.0 = double resolution)
    pixfrac: 0.7                # Drop size as fraction of input pixel side (0.0–1.0]
    kernel: Square              # Kernel shape: Square (top-hat, flux-conserving) or Gaussian
    rejection: None             # Bad-pixel rejection: None, SigmaClip, or Winsorize
    kappa: 2.0                  # Rejection threshold in standard deviations (SigmaClip/Winsorize)
    iterations: 5               # Sigma-clip iterations (SigmaClip only; 3–5 is usually enough)
                                # Drizzle temp files use the same tempDir as the stack config above.
    crop:                       # Drizzle only a sub-region (reference-frame coords, before scaling)
      enabled: false            # Set true to activate
      x: 0                     # Left edge of crop window
      y: 0                     # Top edge of crop window
      width: 0                 # Width of crop window
      height: 0                # Height of crop window
```

**Stacking precision notes:**
- `precision: Float` (default) — uses 32-bit floats for the stacking buffer. Sufficient for images from 8-bit or 16-bit sensors; halves disk and memory usage compared to Double.
- `precision: Double` — uses 64-bit doubles. Recommended when input frames are 32-bit float FITS files where the extra dynamic range and precision matter.

**Memory and disk management:**

Stacking requires holding all pixel values for all frames simultaneously. The default approach memory-maps temporary files to disk (avoiding JVM heap exhaustion), but this requires `numImages × numChannels × numPixels × bytesPerElement` bytes of temp space.

For a typical 50-frame stack of 24 MP images:
- Float precision: 50 × 3 × 24,000,000 × 4 bytes ≈ 14 GB of temp space
- Double precision: 50 × 3 × 24,000,000 × 8 bytes ≈ 29 GB of temp space

When the calculated requirement exceeds `maxDiskSpaceBytes`, the pipeline automatically switches to **tile-based processing**: the image is divided into horizontal strips sized to fit within `maxDiskSpaceBytes`, and each image is loaded from disk once per strip. This trades disk space for additional I/O time.

Set `tempDir` to a path on a partition with sufficient space when the system temp directory is too small.

**Drizzle notes:**
- Requires dithered frames (sub-pixel offsets between exposures) to be effective
- `scale: 2.0` doubles linear dimensions — a 24 MP sensor produces a ~96 MP result
- `pixfrac: 0.7` is a good starting point; lower values increase resolution but raise noise
- `kernel: Square` is recommended for most astrophotography (exact flux conservation)
- `kernel: Gaussian` produces smoother blending, useful when rotation between frames is significant
- `rejection: SigmaClip` with `kappa: 2.0` rejects hot pixels and cosmic rays; use `Winsorize` for a softer clamp
- `rejection: None` (default) is equivalent to classic one-pass drizzle with no outlier removal
- Drizzle's two-pass mmap temp files use the same `tempDir` set at the stack level

### Enhancement Configuration
```yaml
enhance:
  enhancedOutputDirectory: "astro-process/enhanced" # Output directory for enhanced images
  input: null                   # Named source to start from (null = first source or "main")
  branches: null                # If set, takes precedence over steps; each branch is an
                                # independent step sequence producing its own output file.
                                # Use {branchName} token to distinguish outputs.
  measure:                      # Area for measuring percentiles (optional)
    enabled: false              # Whether to use specific measurement area
    x: 0                        # X coordinate of measurement rectangle
    y: 0                        # Y coordinate of measurement rectangle
    width: 0                    # Width of measurement rectangle
    height: 0                   # Height of measurement rectangle
  regionOfInterest:             # Save ROI for each step (optional)
    enabled: false              # Whether to save region of interest
    x: 0                        # X coordinate of ROI rectangle
    y: 0                        # Y coordinate of ROI rectangle
    width: 0                    # Width of ROI rectangle
    height: 0                   # Height of ROI rectangle
  histogram:                    # Histogram generation settings
    enabled: true               # Whether to generate histograms for each step
    histogramWidth: 1000        # Width of histogram images
    histogramHeight: 400        # Height of histogram images
    printPercentiles: false     # Whether to print percentile values
  steps:                        # Processing steps (executed in order)
    # Each step supports an optional top-level "enabled" flag to skip it:
    #   - sigmoid:
    #       enabled: false        # Temporarily disable this step without removing it
    #       midpoint: 0.01
    - debayer:
        enabled: false  # usually done earlier in calibration step
        cleanupBadPixels: true
        bayerPattern: "RGGB"
        interpolation: "AHD"    # Debayer algorithm: None, SuperPixel, SuperPixelHalf, Monochrome, Nearest, Bilinear, AHD, GLI, AMaZE, VNG, PPG
    # Rotation Step
    - rotate:
        angle: 0.0              # Rotation angle: 90, 180, 270, or any angle in degrees
    # Cropping Step
    - crop:
        x: 100                  # X coordinate (negative values count from right)
        y: 100                  # Y coordinate (negative values count from bottom)
        width: -100             # Width (negative = image_width - x + width)
        height: -100            # Height (negative = image_height - y + height)
    # White Balance Step
    - whitebalance:
        enabled: true
        type: "Local"           # Global, Local, Custom
        fixPoints:              # Fix points for local white balance
          type: "FourCorners"   # Grid, FourCorners, EightCorners, Custom
          borderDistance: 100   # Distance from image border
          gridSize: 2           # Grid size (for Grid type)
          customPoints: []      # Custom point coordinates (for Custom type)
        localMedianRadius: 50   # Radius for local median calculation
        valueRangeMin: 0.0      # Minimum value range for white balance
        valueRangeMax: 0.9      # Maximum value range for white balance
        customRed: 1.0          # Custom red multiplier (for Custom type)
        customGreen: 1.0        # Custom green multiplier (for Custom type)
        customBlue: 1.0         # Custom blue multiplier (for Custom type)
    # Background Removal Step
    - removeBackground:
        fixPoints:              # Fix points for background sampling
          type: "FourCorners"   # Grid, FourCorners, EightCorners, Custom
          borderDistance: 100   # Distance from border for corner points
          gridSize: 2           # Grid size (for Grid type)
          customPoints: []      # Custom points (for Custom type)
        medianRadius: 50        # Radius for median calculation at fix points
        power: 1.5              # Interpolation power (1.0-3.0)
        offset: 0.01            # Background offset value
    # Auto Stretch (if this is enabled then usually no other stretch step is needed)
    - autoStretch:
        shadowClipping: 2.8      # black point = median - k*noise
        targetBackground: 0.1    # where to place the background after stretch (0.05–0.3)
        perChannel: false        # false = luminance-based (preserves color), true = per-channel
    # Linear Percentile Stretch
    - linearPercentile:
        minPercentile: 0.0001   # Minimum percentile (0.0001-0.01)
        maxPercentile: 0.9999   # Maximum percentile (0.95-0.9999)
      addToHighDynamicRange: true # Add this result to HDR processing
    # Sigmoid Stretch
    - sigmoid:
        midpoint: 0.01          # Midpoint of sigmoid curve (0.001-1.0)
        strength: 1.1           # Strength of sigmoid curve (0.1-5.0)
      addToHighDynamicRange: true # Add this result to HDR processing
    # Blur Step
    - blur:
        strength: 0.1           # Blur strength (0.0-1.0)
    # Sharpen Step
    - sharpen:
        strength: 0.5           # Sharpen strength (0.0-2.0)
    # Unsharp Mask Step
    - unsharpMask:
        radius: 1               # Unsharp mask radius (1-10)
        strength: 1.0           # Unsharp mask strength (0.1-3.0)
    # Noise Reduction Step
    - reduceNoise:
        algorithm: "MultiScaleMedianOverAllChannels" # or "MultiScaleMedianOverGrayChannel"
        thresholding: "Soft"    # Hard, Soft, Sigmoid, SigmoidLike
        thresholds:             # Threshold levels (multiple levels supported)
          - 0.01
          - 0.001
    # Deconvolution Step (Richardson-Lucy)
    - deconvolve:
        algorithm: "RichardsonLucy"  # Algorithm: RichardsonLucy, Wiener
        psfSigma: 1.5            # PSF sigma for Gaussian kernel (0.5-5.0)
        iterations: 20           # Number of iterations (5-100)
    # High Dynamic Range Step
    - highDynamicRange:
        saturationBlurRadius: 3 # Blur radius for saturation calculation
        contrastWeight: 0.2     # Weight for contrast (0.0-1.0)
        saturationWeight: 0.1   # Weight for saturation (0.0-1.0)
        exposureWeight: 1.0     # Weight for exposure (0.0-2.0)
```

### Annotation Configuration
```yaml
annotate:
  enabled: false                # Whether to create annotated images
  annotatedOutputDirectory: "astro-process/annotated" # Output directory
  decorate:                     # Text and marker decorations
    enabled: true               # Whether to add decorations
    title: "Object Name"        # Main title (supports tokens like {targetName})
    subtitle: "{stackedCount}x{exposureTime}" # Subtitle (supports tokens)
    text: "Object Description"  # Additional text
    colorTheme: "Cyan"          # White, Cyan, Red, Green, Blue
    grid: false                 # Overlay a coordinate grid on the image
    platesolveMarkers:          # Auto-markers from plate solve results
      enabled: true             # Whether to add markers for detected objects
      magnitude: 10.0           # Only show objects brighter than this magnitude
      minObjectSize: 50         # Minimum object size in pixels to display
      whiteList: null           # Only show these object names (null = show all)
      blackList: null           # Never show these object names (null = block none)
    markerStyle: "Square"       # Square, Rectangle, Circle, Oval, None
    markerLabelStyle: "Index"   # Index, Name, None
    markers:                    # List of manually placed markers
      - name: "Star Name"       # Marker name
        x: 100                  # X coordinate
        y: 100                  # Y coordinate
        size: 100               # Marker size
        info1: "Additional info" # Extra information line 1
        info2: "More info"      # Extra information line 2
  draw:                         # Custom drawing operations
    enabled: false              # Whether to enable custom drawing
    margin:                     # Margins for drawing area
      top: 0                    # Top margin
      left: 0                   # Left margin
      bottom: 0                 # Bottom margin
      right: 0                  # Right margin
    steps:                      # Drawing steps (executed in order)
      - color:                  # Set drawing color
          color: "ffffff"       # Hex color code (without #)
      - stroke:                 # Set stroke width
          width: 1.0            # Stroke width in pixels
      - fontSize:               # Set font size
          size: 12.0            # Font size in points
      - line:                   # Draw line
          x1: 0                 # Start X coordinate
          y1: 0                 # Start Y coordinate
          x2: 100               # End X coordinate
          y2: 100               # End Y coordinate
      - rectangle:              # Draw rectangle
          x: 0                  # X coordinate
          y: 0                  # Y coordinate
          width: 100            # Rectangle width
          height: 100           # Rectangle height
      - text:                   # Draw text
          x: 10                 # X coordinate
          y: 20                 # Y coordinate
          text: "Hello World"   # Text to draw (supports tokens)
```

### Output Configuration
```yaml
output:
  outputName: "{firstInput}_{calibration}_{stackedCount}x"
  # Token-based output naming. Always-available tokens:
  # {parentDir}    - Parent directory name
  # {firstInput}   - First input filename (without extension)
  # {inputCount}   - Number of input files processed
  # {stackedCount} - Number of successfully stacked files
  # {calibration}  - Calibration types used (e.g., "bias,dark,flat")
  # {branchName}   - Current branch name (only set when enhance.branches is used)
  # {frameIndex}   - 1-based frame number (only set when stack.perFrame is true)
  # Custom tokens (e.g. {targetName}, {exposureTime}, {iso}) are only available
  # when filenameTokens.enabled is true and the token name is listed under
  # format.filenameTokens.names
  outputImageExtensions:        # List of output formats to generate
    - "tif"                     # TIFF format (best quality, large files)
    - "jpg"                     # JPEG format (smaller files, some quality loss)
    - "png"                     # PNG format (lossless, good for web)
  outputDirectory: "astro-process/output" # Final output directory
```

### Available Enhancement Step Types

The enhancement pipeline supports these step types (use exactly one per step):

- **`debayer`** - Convert Bayer pattern to RGB (uses AHD by default)
- **`rotate`** - Rotate image by specified angle
- **`crop`** - Crop image to specified rectangle
- **`whitebalance`** - Apply color correction
- **`removeBackground`** - Remove background gradients
- **`autoStretch`** - Automatic STF (Screen Transfer Function) stretch; estimates background and noise from the image, then applies a Midtone Transfer Function to bring the background to a target brightness
- **`linearPercentile`** - Linear histogram stretching
- **`sigmoid`** - S-curve contrast enhancement
- **`blur`** - Apply Gaussian blur
- **`sharpen`** - Apply sharpening filter
- **`unsharpMask`** - Apply unsharp mask filter
- **`reduceNoise`** - Multi-scale noise reduction
- **`deconvolve`** - Richardson-Lucy deconvolution to restore resolution
- **`cosmeticCorrection`** - Remove hot/cold pixels
- **`highDynamicRange`** - Combine multiple enhancement results
- **`extractStars`** - Separate stars and background, process independently, recombine
- **`decompose`** - Split into LRGB/RGB/HSB components, process each, recombine
- **`compositeChannels`** - Assemble named sources (R, G, B) into a single RGB image
- **`mergeWith`** - HDR-blend a named source into the current image
- **`stackSources`** - Combine named sources using any stack algorithm
- **`maskedProcess`** - Apply different steps inside/outside a spatial mask

### Parameter Ranges and Tips

**Debayer Algorithms:**
- `None` / `Nearest` / `SuperPixel` / `SuperPixelHalf` — fast but low quality; useful for quick preview or alignment passes
- `Bilinear` — simple 4-neighbour average; fast, slight colour blurring at edges
- `Monochrome` — converts Bayer mosaic to luminance only (no colour separation)
- `AHD` — Adaptive Homogeneity-Directed; good quality, edge-aware; the default
- `GLI` — Gradient-Limited Interpolation; similar to AHD with gradient-guided green channel
- `VNG` — Variable Number of Gradients; selects only low-gradient directions per pixel, reducing zipper artefacts at fine edges; good balance of quality and sharpness
- `PPG` — Patterned Pixel Grouping; uses hue-transit (ratio) correction for natural colour transitions; works well for high-saturation subjects
- `AMaZE` — highest quality; multi-pass Laplacian-corrected colour-difference interpolation; slowest but best detail and colour accuracy

**Star Detection:**
- `starThreshold`: 0.1 (more stars, noisier) to 0.5 (fewer stars, cleaner)
- `maxStars`: 50 (faster) to 500 (more accurate alignment)
- `positionTolerance`: 1.0 (strict) to 10.0 (tolerant)

**Auto Stretch (autoStretch):**
- `shadowClipping`: 1.0 (bright background) to 5.0 (dark background) — multiplier for MAD-based noise estimate that sets the black point; default 2.8 matches PixInsight AutoSTF
- `targetBackground`: 0.05 (dark) to 0.3 (bright) — normalized value where the background lands after stretching; default 0.1
- `perChannel`: `false` (luminance-based, preserves color balance) / `true` (per-channel, more aggressive but may shift colors)

**Enhancement Values:**
- `minPercentile`: 0.0001 (aggressive) to 0.01 (conservative)
- `maxPercentile`: 0.95 (conservative) to 0.9999 (aggressive)
- `sigmoid.midpoint`: 0.001 (very bright) to 1.0 (very dark)
- `sigmoid.strength`: 0.1 (subtle) to 5.0 (extreme)

**Noise Reduction:**
- Use lower threshold values (0.001) for aggressive noise reduction
- Use higher threshold values (0.1) for subtle noise reduction
- Multiple threshold levels process different noise scales

**Cosmetic Correction:**
- `mode`: `Hot` (bright outliers), `Cold` (dark outliers), or `Both` (default)
- `sigmaThreshold`: 3.0 (aggressive) to 10.0 (conservative)
- `fixRadius`: 1 (3x3 area) to 3 (7x7 area)

**Deconvolution:**
- `psfSigma`: 1.0 (tight stars) to 3.0 (looser blur - 1.5 is typical)
- `iterations`: 5 (faster) to 100 (more aggressive restoration)
- Best results achieved after good calibration and stacking
- Too many iterations can introduce artifacts/noise amplification

</details>

---

**Ready to process your first images?** Just run:
```sh
cd /your/images/directory
kimage-astro-process init
kimage-astro-process process
```

That's it! Your enhanced astrophotography images will be waiting in `astro-process/output/`.

