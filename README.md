# kimage Astro Image Processing

This documentation explains how to use the `ch.obermuhlner.kimage.astro.process` package to process astrophotography images. The package allows you to calibrate, align, stack, and enhance images, automating much of the astrophotography workflow. Focus is placed on achieving high-quality images through efficient star alignment, calibration, and enhancement operations.

## Getting Started

To start processing your astrophotography images, you need to configure the `ProcessConfig` according to your needs and invoke the appropriate processing commands. The main entry points are:

- **Process Configuration (`ProcessConfig`)**: Configure input formats, calibration, alignment, stacking, and enhancement settings.
- **Commands**: `init`, `process`, `config`, `stars`.

### Commands Overview

- **`init`**: Initializes the default configuration in a YAML file (`kimage-astro-process.yaml`). This file can be edited to customize your image processing pipeline.
- **`process`**: Runs the complete pipeline to calibrate, align, stack, and enhance the images based on the given configuration.
- **`config`**: Displays the current configuration.
- **`stars`**: Performs an analysis of the stars in the images, useful for estimating focus and image quality.

## Configuration (`ProcessConfig`)

The `ProcessConfig` is a comprehensive structure that allows you to specify how each step of the astrophotography workflow should be handled. Below is a detailed breakdown of all configuration options available in `ProcessConfig` using YAML snippets for clarity.

### Format Configuration (`FormatConfig`)

Defines input/output formats, debayer settings, and directory paths for input images.

```yaml
format:
  inputImageExtension: "fit"   # The file extension for input images.
  outputImageExtension: "tif"  # The file extension for output images.
  inputDirectory: "."          # Directory where input images are stored.
  debayer:                      # Configuration for debayering raw images.
    enabled: true               # Whether debayering is enabled.
    cleanupBadPixels: true      # Whether to clean up bad pixels.
    bayerPattern: "RGGB"       # The Bayer pattern of the camera sensor (e.g., RGGB).
```

### Calibration Configuration (`CalibrateConfig`)

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

### Alignment Configuration (`AlignConfig`)

Defines parameters for star detection and alignment.

```yaml
align:
  starThreshold: 0.2             # Threshold for detecting stars.
  maxStars: 100                  # Maximum number of stars to use for alignment.
  positionTolerance: 2.0         # Tolerance for star position differences during alignment.
  alignedOutputDirectory: "astro-process/aligned" # Directory for aligned images.
```

### Stacking Configuration (`StackConfig`)

Specifies the output directory for stacked images.

```yaml
stack:
  stackedOutputDirectory: "astro-process/stacked" # Directory for stacked images.
```

### Enhancement Configuration (`EnhanceConfig`)

The enhancement configuration is divided into several parts: debayering, rotation, cropping, noise reduction, background correction, white balance, color stretching, histogram settings, and final format.

#### Debayering Configuration

Settings related to debayering during the enhancement phase.

```yaml
enhance:
  enabled: true                           # Whether image enhancement is enabled.
  enhancedOutputDirectory: "astro-process/enhanced" # Directory for enhanced images.
  debayer:                                # Debayer configuration for enhancement.
    enabled: false
```

#### Rotation Configuration

Settings for rotating images.

```yaml
  rotate:                                 # Configuration for rotating images.
    angle: 0.0                            # Rotation angle in degrees.
```

#### Cropping Configuration

Settings for cropping images.

```yaml
  crop:                                   # Configuration for cropping images.
    enabled: false
    x: 0                                  # X coordinate of the top-left corner.
    y: 0                                  # Y coordinate of the top-left corner.
    width: 0                              # Width of the crop area.
    height: 0                             # Height of the crop area.
```

#### Noise Reduction Configuration

Settings for reducing noise in images.

```yaml
  noise:                                  # Noise reduction settings.
    enabled: true
    algorithm: "MultiScaleMedianOverAllChannels" # Noise reduction algorithm to use.
    thresholding: "Soft"                 # Type of thresholding (Hard, Soft, Sigmoid).
    thresholds:                           # Threshold levels for noise reduction.
      - 0.0001
```

#### Background Correction Configuration

Settings for correcting the background of images.

```yaml
  background:                             # Background correction settings.
    enabled: false
    fixPoints:                            # Configuration for fixed points.
      type: "FourCorners"                # Type of fixed points (Grid, FourCorners, EightCorners, Custom).
      borderDistance: 100                 # Distance from the image border.
      gridSize: 2                         # Size of the grid for fixed points.
      customPoints: []                    # List of custom points.
    medianRadius: 50                      # Radius for median calculation.
    power: 1.5                            # Power for interpolation.
    offset: 0.01                          # Offset for background correction.
```

#### White Balance Configuration

Settings for white balance adjustments.

```yaml
  whitebalance:                           # White balance settings.
    enabled: true
    type: "Global"                       # White balance type (Global, Local, Custom).
    fixPoints:                            # Fix points for local white balance.
      type: "FourCorners"
      borderDistance: 100
    localMedianRadius: 50                 # Radius for local median calculation.
    valueRangeMin: 0.0                    # Minimum value range for white balance.
    valueRangeMax: 0.9                    # Maximum value range for white balance.
    customRed: 1.0                        # Custom red balance.
    customGreen: 1.0                      # Custom green balance.
    customBlue: 1.0                       # Custom blue balance.
```

#### Color Stretching Configuration

Settings for color stretching to enhance image contrast and dynamic range.

```yaml
  colorStretch:                           # Color stretching settings.
    enabled: true
    measure:                              # Measurement area for color stretching.
      enabled: false
      x: 0
      y: 0
      width: 0
      height: 0
    steps:                                # Steps for color stretching.
      - type: "LinearPercentile"          # Type of color stretch (LinearPercentile).
        linearPercentileMin: 0.0001        # Minimum percentile for linear stretching.
        linearPercentileMax: 0.9999        # Maximum percentile for linear stretching.
        addToHighDynamicRange: false       # Add to HDR calculation.
      - type: "Sigmoid"                   # Type of color stretch (Sigmoid).
        sigmoidMidpoint: 0.1               # Midpoint for sigmoid stretching.
        sigmoidFactor: 10.0                # Factor for sigmoid stretching.
        addToHighDynamicRange: false       # Add to HDR calculation.
      - type: "Blur"                      # Type of color stretch (Blur).
        blurStrength: 0.1                  # Strength of the blur effect.
        addToHighDynamicRange: false       # Add to HDR calculation.
      - type: "HighDynamicRange"          # Type of color stretch (HighDynamicRange).
        addToHighDynamicRange: false       # Add to HDR calculation.       # Add to HDR calculation.
```

#### Histogram Configuration

Settings for generating histograms of the images.

```yaml
  histogram:                              # Histogram settings.
    enabled: true
    histogramWidth: 1000                  # Width of the histogram.
    histogramHeight: 400                  # Height of the histogram.
    printPercentiles: false               # Whether to print histogram percentiles.
```

#### Final Format Configuration

Settings for the final output format of enhanced images.

```yaml
  finalFormat:                            # Final output format settings.
    outputImageExtensions:                # List of output image formats.
      - "tif"
      - "jpg"
      - "png"
```

### Quick Mode Configuration

The quick mode allows you to process a limited number of images for testing purposes.

```yaml
quick: false                 # Enable or disable quick mode.
quickCount: 3                # Number of images to process in quick mode.
```

## Using YAML Config Files

After running `init`, you will have a default configuration file (`kimage-astro-process.yaml`). Edit this file to change settings such as input image directories, calibration options, alignment parameters, and enhancement steps.

## Typical Workflow

### 1. Initialize the Configuration

First, create a default configuration file with:

```sh
$ kimage-astro-process init
```

This will generate a YAML file you can modify according to your needs.

### 2. Configure Image Processing

Edit `kimage-astro-process.yaml` to adjust settings. Here are some common modifications:

- **Input Directory**: Update the `inputDirectory` in `format` to point to where your images are stored.
- **Calibration**: Specify paths for calibration frames like `biasDirectory`, `flatDirectory`, etc., in the `calibrate` section.
- **Enhancements**: Enable or disable features such as noise reduction, color stretching, and rotation in the `enhance` section.

### 3. Run the Image Processing Pipeline

Run the complete processing pipeline:

```sh
$ kimage-astro-process process
```

This command will process all images according to the configuration, including calibration, alignment, stacking, and enhancement.

### 4. View and Analyze Stars

To analyze star quality in your images, run:

```sh
$ java -jar kimage-astro-process.jar stars
```

This will output metrics such as the number of stars, Full Width at Half Maximum (FWHM), and sharpness scores, which help evaluate the quality of your focus and tracking.

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

- **Color Stretching**: Options like `stretchLinearPercentile` and `stretchSigmoidLike` allow you to enhance image contrast and dynamic range.
- **Noise Reduction**: Different algorithms are available (`MultiScaleMedianOverAllChannels` or `MultiScaleMedianOverGrayChannel`), allowing you to reduce noise while retaining detail.
- **White Balance**: You can apply global, local, or custom white balancing depending on the color needs of your images.

## Example Configuration

Here is a snippet from the default configuration for enhancement:

```yaml
enhance:
  enabled: true
  noise:
    enabled: true
    thresholding: Soft
    thresholds:
      - 0.01
      - 0.001
  colorStretch:
    enabled: true
    steps:
      - type: LinearPercentile
        linearPercentileMin: 0.0001
        linearPercentileMax: 0.9999
```

This example configures noise reduction with soft thresholding and uses linear percentile stretching for contrast enhancement.

## Tips for Effective Image Processing

- **Use Good Calibration Frames**: The quality of bias, dark, and flat frames directly impacts the final image quality.
- **Adjust Star Thresholds Carefully**: If the alignment is inaccurate, tweak `starThreshold` and `positionTolerance`.
- **Quick Mode for Testing**: Use `quick: true` to process a limited number of images while testing your workflow.

## Conclusion

The `kimage` astrophotography processing package provides a powerful and flexible way to calibrate, align, stack, and enhance astrophotography images. By configuring the provided YAML file and executing the appropriate commands, you can streamline your astrophotography workflow to produce high-quality final images.

For more detailed configurations, explore the generated YAML and experiment with different settings to achieve the best results for your specific setup.

