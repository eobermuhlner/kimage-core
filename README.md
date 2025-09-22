# kimage Astro Image Processing

This documentation explains how to use the `ch.obermuhlner.kimage.astro.process` package to process astrophotography images. The package allows you to calibrate, align, stack, and enhance images, automating much of the astrophotography workflow. Focus is placed on achieving high-quality images through efficient star alignment, calibration, and enhancement operations.

## Getting Started

To start processing your astrophotography images, you need to configure the `ProcessConfig` according to your needs and invoke the appropriate processing commands. The main entry points are:

- **Process Configuration (`ProcessConfig`)**: Configure input formats, calibration, alignment, stacking, enhancement, annotation, and output settings.
- **Commands**: `init`, `process`, `config`, `stars`.

### Commands Overview

- **`init`**: Initializes the default configuration in a YAML file (`kimage-astro-process.yaml`). This file can be edited to customize your image processing pipeline.
- **`process`**: Runs the complete pipeline to calibrate, align, stack, and enhance the images based on the given configuration.
- **`config`**: Displays the current configuration.
- **`stars`**: Performs an analysis of the stars in the images, useful for estimating focus and image quality.

## Configuration

The configuration is a comprehensive structure that allows you to specify how each step of the astrophotography workflow should be handled. Below is a detailed breakdown of all configuration options available in `ProcessConfig` using YAML snippets for clarity.

### Format Configuration

Defines input/output formats, debayer settings, and directory paths for input images.

```yaml
format:
  inputImageExtension: "fit"   # The file extension for input images.
  outputImageExtension: "tif"  # The file extension for output images.
  inputDirectory: "."          # Directory where input images are stored.
  filenameTokens:              # Configuration for parsing filename tokens
    enabled: false             # Whether to parse filename tokens
    separator: "_"             # Token separator in filenames
    names:                     # Names of tokens in order
      - "targetType"
      - "targetName"
      - "exposureTime"
      - "binLevel"
      - "camera"
      - "iso"
      - "dateTime"
      - "temperature"
      - "sequenceNumber"
  debayer:                     # Configuration for debayering raw images.
    enabled: true              # Whether debayering is enabled.
    cleanupBadPixels: true     # Whether to clean up bad pixels.
    bayerPattern: "RGGB"       # The Bayer pattern of the camera sensor (RGGB, GRBG, GBRG, BGGR).
```

### Calibration Configuration

Specifies directories and settings for calibration frames, such as bias, dark, flat, and darkflat frames.

```yaml
calibrate:
  inputImageExtension: "fit"   # The file extension for calibration images.
  debayer:                      # Debayer settings for calibration frames.
    enabled: true
    bayerPattern: "RGGB"
  biasDirectory: "bias"         # Directory containing bias frames.
  flatDirectory: "flat"         # Directory containing flat frames.
  darkflatDirectory: "darkflat" # Directory containing dark flat frames.
  darkDirectory: "dark"         # Directory containing dark frames.
  searchParentDirectories: true  # Search parent directories for calibration frames.
  normalizeBackground:           # Configuration for background normalization.
    enabled: true                # Whether to normalize the background.
    offset: 0.01                 # Offset value for background normalization.
  calibratedOutputDirectory: "astro-process/calibrated" # Directory for calibrated images.
```

### Alignment Configuration

Defines parameters for star detection and alignment.

```yaml
align:
  starThreshold: 0.2             # Threshold for detecting stars.
  maxStars: 100                  # Maximum number of stars to use for alignment.
  positionTolerance: 2.0         # Tolerance for star position differences during alignment.
  alignedOutputDirectory: "astro-process/aligned" # Directory for aligned images.
```

### Stacking Configuration

Specifies the output directory for stacked images.

```yaml
stack:
  stackedOutputDirectory: "astro-process/stacked" # Directory for stacked images.
```

### Enhancement Configuration

The enhancement configuration uses a step-based approach where each enhancement operation is defined as a separate step in a sequence.

```yaml
enhance:
  enhancedOutputDirectory: "astro-process/enhanced" # Directory for enhanced images.
  measure:                               # Measurement area for percentile calculations
    enabled: false                       # Whether to use a specific area for measurements
    x: 0                                 # X coordinate of measurement area
    y: 0                                 # Y coordinate of measurement area
    width: 0                             # Width of measurement area
    height: 0                            # Height of measurement area
  regionOfInterest:                      # Region of interest to save separately
    enabled: false                       # Whether to save ROI images
    x: 0                                 # X coordinate of ROI
    y: 0                                 # Y coordinate of ROI
    width: 0                             # Width of ROI
    height: 0                            # Height of ROI
  histogram:                             # Histogram settings
    enabled: true                        # Whether to generate histograms
    histogramWidth: 1000                 # Width of the histogram
    histogramHeight: 400                 # Height of the histogram
    printPercentiles: false              # Whether to print histogram percentiles
  steps:                                 # Enhancement steps (processed in order)
    - rotate:                            # Rotation step
        angle: 0.0                       # Rotation angle in degrees (90, 180, 270 for exact rotations)
    - crop:                              # Cropping step
        x: 100                           # X coordinate (negative values count from right/bottom)
        y: 100                           # Y coordinate
        width: -100                      # Width (negative = image width - x + width)
        height: -100                     # Height (negative = image height - y + height)
    - debayer:                           # Debayer step (if not done earlier)
        enabled: true                    # Whether debayering is enabled
        cleanupBadPixels: true           # Whether to clean up bad pixels
        bayerPattern: "RGGB"             # Bayer pattern
    - whitebalance:                      # White balance step
        enabled: true                    # Whether white balance is enabled
        type: "Local"                    # Type: Global, Local, Custom
        fixPoints:                       # Fix points for local white balance
          type: "FourCorners"            # Grid, FourCorners, EightCorners, Custom
          borderDistance: 100            # Distance from border for corners
          gridSize: 2                    # Grid size (for Grid type)
          customPoints: []               # Custom points (for Custom type)
        localMedianRadius: 50            # Radius for local median
        valueRangeMin: 0.2               # Minimum value range
        valueRangeMax: 0.9               # Maximum value range
        customRed: 1.0                   # Custom red multiplier
        customGreen: 1.0                 # Custom green multiplier
        customBlue: 1.0                  # Custom blue multiplier
    - removeBackground:                  # Background removal step
        fixPoints:                       # Fix points configuration
          type: "FourCorners"            # Type of fix points
          borderDistance: 100            # Distance from border
          gridSize: 2                    # Grid size
          customPoints: []               # Custom points
        medianRadius: 50                 # Radius for median calculation
        power: 1.5                       # Interpolation power
        offset: 0.01                     # Background offset
    - linearPercentile:                  # Linear percentile stretch
        minPercentile: 0.0001            # Minimum percentile
        maxPercentile: 0.9999            # Maximum percentile
      addToHighDynamicRange: true        # Add result to HDR processing
    - sigmoid:                           # Sigmoid stretch
        midpoint: 0.01                   # Midpoint of sigmoid curve
        strength: 1.1                    # Strength of sigmoid curve
      addToHighDynamicRange: true        # Add result to HDR processing
    - blur:                              # Blur step
        strength: 0.1                    # Blur strength (0.0 = no blur, 1.0 = full blur)
    - sharpen:                           # Sharpen step
        strength: 0.5                    # Sharpen strength
    - unsharpMask:                       # Unsharp mask step
        radius: 1                        # Unsharp mask radius
        strength: 1.0                    # Unsharp mask strength
    - reduceNoise:                       # Noise reduction step
        algorithm: "MultiScaleMedianOverAllChannels" # Algorithm: MultiScaleMedianOverAllChannels, MultiScaleMedianOverGrayChannel
        thresholding: "Soft"             # Thresholding: Hard, Soft, Sigmoid, SigmoidLike
        thresholds:                      # Threshold levels
          - 0.01
          - 0.001
    - highDynamicRange:                  # HDR combination step
        saturationBlurRadius: 3          # Blur radius for saturation
        contrastWeight: 0.2              # Weight for contrast
        saturationWeight: 0.1            # Weight for saturation
        exposureWeight: 1.0              # Weight for exposure
```

### Annotation Configuration

Configuration for adding annotations, decorations, and custom drawings to the final images.

```yaml
annotate:
  enabled: false                         # Whether annotation is enabled
  annotatedOutputDirectory: "astro-process/annotated" # Directory for annotated images
  decorate:                              # Decoration settings
    enabled: true                        # Whether decoration is enabled
    title: "Object Name"                 # Title text (supports tokens)
    subtitle: "{stackedCount}x{exposureTime}" # Subtitle text (supports tokens)
    text: "Object Description"           # Additional text
    colorTheme: "Cyan"                   # Color theme: Green, Cyan, Red, Blue, Yellow, Magenta
    markerStyle: "Square"                # Marker style: Rectangle, Square, Circle
    markerLabelStyle: "Index"            # Label style: Index, Name, Info1, Info2, None
    markers:                             # List of markers to add
      - name: "Star"                     # Marker name
        x: 100                           # X coordinate
        y: 100                           # Y coordinate
        size: 100                        # Marker size
        info1: "Bright star"             # Additional info 1
        info2: "Magnitude 2.5"           # Additional info 2
  draw:                                  # Custom drawing settings
    enabled: false                       # Whether custom drawing is enabled
    margin:                              # Margins for drawing area
      top: 0                             # Top margin
      left: 0                            # Left margin
      bottom: 0                          # Bottom margin
      right: 0                           # Right margin
    steps:                               # Drawing steps (processed in order)
      - color:                           # Set drawing color
          color: "ffffff"                # Color in hex format
      - stroke:                          # Set stroke width
          width: 1.0                     # Stroke width
      - fontSize:                        # Set font size
          size: 12.0                     # Font size
      - line:                            # Draw line
          x1: 0                          # Start X coordinate
          y1: 0                          # Start Y coordinate
          x2: 100                        # End X coordinate
          y2: 100                        # End Y coordinate
      - rectangle:                       # Draw rectangle
          x: 0                           # X coordinate
          y: 0                           # Y coordinate
          width: 100                     # Rectangle width
          height: 100                    # Rectangle height
      - text:                            # Draw text
          x: 10                          # X coordinate
          y: 20                          # Y coordinate
          text: "Hello World"            # Text to draw (supports tokens)
```

### Output Configuration

Configuration for final output files and naming.

```yaml
output:
  outputName: "{targetName}_{stackedCount}x{exposureTime}_{iso}_{calibration}" # Output filename pattern (supports tokens)
  outputImageExtensions:                 # List of output formats
    - "tif"                              # TIFF format
    - "jpg"                              # JPEG format
    - "png"                              # PNG format
  outputDirectory: "astro-process/output" # Directory for final output images
```

### Available Tokens

The following tokens can be used in filename patterns, titles, and text:

- `{parentDir}` - Parent directory name
- `{firstInput}` - First input filename (without extension)
- `{inputCount}` - Number of input files
- `{stackedCount}` - Number of successfully stacked files
- `{calibration}` - Calibration types used (e.g., "bias,dark,flat")

When `filenameTokens.enabled` is true, additional tokens are available based on the parsed filename.

### Quick Mode Configuration

The quick mode allows you to process a limited number of images for testing purposes.

```yaml
quick: false                 # Enable or disable quick mode.
quickCount: 3                # Number of images to process in quick mode.
```

## Typical Workflow

### 1. Initialize the Configuration

First, create a default configuration file with:

```sh
$ kimage-astro-process init
```

This will generate an initial YAML file `kimage-astro-process.yaml`.

Edit this file to change settings such as input image directories, calibration options, alignment parameters, and enhancement steps.

### 2. Configure Image Processing

Edit `kimage-astro-process.yaml` to adjust settings. Here are some common modifications:

- **Input Directory**: Update the `inputDirectory` in `format` to point to where your images are stored.
- **Calibration**: Specify paths for calibration frames like `biasDirectory`, `flatDirectory`, etc., in the `calibrate` section.
- **Enhancement Steps**: Modify the `enhance.steps` array to add, remove, or reorder processing steps.
- **Output Settings**: Configure output formats, naming patterns, and directories in the `output` section.
- **Annotation**: Enable and configure annotations in the `annotate` section to add titles, markers, and custom drawings.

### 3. Run the Image Processing Pipeline

Run the complete processing pipeline:

```sh
$ kimage-astro-process process
```

This command will process all images according to the configuration, including calibration, alignment, stacking, and enhancement.

### 4. View and Analyze Stars

To analyze star quality in your images, run:

```sh
$ kimage-astro-process stars
```

This will output metrics such as the number of stars, Full Width at Half Maximum (FWHM), and sharpness scores, which help evaluate the quality of your focus and tracking. The analysis includes a composite score that considers star sharpness, count, overall sharpness (via Laplacian), and brightness balance.

## Key Features and Settings

### Calibration

- **Bias, Dark, Flat Calibration**: Ensure you provide correct directories for these calibration images in the configuration.
- **Normalization**: The `normalizeBackground` setting helps balance the background, improving the quality of stacked results.

### Alignment

- **Star Detection**: Configured via `starThreshold` and `maxStars`, the alignment phase detects stars and aligns images based on their positions.
- **Tolerance Settings**: `positionTolerance` can be adjusted to refine the alignment accuracy.

### Stacking

The `stackedOutputDirectory` defines where the stacked images are saved. This process combines multiple images to reduce noise and enhance the signal-to-noise ratio.

### Enhancement

The enhancement pipeline uses a step-based approach where operations are applied in sequence:

- **Step-Based Processing**: Each enhancement operation (crop, rotate, white balance, etc.) is a separate step that can be configured individually.
- **Linear Percentile Stretching**: Enhances image contrast by stretching the histogram between specified percentiles.
- **Sigmoid Stretching**: Applies S-curve stretching for more natural contrast enhancement.
- **High Dynamic Range (HDR)**: Combines multiple processing steps to create HDR images with enhanced detail.
- **Noise Reduction**: Two algorithms available (`MultiScaleMedianOverAllChannels` or `MultiScaleMedianOverGrayChannel`) with configurable thresholding.
- **White Balance**: Global, local, or custom white balancing to correct color casts.
- **Background Removal**: Removes uneven backgrounds using interpolation from fix points.
- **Additional Operations**: Blur, sharpen, unsharp mask, rotation, and cropping.

## Example Configuration

Here is the default configuration that gets generated by the `init` command:

```yaml
quick: false
quickCount: 3
format:
  debayer:
    enabled: true
    bayerPattern: RGGB
  inputImageExtension: fit
  outputImageExtension: tif
  filenameTokens:
    enabled: false
    names:
      - targetType
      - targetName
      - exposureTime
      - binLevel
      - camera
      - iso
      - dateTime
      - temperature
      - sequenceNumber
enhance:
  steps:
  - rotate:
      angle: 0
  - crop:
      x: 100
      y: 100
      width: -100
      height: -100
  - whitebalance:
      enabled: true
      type: Local
      fixPoints:
        type: FourCorners
        borderDistance: 100
      localMedianRadius: 50
      valueRangeMin: 0.2
      valueRangeMax: 0.9
  - linearPercentile:
      minPercentile: 0.0001
      maxPercentile: 0.9999
    addToHighDynamicRange: true
  - blur:
      strength: 0.1
  - sigmoid:
      midpoint: 0.01
      strength: 1.1
  - linearPercentile:
      minPercentile: 0.0001
      maxPercentile: 0.9999
    addToHighDynamicRange: true
  - sigmoid:
      midpoint: 0.4
      strength: 1.1
    addToHighDynamicRange: true
  - sigmoid:
      midpoint: 0.4
      strength: 1.1
    addToHighDynamicRange: true
  - sigmoid:
      midpoint: 0.4
      strength: 1.1
    addToHighDynamicRange: true
  - sigmoid:
      midpoint: 0.4
      strength: 1.1
    addToHighDynamicRange: true
  - highDynamicRange:
      contrastWeight: 0.2
      saturationWeight: 0.1
      exposureWeight: 1.0
  - sigmoid:
      midpoint: 0.3
      strength: 1.1
  - reduceNoise:
      thresholding: Soft
      thresholds:
        - 0.01
        - 0.001
annotate:
  enabled: false
  decorate:
    enabled: true
    title: "Object Name"
    subtitle: "{stackedCount}x{exposureTime}"
    text: "Object Description"
    markerStyle: Square
    markerLabelStyle: Index
    colorTheme: Cyan
output:
  outputName: "{targetName}_{stackedCount}x{exposureTime}_{iso}_{calibration}"
  outputImageExtensions:
    - tif
    - jpg
    - png
```

This configuration demonstrates a sophisticated enhancement pipeline with multiple sigmoid stretches, HDR processing, and noise reduction.

## Tips for Effective Image Processing

- **Use Good Calibration Frames**: The quality of bias, dark, and flat frames directly impacts the final image quality.
- **Adjust Star Thresholds Carefully**: If the alignment is inaccurate, tweak `starThreshold` and `positionTolerance`.
- **Quick Mode for Testing**: Use `quick: true` to process a limited number of images while testing your workflow.
- **Check stacked `max` file**: The stacked `max` file shows problems with the aligned images. Check for elongated, blurry or streaky stars and delete the culprit single frame.
- **Step-Based Enhancement**: The order of enhancement steps matters. Generally follow: crop → white balance → stretching → noise reduction.
- **HDR Processing**: Use `addToHighDynamicRange: true` on steps you want to include in HDR combination, then add a `highDynamicRange` step.
- **Token-Based Naming**: Use filename tokens to organize outputs systematically and extract metadata from filenames.
- **Region of Interest**: Enable `regionOfInterest` in enhancement config to save cropped versions of each step for detailed analysis.

## Conclusion

The `kimage` astrophotography processing package provides a powerful and flexible way to calibrate, align, stack, and enhance astrophotography images. By configuring the provided YAML file and executing the appropriate commands, you can streamline your astrophotography workflow to produce high-quality final images.

For more detailed configurations, explore the generated YAML and experiment with different settings to achieve the best results for your specific setup.

