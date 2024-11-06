package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.astro.align.applyTransformationToImage
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.astro.align.formatTransformation
import ch.obermuhlner.kimage.astro.align.processCalibrationImages
import ch.obermuhlner.kimage.astro.background.createFixPointFourCorners
import ch.obermuhlner.kimage.astro.background.getFixPointValues
import ch.obermuhlner.kimage.astro.background.interpolate
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchSigmoid
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
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
import ch.obermuhlner.kimage.core.image.noise.reduceNoiseUsingMedianTransform
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.image.stack.stack
import ch.obermuhlner.kimage.core.image.statistics.normalizeImage
import ch.obermuhlner.kimage.core.image.times
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalance
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
    var enhance: EnhanceConfig = EnhanceConfig()
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
    var biasDirectory: String = "../bias",
    var flatDirectory: String = "../flat",
    var darkflatDirectory: String = "../darkflat",
    var darkDirectory: String = "../dark",
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
)

data class CropConfig(
    var enabled: Boolean = true,
    var x: Int = 20,
    var y: Int = 20,
    var width: Int = -20,
    var height: Int = -20,
)

data class NoiseConfig(
    var enabled: Boolean = true,
    var radius: Int = 1,
    var threshold: Double = 0.0001
)

data class BackgroundConfig(
    var enabled: Boolean = false,
    var gridSize: Int = 2,
    var borderDistance: Int = 100,
    var medianRadius: Int = 50,
    var power: Double = 1.5,
    var offset: Double = 0.01,
)

data class WhitebalanceConfig(
    var enabled: Boolean = true,
)

data class ColorStretchConfig(
    var enabled: Boolean = true,
    var steps: MutableList<ColorStretchStepConfig> = mutableListOf()
)

data class ColorStretchStepConfig(
    var enabled: Boolean = true,
    var type: ColorStretchStepType = ColorStretchStepType.LinearPercentile,
    var sigmoidMidpoint: Double = 0.25,
    var sigmoidFactor: Double = 6.0,
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
    var histogramWidth: Int = 1024,
    var histogramHeight: Int = 400,
)

fun main(args: Array<String>) {
    val yaml = Yaml()
    val configFile = File("kimage-astro-process.yaml")

    val config = if (configFile.exists()) {
        configFile.inputStream().use { input ->
            yaml.loadAs(input, ProcessConfig::class.java)
        }
    } else {
        ProcessConfig(
            enhance = EnhanceConfig(
                colorStretch = ColorStretchConfig(
                    steps = mutableListOf(
                        ColorStretchStepConfig(
                            type = ColorStretchStepType.LinearPercentile
                        ),
                        ColorStretchStepConfig(
                            type = ColorStretchStepType.Sigmoid
                        ),
                        ColorStretchStepConfig(
                            type = ColorStretchStepType.LinearPercentile
                        ),
                        ColorStretchStepConfig(
                            type = ColorStretchStepType.Blur
                        )
                    )
                )
            )
        )
    }

    val command = if (args.isNotEmpty()) args[0] else "process"

    when(command) {
        "process" -> {
            println(yaml.dumpAsMap(config))
            astroProcess(config)
        }
        "config" -> {
            println(yaml.dumpAsMap(config))
        }
        "init" -> {
            configFile.writeText(yaml.dumpAsMap(config))
        }
        "stars" -> {
            imageAnalysis(config)
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

fun imageAnalysis(config: ProcessConfig) {
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

fun astroProcess(config: ProcessConfig) {
    val currentDir = File(".")

    println("### Processing calibration images ...")

    println()
    val bias = elapsed("Processing bias frames") {
        processCalibrationImages(
            currentDir.resolve(config.calibrate.biasDirectory),
            "bias",
            config.calibrate.debayer.enabled
        )
    }
    println()
    var flat = elapsed("Processing flat frames") {
        processCalibrationImages(
            currentDir.resolve(config.calibrate.flatDirectory),
            "flat",
            config.calibrate.debayer.enabled
        ).normalizeImage()
    }
    println()
    var darkflat = elapsed("Processing darkflat frames") {
        processCalibrationImages(
            currentDir.resolve(config.calibrate.darkflatDirectory),
            "darkflat",
            config.calibrate.debayer.enabled
        )
    }
    println()
    var dark = elapsed("Processing dark frames") {
        processCalibrationImages(
            currentDir.resolve(config.calibrate.darkDirectory),
            "dark",
            config.calibrate.debayer.enabled
        )
    }

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
                                debayerImageIfConfigured(light, config.format.debayer.copy(cleanupBadPixels = false))
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

    val calibratedFiles = elapsed("Calibrating ${inputFiles.size} light frames") {
        inputFiles.map { inputFile ->
            val outputFile = currentDir.resolve(config.calibrate.calibratedOutputDirectory)
                .resolve("${inputFile.nameWithoutExtension}.${config.format.outputImageExtension}")
            if (outputFile.exists()) {
                return@map outputFile
            }

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

    val alignedFiles = calibratedFiles.mapNotNull { calibratedFile ->
        val outputFile = currentDir.resolve(config.align.alignedOutputDirectory)
            .resolve("${calibratedFile.nameWithoutExtension}.${config.format.outputImageExtension}")
        if (outputFile.exists()) {
            return@mapNotNull outputFile
        }

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

    println()
    println("### Stacking ${alignedFiles.size} aligned images ...")

    val outputFile = currentDir.resolve(config.stack.stackedOutputDirectory)
        .resolve("${inputFiles[0].nameWithoutExtension}_stacked_${alignedFiles.size}.${config.format.outputImageExtension}")
    val stackedImage = if (!outputFile.exists()) {
        val alignedFileSuppliers = alignedFiles.map {
            {
                println("Loading $it")
                ImageReader.read(it)
            }
        }
        val stackedImage = elapsed("Stacking images") { stack(alignedFileSuppliers) }

        println("Saving $outputFile")
        ImageWriter.write(stackedImage, outputFile)
        stackedImage
    } else {
        println("Stacked image already exists: $outputFile")
        ImageReader.read(outputFile)
    }

    println()
    println("### Enhancing stacked image ...")
    println()
    elapsed("enhance") {
        astroEnhance(config.format, config.enhance, stackedImage)
    }
}

fun astroEnhance(
    formatConfig: FormatConfig,
    enhanceConfig: EnhanceConfig,
    inputFile: File,
) {
    println("Reading $inputFile")
    var inputImage = ImageReader.read(inputFile)

    return astroEnhance(formatConfig, enhanceConfig, inputImage)
}

fun astroEnhance(
    formatConfig: FormatConfig,
    enhanceConfig: EnhanceConfig,
    inputImage: Image,
) {
    val currentDir = File(".")
    currentDir.resolve(enhanceConfig.enhancedOutputDirectory).mkdirs()

    var image = inputImage

    var stepIndex = 1

    fun step(name: String, stretchFunc: (Image) -> Image) {
        elapsed(name) {
            elapsed("  execute function: $name") {
                image = stretchFunc(image)
            }
            elapsed("  write result image") {
                ImageWriter.write(image, currentDir.resolve(enhanceConfig.enhancedOutputDirectory).resolve("step_${stepIndex}_$name.${formatConfig.outputImageExtension}"))
            }
            if (enhanceConfig.histogram.enabled) {
                elapsed("  write histogram") {
                    ImageWriter.write(image.histogramImage(enhanceConfig.histogram.histogramWidth, enhanceConfig.histogram.histogramHeight), currentDir.resolve(enhanceConfig.enhancedOutputDirectory).resolve("histogram_step_${stepIndex}_$name.${formatConfig.outputImageExtension}"))
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
            val width = if (enhanceConfig.crop.width < 0) it.width - enhanceConfig.crop.x + enhanceConfig.crop.width else enhanceConfig.crop.width
            val height = if (enhanceConfig.crop.height < 0) it.height - enhanceConfig.crop.y + enhanceConfig.crop.height else enhanceConfig.crop.height
            it.crop(enhanceConfig.crop.x, enhanceConfig.crop.y, width, height, false)
        }
    }

    if (enhanceConfig.background.enabled) {
        step("background") {
            //val fixPoints = image.createFixPointGrid(enhanceConfig.background.gridSize, enhanceConfig.background.gridSize)
            val fixPoints = image.createFixPointFourCorners(enhanceConfig.background.borderDistance)
            val fixPointValues = it.getFixPointValues(fixPoints, enhanceConfig.background.medianRadius)
            val background = it.interpolate(fixPointValues, power = enhanceConfig.background.power)
            it - background + enhanceConfig.background.offset
        }
    }

    if (enhanceConfig.whitebalance.enabled) {
        step("whitebalance") {
            it.applyWhitebalance()
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
                        it.stretchLinearPercentile(colorStretchStepConfig.linearPercentileMin, colorStretchStepConfig.linearPercentileMax)
                    }
                    ColorStretchStepType.Sigmoid -> {
                        it.stretchSigmoid(colorStretchStepConfig.sigmoidMidpoint, colorStretchStepConfig.sigmoidFactor)
                    }
                    ColorStretchStepType.Blur -> {
                        (it * (1.0 - colorStretchStepConfig.blurStrength)) + (it.gaussianBlur3Filter() * colorStretchStepConfig.blurStrength)
                    }
                    ColorStretchStepType.HighDynamicRange -> {
                        highDynamicRange(hdrSourceImages.map { { it } })
                    }
                }
                if (colorStretchStepConfig.addToHighDynamicRange) {
                    hdrSourceImages.add(stepResultImage)
                }
                stepResultImage
            }
        }
    }

    if (enhanceConfig.noise.enabled) {
        step("noise reduction") {
            it.reduceNoiseUsingMedianTransform(enhanceConfig.noise.radius, enhanceConfig.noise.threshold)
        }
    }

    val enhancedFile = currentDir.resolve(enhanceConfig.enhancedOutputDirectory).resolve("enhanced.${formatConfig.outputImageExtension}")
    println("Writing $enhancedFile")
    ImageWriter.write(image, enhancedFile)
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