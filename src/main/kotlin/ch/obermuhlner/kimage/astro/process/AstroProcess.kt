package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.align.*
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.*
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme.Green
import ch.obermuhlner.kimage.astro.annotate.DeepSkyObjects
import ch.obermuhlner.kimage.astro.annotate.WCSConverter
import ch.obermuhlner.kimage.astro.annotate.WCSParser
import ch.obermuhlner.kimage.astro.background.*
import ch.obermuhlner.kimage.astro.color.histogram
import ch.obermuhlner.kimage.astro.color.stretchAutoSTF
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchSigmoidLike
import ch.obermuhlner.kimage.astro.cosmetic.CosmeticCorrectionConfig
import ch.obermuhlner.kimage.astro.cosmetic.CosmeticCorrectionMode
import ch.obermuhlner.kimage.astro.cosmetic.cosmeticCorrect
import ch.obermuhlner.kimage.astro.platesolve.AstapPlateSolver
import ch.obermuhlner.kimage.core.image.*
import ch.obermuhlner.kimage.core.image.awt.graphics
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.DebayerInterpolation
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.div
import ch.obermuhlner.kimage.core.image.filter.*
import ch.obermuhlner.kimage.core.image.hdr.highDynamicRange
import ch.obermuhlner.kimage.core.image.histogram.histogramImage
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.lrgb.replaceBrightness
import ch.obermuhlner.kimage.core.image.noise.*
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.image.stack.*
import ch.obermuhlner.kimage.core.image.stack.StackConfig as CoreStackConfig
import ch.obermuhlner.kimage.core.matrix.FloatMatrix
import ch.obermuhlner.kimage.core.image.statistics.normalizeImage
import ch.obermuhlner.kimage.core.image.transform.rotateLeft
import ch.obermuhlner.kimage.core.image.transform.rotateRight
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.values.onEach
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalance
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalanceCustom
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalanceGlobal
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalanceLocal
import ch.obermuhlner.kimage.core.math.*
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.values.values
import ch.obermuhlner.kimage.util.elapsed
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.math.*

data class ProcessConfig(
    var quick: Boolean = false,
    var quickCount: Int = 3,
    var target: TargetConfig = TargetConfig(),
    var format: FormatConfig = FormatConfig(),
    var calibrate: CalibrateConfig = CalibrateConfig(),
    var normalizeBackground: NormalizeBackgroundConfig = NormalizeBackgroundConfig(),
    var platesolve: PlatesolveConfig = PlatesolveConfig(),
    var align: AlignConfig = AlignConfig(),
    var stack: StackConfig = StackConfig(),
    var enhance: EnhanceConfig = EnhanceConfig(),
    var annotate: AnnotateConfig = AnnotateConfig(),
    var output: OutputFormatConfig = OutputFormatConfig(),
    var sources: MutableList<SourceConfig>? = null,
)

data class TargetConfig(
    var name: String? = null,
    var ra: Double? = null,
    var dec: Double? = null,
    var angle: Double? = null,
    var fov: Double? = null,
)

data class PlatesolveConfig(
    var enabled: Boolean = false,
    var platesolveType: PlatesolveType = PlatesolveType.Astap,
    var executable: String? = null,
)

enum class PlatesolveType {
    Astap,
    Internal,
    Custom
}

data class AnnotateConfig(
    var enabled: Boolean = false,
    var decorate: DecorationConfig = DecorationConfig(),
    var draw: DrawConfig = DrawConfig(),
    var annotatedOutputDirectory: String = "astro-process/annotated",
)

data class DecorationConfig(
    var enabled: Boolean = false,
    var title: String = "",
    var subtitle: String = "",
    var text: String = "",
    var colorTheme: ColorTheme = Green,
    var platesolveMarkers: PlatesolveMarkersConfig = PlatesolveMarkersConfig(),
    var grid: Boolean = false,
    var markerStyle: MarkerStyle = MarkerStyle.Rectangle,
    var markerLabelStyle: MarkerLabelStyle = MarkerLabelStyle.Index,
    var markers: MutableList<MarkerConfig> = mutableListOf()
)

data class PlatesolveMarkersConfig(
    var enabled: Boolean = true,
    var magnitude: Double = Double.MAX_VALUE,
    var minObjectSize: Int = 50,
    var whiteList: List<String>? = null,
    var blackList: List<String>? = null,
)

data class MarkerConfig(
    var name: String = "",
    var x: Int = 0,
    var y: Int = 0,
    var size: Int = 100,
    var info1: String = "",
    var info2: String = "",
)

data class DrawConfig(
    var enabled: Boolean = false,
    var margin: MarginConfig = MarginConfig(),
    var steps: MutableList<DrawStepConfig> = mutableListOf(),
)

data class DrawStepConfig(
    var color: DrawStepColorConfig? = null,
    var stroke: DrawStepStrokeConfig? = null,
    var fontSize: DrawStepFontSizeConfig? = null,
    var line: DrawStepLineConfig? = null,
    var rectangle: DrawStepRectangleConfig? = null,
    var text: DrawStepTextConfig? = null,
) {
    val type: DrawStepType
        get() = when {
            color != null -> DrawStepType.Color
            stroke != null -> DrawStepType.Stroke
            fontSize != null -> DrawStepType.FontSize
            line != null -> DrawStepType.Line
            rectangle != null -> DrawStepType.Rectangle
            text != null -> DrawStepType.Text
            else -> throw IllegalArgumentException("No annotate step configuration found")
        }
}

enum class DrawStepType {
    Color,
    Stroke,
    FontSize,
    Line,
    Rectangle,
    Text,
}

data class DrawStepColorConfig(
    var color: String = "ffffff",
)

data class DrawStepStrokeConfig(
    var width: Float = 1.0f,
)

data class DrawStepFontSizeConfig(
    var size: Float = 1.0f,
)

data class DrawStepLineConfig(
    var x1: Int = 0,
    var y1: Int = 0,
    var x2: Int = 0,
    var y2: Int = 0,
)

data class DrawStepRectangleConfig(
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 0,
    var height: Int = 0,
)

data class DrawStepTextConfig(
    var x: Int = 0,
    var y: Int = 0,
    var text: String = ""
)

data class MarginConfig(
    var top: Int = 0,
    var left: Int = 0,
    var bottom: Int = 0,
    var right: Int = 0,
)
data class FormatConfig(
    var inputImageExtension: String = "fit",
    var inputDirectory: String = ".",
    var filenameTokens: FilenameTokensConfig = FilenameTokensConfig(),
    var debayer: DebayerConfig = DebayerConfig(),
    var outputImageExtension: String = "tif",
    var minExposureSeconds: Double? = null,
    var maxExposureSeconds: Double? = null,
)

data class FilenameTokensConfig(
    var enabled: Boolean = false,
    var separator: String = "_",
    var names: MutableList<String> = mutableListOf()
)

data class DebayerConfig(
    var enabled: Boolean = true,
    var cleanupBadPixels: Boolean = true,
    var bayerPattern: BayerPattern = BayerPattern.RGGB,
    var interpolation: DebayerInterpolation = DebayerInterpolation.AMaZE,
)

data class CalibrateConfig(
    var enabled: Boolean = true,
    var inputImageExtension: String = "fit",
    var debayer: DebayerConfig = DebayerConfig(),
    var biasDirectory: String = "bias",
    var flatDirectory: String = "flat",
    var darkflatDirectory: String = "darkflat",
    var darkDirectory: String = "dark",
    var searchParentDirectories: Boolean = true,
    var darkskip: Boolean = false,
    var darkScalingFactor: Double = 1.0,
    var calibratedOutputDirectory: String = "astro-process/calibrated",
)

data class NormalizeBackgroundConfig(
    var enabled: Boolean = true,
    var normalize: BackgroundNormalizeConfig = BackgroundNormalizeConfig(),
    var neutralize: BackgroundNeutralizeConfig = BackgroundNeutralizeConfig(),
    var outputDirectory: String = "astro-process/normalized",
)

data class BackgroundNormalizeConfig(
    var enabled: Boolean = true,
    var offset: Double = 0.01,
)

data class BackgroundNeutralizeConfig(
    var enabled: Boolean = false,
    var offset: Double = 0.01,
)

data class AlignConfig(
    var starThreshold: Double = 0.2,
    var maxStars: Int = 100,
    var positionTolerance: Double = 2.0,
    var alignedOutputDirectory: String = "astro-process/aligned",
)

data class StackConfig(
    var stackedOutputDirectory: String = "astro-process/stacked",
    var algorithm: StackAlgorithm = StackAlgorithm.Median,
    var perFrame: Boolean = false,
    var kappa: Double = 2.0,
    var iterations: Int = 10,
    var precision: StackPrecision = StackPrecision.Float,
    var tempDir: String? = null,
    var maxDiskSpaceBytes: String = "max",
    var drizzle: DrizzleConfig = DrizzleConfig(),
)

data class EnhanceConfig(
    var measure: RectangleConfig = RectangleConfig(),
    var regionOfInterest: RectangleConfig = RectangleConfig(),
    var steps: MutableList<EnhanceStepConfig> = mutableListOf(),
    var branches: MutableList<BranchConfig>? = null,
    var input: String? = null,
    var histogram: HistogramConfig = HistogramConfig(),
    var enhancedOutputDirectory: String = "astro-process/enhanced",
)

data class EnhanceStepConfig(
    var enabled: Boolean = true,
    var debayer: DebayerConfig? = null,
    var crop: RectangleConfig? = null,
    var rotate: RotateConfig? = null,
    var reduceNoise: ReduceNoiseConfig? = null,
    var whitebalance: WhitebalanceConfig? = null,
    var removeBackground: RemoveBackgroundConfig? = null,
    var autoStretch: AutoStretchConfig? = null,
    var sigmoid: SigmoidConfig? = null,
    var linearPercentile: LinearPercentileConfig? = null,
    var blur: BlurConfig? = null,
    var sharpen: SharpenConfig? = null,
    var unsharpMask: UnsharpMaskConfig? = null,
    var highDynamicRange: HighDynamicRangeConfig? = null,
    var cosmeticCorrection: CosmeticCorrectionConfig? = null,
    var deconvolve: DeconvolutionConfig? = null,
    var extractStars: ExtractStarsConfig? = null,
    var decompose: DecomposeConfig? = null,
    var compositeChannels: CompositeChannelsConfig? = null,
    var mergeWith: MergeWithConfig? = null,
    var stackSources: StackSourcesConfig? = null,
    var maskedProcess: MaskedProcessConfig? = null,
    var quantize: QuantizeConfig? = null,
    var edge: EdgeConfig? = null,
    var addToHighDynamicRange: Boolean = false,
) {
    val type: EnhanceStepType
        get() = when {
            debayer != null -> EnhanceStepType.Debayer
            crop != null -> EnhanceStepType.Crop
            rotate != null -> EnhanceStepType.Rotate
            reduceNoise != null -> EnhanceStepType.ReduceNoise
            whitebalance != null -> EnhanceStepType.Whitebalance
            removeBackground != null -> EnhanceStepType.RemoveBackground
            autoStretch != null -> EnhanceStepType.AutoStretch
            sigmoid != null -> EnhanceStepType.Sigmoid
            linearPercentile != null -> EnhanceStepType.LinearPercentile
            blur != null -> EnhanceStepType.Blur
            sharpen != null -> EnhanceStepType.Sharpen
            unsharpMask != null -> EnhanceStepType.UnsharpMask
            highDynamicRange != null -> EnhanceStepType.HighDynamicRange
            cosmeticCorrection != null -> EnhanceStepType.CosmeticCorrection
            deconvolve != null -> EnhanceStepType.Deconvolve
            extractStars != null -> EnhanceStepType.ExtractStars
            decompose != null -> EnhanceStepType.Decompose
            compositeChannels != null -> EnhanceStepType.CompositeChannels
            mergeWith != null -> EnhanceStepType.MergeWith
            stackSources != null -> EnhanceStepType.StackSources
            maskedProcess != null -> EnhanceStepType.MaskedProcess
            quantize != null -> EnhanceStepType.Quantize
            edge != null -> EnhanceStepType.Edge
            else -> throw IllegalArgumentException("No enhancement step configuration found")
        }
}

enum class EnhanceStepType {
    Debayer,
    Crop,
    Rotate,
    Whitebalance,
    RemoveBackground,
    AutoStretch,
    LinearPercentile,
    Sigmoid,
    Blur,
    Sharpen,
    UnsharpMask,
    ReduceNoise,
    HighDynamicRange,
    CosmeticCorrection,
    Deconvolve,
    ExtractStars,
    Decompose,
    CompositeChannels,
    MergeWith,
    StackSources,
    MaskedProcess,
    Quantize,
    Edge,
}

data class HighDynamicRangeConfig(
    var saturationBlurRadius: Int = 3,
    var contrastWeight: Double = 0.2,
    var saturationWeight: Double = 0.1,
    var exposureWeight: Double = 1.0,
)

data class SharpenConfig(
    var strength: Double = 0.5,
)

data class RotateConfig(
    var angle: Double = 0.0,
)

data class RectangleConfig(
    var enabled: Boolean = false,
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 0,
    var height: Int = 0,
)

enum class Thresholding {
    Hard,
    Soft,
    Sigmoid,
    SigmoidLike
}

enum class NoiseReductionAlgorithm {
    MultiScaleMedianOverAllChannels,
    MultiScaleMedianOverGrayChannel,
}

data class ReduceNoiseConfig(
    var algorithm: NoiseReductionAlgorithm = NoiseReductionAlgorithm.MultiScaleMedianOverAllChannels,
    var thresholding: Thresholding = Thresholding.Soft,
    var thresholds: MutableList<Double> = mutableListOf(0.0001)
)

data class CosmeticCorrectionConfig(
    var enabled: Boolean = true,
    var mode: CosmeticCorrectionMode = CosmeticCorrectionMode.Both,
    var sigmaThreshold: Double = 5.0,
    var checkRadius: Int = 2,
    var fixRadius: Int = 1,
    var minNetNoise: Double = 0.01
)

data class DeconvolutionConfig(
    var algorithm: DeconvolutionAlgorithm = DeconvolutionAlgorithm.RichardsonLucy,
    var psfSigma: Double = 1.5,
    var iterations: Int = 20,
    var noiseLevel: Double = 0.01,
)

enum class DeconvolutionAlgorithm {
    RichardsonLucy,
    Wiener
}

data class RemoveBackgroundConfig(
    var fixPoints: FixPointsConfig = FixPointsConfig(),
    var medianRadius: Int = 50,
    var power: Double = 1.5,
    var offset: Double = 0.01,
)

data class FixPointsConfig(
    var type: FixPointType = FixPointType.FourCorners,
    var borderDistance: Int = 100,
    var gridSize: Int = 2,
    var customPoints: MutableList<PointXYConfig> = mutableListOf()
)

data class PointXYConfig(
    var x: Int = 0,
    var y: Int = 0,
)

enum class FixPointType {
    Grid,
    FourCorners,
    EightCorners,
    Custom
}

data class WhitebalanceConfig(
    var enabled: Boolean = true,
    var type: WhitebalanceType = WhitebalanceType.Global,
    var fixPoints: FixPointsConfig = FixPointsConfig(),
    var localMedianRadius: Int = 50,
    var valueRangeMin: Double = 0.0,
    var valueRangeMax: Double = 0.9,
    var customRed: Double = 1.0,
    var customGreen: Double = 1.0,
    var customBlue: Double = 1.0
)

enum class WhitebalanceType {
    Global,
    Local,
    Custom
}

data class AutoStretchConfig(
    var shadowClipping: Double = 2.8,
    var targetBackground: Double = 0.1,
    var perChannel: Boolean = false,
)

data class SigmoidConfig(
    var midpoint: Double = 0.5,
    var strength: Double = 1.0,
)

data class LinearPercentileConfig(
    var minPercentile: Double = 0.0001,
    var maxPercentile: Double = 0.9999,
)

data class BlurConfig(
    var strength: Double = 0.1,
)

data class QuantizeConfig(
    var levels: Int = 16,
) {
    init {
        require(levels >= 2) { "levels must be >= 2, got $levels" }
    }
}

enum class EdgeAlgorithm {
    Sobel, Sobel3, Sobel5, Laplacian, EdgeStrong, EdgeCross, EdgeDiagonal, EdgeEnhancement
}

data class EdgeConfig(
    var algorithm: EdgeAlgorithm = EdgeAlgorithm.Sobel,
    var strength: Double = 1.0,
)

data class UnsharpMaskConfig(
    var radius: Int = 1,
    var strength: Double = 1.0,
)

data class HistogramConfig(
    var enabled: Boolean = true,
    var histogramWidth: Int = 1000,
    var histogramHeight: Int = 400,
    var printPercentiles: Boolean = false,
)

data class OutputFormatConfig(
    var outputName: String = "{${InfoTokens.firstInput.name}}_{${InfoTokens.calibration.name}}_{${InfoTokens.stackedCount.name}}x",
    var outputImageExtensions: MutableList<String> = mutableListOf("tif", "jpg", "png"),
    var outputDirectory: String = "astro-process/output",
)

enum class InfoTokens {
    parentDir,
    firstInput,
    inputCount,
    stackedCount,
    calibration,
    branchName,
    frameIndex,
}

data class BranchConfig(
    var name: String = "",
    var steps: MutableList<EnhanceStepConfig> = mutableListOf(),
)

data class SourceConfig(
    var name: String = "",
    var format: FormatConfig = FormatConfig(),
    var calibrate: CalibrateConfig = CalibrateConfig(),
    var align: AlignConfig = AlignConfig(),
    var stack: StackConfig = StackConfig(),
)

data class ExtractStarsConfig(
    var factor: Double = 2.0,
    var softMaskBlurRadius: Int = 5,
    var starsBranch: BranchConfig = BranchConfig(name = "stars"),
    var backgroundBranch: BranchConfig = BranchConfig(name = "background"),
)

enum class DecomposeMode { LRGB, RGB, HSB }

data class DecomposeConfig(
    var mode: DecomposeMode = DecomposeMode.LRGB,
    var luminance: BranchConfig? = null,
    var color: BranchConfig? = null,
    var red: BranchConfig? = null,
    var green: BranchConfig? = null,
    var blue: BranchConfig? = null,
    var hue: BranchConfig? = null,
    var saturation: BranchConfig? = null,
    var brightness: BranchConfig? = null,
)

data class CompositeChannelsConfig(
    var red: String = "",
    var green: String = "",
    var blue: String = "",
)

enum class MergeMethod { Hdr }

data class MergeWithConfig(
    var image: String = "",
    var method: MergeMethod = MergeMethod.Hdr,
)

data class StackSourcesConfig(
    var images: MutableList<String> = mutableListOf(),
    var algorithm: StackAlgorithm = StackAlgorithm.Median,
    var weights: MutableList<Double>? = null,
    var outputName: String? = null,
)

data class MaskedProcessConfig(
    var mask: MaskConfig = MaskConfig(),
    var insideMask: BranchConfig = BranchConfig(name = "inside"),
    var outsideMask: BranchConfig = BranchConfig(name = "outside"),
)

enum class MaskSource { Stars, Luminance, File, Platesolve }

data class MaskConfig(
    var source: MaskSource = MaskSource.Luminance,
    var threshold: Double = 0.3,
    var blur: Int = 10,
    var factor: Double = 2.0,
    var maskFile: String? = null,
    var objectName: String? = null,
)

val defaultAstroProcessConfigText = """
# kimage Astrophotography Processing Configuration
#
# This file defines the complete pipeline for processing astrophotography images:
# 1. Calibration - Remove sensor artifacts using bias/dark/flat frames
# 2. Alignment - Align images based on star positions
# 3. Stacking - Combine aligned images to reduce noise
# 4. Enhancement - Apply contrast stretching, noise reduction, etc.
#
# Most settings work well by default. Common changes:
# - Set inputImageExtension to your file format ("fit", "tif", "jpg", etc.)
# - Enable filenameTokens if your files have structured names
# - Enable quick mode for testing (quick: true, quickCount: 3)
# - Adjust enhancement steps for different brightness/contrast needs

# Quick Mode Settings - Process limited images for testing
quick: false                    # Set to true for testing with fewer images
quickCount: 3                   # Number of images to process in quick mode

# Input/Output Format Configuration
format:
  debayer:                      # Debayering configuration for raw camera files
    enabled: true               # Convert Bayer pattern to RGB (disable if already RGB)
    bayerPattern: RGGB          # Camera sensor pattern: RGGB, GRBG, GBRG, BGGR
  inputImageExtension: fit      # Input file format (fit, tif, jpg, png, etc.)
  outputImageExtension: tif     # Intermediate file format (tif recommended)
  filenameTokens:               # Extract metadata from structured filenames
    enabled: false              # Set to true if filenames contain structured info
    separator: "_"              # Character separating tokens in filenames
    names:                      # Token names matching filename order (separated by separator)
      - targetType              # e.g., "Light", "Dark", "Flat"
      - targetName              # e.g., "M42", "NGC7000", "Orion"
      - exposureTime            # e.g., "300s", "120s", "60s"
      - binLevel                # e.g., "1x1", "2x2", "3x3"
      - camera                  # e.g., "ASI294MC", "Canon5D", "ZWO"
      - iso                     # e.g., "ISO800", "ISO1600", "ISO3200"
      - dateTime                # e.g., "20240115", "2024-01-15"
      - temperature             # e.g., "-10C", "5C", "15C"
      - sequenceNumber          # e.g., "001", "042", "999"

# Enhancement Pipeline - Applied in order after calibration/alignment/stacking
enhance:
  steps:
  # Step 1: Rotation - Correct image orientation
  - rotate:
      angle: 0                  # Rotation angle in degrees (90, 180, 270, or any value)

  # Step 2: Cropping - Remove edge artifacts and unwanted borders
  - crop:
      x: 100                    # Left crop (pixels from left edge)
      y: 100                    # Top crop (pixels from top edge)
      width: -100               # Width (negative = image_width - x + width)
      height: -100              # Height (negative = image_height - y + height)

  # Step 3: White Balance - Correct color casts
  - whitebalance:
      enabled: true
      type: Local               # Local (recommended), Global, or Custom
      fixPoints:                # Points used for local white balance
        type: FourCorners       # FourCorners (recommended), Grid, EightCorners, Custom
        borderDistance: 100     # Distance from image edges for corner sampling
      localMedianRadius: 50     # Radius for local median calculation
      valueRangeMin: 0.2        # Minimum value range for white balance
      valueRangeMax: 0.9        # Maximum value range for white balance

  # Step 4: Initial Histogram Stretch - Bring out initial detail
  - linearPercentile:
      minPercentile: 0.0001     # Black point (lower = more aggressive)
      maxPercentile: 0.9999     # White point (higher = more aggressive)
    addToHighDynamicRange: true # Include this result in HDR processing

  # Step 5: Blur - Smooth transition for HDR processing
  - blur:
      strength: 0.1             # Blur amount (0.0-1.0, subtle smoothing)

  # Step 6: Initial Sigmoid - Brighten darker areas
  - sigmoid:
      midpoint: 0.01            # Midpoint of S-curve (lower = brighter, 0.001-1.0)
      strength: 1.1             # Curve strength (1.0 = linear, higher = more contrast)

  # Step 7: Second Histogram Stretch - Further detail enhancement
  - linearPercentile:
      minPercentile: 0.0001     # Maintain black point
      maxPercentile: 0.9999     # Maintain white point
    addToHighDynamicRange: true # Include this result in HDR processing

  # Steps 8-12: Multiple Sigmoid Applications - Build up contrast gradually
  - sigmoid:
      midpoint: 0.4             # Mid-tone targeting
      strength: 1.1             # Gentle contrast increase
    addToHighDynamicRange: true # Include in HDR
  - sigmoid:
      midpoint: 0.4             # Consistent mid-tone work
      strength: 1.1
    addToHighDynamicRange: true
  - sigmoid:
      midpoint: 0.4             # Continue building contrast
      strength: 1.1
    addToHighDynamicRange: true
  - sigmoid:
      midpoint: 0.4             # Further contrast refinement
      strength: 1.1
    addToHighDynamicRange: true

  # Step 13: HDR Combination - Merge multiple enhancement results
  - highDynamicRange:
      contrastWeight: 0.2       # Weight for contrast enhancement (0.0-1.0)
      saturationWeight: 0.1     # Weight for color saturation (0.0-1.0)
      exposureWeight: 1.0       # Weight for exposure blending (0.0-2.0)

  # Step 14: Final Sigmoid - Last contrast adjustment
  - sigmoid:
      midpoint: 0.3             # Final midpoint adjustment
      strength: 1.1             # Final contrast polish

  # Step 15: Noise Reduction - Clean up the final image
  - reduceNoise:
      thresholding: Soft        # Soft (recommended), Hard, Sigmoid, SigmoidLike
      thresholds:               # Multiple scales for different noise types
        - 0.01                  # Coarse noise threshold
        - 0.001                 # Fine noise threshold

# Annotation Configuration - Add titles, markers, and graphics to final images
annotate:
  enabled: false                # Set to true to create annotated versions
  decorate:                     # Text and marker decorations
    enabled: true               # Enable decorative elements
    title: "Object Name"        # Main title (use tokens like {targetName})
    subtitle: "{stackedCount}x{exposureTime}" # Subtitle with dynamic info
    text: "Object Description"  # Additional descriptive text
    markerStyle: Square         # Marker shape: Square, Rectangle, Circle
    markerLabelStyle: Index     # Label style: Index, Name, Info1, Info2, None
    colorTheme: Cyan            # Color scheme: Cyan, Green, Red, Blue, Yellow, Magenta

# Output Configuration - Final file naming and formats
output:
  # Output filename pattern using tokens
  # Available tokens: {parentDir}, {firstInput}, {inputCount}, {stackedCount},
  # {calibration}, plus any custom tokens from filenameTokens
  outputName: "{targetName}_{stackedCount}x{exposureTime}_{iso}_{calibration}"
  outputImageExtensions:        # Generate multiple formats
    - tif                       # TIFF - Best quality, large files
    - jpg                       # JPEG - Good for sharing, smaller files
    - png                       # PNG - Good for web, lossless compression
""".trimIndent()

fun main(args: Array<String>) {
    val yaml = Yaml()
    val configFile = File("kimage-astro-process.yaml")
    val defaultConfig = yaml.loadAs(defaultAstroProcessConfigText, ProcessConfig::class.java)

    val config = if (configFile.exists()) {
        configFile.inputStream().use { input ->
            yaml.loadAs(input, ProcessConfig::class.java)
        }
    } else {
        defaultConfig
    }

    val command = if (args.isNotEmpty()) args[0] else "process"

    when(command) {
        "process" -> {
            println(yaml.dumpAsMap(config))
            AstroProcess(config).processAstro()
        }
        "config" -> {
            println(yaml.dumpAsMap(config))
        }
        "init" -> {
            val detection = detectSmartInitConfig()
            printDetectionSummary(detection)
            configFile.writeText(generateSmartConfigText(detection))
            println("Created ${configFile.name}")
        }
        "stars" -> {
            AstroProcess(config).imageAnalysis()
        }
        else -> {
            println("""
                Commands:
                - init
                - process
                - config
                - stars
            """.trimIndent())
        }
    }
}

class AstroProcess(val config: ProcessConfig) {
    var workingDirectory: File = File(".")

    fun imageAnalysis() {
        val currentDir = workingDirectory
        val files = currentDir.listFiles() ?: return
        val inputImageExtension: String = config.format.inputImageExtension

        val inputFiles = files.filter { it.extension == inputImageExtension }.filterNotNull().sorted()

        for (inputFile in inputFiles) {
            var image = ImageReader.read(inputFile)
            image = debayerImageIfConfigured(image, config.format.debayer.copy(cleanupBadPixels = false))

            val stars = findStars(image)

            val medianFwmhX = stars.map { it.fwhmX }.median()
            val medianFwmhY = stars.map { it.fwhmY }.median()
            val medianFwmh = stars.map { (it.fwhmX + it.fwhmY) / 2.0 }.median()

            val medianValue = image.values().median()
            val medianRed = image[Channel.Red].values().median()
            val medianGreen = image[Channel.Green].values().median()
            val medianBlue = image[Channel.Blue].values().median()

            val laplacianImage = image.laplacianFilter()
            val laplacianStddev = laplacianImage.values().stddev()

            // Score calculation
            val fwhmScore = 1.0 / medianFwmh  // Inverse of FWHM, sharper stars give higher score
            val starCountScore = stars.size.toDouble()  // More stars contribute positively
            val sharpnessScore = laplacianStddev  // Higher stddev indicates better sharpness
            val brightnessScore = 1.0 - kotlin.math.abs(medianValue - 0.5)  // Penalize extreme brightness

            val totalScore = fwhmScore * 0.4 + starCountScore * 0.2 + sharpnessScore * 0.3 + brightnessScore * 0.1

            println("$inputFile : $totalScore score, ${stars.size} stars, median FWMH $medianFwmh (x $medianFwmhX, y $medianFwmhY), median value $medianValue (R $medianRed, G $medianGreen, B $medianBlue)")
        }
    }

    fun processAstro() {
        val infoTokens = mutableMapOf<String, String>()
        var dirty = false
        val currentDir = workingDirectory

        currentDir.absoluteFile.parentFile?.let {
            infoTokens[InfoTokens.parentDir.name] = it.name
        }

        var bias: Image? = null
        var flat: Image? = null
        var darkflat: Image? = null
        var dark: Image? = null

        if (config.calibrate.enabled) {
            println("### Processing calibration images ...")

            println()
            val (biasResult, dirtyBias) = elapsed("Processing bias frames") {
                processCalibrationImages(
                    "bias",
                    currentDir,
                    dirty,
                    config.calibrate.biasDirectory,
                    config.calibrate.searchParentDirectories,
                    config.calibrate.debayer.enabled,
                    config.calibrate.debayer.bayerPattern,
                    config.format.inputImageExtension,
                    config.format.outputImageExtension,
                )
            }
            bias = biasResult
            if (bias != null) {
                infoTokens.merge(InfoTokens.calibration.name, "bias") { old, new -> "$old,$new" }
            }
            dirty = dirty || dirtyBias

            println()
            val (flatResult, dirtyFlat) = elapsed("Processing flat frames") {
                processCalibrationImages(
                    "flat",
                    currentDir,
                    dirty,
                    config.calibrate.flatDirectory,
                    config.calibrate.searchParentDirectories,
                    config.calibrate.debayer.enabled,
                    config.calibrate.debayer.bayerPattern,
                    config.format.inputImageExtension,
                    config.format.outputImageExtension,
                ).let {
                    Pair(it.first?.normalizeImage(), it.second)
                }
            }
            flat = flatResult
            if (flat != null) {
                infoTokens.merge(InfoTokens.calibration.name, "flat") { old, new -> "$old,$new" }
            }
            dirty = dirty || dirtyFlat

            println()
            val (darkflatResult, dirtyDarkFlat) = elapsed("Processing darkflat frames") {
                processCalibrationImages(
                    "darkflat",
                    currentDir,
                    dirty,
                    config.calibrate.darkflatDirectory,
                    config.calibrate.searchParentDirectories,
                    config.calibrate.debayer.enabled,
                    config.calibrate.debayer.bayerPattern,
                    config.format.inputImageExtension,
                    config.format.outputImageExtension,
                )
            }
            darkflat = darkflatResult
            if (darkflat != null) {
                infoTokens.merge(InfoTokens.calibration.name, "darkflat") { old, new -> "$old,$new" }
            }
            dirty = dirty || dirtyDarkFlat

            println()
            val (darkResult, dirtyDark) = elapsed("Processing dark frames") {
                processCalibrationImages(
                    "dark",
                    currentDir,
                    dirty,
                    config.calibrate.darkDirectory,
                    config.calibrate.searchParentDirectories,
                    config.calibrate.debayer.enabled,
                    config.calibrate.debayer.bayerPattern,
                    config.format.inputImageExtension,
                    config.format.outputImageExtension,
                )
            }
            dark = darkResult
            if (dark != null) {
                infoTokens.merge(InfoTokens.calibration.name, "dark") { old, new -> "$old,$new" }
            }
            dirty = dirty || dirtyDark

            println()
            println("Images used for calibration:")
            if (bias != null) println("- bias frame")
            if (dark != null) println("- dark frame")
            if (darkflat != null) println("- darkflat frame")
            if (flat != null) println("- flat frame")
            if (bias == null && dark == null && darkflat == null && flat == null) println("- no calibration frames used ")
        } else {
            println("### Calibration disabled - skipping bias/dark/flat/darkflat frames")
            println("- no calibration frames used ")
        }

        val files = currentDir.listFiles() ?: return

        currentDir.resolve(config.calibrate.calibratedOutputDirectory).mkdirs()
        currentDir.resolve(config.align.alignedOutputDirectory).mkdirs()
        currentDir.resolve(config.stack.stackedOutputDirectory).mkdirs()

        if (flat != null) {
            if (!config.calibrate.darkskip && dark != null) {
                println("Subtracting dark from flat")
                val darkScaling = config.calibrate.darkScalingFactor
                if (darkScaling != 1.0) {
                    flat -= dark * darkScaling
                } else {
                    flat -= dark
                }
            }
            if (darkflat != null) {
                println("Subtracting darkflat from flat")
                flat -= darkflat
            }
        }

        var inputFiles = files.filter { it.extension == config.format.inputImageExtension }.filterNotNull().sorted()
        if (config.quick) {
            println()
            println("Quick mode: only processing ${config.quickCount} input file")
            inputFiles = inputFiles.take(config.quickCount)
        }

        infoTokens[InfoTokens.inputCount.name] = inputFiles.size.toString()
        if (inputFiles.isEmpty()) {
            println("No input files found with extension '${config.format.inputImageExtension}' in $currentDir")
            return
        }
        inputFiles[0].let {
            infoTokens[InfoTokens.firstInput.name] = it.nameWithoutExtension
            if (config.format.filenameTokens.enabled) {
                val tokens = it.nameWithoutExtension.split(config.format.filenameTokens.separator)
                require(tokens.size == config.format.filenameTokens.names.size) { "Wrong number of tokens in filename '${it.nameWithoutExtension}', expected ${config.format.filenameTokens.names.size} but found ${tokens.size}" }
                for (i in tokens.indices) {
                    infoTokens[config.format.filenameTokens.names[i]] = tokens[i]
                }
            }
        }

        val correctedTargetConfig = resolvedTarget(config.target)

        val wcsData: Map<String, String>? = inputFiles[0].let { referenceInputFile ->
            if (config.platesolve.enabled) {
                println()
                println("### Platesolve reference image ...")

                val plateSolver = when (config.platesolve.platesolveType) {
                    PlatesolveType.Astap -> AstapPlateSolver(config.platesolve.executable ?: "astap_cli")
                    PlatesolveType.Internal -> ch.obermuhlner.kimage.astro.platesolve.InternalPlateSolver()
                    PlatesolveType.Custom -> throw UnsupportedOperationException("Custom plate solver not yet implemented")
                }

                var image = ImageReader.read(referenceInputFile)
                image = debayerImageIfConfigured(image, config.format.debayer.copy(cleanupBadPixels = false))

                elapsed("Platesolving reference image") {
                    plateSolver.solve(image, referenceInputFile, correctedTargetConfig.ra, correctedTargetConfig.dec)
                }
            } else {
                null
            }
        }

        val wcsFile = Paths.get("astro-process", "reference.wcs").toFile()

        if (wcsData != null) {
            println()
            println("Platesolved:")
            wcsData.forEach {
                println("  wcs.${it.key} = ${it.value}")
                infoTokens["wcs." + it.key] = it.value
            }

            wcsData["RA"]?.toDouble()?.let {
                correctedTargetConfig.ra = it
            }
            wcsData["DEC"]?.toDouble()?.let {
                correctedTargetConfig.dec = it
            }
        }

        correctedTargetConfig.ra?.let {
            infoTokens["ra"] = raToString(it)
        }
        correctedTargetConfig.dec?.let {
            infoTokens["dec"] = decToString(it)
        }

        println()
        println("### Calibrating ${inputFiles.size} images ...")

        var dirtyCalibrated = false
        val dirtyCalibratedFiles = mutableSetOf<File>()
        val calibratedFiles = elapsed("Calibrating ${inputFiles.size} light frames") {
            inputFiles.map { inputFile ->
                val outputFile = currentDir.resolve(config.calibrate.calibratedOutputDirectory)
                    .resolve("${inputFile.nameWithoutExtension}.${config.format.outputImageExtension}")
                if (outputFile.exists() && !dirty) {
                    return@map outputFile
                }
                dirtyCalibrated = true
                dirtyCalibratedFiles.add(outputFile)

                println()
                println("Loading $inputFile")
                var light = elapsed("Reading light frame $inputFile") { ImageReader.read(inputFile) }

                if (config.format.debayer.enabled && config.calibrate.debayer.enabled) {
                    light = elapsed("Debayering light frame $inputFile") {
                        debayerImageIfConfigured(light, config.format.debayer)
                    }
                }

                elapsed("Calibrating light frame $inputFile") {
                    if (bias != null) {
                        light -= bias
                    }
                    if (dark != null && !config.calibrate.darkskip) {
                        val darkScaling = config.calibrate.darkScalingFactor
                        if (darkScaling != 1.0) {
                            light -= dark * darkScaling
                        } else {
                            light -= dark
                        }
                    }
                    if (flat != null) {
                        light /= flat
                    }

                    if (config.format.debayer.enabled && !config.calibrate.debayer.enabled) {
                        light = elapsed("Debayering light frame $inputFile") {
                            debayerImageIfConfigured(light, config.format.debayer)
                        }
                    }

                    light.applyEach { v -> clamp(v, 0.0, 1.0) }
                }

                println("Saving $outputFile")
                elapsed("Writing calibrated light frame") { ImageWriter.write(light, outputFile) }

                outputFile
            }
        }

        println()
        println("### Normalizing backgrounds for ${calibratedFiles.size} images ...")

        var dirtyNormalized = false
        val dirtyNormalizedFiles = mutableSetOf<File>()
        val preAlignFiles = if (config.normalizeBackground.enabled) {
            val normalizedOutputDir = currentDir.resolve(config.normalizeBackground.outputDirectory)
            normalizedOutputDir.mkdirs()

            val minBackground: Map<Channel, Double> = if (config.normalizeBackground.normalize.enabled) {
                val minBackgroundFile = normalizedOutputDir.resolve("minBackground.yaml")
                if (minBackgroundFile.exists() && !dirty) {
                    val yaml = Yaml()
                    val stringMap = minBackgroundFile.inputStream().use { yaml.load<Map<String, Double>>(it) }
                    stringMap.mapKeys { Channel.valueOf(it.key) }
                } else {
                    val minBg = mutableMapOf<Channel, Double>()
                    elapsed("Computing minimum background from ${calibratedFiles.size} calibrated frames") {
                        calibratedFiles.forEach { calibratedFile ->
                            val normalizedFile = normalizedOutputDir
                                .resolve("${calibratedFile.nameWithoutExtension}.${config.format.outputImageExtension}")
                            if (!normalizedFile.exists() || dirty) {
                                val light = elapsed("Reading calibrated frame $calibratedFile") {
                                    ImageReader.read(calibratedFile)
                                }
                                for (channel in light.channels) {
                                    val median = light[channel].values().median()
                                    println("Background $channel: $median")
                                    minBg[channel] = minBg[channel]?.let { min(it, median) } ?: median
                                }
                            }
                        }
                    }
                    val yaml = Yaml()
                    minBackgroundFile.writeText(yaml.dumpAsMap(minBg.mapKeys { it.key.name }))
                    minBg
                }
            } else {
                emptyMap()
            }

            calibratedFiles.map { calibratedFile ->
                val outputFile = normalizedOutputDir
                    .resolve("${calibratedFile.nameWithoutExtension}.${config.format.outputImageExtension}")
                if (outputFile.exists() && !dirty && !dirtyCalibratedFiles.contains(calibratedFile)) {
                    return@map outputFile
                }
                dirtyNormalized = true
                dirtyNormalizedFiles.add(outputFile)

                println()
                println("Loading calibrated $calibratedFile")
                var light = elapsed("Reading calibrated frame") { ImageReader.read(calibratedFile) }

                if (config.normalizeBackground.neutralize.enabled) {
                    val cfg = config.normalizeBackground.neutralize
                    elapsed("Neutralizing background of $calibratedFile") {
                        for (channel in light.channels) {
                            val median = light[channel].values().median()
                            light[channel].applyEach { v -> v - median + cfg.offset }
                        }
                    }
                }

                if (config.normalizeBackground.normalize.enabled) {
                    elapsed("Normalizing background of $calibratedFile") {
                        for (channel in light.channels) {
                            val lowestBackground = minBackground[channel]
                            if (lowestBackground != null) {
                                val background = light[channel].values().median()
                                val delta = background - lowestBackground - config.normalizeBackground.normalize.offset
                                light[channel].applyEach { v -> v - delta }
                            }
                        }
                    }
                }

                light.applyEach { v -> clamp(v, 0.0, 1.0) }

                println("Saving $outputFile")
                elapsed("Writing normalized frame") { ImageWriter.write(light, outputFile) }
                outputFile
            }
        } else {
            calibratedFiles
        }
        dirty = dirty || dirtyNormalized

        println()
        println("### Aligning ${preAlignFiles.size} images ...")

        val referenceCalibratedFile = preAlignFiles.first()
        var referenceStars: List<Star> = emptyList<Star>()
        var referenceImageWidth = 0
        var referenceImageHeight = 0

        println()
        println("Loading reference $referenceCalibratedFile")
        val referenceLight = ImageReader.read(referenceCalibratedFile)

        elapsed("Finding reference stars") {
            referenceStars = findStars(referenceLight, config.align.starThreshold)
            referenceImageWidth = referenceLight.width
            referenceImageHeight = referenceLight.height
        }

        var dirtyAligned = false
        val alignedFiles = preAlignFiles
            .mapNotNull { calibratedFile ->
                val outputFile = currentDir.resolve(config.align.alignedOutputDirectory)
                    .resolve("${calibratedFile.nameWithoutExtension}.${config.format.outputImageExtension}")
                if (outputFile.exists() && !dirty && !dirtyNormalizedFiles.contains(calibratedFile) && !dirtyCalibratedFiles.contains(calibratedFile)) {
                    return@mapNotNull outputFile
                }

                println()
                println("Loading $calibratedFile")
                val light = ImageReader.read(calibratedFile)

                val alignedImage = if (calibratedFile == referenceCalibratedFile) {
                    saveTransformMatrix(DoubleMatrix.identity(3), transformFileFor(outputFile))
                    ImageReader.read(referenceCalibratedFile)
                } else {
                    val imageStars = elapsed("Finding image stars") { findStars(light, config.align.starThreshold) }
                    val transform = elapsed("Calculating transformation matrix") {
                        calculateTransformationMatrix(
                            referenceStars.take(config.align.maxStars),
                            imageStars.take(config.align.maxStars),
                            referenceImageWidth,
                            referenceImageHeight,
                            positionTolerance = config.align.positionTolerance
                        )
                    }
                    if (transform != null) {
                        println(message = formatTransformation(decomposeTransformationMatrix(transform)))
                        saveTransformMatrix(transform, transformFileFor(outputFile))

                        elapsed("Applying transformation to image") {
                            applyTransformationToImage(light, transform)
                        }
                    } else {
                        null
                    }
                }

                if (alignedImage != null) {
                    dirtyAligned = true
                    println("Saving $outputFile")
                    ImageWriter.write(alignedImage, outputFile)

                    outputFile
                } else {
                    println("Failed to align $calibratedFile")
                    null
                }
            }
        dirty = dirty || dirtyCalibrated || dirtyAligned

        val sourceRegistry = mutableMapOf<String, Image>()

        if (config.stack.perFrame) {
            println()
            println("### Per-frame mode: processing ${alignedFiles.size} aligned images individually ...")
            infoTokens[InfoTokens.stackedCount.name] = alignedFiles.size.toString()

            for ((frameIndex, alignedFile) in alignedFiles.withIndex()) {
                val frameImage = ImageReader.read(alignedFile)
                val frameInfoTokens = infoTokens.toMutableMap()
                frameInfoTokens[InfoTokens.frameIndex.name] = (frameIndex + 1).toString()

                val frameCacheDir = currentDir.resolve(config.enhance.enhancedOutputDirectory)
                    .resolve(alignedFile.nameWithoutExtension)
                frameCacheDir.mkdirs()

                println()
                println("### Enhancing frame ${frameIndex + 1}/${alignedFiles.size}: $alignedFile ...")
                val enhancedImage = processEnhanceSteps(
                    frameImage, dirty, config.enhance.steps, config.format, config.enhance, frameCacheDir, sourceRegistry
                ).first
                processOutput(enhancedImage, frameInfoTokens, config.output)
                processAnnotate(enhancedImage, frameInfoTokens, wcsFile, config.annotate, config.output)
            }
        } else {
            println()
            println("### Stacking ${alignedFiles.size} aligned images ...")
            infoTokens[InfoTokens.stackedCount.name] = alignedFiles.size.toString()

            var dirtyStacked = false
            val outputStackedFile = currentDir
                .resolve(config.stack.stackedOutputDirectory)
                .resolve("${inputFiles[0].nameWithoutExtension}_stacked_${alignedFiles.size}.${config.format.outputImageExtension}")
            val stackedImage = if (!outputStackedFile.exists() || dirty) {
                val outputStackedMaxFile = currentDir
                    .resolve(config.stack.stackedOutputDirectory)
                    .resolve("${inputFiles[0].nameWithoutExtension}_stacked-max_${alignedFiles.size}.${config.format.outputImageExtension}")
                dirtyStacked = true
                var stackedMaxImage: Image? = null

                fun accumulateMax(image: Image) {
                val current = stackedMaxImage
                if (current == null) {
                    stackedMaxImage = MatrixImage(image)
                } else {
                    maxInPlace(current as MatrixImage, image)
                }
            }

            val stackedImage = if (config.stack.algorithm == StackAlgorithm.Drizzle) {
                elapsed("Drizzle stacking images") {
                    val calibratedByName = calibratedFiles.associateBy { it.nameWithoutExtension }
                    val frames = alignedFiles.map { alignedFile ->
                        val calibratedFile = calibratedByName[alignedFile.nameWithoutExtension]
                            ?: error("No calibrated file found for ${alignedFile.name}")
                        val transform = loadTransformMatrix(transformFileFor(alignedFile))
                        val imageSupplier: () -> Image = {
                            println("Loading $calibratedFile for drizzle")
                            ImageReader.read(calibratedFile)
                        }
                        imageSupplier to transform
                    }
                    drizzle(frames, config.stack.drizzle, tempDir = config.stack.tempDir?.let { File(it) }, maxDiskSpaceBytes = parseDiskSpaceBytes(config.stack.maxDiskSpaceBytes)) { image -> accumulateMax(image) }
                }
            } else {val alignedFileSuppliers = alignedFiles.map {
                    {
                        println("Loading $it")
                        val image = ImageReader.read(it)
                        accumulateMax(image)
                        image
                    }
                }
                 elapsed("Stacking images") {
                    stack(imageSuppliers = alignedFileSuppliers,
                        config = CoreStackConfig(
                        algorithm = config.stack.algorithm,
                        kappa = config.stack.kappa,
                            iterations = config.stack.iterations,
                            precision = config.stack.precision,
                            tempDir = config.stack.tempDir?.let { File(it) },
                            maxDiskSpaceBytes = parseDiskSpaceBytes(config.stack.maxDiskSpaceBytes),
                        )
                    )
                    }
                }

                println("Saving $outputStackedFile")
                ImageWriter.write(stackedImage, outputStackedFile)

                stackedMaxImage?.let {
                    println("Saving $outputStackedMaxFile")
                    ImageWriter.write(it, outputStackedMaxFile)
                }

                stackedImage
            } else {
                println("Stacked image already exists: $outputStackedFile")
                ImageReader.read(outputStackedFile)
            }
            dirty = dirty || dirtyStacked
            sourceRegistry["main"] = stackedImage

            // Multi-source enhancement: load stacked images for named sources
            for (source in config.sources ?: emptyList()) {
                val sourceCacheDir = currentDir.resolve("astro-process/${source.name}")
                val sourceStackDir = sourceCacheDir.resolve("stacked")
                val sourceStackFiles = sourceStackDir.listFiles()?.filter {
                    it.extension == config.format.outputImageExtension
                } ?: emptyList()
                if (sourceStackFiles.isNotEmpty()) {
                    sourceRegistry[source.name] = ImageReader.read(sourceStackFiles.first())
                }
            }

            val inputSourceName = config.enhance.input ?: "main"
            val enhanceInputImage = sourceRegistry[inputSourceName] ?: stackedImage

            println()
            println("### Enhancing stacked image ...")
            println()

            if (!config.enhance.branches.isNullOrEmpty()) {
                val (commonImage, commonDirty) = if (config.enhance.steps.isNotEmpty()) {
                    val commonCacheDir = currentDir.resolve(config.enhance.enhancedOutputDirectory).resolve("common")
                    commonCacheDir.mkdirs()
                    elapsed("enhance common steps") {
                        processEnhanceSteps(
                            enhanceInputImage, dirty, config.enhance.steps, config.format, config.enhance, commonCacheDir, sourceRegistry
                        )
                    }
                } else {
                    Pair(enhanceInputImage, dirty)
                }

                for (branch in config.enhance.branches!!) {
                    val branchInfoTokens = infoTokens.toMutableMap()
                    branchInfoTokens[InfoTokens.branchName.name] = branch.name

                    val branchCacheDir = currentDir.resolve(config.enhance.enhancedOutputDirectory)
                        .resolve(branch.name.ifEmpty { "anonymous" })
                    branchCacheDir.mkdirs()

                    val enhancedImage = elapsed("enhance branch '${branch.name}'") {
                        processEnhanceSteps(
                            commonImage, commonDirty, branch.steps, config.format, config.enhance, branchCacheDir, sourceRegistry
                        ).first
                    }

                    println()
                    println("### Writing output images for branch '${branch.name}' ...")
                    processOutput(enhancedImage, branchInfoTokens, config.output)
                    processAnnotate(enhancedImage, branchInfoTokens, wcsFile, config.annotate, config.output)
                }
            } else {
                val enhancedImage = elapsed("enhance") {
                    processEnhance(enhanceInputImage, dirty, config.format, config.enhance, sourceRegistry)
                }

                println()
                println("### Writing output images ...")
                println()
                processOutput(enhancedImage, infoTokens, config.output)

                println()
                println("### Annotating image ...")
                println()
                processAnnotate(enhancedImage, infoTokens, wcsFile, config.annotate, config.output)
            }
        }
    }

    fun processEnhance(
        inputImage: Image,
        alreadyDirty: Boolean,
        formatConfig: FormatConfig,
        enhanceConfig: EnhanceConfig,
        sourceRegistry: MutableMap<String, Image> = mutableMapOf(),
    ): Image {
        val cacheDir = workingDirectory.resolve(enhanceConfig.enhancedOutputDirectory)
        cacheDir.mkdirs()
        return processEnhanceSteps(inputImage, alreadyDirty, enhanceConfig.steps, formatConfig, enhanceConfig, cacheDir, sourceRegistry).first
    }

    private fun processEnhanceSteps(
        inputImage: Image,
        alreadyDirty: Boolean,
        steps: List<EnhanceStepConfig>,
        formatConfig: FormatConfig,
        enhanceConfig: EnhanceConfig,
        cacheDir: File,
        sourceRegistry: MutableMap<String, Image>,
    ): Pair<Image, Boolean> {
        val currentDir = workingDirectory
        cacheDir.mkdirs()

        var image = inputImage
        var dirty = alreadyDirty
        var stepIndex = 1

        fun step(name: String, stretchFunc: (Image) -> Image) {
            elapsed(name) {
                val stepImageFile = cacheDir.resolve("step_${stepIndex}_$name.${formatConfig.outputImageExtension}")
                if (stepImageFile.exists() && !dirty) {
                    image = elapsed("  Reading already existing file: $stepImageFile") {
                        ImageReader.read(stepImageFile)
                    }
                } else {
                    dirty = true
                    elapsed("  Executing function: $name") {
                        image = stretchFunc(image)
                    }
                    elapsed("  Writing result image") {
                        ImageWriter.write(image, stepImageFile)
                    }
                    if (enhanceConfig.histogram.enabled) {
                        elapsed("  Writing histogram") {
                            ImageWriter.write(
                                image.histogramImage(
                                    enhanceConfig.histogram.histogramWidth,
                                    enhanceConfig.histogram.histogramHeight
                                ),
                                cacheDir.resolve("histogram_step_${stepIndex}_$name.${formatConfig.outputImageExtension}")
                            )
                        }
                        if (enhanceConfig.histogram.printPercentiles) {
                            elapsed("  Printing histogram percentiles") {
                                val histogram = Histogram(enhanceConfig.histogram.histogramWidth)
                                histogram.add(image[Channel.Gray])
                                for (percentile in listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99)) {
                                    val percentileInt = (percentile * 100 + 0.5).toInt()
                                    println("    percentile ${percentileInt}% : ${histogram.estimatePercentile(percentile)}")
                                }
                            }
                        }
                    }
                }
                stepIndex++
            }
        }

        val hdrSourceImages = mutableListOf<Image>()
        for (enhanceStepIndex in steps.indices) {
            val enhanceStepConfig = steps[enhanceStepIndex]
            if (!enhanceStepConfig.enabled) {
                stepIndex++
                continue
            }
            val type = enhanceStepConfig.type
            step(type.name) {
                val stepResultImage = when (type) {
                    EnhanceStepType.Debayer -> {
                        val badPixels = if (enhanceStepConfig.debayer!!.cleanupBadPixels) {
                            val mosaic = image[Channel.Red]
                            val badPixels = mosaic.findBayerBadPixels()
                            println("Found ${badPixels.size} bad pixels")
                            badPixels
                        } else {
                            emptySet()
                        }
                        it.debayer(enhanceStepConfig.debayer!!.bayerPattern, interpolation = enhanceStepConfig.debayer!!.interpolation, badpixelCoords = badPixels)
                    }

                    EnhanceStepType.Crop -> {
                        val width =
                            if (enhanceStepConfig.crop!!.width < 0) it.width - enhanceStepConfig.crop!!.x + enhanceStepConfig.crop!!.width else enhanceStepConfig.crop!!.width
                        val height =
                            if (enhanceStepConfig.crop!!.height < 0) it.height - enhanceStepConfig.crop!!.y + enhanceStepConfig.crop!!.height else enhanceStepConfig.crop!!.height
                        it.crop(enhanceStepConfig.crop!!.x, enhanceStepConfig.crop!!.y, width, height)
                    }

                    EnhanceStepType.Rotate -> {
                        when (enhanceStepConfig.rotate!!.angle) {
                            90.0, -270.0 -> it.rotateRight()
                            180.0, -180.0 -> it.rotateRight().rotateRight()
                            -90.0, 270.0 -> it.rotateLeft()
                            else -> {
                                val angleRad = enhanceStepConfig.rotate!!.angle.toRadians()
                                val cosA = cos(angleRad)
                                val sinA = sin(angleRad)
                                val transformationMatrix = DoubleMatrix.matrixOf(
                                    3, 3,
                                    cosA, -sinA, 0.0,
                                    sinA, cosA, 0.0,
                                    0.0, 0.0, 1.0
                                )
                                applyTransformationToImage(it, transformationMatrix)
                            }
                        }
                    }

                    EnhanceStepType.LinearPercentile -> {
                        if (enhanceConfig.measure.enabled) {
                            it.stretchLinearPercentile(
                                enhanceStepConfig.linearPercentile!!.minPercentile,
                                enhanceStepConfig.linearPercentile!!.maxPercentile,
                                it.crop(enhanceConfig.measure.x, enhanceConfig.measure.y, enhanceConfig.measure.width, enhanceConfig.measure.height).histogram()
                            )
                        } else {
                            it.stretchLinearPercentile(
                                enhanceStepConfig.linearPercentile!!.minPercentile,
                                enhanceStepConfig.linearPercentile!!.maxPercentile
                            )
                        }
                    }

                    EnhanceStepType.RemoveBackground -> {
                        val fixPoints = getFixPoints(it, enhanceStepConfig.removeBackground!!.fixPoints)
                        val fixPointValues = it.getFixPointValues(fixPoints, enhanceStepConfig.removeBackground!!.medianRadius)
                        val background = it.interpolate(fixPointValues, power = enhanceStepConfig.removeBackground!!.power)
                        it - background + enhanceStepConfig.removeBackground!!.offset
                    }

                    EnhanceStepType.AutoStretch -> {
                        it.stretchAutoSTF(
                            enhanceStepConfig.autoStretch!!.shadowClipping,
                            enhanceStepConfig.autoStretch!!.targetBackground,
                            enhanceStepConfig.autoStretch!!.perChannel,
                        )
                    }

                    EnhanceStepType.Whitebalance -> {
                        when (enhanceStepConfig.whitebalance!!.type) {
                            WhitebalanceType.Global -> it.applyWhitebalanceGlobal(
                                enhanceStepConfig.whitebalance!!.valueRangeMin,
                                enhanceStepConfig.whitebalance!!.valueRangeMax
                            )
                            WhitebalanceType.Local -> it.applyWhitebalanceLocal(
                                getFixPoints(it, enhanceStepConfig.whitebalance!!.fixPoints),
                                enhanceStepConfig.whitebalance!!.localMedianRadius
                            )
                            WhitebalanceType.Custom -> it.applyWhitebalanceCustom(
                                enhanceStepConfig.whitebalance!!.customRed,
                                enhanceStepConfig.whitebalance!!.customGreen,
                                enhanceStepConfig.whitebalance!!.customBlue
                            )
                        }
                        it
                    }

                    EnhanceStepType.Sigmoid -> {
                        it.stretchSigmoidLike(enhanceStepConfig.sigmoid!!.midpoint, enhanceStepConfig.sigmoid!!.strength)
                    }

                    EnhanceStepType.Blur -> {
                        (it * (1.0 - enhanceStepConfig.blur!!.strength)) + (it.gaussianBlur3Filter() * enhanceStepConfig.blur!!.strength)
                    }

                    EnhanceStepType.Sharpen -> {
                        it.sharpenFilter() * enhanceStepConfig.sharpen!!.strength + it * (1.0 - enhanceStepConfig.sharpen!!.strength)
                    }

                    EnhanceStepType.UnsharpMask -> {
                        it.unsharpMaskFilter(enhanceStepConfig.unsharpMask!!.radius, enhanceStepConfig.unsharpMask!!.strength)
                    }

                    EnhanceStepType.ReduceNoise -> {
                        val thresholdingFunc: (Double, Double) -> Double = when (enhanceStepConfig.reduceNoise!!.thresholding) {
                            Thresholding.Hard -> { v, threshold -> thresholdHard(v, threshold) }
                            Thresholding.Soft -> { v, threshold -> thresholdSoft(v, threshold) }
                            Thresholding.Sigmoid -> { v, threshold -> thresholdSigmoid(v, threshold) }
                            Thresholding.SigmoidLike -> { v, threshold -> thresholdSigmoidLike(v, threshold) }
                        }
                        when (enhanceStepConfig.reduceNoise!!.algorithm) {
                            NoiseReductionAlgorithm.MultiScaleMedianOverAllChannels ->
                                it.reduceNoiseUsingMultiScaleMedianTransformOverAllChannels(enhanceStepConfig.reduceNoise!!.thresholds, thresholdingFunc)
                            NoiseReductionAlgorithm.MultiScaleMedianOverGrayChannel ->
                                it.reduceNoiseUsingMultiScaleMedianTransformOverGrayChannel(enhanceStepConfig.reduceNoise!!.thresholds, thresholdingFunc)
                        }
                    }

                    EnhanceStepType.HighDynamicRange -> {
                        highDynamicRange(hdrSourceImages.map { img -> { img } })
                    }

                    EnhanceStepType.CosmeticCorrection -> {
                        it.cosmeticCorrect(enhanceStepConfig.cosmeticCorrection!!)
                    }

                    EnhanceStepType.Deconvolve -> {
                        when (enhanceStepConfig.deconvolve!!.algorithm) {
                            DeconvolutionAlgorithm.RichardsonLucy -> it.richardsonLucyDeconvolution(
                                enhanceStepConfig.deconvolve!!.psfSigma,
                                enhanceStepConfig.deconvolve!!.iterations
                            )
                            DeconvolutionAlgorithm.Wiener -> it.wienerDeconvolution(
                                enhanceStepConfig.deconvolve!!.psfSigma,
                                enhanceStepConfig.deconvolve!!.iterations,
                                    enhanceStepConfig.deconvolve!!.noiseLevel
                            )
                        }
                    }

                    EnhanceStepType.ExtractStars -> {
                        val cfg = enhanceStepConfig.extractStars!!
                        val subCacheDir = cacheDir.resolve("step_${stepIndex}_ExtractStars")
                        processExtractStars(it, cfg, formatConfig, enhanceConfig, subCacheDir, dirty, sourceRegistry)
                    }

                    EnhanceStepType.Decompose -> {
                        val cfg = enhanceStepConfig.decompose!!
                        val subCacheDir = cacheDir.resolve("step_${stepIndex}_Decompose")
                        processDecompose(it, cfg, formatConfig, enhanceConfig, subCacheDir, dirty, sourceRegistry)
                    }

                    EnhanceStepType.CompositeChannels -> {
                        val cfg = enhanceStepConfig.compositeChannels!!
                        val redMatrix = extractSingleChannelMatrix(sourceRegistry[cfg.red] ?: error("Source '${cfg.red}' not found in registry"))
                        val greenMatrix = extractSingleChannelMatrix(sourceRegistry[cfg.green] ?: error("Source '${cfg.green}' not found in registry"))
                        val blueMatrix = extractSingleChannelMatrix(sourceRegistry[cfg.blue] ?: error("Source '${cfg.blue}' not found in registry"))
                        MatrixImage(redMatrix, greenMatrix, blueMatrix)
                    }

                    EnhanceStepType.MergeWith -> {
                        val cfg = enhanceStepConfig.mergeWith!!
                        val otherImage = sourceRegistry[cfg.image] ?: error("Source '${cfg.image}' not found in registry")
                        when (cfg.method) {
                            MergeMethod.Hdr -> highDynamicRange(listOf({ it }, { otherImage }))
                        }
                    }

                    EnhanceStepType.StackSources -> {
                        val cfg = enhanceStepConfig.stackSources!!
                        val sourceImages = cfg.images.map { name ->
                            sourceRegistry[name] ?: error("Source '$name' not found in registry")
                        }
                        val weights = cfg.weights
                        val suppliers = sourceImages.map { img -> { img } }
                        val result = stack(algorithm = cfg.algorithm, imageSuppliers = suppliers)
                        if (cfg.outputName != null) {
                            sourceRegistry[cfg.outputName!!] = result
                        }
                        result
                    }

                    EnhanceStepType.MaskedProcess -> {
                        val cfg = enhanceStepConfig.maskedProcess!!
                        val subCacheDir = cacheDir.resolve("step_${stepIndex}_MaskedProcess")
                        processMaskedProcess(it, cfg, formatConfig, enhanceConfig, subCacheDir, dirty, sourceRegistry)
                    }

                    EnhanceStepType.Quantize -> {
                        val levels = enhanceStepConfig.quantize!!.levels.toDouble()
                        it.onEach { v -> floor(v * levels) / levels }
                    }

                    EnhanceStepType.Edge -> {
                        val cfg = enhanceStepConfig.edge!!
                        val edgeImage = when (cfg.algorithm) {
                            EdgeAlgorithm.Sobel -> it.sobelFilter()
                            EdgeAlgorithm.Sobel3 -> it.sobel3Filter()
                            EdgeAlgorithm.Sobel5 -> it.sobel5Filter()
                            EdgeAlgorithm.Laplacian -> it.laplacianFilter()
                            EdgeAlgorithm.EdgeStrong -> it.edgeDetectionStrongFilter()
                            EdgeAlgorithm.EdgeCross -> it.edgeDetectionCrossFilter()
                            EdgeAlgorithm.EdgeDiagonal -> it.edgeDetectionDiagonalFilter()
                            EdgeAlgorithm.EdgeEnhancement -> it.edgeEnhancementFilter()
                        }
                        if (cfg.strength == 1.0) edgeImage
                        else edgeImage.onEach { v -> (v * cfg.strength).coerceIn(0.0, 1.0) }
                    }
                }
                if (enhanceConfig.regionOfInterest.enabled) {
                    val roiImage = stepResultImage.crop(
                        enhanceConfig.regionOfInterest.x,
                        enhanceConfig.regionOfInterest.y,
                        enhanceConfig.regionOfInterest.width,
                        enhanceConfig.regionOfInterest.height
                    )
                    val roiImageFile = cacheDir.resolve("region_step_${stepIndex}_${type.name}.${formatConfig.outputImageExtension}")
                    ImageWriter.write(roiImage, roiImageFile)
                }
                stepResultImage
            }
            if (enhanceStepConfig.addToHighDynamicRange) {
                hdrSourceImages.add(image)
            }
        }
        return Pair(image, dirty)
    }

    private fun extractSingleChannelMatrix(image: Image): ch.obermuhlner.kimage.core.matrix.Matrix {
        return if (image.channels.contains(Channel.Gray)) {
            image[Channel.Gray]
        } else {
            val r = image[Channel.Red]
            val g = image[Channel.Green]
            val b = image[Channel.Blue]
            FloatMatrix(r.rows, r.cols) { row, col ->
                (0.2126 * r[row, col] + 0.7152 * g[row, col] + 0.0722 * b[row, col]).toFloat()
            }
        }
    }

    private fun loadOrFindStars(
        starsYamlFile: File,
        image: Image,
        alignConfig: AlignConfig,
    ): List<ch.obermuhlner.kimage.astro.align.Star> {
        if (starsYamlFile.exists()) {
            val yaml = Yaml()
            val list = starsYamlFile.inputStream().use { yaml.load<List<Map<String, Any>>>(it) }
            return list?.map { map ->
                ch.obermuhlner.kimage.astro.align.Star(
                    x = (map["x"] as Number).toDouble(),
                    y = (map["y"] as Number).toDouble(),
                    brightness = (map["brightness"] as Number).toDouble(),
                    fwhmX = (map["fwhmX"] as Number).toDouble(),
                    fwhmY = (map["fwhmY"] as Number).toDouble(),
                )
            } ?: emptyList()
        }
        return findStars(image, alignConfig.starThreshold).take(alignConfig.maxStars)
    }

    private fun buildStarMask(image: Image, stars: List<ch.obermuhlner.kimage.astro.align.Star>, factor: Double, blurRadius: Int): ch.obermuhlner.kimage.core.matrix.Matrix {
        val maskMatrix = FloatMatrix(image.height, image.width) { _, _ -> 0f }
        val width = image.width
        val height = image.height
        for (star in stars) {
            val radiusX = (star.fwhmX * factor / 2 + 0.5).toInt().coerceAtLeast(1)
            val radiusY = (star.fwhmY * factor / 2 + 0.5).toInt().coerceAtLeast(1)
            val cx = star.intX
            val cy = star.intY
            for (y in (cy - radiusY)..(cy + radiusY)) {
                for (x in (cx - radiusX)..(cx + radiusX)) {
                    if (x in 0 until width && y in 0 until height) {
                        val dx = (x - cx).toDouble() / radiusX
                        val dy = (y - cy).toDouble() / radiusY
                        if (dx * dx + dy * dy <= 1.0) {
                            maskMatrix[y, x] = 1.0
                        }
                    }
                }
            }
        }
        val maskImage = MatrixImage(maskMatrix)
        val blurred = if (blurRadius > 0) maskImage.gaussianBlurFilter(blurRadius) else maskImage
        return blurred[Channel.Red]
    }

    private fun blendWithMask(insideImage: Image, outsideImage: Image, maskMatrix: ch.obermuhlner.kimage.core.matrix.Matrix): Image {
        val width = insideImage.width
        val height = insideImage.height
        val channels = insideImage.channels
        return MatrixImage(width, height, channels) { channel, rows, cols ->
            FloatMatrix(rows, cols) { row, col ->
                val m = maskMatrix[row, col].toFloat()
                val inside = insideImage[channel][row, col].toFloat()
                val outside = outsideImage[channel][row, col].toFloat()
                (inside * m + outside * (1f - m)).coerceIn(0f, 1f)
            }
        }
    }

    private fun processExtractStars(
        image: Image,
        cfg: ExtractStarsConfig,
        formatConfig: FormatConfig,
        enhanceConfig: EnhanceConfig,
        subCacheDir: File,
        dirty: Boolean,
        sourceRegistry: MutableMap<String, Image>,
    ): Image {
        subCacheDir.mkdirs()

        val starsYamlFile = workingDirectory.resolve(config.align.alignedOutputDirectory).resolve("stars.yaml")
        val stars = loadOrFindStars(starsYamlFile, image, config.align)

        val starImage = copyOnlyStars(image, stars, cfg.factor)
        val backgroundImage = image - starImage

        val starsCacheDir = subCacheDir.resolve(cfg.starsBranch.name.ifEmpty { "stars" })
        val backgroundCacheDir = subCacheDir.resolve(cfg.backgroundBranch.name.ifEmpty { "background" })
        starsCacheDir.mkdirs()
        backgroundCacheDir.mkdirs()

        val processedStars = processEnhanceSteps(starImage, dirty, cfg.starsBranch.steps, formatConfig, enhanceConfig, starsCacheDir, sourceRegistry).first
        val processedBackground = processEnhanceSteps(backgroundImage, dirty, cfg.backgroundBranch.steps, formatConfig, enhanceConfig, backgroundCacheDir, sourceRegistry).first

        val maskMatrix = buildStarMask(image, stars, cfg.factor, cfg.softMaskBlurRadius)
        return blendWithMask(processedStars, processedBackground, maskMatrix)
    }

    private fun processDecompose(
        image: Image,
        cfg: DecomposeConfig,
        formatConfig: FormatConfig,
        enhanceConfig: EnhanceConfig,
        subCacheDir: File,
        dirty: Boolean,
        sourceRegistry: MutableMap<String, Image>,
    ): Image {
        subCacheDir.mkdirs()
        return when (cfg.mode) {
            DecomposeMode.LRGB -> {
                val luminanceMatrix = image[Channel.Gray]
                val luminanceImage = MatrixImage(luminanceMatrix)

                val processedLuminance = if (cfg.luminance != null) {
                    val lCacheDir = subCacheDir.resolve(cfg.luminance!!.name.ifEmpty { "luminance" })
                    lCacheDir.mkdirs()
                    processEnhanceSteps(luminanceImage, dirty, cfg.luminance!!.steps, formatConfig, enhanceConfig, lCacheDir, sourceRegistry).first
                } else {
                    luminanceImage
                }

                val processedColor = if (cfg.color != null) {
                    val cCacheDir = subCacheDir.resolve(cfg.color!!.name.ifEmpty { "color" })
                    cCacheDir.mkdirs()
                    processEnhanceSteps(image, dirty, cfg.color!!.steps, formatConfig, enhanceConfig, cCacheDir, sourceRegistry).first
                } else {
                    image
                }

                processedColor.replaceBrightness(processedLuminance, 1.0, Channel.Gray)
            }

            DecomposeMode.RGB -> {
                fun processBranch(branch: BranchConfig?, channelMatrix: ch.obermuhlner.kimage.core.matrix.Matrix, branchName: String): ch.obermuhlner.kimage.core.matrix.Matrix {
                    if (branch == null) return channelMatrix
                    val branchCacheDir = subCacheDir.resolve(branch.name.ifEmpty { branchName })
                    branchCacheDir.mkdirs()
                    val channelImage = MatrixImage(channelMatrix)
                    val processed = processEnhanceSteps(channelImage, dirty, branch.steps, formatConfig, enhanceConfig, branchCacheDir, sourceRegistry).first
                    return processed[Channel.Gray]
                }
                val redResult = processBranch(cfg.red, image[Channel.Red], "red")
                val greenResult = processBranch(cfg.green, image[Channel.Green], "green")
                val blueResult = processBranch(cfg.blue, image[Channel.Blue], "blue")
                MatrixImage(redResult, greenResult, blueResult)
            }

            DecomposeMode.HSB -> {
                fun processBranch(branch: BranchConfig?, channelMatrix: ch.obermuhlner.kimage.core.matrix.Matrix, branchName: String): ch.obermuhlner.kimage.core.matrix.Matrix {
                    if (branch == null) return channelMatrix
                    val branchCacheDir = subCacheDir.resolve(branch.name.ifEmpty { branchName })
                    branchCacheDir.mkdirs()
                    val channelImage = MatrixImage(channelMatrix)
                    val processed = processEnhanceSteps(channelImage, dirty, branch.steps, formatConfig, enhanceConfig, branchCacheDir, sourceRegistry).first
                    return processed[Channel.Gray]
                }
                val hueResult = processBranch(cfg.hue, image[Channel.Hue], "hue")
                val saturationResult = processBranch(cfg.saturation, image[Channel.Saturation], "saturation")
                val brightnessResult = processBranch(cfg.brightness, image[Channel.Brightness], "brightness")
                val hsbImage = MatrixImage(
                    image.width, image.height,
                    Channel.Hue to hueResult,
                    Channel.Saturation to saturationResult,
                    Channel.Brightness to brightnessResult,
                )
                MatrixImage(
                    image.width, image.height,
                    Channel.Red to hsbImage[Channel.Red],
                    Channel.Green to hsbImage[Channel.Green],
                    Channel.Blue to hsbImage[Channel.Blue],
                )
            }
        }
    }

    private fun processMaskedProcess(
        image: Image,
        cfg: MaskedProcessConfig,
        formatConfig: FormatConfig,
        enhanceConfig: EnhanceConfig,
        subCacheDir: File,
        dirty: Boolean,
        sourceRegistry: MutableMap<String, Image>,
    ): Image {
        subCacheDir.mkdirs()

        val maskMatrix = when (cfg.mask.source) {
            MaskSource.Stars -> {
                val starsYamlFile = workingDirectory.resolve(config.align.alignedOutputDirectory).resolve("stars.yaml")
                val stars = loadOrFindStars(starsYamlFile, image, config.align)
                buildStarMask(image, stars, cfg.mask.factor, cfg.mask.blur)
            }
            MaskSource.Luminance -> {
                val threshold = cfg.mask.threshold
                val blur = cfg.mask.blur
                val binaryMatrix = FloatMatrix(image.height, image.width) { row, col ->
                    if (image[Channel.Gray][row, col] >= threshold) 1f else 0f
                }
                val binaryImage = MatrixImage(binaryMatrix)
                val blurred = if (blur > 0) binaryImage.gaussianBlurFilter(blur) else binaryImage
                blurred[Channel.Red]
            }
            MaskSource.File -> {
                val maskFile = File(cfg.mask.maskFile ?: error("maskFile must be set for MaskSource.File"))
                val loadedMask = ImageReader.read(maskFile)
                loadedMask[Channel.Gray]
            }
            MaskSource.Platesolve -> {
                val objName = cfg.mask.objectName ?: error("objectName must be set for MaskSource.Platesolve")
                val dso = DeepSkyObjects.name(objName) ?: error("Unknown deep sky object: $objName")
                FloatMatrix(image.height, image.width) { _, _ -> 0f }
            }
        }

        val insideCacheDir = subCacheDir.resolve(cfg.insideMask.name.ifEmpty { "inside" })
        val outsideCacheDir = subCacheDir.resolve(cfg.outsideMask.name.ifEmpty { "outside" })
        insideCacheDir.mkdirs()
        outsideCacheDir.mkdirs()

        val processedInside = processEnhanceSteps(image, dirty, cfg.insideMask.steps, formatConfig, enhanceConfig, insideCacheDir, sourceRegistry).first
        val processedOutside = processEnhanceSteps(image, dirty, cfg.outsideMask.steps, formatConfig, enhanceConfig, outsideCacheDir, sourceRegistry).first

        return blendWithMask(processedInside, processedOutside, maskMatrix)
    }

    private fun processOutput(
        image: Image,
        infoTokens: Map<String, String>,
        outputConfig: OutputFormatConfig
    ): Image {
        val currentDir = workingDirectory

        currentDir.resolve(outputConfig.outputDirectory).mkdirs()

        val outputName = outputConfig.outputName.replaceTokens(infoTokens)
        for (finalOutputImageExtension in outputConfig.outputImageExtensions) {
            val enhancedFile = currentDir.resolve(outputConfig.outputDirectory)
                .resolve("$outputName.$finalOutputImageExtension")
            println("Writing $enhancedFile")
            ImageWriter.write(image, enhancedFile)
        }

        return image
    }

    fun processAnnotate(
        inputImage: Image,
        infoTokens: Map<String, String>,
        wcsFile: File?,
        annotateConfig: AnnotateConfig,
        outputConfig: OutputFormatConfig
    ): Image {
        if (!annotateConfig.enabled) return inputImage

        val currentDir = workingDirectory

        val annotateOutputDir = currentDir.resolve(annotateConfig.annotatedOutputDirectory)
        annotateOutputDir.mkdirs()

        var annotatedImage = inputImage

        annotatedImage = if (annotateConfig.decorate.enabled) {
            val annotateZoom = AnnotateZoom()
            annotateZoom.title = annotateConfig.decorate.title.replaceTokens(infoTokens)
            annotateZoom.subtitle = annotateConfig.decorate.subtitle.replaceTokens(infoTokens)
            annotateZoom.text = annotateConfig.decorate.text.replaceTokens(infoTokens)
            annotateZoom.setColorTheme(annotateConfig.decorate.colorTheme)
            annotateZoom.markerStyle = annotateConfig.decorate.markerStyle
            annotateZoom.markerLabelStyle = annotateConfig.decorate.markerLabelStyle
            annotateZoom.magnitude = Optional.ofNullable(config.annotate.decorate.platesolveMarkers.magnitude)
            annotateZoom.minObjectSize = config.annotate.decorate.platesolveMarkers.minObjectSize
            annotateZoom.whiteList = Optional.ofNullable(config.annotate.decorate.platesolveMarkers.whiteList)
            annotateZoom.blackList = Optional.ofNullable(config.annotate.decorate.platesolveMarkers.blackList)
            annotateZoom.grid = config.annotate.decorate.grid
            for (markerConfig in annotateConfig.decorate.markers) {
                annotateZoom.addMarker(Marker(
                    markerConfig.name,
                    markerConfig.x,
                    markerConfig.y,
                    markerConfig.size,
                    markerConfig.info1,
                    markerConfig.info2
                ))
            }
            val wcsConverter = wcsFile?.takeIf { it.exists() }?.let {
                val wcsData = WCSParser.parse(wcsFile)
                WCSConverter(wcsData)
            }

            if (wcsConverter != null && config.annotate.decorate.platesolveMarkers.enabled) {
                annotateZoom.addPlatesolve(inputImage, wcsConverter)
            }
            annotateZoom.annotate(annotatedImage, wcsConverter)
        } else {
            annotatedImage
        }

        annotatedImage = if (annotateConfig.draw.enabled) {
            val drawConfig = annotateConfig.draw
            graphics(
                annotatedImage,
                drawConfig.margin.top,
                drawConfig.margin.left,
                drawConfig.margin.bottom,
                drawConfig.margin.right
            ) { graphics2D, width, height, _, _ ->
                for (drawStepIndex in drawConfig.steps.indices) {
                    val drawStepConfig = drawConfig.steps[drawStepIndex]

                    when (drawStepConfig.type) {
                        DrawStepType.Color -> {
                            val colorConfig = drawStepConfig.color!!
                            graphics2D.color = java.awt.Color(colorConfig.color.toInt(16))
                        }

                        DrawStepType.Stroke -> {
                            val strokeConfig = drawStepConfig.stroke!!
                            graphics2D.stroke = java.awt.BasicStroke(strokeConfig.width)
                        }

                        DrawStepType.FontSize -> {
                            val fontSizeConfig = drawStepConfig.fontSize!!
                            graphics2D.font = graphics2D.font.deriveFont(fontSizeConfig.size)
                        }

                        DrawStepType.Line -> {
                            val lineConfig = drawStepConfig.line!!
                            graphics2D.drawLine(lineConfig.x1, lineConfig.y1, lineConfig.x2, lineConfig.y2)
                        }

                        DrawStepType.Rectangle -> {
                            val rectangleConfig = drawStepConfig.rectangle!!
                            graphics2D.drawRect(rectangleConfig.x, rectangleConfig.y, rectangleConfig.width, rectangleConfig.height)
                        }

                        DrawStepType.Text -> {
                            val textConfig = drawStepConfig.text!!
                            val text = textConfig.text.replaceTokens(infoTokens)
                            graphics2D.drawString(text, textConfig.x, textConfig.y)
                        }
                    }
                }
            }
        } else {
            annotatedImage
        }

        val outputName = outputConfig.outputName.replaceTokens(infoTokens)
        for (finalOutputImageExtension in outputConfig.outputImageExtensions) {
            val annotatedFile = currentDir.resolve(annotateConfig.annotatedOutputDirectory)
                .resolve("$outputName.$finalOutputImageExtension")
            println("Writing $annotatedFile")
            ImageWriter.write(annotatedImage, annotatedFile)
        }

        return annotatedImage
    }

    private fun String.replaceTokens(tokenValues: Map<String, String>): String {
        val tokenRegex = Regex("\\{(\\w+)}")
        return this.replace(tokenRegex) { match ->
            val key = match.groupValues[1]
            tokenValues[key] ?: ""
        }
    }

    private fun debayerImageIfConfigured(image: Image, debayerConfig: DebayerConfig): Image {
        return if (debayerConfig.enabled) {
            val badPixels = if (debayerConfig.cleanupBadPixels) {
                val mosaic = image[Channel.Red]
                val badPixels = mosaic.findBayerBadPixels()
                println("Found ${badPixels.size} bad pixels")
                badPixels
            } else {
                emptySet()
            }
            image.debayer(debayerConfig.bayerPattern, interpolation = debayerConfig.interpolation, badpixelCoords = badPixels)
        } else {
            image
        }
    }

    private fun getFixPoints(image: Image, fixPointsConfig: FixPointsConfig): List<PointXY> {
        return when (fixPointsConfig.type) {
            FixPointType.Grid -> image.createFixPointGrid(fixPointsConfig.gridSize)
            FixPointType.FourCorners -> image.createFixPointFourCorners(fixPointsConfig.borderDistance)
            FixPointType.EightCorners -> image.createFixPointEightCorners(fixPointsConfig.borderDistance)
            FixPointType.Custom -> fixPointsConfig.customPoints.map { PointXY(it.x, it.y)}
        }
    }

    private fun resolvedTarget(target: TargetConfig): TargetConfig {
        val resolved = target.copy()

        if (resolved.ra == null && resolved.dec == null) {
            val name = target.name
            if (name != null) {
                val ngc = DeepSkyObjects.name(name)
                if (ngc != null) {
                    resolved.ra = ngc.ra
                    resolved.dec = ngc.dec
                }
            }
        }

        return resolved
    }

    private fun transformFileFor(imageFile: File): File =
        imageFile.resolveSibling("${imageFile.nameWithoutExtension}.transform")

    private fun saveTransformMatrix(transform: ch.obermuhlner.kimage.core.matrix.Matrix, file: File) {
        val sb = StringBuilder()
        for (row in 0 until transform.rows) {
            sb.append((0 until transform.cols).joinToString(" ") { col -> transform[row, col].toString() })
            sb.append("\n")
        }
        file.writeText(sb.toString())
    }

    private fun loadTransformMatrix(file: File): ch.obermuhlner.kimage.core.matrix.Matrix {
        val lines = file.readLines().filter { it.isNotBlank() }
        val values = lines.map { line -> line.trim().split(" ").map { it.toDouble() } }
        val rows = values.size
        val cols = values[0].size
        return DoubleMatrix(rows, cols) { row, col -> values[row][col] }
    }

    private fun executeCommand(vararg arguments: String): String {
        val process = ProcessBuilder(*arguments)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            println("WARNING: Command '${arguments[0]}' exited with code $exitCode")
        }

        return output
    }
}

fun raToString(raDegrees: Double): String {
    val totalHours = raDegrees / 15.0
    val hours = floor(totalHours).toInt()
    val minutes = floor((totalHours - hours) * 60).toInt()
    val seconds = (totalHours * 3600) - (hours * 3600) - (minutes * 60)
    return String.format("%02dh %02dm %05.2fs", hours, minutes, seconds)
}

fun decToString(decDegrees: Double): String {
    val sign = if (decDegrees >= 0) "+" else "-"
    val absDeg = abs(decDegrees)
    val degrees = floor(absDeg).toInt()
    val minutes = floor((absDeg - degrees) * 60).toInt()
    val seconds = (absDeg * 3600) - (degrees * 3600) - (minutes * 60)
    return String.format("%s%02d° %02d' %05.2f\"", sign, degrees, minutes, seconds)
}
