package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.astro.align.applyTransformationToImage
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.astro.align.formatTransformation
import ch.obermuhlner.kimage.astro.align.processCalibrationImages
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme.Green
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.Marker
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.MarkerLabelStyle
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.MarkerStyle
import ch.obermuhlner.kimage.astro.background.createFixPointEightCorners
import ch.obermuhlner.kimage.astro.background.createFixPointFourCorners
import ch.obermuhlner.kimage.astro.background.createFixPointGrid
import ch.obermuhlner.kimage.astro.background.getFixPointValues
import ch.obermuhlner.kimage.astro.background.interpolate
import ch.obermuhlner.kimage.astro.color.histogram
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchSigmoidLike
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.image.awt.graphics
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.div
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur3Filter
import ch.obermuhlner.kimage.core.image.filter.laplacianFilter
import ch.obermuhlner.kimage.core.image.filter.sharpenFilter
import ch.obermuhlner.kimage.core.image.filter.unsharpMaskFilter
import ch.obermuhlner.kimage.core.image.hdr.highDynamicRange
import ch.obermuhlner.kimage.core.image.histogram.histogramImage
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.noise.reduceNoiseUsingMultiScaleMedianTransformOverAllChannels
import ch.obermuhlner.kimage.core.image.noise.reduceNoiseUsingMultiScaleMedianTransformOverGrayChannel
import ch.obermuhlner.kimage.core.image.noise.thresholdHard
import ch.obermuhlner.kimage.core.image.noise.thresholdSigmoid
import ch.obermuhlner.kimage.core.image.noise.thresholdSigmoidLike
import ch.obermuhlner.kimage.core.image.noise.thresholdSoft
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.image.stack.max
import ch.obermuhlner.kimage.core.image.stack.stack
import ch.obermuhlner.kimage.core.image.statistics.normalizeImage
import ch.obermuhlner.kimage.core.image.times
import ch.obermuhlner.kimage.core.image.transform.rotateLeft
import ch.obermuhlner.kimage.core.image.transform.rotateRight
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalance
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalanceGlobal
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalanceLocal
import ch.obermuhlner.kimage.core.math.Histogram
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.math.toRadians
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.values.values
import ch.obermuhlner.kimage.util.elapsed
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class ProcessConfig(
    var quick: Boolean = false,
    var quickCount: Int = 3,
    var format: FormatConfig = FormatConfig(),
    var calibrate: CalibrateConfig = CalibrateConfig(),
    var align: AlignConfig = AlignConfig(),
    var stack: StackConfig = StackConfig(),
    var enhance: EnhanceConfig = EnhanceConfig(),
    var annotate: AnnotateConfig = AnnotateConfig(),
    var output: OutputFormatConfig = OutputFormatConfig(),
)

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
    var markerStyle: MarkerStyle = MarkerStyle.Rectangle,
    var markerLabelStyle: MarkerLabelStyle = MarkerLabelStyle.Index,
    var markers: MutableList<MarkerConfig> = mutableListOf()
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
)

data class CalibrateConfig(
    var inputImageExtension: String = "fit",
    var debayer: DebayerConfig = DebayerConfig(),
    var biasDirectory: String = "bias",
    var flatDirectory: String = "flat",
    var darkflatDirectory: String = "darkflat",
    var darkDirectory: String = "dark",
    var searchParentDirectories: Boolean = true,
    var normalizeBackground: NormalizeBackgroundConfig = NormalizeBackgroundConfig(),
    var calibratedOutputDirectory: String = "astro-process/calibrated",
)

data class NormalizeBackgroundConfig(
    var enabled: Boolean = true,
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
)

data class EnhanceConfig(
    var measure: RectangleConfig = RectangleConfig(),
    var regionOfInterest: RectangleConfig = RectangleConfig(),
    var steps: MutableList<EnhanceStepConfig> = mutableListOf(),
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
    var sigmoid: SigmoidConfig? = null,
    var linearPercentile: LinearPercentileConfig? = null,
    var blur: BlurConfig? = null,
    var sharpen: SharpenConfig? = null,
    var unsharpMask: UnsharpMaskConfig? = null,
    var highDynamicRange: HighDynamicRangeConfig? = null,
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
            sigmoid != null -> EnhanceStepType.Sigmoid
            linearPercentile != null -> EnhanceStepType.LinearPercentile
            blur != null -> EnhanceStepType.Blur
            sharpen != null -> EnhanceStepType.Sharpen
            unsharpMask != null -> EnhanceStepType.UnsharpMask
            highDynamicRange != null -> EnhanceStepType.HighDynamicRange
            else -> throw IllegalArgumentException("No enhancement step configuration found")
        }
}

enum class EnhanceStepType {
    Debayer,
    Crop,
    Rotate,
    Whitebalance,
    RemoveBackground,
    LinearPercentile,
    Sigmoid,
    Blur,
    Sharpen,
    UnsharpMask,
    ReduceNoise,
    HighDynamicRange
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
}

val defaultAstroProcessConfigText = """
quick: false
quickCount: 3
format:
  debayer:
    enabled: true
    bayerPattern: RGGB
  inputImageExtension: fit
  outputImageExtension: tif
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
      midpoint: 0.3
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
      midpoint: 0.4
      strength: 1.5
  - reduceNoise:
      thresholding: Soft
      thresholds:
        - 0.01
        - 0.001
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
            configFile.writeText(defaultAstroProcessConfigText)
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
    fun imageAnalysis() {
        val currentDir = File(".")
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
        val currentDir = File(".")

        currentDir.absoluteFile.parentFile?.let {
            infoTokens[InfoTokens.parentDir.name] = it.name
        }

        println("### Processing calibration images ...")

        println()
        val (bias, dirtyBias) = elapsed("Processing bias frames") {
            processCalibrationImages(
                "bias",
                currentDir,
                dirty,
                config.calibrate.biasDirectory,
                config.calibrate.searchParentDirectories,
                config.calibrate.debayer.enabled
            )
        }
        if (bias != null) {
            infoTokens.merge(InfoTokens.calibration.name, "bias") { old, new -> "$old,$new" }
        }
        dirty = dirty || dirtyBias

        println()
        var (flat, dirtyFlat) = elapsed("Processing flat frames") {
            processCalibrationImages(
                "flat",
                currentDir,
                dirty,
                config.calibrate.flatDirectory,
                config.calibrate.searchParentDirectories,
                config.calibrate.debayer.enabled
            ).let {
                Pair(it.first.normalizeImage(),it.second)
            }
        }
        if (flat != null) {
            infoTokens.merge(InfoTokens.calibration.name, "flat") { old, new -> "$old,$new" }
        }
        dirty = dirty || dirtyFlat

        println()
        var (darkflat, dirtyDarkFlat) = elapsed("Processing darkflat frames") {
            processCalibrationImages(
                "darkflat",
                currentDir,
                dirty,
                config.calibrate.darkflatDirectory,
                config.calibrate.searchParentDirectories,
                config.calibrate.debayer.enabled
            )
        }
        if (darkflat != null) {
            infoTokens.merge(InfoTokens.calibration.name, "darkflat") { old, new -> "$old,$new" }
        }
        dirty = dirty || dirtyDarkFlat

        println()
        var (dark, dirtyDark) = elapsed("Processing dark frames") {
            processCalibrationImages(
                "dark",
                currentDir,
                dirty,
                config.calibrate.darkDirectory,
                config.calibrate.searchParentDirectories,
                config.calibrate.debayer.enabled
            )
        }
        if (dark != null) {
            infoTokens.merge(InfoTokens.calibration.name, "dark") { old, new -> "$old,$new" }
        }
        dirty = dirty || dirtyDark

        val files = currentDir.listFiles() ?: return

        currentDir.resolve(config.calibrate.calibratedOutputDirectory).mkdirs()
        currentDir.resolve(config.align.alignedOutputDirectory).mkdirs()
        currentDir.resolve(config.stack.stackedOutputDirectory).mkdirs()

        val applyBiasOnCalibration = false

        if (applyBiasOnCalibration && bias != null) {
            if (dark != null) {
                dark -= bias
            }
            if (darkflat != null) {
                darkflat -= bias
            }
            if (flat != null) {
                flat -= bias
            }
        }

        if (flat != null && darkflat != null) {
            flat -= darkflat
        }

        var inputFiles = files.filter { it.extension == config.format.inputImageExtension }.filterNotNull().sorted()
        if (config.quick) {
            println()
            println("Quick mode: only processing ${config.quickCount} input file")
            inputFiles = inputFiles.take(config.quickCount)
        }

        infoTokens[InfoTokens.inputCount.name] = inputFiles.size.toString()
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

        println()
        println("### Calibrating ${inputFiles.size} images ...")

        val (minBackground, dirtyMinBackground) = if (config.calibrate.normalizeBackground.enabled) {
            val minBackgroundFile = currentDir.resolve(config.calibrate.calibratedOutputDirectory)
                .resolve("minBackground.yaml")
            if (minBackgroundFile.exists()) {
                val yaml = Yaml()
                val stringMap = minBackgroundFile.inputStream().use {
                    yaml.load<Map<String, Double>>(it)
                }
                val map = stringMap.mapKeys { Channel.valueOf(it.key) }
                Pair(map, false)
            } else {
                val minBackground = mutableMapOf<Channel, Double>()
                println()
                elapsed("Normalizing backgrounds for ${inputFiles.size} input files") {
                    inputFiles.forEach { inputFile ->
                        val calibratedFile = currentDir.resolve(config.calibrate.calibratedOutputDirectory)
                            .resolve("${inputFile.nameWithoutExtension}.${config.format.outputImageExtension}")
                        if (!calibratedFile.exists()) {
                            println()
                            println("Loading $inputFile")
                            var light = elapsed("Reading light frame") { ImageReader.read(inputFile) }
                            if (config.format.debayer.enabled) {
                                elapsed("Debayering light frame $inputFile") {
                                    light =
                                        debayerImageIfConfigured(
                                            light,
                                            config.format.debayer.copy(cleanupBadPixels = false)
                                        )
                                }
                            }

                            for (channel in light.channels) {
                                val median = light[channel].values().median()
                                println("Background: $channel: $median")
                                minBackground[channel] = minBackground[channel]?.let { min(it, median) } ?: median
                            }
                        }
                    }
                }

                val yaml = Yaml()
                val stringMap = minBackground.mapKeys { it.key.name }
                minBackgroundFile.writeText(yaml.dumpAsMap(stringMap))
                Pair(minBackground, true)
            }
        } else {
            Pair(emptyMap(), false)
        }
        dirty = dirty || dirtyMinBackground


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
                if (config.format.debayer.enabled) {
                    light = elapsed("Debayering light frame $inputFile") {
                        debayerImageIfConfigured(light, config.format.debayer)
                    }
                }

                elapsed("Calibrating light frame $inputFile") {
                    if (bias != null) {
                        light -= bias
                    }
                    if (dark != null) {
                        light -= dark
                    }
                    if (flat != null) {
                        light /= flat
                    }

                    if (config.calibrate.normalizeBackground.enabled) {
                        for (channel in light.channels) {
                            val lowestBackground = minBackground[channel]
                            if (lowestBackground != null) {
                                val background = light[channel].values().median()
                                val delta = background - lowestBackground - config.calibrate.normalizeBackground.offset
                                light[channel].applyEach { v -> v - delta }
                            }
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
        println("### Aligning ${calibratedFiles.size} images ...")

        val referenceCalibratedFile = calibratedFiles.first()
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
        val alignedFiles = calibratedFiles
            .mapNotNull { calibratedFile ->
                val outputFile = currentDir.resolve(config.align.alignedOutputDirectory)
                    .resolve("${calibratedFile.nameWithoutExtension}.${config.format.outputImageExtension}")
                if (outputFile.exists() && !dirty && !dirtyCalibratedFiles.contains(calibratedFile)) {
                    return@mapNotNull outputFile
                }
                dirtyAligned = true

                println()
                println("Loading $calibratedFile")
                val light = ImageReader.read(calibratedFile)

                val alignedImage = if (calibratedFile == referenceCalibratedFile) {
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
                        println(formatTransformation(decomposeTransformationMatrix(transform)))

                        elapsed("Applying transformation to image") {
                            applyTransformationToImage(light, transform)
                        }
                    } else {
                        null
                    }
                }

                if (alignedImage != null) {
                    println("Saving $outputFile")
                    ImageWriter.write(alignedImage, outputFile)

                    outputFile
                } else {
                    null
                }
            }
        dirty = dirty || dirtyCalibrated || dirtyAligned

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

            val alignedFileSuppliers = alignedFiles.map {
                {
                    println("Loading $it")
                    val image = ImageReader.read(it)
                    stackedMaxImage = stackedMaxImage?.let { max(it, image) } ?: image
                    image
                }
            }
            val stackedImage = elapsed("Stacking images") { stack(alignedFileSuppliers) }

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

        println()
        println("### Enhancing stacked image ...")
        println()
        val enhancedImage = elapsed("enhance") {
            processEnhance(stackedImage, dirty, config.format, config.enhance)
        }

        println()
        println("### Writing output images ...")
        println()
        processOutput(enhancedImage, infoTokens, config.output)

        println()
        println("### Annotating image ...")
        println()
        processAnnotate(enhancedImage, infoTokens, config.annotate, config.output)
    }

    fun processEnhance(
        inputImage: Image,
        alreadyDirty: Boolean,
        formatConfig: FormatConfig,
        enhanceConfig: EnhanceConfig,
    ): Image {
        val currentDir = File(".")
        currentDir.resolve(enhanceConfig.enhancedOutputDirectory).mkdirs()

        var image = inputImage
        var dirty = alreadyDirty

        var stepIndex = 1

        fun step(name: String, stretchFunc: (Image) -> Image) {
            elapsed(name) {
                val stepImageFile = currentDir.resolve(enhanceConfig.enhancedOutputDirectory)
                    .resolve("step_${stepIndex}_$name.${formatConfig.outputImageExtension}")
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
                                currentDir.resolve(enhanceConfig.enhancedOutputDirectory)
                                    .resolve("histogram_step_${stepIndex}_$name.${formatConfig.outputImageExtension}")
                            )
                        }
                        if (enhanceConfig.histogram.printPercentiles) {
                            elapsed("  Printing histogram percentiles") {
                                val histogram = Histogram(enhanceConfig.histogram.histogramWidth)
                                histogram.add(image[Channel.Gray])
                                for (percentile in listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99)) {
                                    val percentileInt = (percentile * 100 + 0.5).toInt()
                                    println(
                                        "    percentile ${percentileInt}% : ${
                                            histogram.estimatePercentile(
                                                percentile
                                            )
                                        }"
                                    )
                                }
                            }
                        }
                    }
                }
                stepIndex++
            }
        }

        val hdrSourceImages = mutableListOf<Image>()
        for (enhanceStepIndex in enhanceConfig.steps.indices) {
            val enhanceStepConfig = enhanceConfig.steps[enhanceStepIndex]
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
                        it.debayer(enhanceStepConfig.debayer!!.bayerPattern, badpixelCoords = badPixels)
                    }

                    EnhanceStepType.Crop -> {
                        val width =
                            if (enhanceStepConfig.crop!!.width < 0) it.width - enhanceStepConfig.crop!!.x + enhanceStepConfig.crop!!.width else enhanceStepConfig.crop!!.width
                        val height =
                            if (enhanceStepConfig.crop!!.height < 0) it.height - enhanceStepConfig.crop!!.y + enhanceStepConfig.crop!!.height else enhanceStepConfig.crop!!.height
                        it.crop(
                            enhanceStepConfig.crop!!.x,
                            enhanceStepConfig.crop!!.y,
                            width,
                            height
                        )
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
                                it.crop(
                                    enhanceConfig.measure.x,
                                    enhanceConfig.measure.y,
                                    enhanceConfig.measure.width,
                                    enhanceConfig.measure.height,
                                ).histogram()
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

                            WhitebalanceType.Custom -> it.applyWhitebalance(
                                enhanceStepConfig.whitebalance!!.customRed,
                                enhanceStepConfig.whitebalance!!.customGreen,
                                enhanceStepConfig.whitebalance!!.customBlue
                            )
                        }
                        it
                    }

                    EnhanceStepType.Sigmoid -> {
                        it.stretchSigmoidLike(
                            enhanceStepConfig.sigmoid!!.midpoint,
                            enhanceStepConfig.sigmoid!!.strength
                        )
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
                            NoiseReductionAlgorithm.MultiScaleMedianOverAllChannels -> {
                                it.reduceNoiseUsingMultiScaleMedianTransformOverAllChannels(enhanceStepConfig.reduceNoise!!.thresholds, thresholdingFunc)
                            }

                            NoiseReductionAlgorithm.MultiScaleMedianOverGrayChannel -> {
                                it.reduceNoiseUsingMultiScaleMedianTransformOverGrayChannel(enhanceStepConfig.reduceNoise!!.thresholds, thresholdingFunc)
                            }
                        }
                    }

                    EnhanceStepType.HighDynamicRange -> {
                        highDynamicRange(hdrSourceImages.map { { it } })
                    }

                    else -> it
                }
                if (enhanceConfig.regionOfInterest.enabled) {
                    val roiImage = stepResultImage.crop(
                        enhanceConfig.regionOfInterest.x,
                        enhanceConfig.regionOfInterest.y,
                        enhanceConfig.regionOfInterest.width,
                        enhanceConfig.regionOfInterest.height
                    )
                    val roiImageFile = currentDir.resolve(enhanceConfig.enhancedOutputDirectory)
                        .resolve("region_step_${stepIndex}_${type.name}.${formatConfig.outputImageExtension}")
                    ImageWriter.write(roiImage, roiImageFile)
                }
                stepResultImage
            }
            if (enhanceStepConfig.addToHighDynamicRange) {
                hdrSourceImages.add(image)
            }
        }
        return image
    }

    private fun processOutput(
        image: Image,
        infoTokens: Map<String, String>,
        outputConfig: OutputFormatConfig
    ): Image {
        val currentDir = File(".")

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
        annotateConfig: AnnotateConfig,
        outputConfig: OutputFormatConfig
    ): Image {
        if (!annotateConfig.enabled) return inputImage

        val currentDir = File(".")

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
            annotateZoom.annotate(annotatedImage)
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
                            graphics2D.drawLine(lineConfig.x1, lineConfig.y1, lineConfig.x2, lineConfig.x2)
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
            image.debayer(debayerConfig.bayerPattern, badpixelCoords = badPixels)
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

    private fun <T> readConfig(file: File, clazz: Class<T>): T {
        val yaml = Yaml()
        return file.inputStream().use { input ->
            yaml.loadAs(input, clazz::class.java)
        }
    }

    private fun <T> writeConfig(file: File, config: T) {
        val yaml = Yaml()
        file.writeText(yaml.dumpAsMap(config))
    }
}