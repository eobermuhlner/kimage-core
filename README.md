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

The `ProcessConfig` is a comprehensive structure that allows you to specify how each step of the astrophotography workflow should be handled. It includes options for image formats, calibration settings, alignment, stacking, and image enhancement.

Here are the key configuration sections:

- **Format Config** (`FormatConfig`): Defines input/output formats, debayer settings, and directory paths for input images.
- **Calibration Config** (`CalibrateConfig`): Specifies the directories and settings for calibration frames, such as bias, dark, flat, and darkflat frames.
- **Alignment Config** (`AlignConfig`): Defines parameters for star detection and alignment.
- **Stacking Config** (`StackConfig`): Specifies the output directory for stacked images.
- **Enhancement Config** (`EnhanceConfig`): Provides settings for debayering, noise reduction, white balance, color stretching, cropping, and final formatting.

### Using YAML Config Files

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
$ kimage-astro-process stars
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

