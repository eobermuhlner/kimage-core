package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.astro.align.applyTransformationToImage
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.astro.align.formatTransformation
import ch.obermuhlner.kimage.astro.align.processCalibrationImages
import ch.obermuhlner.kimage.astro.background.createFixPointGrid
import ch.obermuhlner.kimage.astro.background.interpolate
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchSigmoid
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.debayerCleanupBadPixels
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.div
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur3Filter
import ch.obermuhlner.kimage.core.image.hdr.highDynamicRange
import ch.obermuhlner.kimage.core.image.histogram.histogramImage
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.image.stack.stack
import ch.obermuhlner.kimage.core.image.statistics.normalizeImage
import ch.obermuhlner.kimage.core.image.times
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalance
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.median
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
    var calibratedOutputDirectory: String = "calibrated",
)

data class NormalizeBackgroundConfig(
    var enabled: Boolean = true,
    var offset: Double = 0.01,
)

data class AlignConfig(
    var starThreshold: Double = 0.2,
    var maxStars: Int = 100,
    var positionTolerance: Double = 2.0,
    var alignedOutputDirectory: String = "aligned",
)

data class StackConfig(
    var stackedOutputDirectory: String = "stacked",
)

data class EnhanceConfig(
    var outputImageExtension: String = "tif",
    var enhancedDirectory: String = "enhanced",
    var debayer: DebayerConfig = DebayerConfig(enabled = false),
    var crop: CropConfig = CropConfig(),
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

data class BackgroundConfig(
    var enabled: Boolean = false,
    var gridSize: Int = 2,
    var power: Double = 1.5,
    var offset: Double = 0.01,
)

data class WhitebalanceConfig(
    var enabled: Boolean = true,
)

data class ColorStretchConfig(
    var enabled: Boolean = true,
    var iterations: Int = 4,
    var firstLinearMinPercentile: Double = 0.001,
    var firstLinearMaxPercentile: Double = 0.999,
    var sigmoidMidpoint: Double = 0.25,
    var sigmoidFactor: Double = 6.0,
    var linearMinPercentile: Double = 0.0001,
    var linearMaxPercentile: Double = 0.9999,
    var blurEnabled: Boolean = true,
    var blurStrength: Double = 0.1,
    var highDynamicRange: HighDynamicRangeConfig = HighDynamicRangeConfig(),
)

data class HighDynamicRangeConfig(
    var enabled: Boolean = true,
    var finalStretch: Boolean = false,
)

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
        ProcessConfig()
    }

    val command = if (args.isNotEmpty()) args[0] else "process"

    when(command) {
        "process" -> {
            println(yaml.dumpAsMap(config))
            astroProcess(config)
        }
        "config" -> println(yaml.dumpAsMap(config))
        else -> println("""
            Commands:
            - process
            - config
        """.trimIndent())
    }
}

fun astroProcess(config: ProcessConfig) {
    val inputImageExtension: String = config.format.inputImageExtension
    val outputImageExtension: String = config.format.outputImageExtension
    val debayerLightFrames: Boolean = config.format.debayer.enabled
    val bayerPattern: BayerPattern = config.format.debayer.bayerPattern

    val debayerCalibrationFrames: Boolean = config.calibrate.debayer.enabled
    val biasDirectory: String = config.calibrate.biasDirectory
    val flatDirectory: String = config.calibrate.flatDirectory
    val darkflatDirectory: String = config.calibrate.darkflatDirectory
    val darkDirectory: String = config.calibrate.darkDirectory
    val calibratedDirectory: String = config.calibrate.calibratedOutputDirectory
    val alignedDirectory: String = config.align.alignedOutputDirectory
    val stackedDirectory: String = config.stack.stackedOutputDirectory
    val normalizeBackground = config.calibrate.normalizeBackground.enabled
    val normalizeBackgroundOffset = config.calibrate.normalizeBackground.offset
    val starThreshold = config.align.starThreshold
    val maxStars = config.align.maxStars
    val positionTolerance = config.align.positionTolerance

    val currentDir = File(".")

    println("### Processing calibration images ...")

    println()
    val bias = elapsed("Processing bias frames") {
        processCalibrationImages(currentDir.resolve(biasDirectory), "bias", debayerCalibrationFrames)
    }
    println()
    var flat = elapsed("Processing flat frames") {
        processCalibrationImages(currentDir.resolve(flatDirectory), "flat", debayerCalibrationFrames).normalizeImage()
    }
    println()
    var darkflat = elapsed("Processing darkflat frames") {
        processCalibrationImages(currentDir.resolve(darkflatDirectory), "darkflat", debayerCalibrationFrames)
    }
    println()
    var dark = elapsed("Processing dark frames") {
        processCalibrationImages(currentDir.resolve(darkDirectory), "dark", debayerCalibrationFrames)
    }

    val files = currentDir.listFiles() ?: return

    currentDir.resolve(calibratedDirectory).mkdirs()
    currentDir.resolve(alignedDirectory).mkdirs()
    currentDir.resolve(stackedDirectory).mkdirs()

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

    var inputFiles = files.filter { it.extension == inputImageExtension }.filterNotNull()
    if (config.quick) {
        println()
        println("Quick mode: only processing ${config.quickCount} input file")
        inputFiles = inputFiles.take(config.quickCount)
    }

    val minBackground = mutableMapOf<Channel, Double>()
    if (normalizeBackground) {
        println()
        elapsed("Normalizing backgrounds for ${inputFiles.size} input files") {
            inputFiles.forEach { inputFile ->
                val calibratedFile = currentDir.resolve(calibratedDirectory).resolve("${inputFile.nameWithoutExtension}.$outputImageExtension")
                if (!calibratedFile.exists()) {
                    println("Loading $inputFile")
                    var light = elapsed("Reading light frame") { ImageReader.read(inputFile) }
                    if (debayerLightFrames) {
                        light = elapsed("Debayering light frame") {
                            val badPixels = light[Channel.Red].findBayerBadPixels()
                            println("Found ${badPixels.size} bad pixels")
                            light.debayer(bayerPattern, badpixelCoords = badPixels)
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
            val outputFile = currentDir.resolve(calibratedDirectory)
                .resolve("${inputFile.nameWithoutExtension}.$outputImageExtension")
            if (outputFile.exists()) {
                return@map outputFile
            }

            println()
            println("Loading $inputFile")
            var light = elapsed("Reading light frame $inputFile") { ImageReader.read(inputFile) }
            if (debayerLightFrames) {
                light = elapsed("Debayering light frame $inputFile") { light.debayerCleanupBadPixels(bayerPattern) }
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

                if (normalizeBackground) {
                    for (channel in light.channels) {
                        val lowestBackground = minBackground[channel]
                        if (lowestBackground != null) {
                            val background = light[channel].values().median()
                            val delta = background - lowestBackground - normalizeBackgroundOffset
                            light[channel].applyEach { v -> v - delta}
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

    var referenceImageProcessed = false
    var referenceStars: List<Star> = emptyList<Star>()
    var referenceImageWidth = 0
    var referenceImageHeight = 0
    val alignedFiles = calibratedFiles.mapNotNull { calibratedFile ->
        val outputFile = currentDir.resolve(alignedDirectory).resolve("${calibratedFile.nameWithoutExtension}.$outputImageExtension")
        if (outputFile.exists()) {
            return@mapNotNull outputFile
        }

        println()
        println("Loading $calibratedFile")
        val light = ImageReader.read(calibratedFile)

        val alignedImage = if (!referenceImageProcessed) {
            referenceImageProcessed = true
            elapsed("Finding reference stars") {
                referenceStars = findStars(light, starThreshold)
                referenceImageWidth = light.width
                referenceImageHeight = light.height
            }
            //light
            val transform = elapsed("Calculating transformation matrix") {
                calculateTransformationMatrix(
                    referenceStars.take(maxStars),
                    referenceStars.take(maxStars),
                    referenceImageWidth,
                    referenceImageHeight,
                    positionTolerance = positionTolerance
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
        } else {
            val imageStars = elapsed("Finding image stars") { findStars(light, starThreshold) }
            val transform = elapsed("Calculating transformation matrix") {
                calculateTransformationMatrix(
                    referenceStars.take(maxStars),
                    imageStars.take(maxStars),
                    referenceImageWidth,
                    referenceImageHeight,
                    positionTolerance = positionTolerance
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

    val outputFile = currentDir.resolve(stackedDirectory).resolve("${inputFiles[0].nameWithoutExtension}_stacked_${alignedFiles.size}.$outputImageExtension")
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
        astroEnhance(config.enhance, stackedImage)
    }
}

fun astroEnhance(
    enhanceConfig: EnhanceConfig,
    inputFile: File,
) {
    println("Reading $inputFile")
    var inputImage = ImageReader.read(inputFile)

    return astroEnhance(enhanceConfig, inputImage)
}

fun astroEnhance(
    enhanceConfig: EnhanceConfig,
    inputImage: Image,
) {
    val currentDir = File(".")
    currentDir.resolve(enhanceConfig.enhancedDirectory).mkdirs()

    var image = inputImage

    var stepIndex = 1

    fun step(name: String, stretchFunc: (Image) -> Image) {
        elapsed(name) {
            elapsed("  execute function") {
                image = stretchFunc(image)
            }
            elapsed("  write result image") {
                ImageWriter.write(image, currentDir.resolve(enhanceConfig.enhancedDirectory).resolve("step_${stepIndex}_$name.${enhanceConfig.outputImageExtension}"))
            }
            if (enhanceConfig.histogram.enabled) {
                elapsed("  write histogram") {
                    ImageWriter.write(image.histogramImage(enhanceConfig.histogram.histogramWidth, enhanceConfig.histogram.histogramHeight), currentDir.resolve(enhanceConfig.enhancedDirectory).resolve("histogram_step_${stepIndex}_$name.${enhanceConfig.outputImageExtension}"))
                }
            }
            stepIndex++
        }
    }

    if (enhanceConfig.crop.enabled) {
        step("crop") {
            val width = if (enhanceConfig.crop.width < 0) it.width - enhanceConfig.crop.x + enhanceConfig.crop.width else enhanceConfig.crop.width
            val height = if (enhanceConfig.crop.height < 0) it.height - enhanceConfig.crop.y + enhanceConfig.crop.height else enhanceConfig.crop.height
            it.crop(enhanceConfig.crop.x, enhanceConfig.crop.y, width, height)
        }
    }

    if (enhanceConfig.debayer.enabled) {
        step("debayering") {
            it.debayerCleanupBadPixels(enhanceConfig.debayer.bayerPattern)
        }
    }

    if (enhanceConfig.background.enabled) {
        step("background") {
            val fixPoints = image.createFixPointGrid(enhanceConfig.background.gridSize, enhanceConfig.background.gridSize)
            val background = it.interpolate(fixPoints, power = enhanceConfig.background.power)
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
        step("stretch-first-linear") {
            it.stretchLinearPercentile(enhanceConfig.colorStretch.firstLinearMinPercentile, enhanceConfig.colorStretch.firstLinearMaxPercentile)
        }

        val hdrSourceImages = mutableListOf<Image>()

        for (stretchIndex in 1..enhanceConfig.colorStretch.iterations) {
            step("stretch$stretchIndex-sigmoid") {
                it.stretchSigmoid(enhanceConfig.colorStretch.sigmoidMidpoint, enhanceConfig.colorStretch.sigmoidFactor)
            }
            step("stretch$stretchIndex-linear") {
                it.stretchLinearPercentile(enhanceConfig.colorStretch.linearMinPercentile, enhanceConfig.colorStretch.linearMaxPercentile)
            }
            if (enhanceConfig.colorStretch.blurEnabled) {
                step("stretch$stretchIndex-blur") {
                    (it * (1.0 - enhanceConfig.colorStretch.blurStrength)) + (it.gaussianBlur3Filter() * enhanceConfig.colorStretch.blurStrength)
                }
            }
            if (enhanceConfig.colorStretch.highDynamicRange.enabled) {
                hdrSourceImages.add(image)
            }
        }

        if (enhanceConfig.colorStretch.highDynamicRange.enabled) {
            step("hdr") {
                highDynamicRange(hdrSourceImages.map { { it } })
            }
            if (enhanceConfig.colorStretch.highDynamicRange.finalStretch) {
                step("stretch-last-sigmoid") {
                    it.stretchSigmoid(enhanceConfig.colorStretch.sigmoidMidpoint, enhanceConfig.colorStretch.sigmoidFactor)
                }
                step("stretch-last-linear") {
                    it.stretchLinearPercentile(enhanceConfig.colorStretch.linearMinPercentile, enhanceConfig.colorStretch.linearMaxPercentile)
                }
            }
        }
    }

    val enhancedFile = currentDir.resolve(enhanceConfig.enhancedDirectory).resolve("enhanced.${enhanceConfig.outputImageExtension}")
    println("Writing $enhancedFile")
    ImageWriter.write(image, enhancedFile)
}
