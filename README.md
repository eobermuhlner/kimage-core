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

# 2. Initialize default configuration
kimage-astro-process init

# 3. Process your images
kimage-astro-process process
```

That's it! Your processed images will appear in `astro-process/output/`.

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
| `init` | Create default configuration file |
| `process` | Run the complete processing pipeline |
| `config` | Show current configuration |
| `stars` | Analyze star quality and focus |

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
├── darkflat/*.fit              # Dark frames (optional)
├── flat/*.fit                  # Flat frames (optional)
└── kimage-astro-process.yaml   # Configuration file
```

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

### Enhancement Steps (Advanced)
Enhancement uses a flexible step-by-step approach. Common steps include:

- **rotate** - Rotate the image
- **crop** - Remove unwanted edges
- **whitebalance** - Color correction
- **linearPercentile** - Histogram stretching
- **sigmoid** - S-curve contrast enhancement
- **reduceNoise** - Noise reduction
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

**Problem: Final image too dark**
1. Make sure black pixel artifacts at borders are cropped away
2. Decrease the `midpoint` in the first `sigmoid` step:
```yaml
enhance:
  steps:
    # ... other steps ...
    - sigmoid:
        midpoint: 0.005      # Lower value = brighter (try 0.005 instead of 0.01)
        strength: 1.1
```

**Problem: Final image too bright/blown out**
1. Increase the `midpoint` in the first `sigmoid` step:
```yaml
enhance:
  steps:
    # ... other steps ...
    - sigmoid:
        midpoint: 0.02       # Higher value = darker (try 0.02 instead of 0.01)
        strength: 1.1
```

### Performance Issues

**Problem: Processing too slow**

- Enable quick mode: `quick: true, quickCount: 3`
- Reduce `maxStars` to 50

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

## Advanced Features

For power users, the software also supports:
- **Custom Enhancement Steps** - Build your own processing pipeline
- **Annotation System** - Add titles, markers, and graphics to final images
- **Token-Based Naming** - Extract metadata from filenames for organized outputs
- **HDR Processing** - Combine multiple enhancement steps for maximum detail
- **Background Removal** - Remove gradients and light pollution

<details>
<summary>Click to see full configuration reference</summary>

## Complete Configuration Reference

### Top-Level Configuration
```yaml
quick: false                 # Enable quick mode (process limited images)
quickCount: 3               # Number of images to process in quick mode
```

### Format Configuration
```yaml
format:
  inputImageExtension: "fit"    # Input file extension (fit, tif, jpg, png, etc.)
  outputImageExtension: "tif"   # Intermediate file extension
  inputDirectory: "."           # Directory containing input images
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
```

### Calibration Configuration
```yaml
calibrate:
  inputImageExtension: "fit"    # File extension for calibration frames
  debayer:                      # Debayering for calibration frames
    enabled: true
    bayerPattern: "RGGB"
  biasDirectory: "bias"         # Directory containing bias frames
  flatDirectory: "flat"         # Directory containing flat frames
  darkflatDirectory: "darkflat" # Directory containing dark flat frames
  darkDirectory: "dark"         # Directory containing dark frames
  searchParentDirectories: true # Search parent directories for calibration frames
  normalizeBackground:          # Background normalization settings
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
```

### Enhancement Configuration
```yaml
enhance:
  enhancedOutputDirectory: "astro-process/enhanced" # Output directory for enhanced images
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
    # Debayer Step (if not done earlier)
    - debayer:
        enabled: true
        cleanupBadPixels: true
        bayerPattern: "RGGB"
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
    colorTheme: "Cyan"          # Green, Cyan, Red, Blue, Yellow, Magenta
    markerStyle: "Square"       # Rectangle, Square, Circle
    markerLabelStyle: "Index"   # Index, Name, Info1, Info2, None
    markers:                    # List of markers to place
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
  outputName: "{targetName}_{stackedCount}x{exposureTime}_{iso}_{calibration}"
  # Token-based output naming. Available tokens:
  # {parentDir} - Parent directory name
  # {firstInput} - First input filename (without extension)
  # {inputCount} - Number of input files processed
  # {stackedCount} - Number of successfully stacked files
  # {calibration} - Calibration types used (e.g., "bias,dark,flat")
  # Plus any custom tokens from filenameTokens configuration
  outputImageExtensions:        # List of output formats to generate
    - "tif"                     # TIFF format (best quality, large files)
    - "jpg"                     # JPEG format (smaller files, some quality loss)
    - "png"                     # PNG format (lossless, good for web)
  outputDirectory: "astro-process/output" # Final output directory
```

### Available Enhancement Step Types

The enhancement pipeline supports these step types (use exactly one per step):

- **`debayer`** - Convert Bayer pattern to RGB
- **`rotate`** - Rotate image by specified angle
- **`crop`** - Crop image to specified rectangle
- **`whitebalance`** - Apply color correction
- **`removeBackground`** - Remove background gradients
- **`linearPercentile`** - Linear histogram stretching
- **`sigmoid`** - S-curve contrast enhancement
- **`blur`** - Apply Gaussian blur
- **`sharpen`** - Apply sharpening filter
- **`unsharpMask`** - Apply unsharp mask filter
- **`reduceNoise`** - Multi-scale noise reduction
- **`highDynamicRange`** - Combine multiple enhancement results

### Parameter Ranges and Tips

**Star Detection:**
- `starThreshold`: 0.1 (more stars, noisier) to 0.5 (fewer stars, cleaner)
- `maxStars`: 50 (faster) to 500 (more accurate alignment)
- `positionTolerance`: 1.0 (strict) to 10.0 (tolerant)

**Enhancement Values:**
- `minPercentile`: 0.0001 (aggressive) to 0.01 (conservative)
- `maxPercentile`: 0.95 (conservative) to 0.9999 (aggressive)
- `sigmoid.midpoint`: 0.001 (very bright) to 1.0 (very dark)
- `sigmoid.strength`: 0.1 (subtle) to 5.0 (extreme)

**Noise Reduction:**
- Use lower threshold values (0.001) for aggressive noise reduction
- Use higher threshold values (0.1) for subtle noise reduction
- Multiple threshold levels process different noise scales

</details>

---

**Ready to process your first images?** Just run:
```sh
cd /your/images/directory
kimage-astro-process init
kimage-astro-process process
```

That's it! Your enhanced astrophotography images will be waiting in `astro-process/output/`.

