package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.astro.align.applyTransformationToImage
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.astro.align.formatTransformation
import ch.obermuhlner.kimage.astro.align.processCalibrationImages
import ch.obermuhlner.kimage.astro.background.createFixPointEightCorners
import ch.obermuhlner.kimage.astro.background.createFixPointFourCorners
import ch.obermuhlner.kimage.astro.background.createFixPointGrid
import ch.obermuhlner.kimage.astro.background.getFixPointValues
import ch.obermuhlner.kimage.astro.background.interpolate
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchSigmoidLike
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.div
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur3Filter
import ch.obermuhlner.kimage.core.image.filter.laplacianFilter
import ch.obermuhlner.kimage.core.image.hdr.highDynamicRange
import ch.obermuhlner.kimage.core.image.histogram.histogramImage
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.noise.reduceNoiseUsingMultiScaleMedianTransformOverAllChannels
import ch.obermuhlner.kimage.core.image.noise.reduceNoiseUsingMultiScaleMedianTransformOverGrayChannel
import ch.obermuhlner.kimage.core.image.noise.thresholdHard
import ch.obermuhlner.kimage.core.image.noise.thresholdSigmoid
import ch.obermuhlner.kimage.core.image.noise.thresholdSoft
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.image.stack.max
import ch.obermuhlner.kimage.core.image.stack.stack
import ch.obermuhlner.kimage.core.image.statistics.normalizeImage
import ch.obermuhlner.kimage.core.image.times
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalance
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalanceGlobal
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalanceLocal
import ch.obermuhlner.kimage.core.math.Histogram
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.matrix.values.values
import ch.obermuhlner.kimage.util.elapsed
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.math.min

data class ProcessConfig(
    var quick: Boolean = false,
    var quickCount: Int = 3,
    var format: FormatConfig = FormatConfig(),
    var calibrate: CalibrateConfig = CalibrateConfig(),
    var align: AlignConfig = AlignConfig(),
    var stack: StackConfig = StackConfig(),
    var enhance: EnhanceConfig = EnhanceConfig(),
)

data class FormatConfig(
    var inputImageExtension: String = "fit",
    var inputDirectory: String = ".",
    var debayer: DebayerConfig = DebayerConfig(),
    var outputImageExtension: String = "tif",
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
    var enhancedOutputDirectory: String = "astro-process/enhanced",
    var debayer: DebayerConfig = DebayerConfig(enabled = false),
    var crop: CropConfig = CropConfig(),
    var noise: NoiseConfig = NoiseConfig(),
    var background: BackgroundConfig = BackgroundConfig(),
    var whitebalance: WhitebalanceConfig = WhitebalanceConfig(),
    var colorStretch: ColorStretchConfig = ColorStretchConfig(),
    var histogram: HistogramConfig = HistogramConfig(),
    var finalFormat: FinalFormatConfig = FinalFormatConfig()
)

data class CropConfig(
    var enabled: Boolean = true,
    var x: Int = 20,
    var y: Int = 20,
    var width: Int = -20,
    var height: Int = -20,
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

data class NoiseConfig(
    var enabled: Boolean = true,
    var algorithm: NoiseReductionAlgorithm = NoiseReductionAlgorithm.MultiScaleMedianOverAllChannels,
    var thresholding: Thresholding = Thresholding.Soft,
    var thresholds: MutableList<Double> = mutableListOf(0.0001)
)

data class BackgroundConfig(
    var enabled: Boolean = false,
    var fixPoints: FixPointsConfig = FixPointsConfig(),
    var medianRadius: Int = 50,
    var power: Double = 1.5,
    var offset: Double = 0.01,
)

data class FixPointsConfig(
    var type: FixPointType = FixPointType.FourCorners,
    var borderDistance: Int = 100,
    var gridSize: Int = 2,
    var customPoints: MutableList<PointXY> = mutableListOf()
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

data class ColorStretchConfig(
    var enabled: Boolean = true,
    var steps: MutableList<ColorStretchStepConfig> = mutableListOf()
)

data class ColorStretchStepConfig(
    var enabled: Boolean = true,
    var type: ColorStretchStepType = ColorStretchStepType.LinearPercentile,
    var sigmoidMidpoint: Double = 0.1,
    var sigmoidFactor: Double = 10.0,
    var linearPercentileMin: Double = 0.0001,
    var linearPercentileMax: Double = 0.9999,
    var blurStrength: Double = 0.1,
    var addToHighDynamicRange: Boolean = false,
)

enum class ColorStretchStepType {
    LinearPercentile,
    Sigmoid,
    Blur,
    HighDynamicRange
}

data class HistogramConfig(
    var enabled: Boolean = true,
    var histogramWidth: Int = 1000,
    var histogramHeight: Int = 400,
    var printPercentiles: Boolean = false,
)

data class FinalFormatConfig(
    var outputImageExtensions: MutableList<String> = mutableListOf("tif", "jpg", "png")
)

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
  crop:
    enabled: true
    x: 100
    y: 100
    width: -100
    height: -100
  whitebalance:
    enabled: true
    type: Local
    fixPoints:
      type: FourCorners
      borderDistance: 100
      customPoints: []
    localMedianRadius: 50
    valueRangeMin: 0.2
    valueRangeMax: 0.9
  colorStretch:
    enabled: true
    steps:
    - type: LinearPercentile
      addToHighDynamicRange: true
    - type: Blur
      blurStrength: 0.1
    - type: Sigmoid
      sigmoidMidpoint: 0.01
      sigmoidFactor: 1.1
    - type: LinearPercentile
      addToHighDynamicRange: true
    - type: Sigmoid
      sigmoidMidpoint: 0.3
      sigmoidFactor: 1.1
      addToHighDynamicRange: true
    - type: Sigmoid
      sigmoidMidpoint: 0.4
      sigmoidFactor: 1.1
      addToHighDynamicRange: true
    - type: Sigmoid
      sigmoidMidpoint: 0.4
      sigmoidFactor: 1.1
      addToHighDynamicRange: true
    - type: HighDynamicRange
    - type: Sigmoid
      sigmoidMidpoint: 0.4
      sigmoidFactor: 1.5
  noise:
    enabled: true
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
            AstroProcess(config).astroProcess()
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

    fun astroProcess() {
        val currentDir = File(".")
        var dirty = false

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

        val minBackground = mutableMapOf<Channel, Double>()
        if (config.calibrate.normalizeBackground.enabled) {
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

            println()
            minBackground.forEach { (channel, median) ->
                println("Minimum background $channel: $median")
            }
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

        var referenceCalibratedFile = calibratedFiles.first()
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
        elapsed("enhance") {
            val referenceName = inputFiles[0].nameWithoutExtension
            astroEnhance(config.format, config.enhance, stackedImage, dirty, referenceName)
        }
    }

    fun astroEnhance(
        formatConfig: FormatConfig,
        enhanceConfig: EnhanceConfig,
        inputFile: File,
    ) {
        println("Reading $inputFile")
        var inputImage = ImageReader.read(inputFile)

        return astroEnhance(formatConfig, enhanceConfig, inputImage, true, inputFile.nameWithoutExtension)
    }

    fun astroEnhance(
        formatConfig: FormatConfig,
        enhanceConfig: EnhanceConfig,
        inputImage: Image,
        alreadyDirty: Boolean,
        referenceName: String
    ) {
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

        if (enhanceConfig.debayer.enabled) {
            step("debayering") {
                debayerImageIfConfigured(it, enhanceConfig.debayer)
            }
        }

        if (enhanceConfig.crop.enabled) {
            step("crop") {
                val width =
                    if (enhanceConfig.crop.width < 0) it.width - enhanceConfig.crop.x + enhanceConfig.crop.width else enhanceConfig.crop.width
                val height =
                    if (enhanceConfig.crop.height < 0) it.height - enhanceConfig.crop.y + enhanceConfig.crop.height else enhanceConfig.crop.height
                it.crop(enhanceConfig.crop.x, enhanceConfig.crop.y, width, height, false)
            }
        }

        if (enhanceConfig.background.enabled) {
            step("background") {
                val fixPoints = getFixPoints(it, enhanceConfig.background.fixPoints)
                val fixPointValues = it.getFixPointValues(fixPoints, enhanceConfig.background.medianRadius)
                val background = it.interpolate(fixPointValues, power = enhanceConfig.background.power)
                it - background + enhanceConfig.background.offset
            }
        }

        if (enhanceConfig.whitebalance.enabled) {
            step("whitebalance") {
                when(enhanceConfig.whitebalance.type) {
                    WhitebalanceType.Global -> it.applyWhitebalanceGlobal(enhanceConfig.whitebalance.valueRangeMin, enhanceConfig.whitebalance.valueRangeMax)
                    WhitebalanceType.Local -> it.applyWhitebalanceLocal(getFixPoints(it, enhanceConfig.whitebalance.fixPoints), enhanceConfig.whitebalance.localMedianRadius)
                    WhitebalanceType.Custom -> it.applyWhitebalance(enhanceConfig.whitebalance.customRed, enhanceConfig.whitebalance.customGreen, enhanceConfig.whitebalance.customBlue)
                }
                it
            }
        }

        if (enhanceConfig.colorStretch.enabled) {
            val hdrSourceImages = mutableListOf<Image>()
            for (colorStretchStepIndex in enhanceConfig.colorStretch.steps.indices) {
                val colorStretchStepConfig = enhanceConfig.colorStretch.steps[colorStretchStepIndex]
                val name = "stretch-$colorStretchStepIndex-${colorStretchStepConfig.type}"
                step(name) {
                    val stepResultImage = when (colorStretchStepConfig.type) {
                        ColorStretchStepType.LinearPercentile -> {
                            it.stretchLinearPercentile(
                                colorStretchStepConfig.linearPercentileMin,
                                colorStretchStepConfig.linearPercentileMax
                            )
                        }

                        ColorStretchStepType.Sigmoid -> {
                            it.stretchSigmoidLike(
                                colorStretchStepConfig.sigmoidMidpoint,
                                colorStretchStepConfig.sigmoidFactor
                            )
                        }

                        ColorStretchStepType.Blur -> {
                            (it * (1.0 - colorStretchStepConfig.blurStrength)) + (it.gaussianBlur3Filter() * colorStretchStepConfig.blurStrength)
                        }

                        ColorStretchStepType.HighDynamicRange -> {
                            highDynamicRange(hdrSourceImages.map { { it } })
                        }
                    }
                    stepResultImage
                }
                if (colorStretchStepConfig.addToHighDynamicRange) {
                    hdrSourceImages.add(image)
                }
            }
        }

        if (enhanceConfig.noise.enabled) {
            step("noise reduction") {
                val thresholdingFunc: (Double, Double) -> Double = when(enhanceConfig.noise.thresholding) {
                    Thresholding.Hard -> { v, threshold -> thresholdHard(v, threshold) }
                    Thresholding.Soft -> { v, threshold -> thresholdSoft(v, threshold) }
                    Thresholding.Sigmoid -> { v, threshold -> thresholdSigmoid(v, threshold) }
                    Thresholding.SigmoidLike ->  { v, threshold -> thresholdSigmoid(v, threshold) }
                }
                when(enhanceConfig.noise.algorithm) {
                    NoiseReductionAlgorithm.MultiScaleMedianOverAllChannels -> {
                        it.reduceNoiseUsingMultiScaleMedianTransformOverAllChannels(enhanceConfig.noise.thresholds, thresholdingFunc)
                    }
                    NoiseReductionAlgorithm.MultiScaleMedianOverGrayChannel -> {
                        it.reduceNoiseUsingMultiScaleMedianTransformOverGrayChannel(enhanceConfig.noise.thresholds, thresholdingFunc)
                    }
                }
            }
        }

        for (finalOutputImageExtension in enhanceConfig.finalFormat.outputImageExtensions) {
            val enhancedFile = currentDir.resolve(enhanceConfig.enhancedOutputDirectory)
                .resolve("$referenceName.$finalOutputImageExtension")
            println("Writing $enhancedFile")
            ImageWriter.write(image, enhancedFile)
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
            FixPointType.Custom -> fixPointsConfig.customPoints
        }
    }
}