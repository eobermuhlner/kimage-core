package ch.obermuhlner.kimage.astro

import ch.obermuhlner.kimage.astro.background.createFixPointGrid
import ch.obermuhlner.kimage.astro.background.interpolate
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchSigmoid
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.debayerCleanupBadPixels
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.hdr.highDynamicRange
import ch.obermuhlner.kimage.core.image.histogram.histogramImage
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.image.whitebalance.applyWhitebalance
import ch.obermuhlner.kimage.util.elapsed
import org.yaml.snakeyaml.Yaml
import java.io.File

data class DebayerConfig(
    var enabled: Boolean = false,
    var bayerPattern: BayerPattern = BayerPattern.RGGB,
)

data class CropConfig(
    var enabled: Boolean = true,
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = -1,
    var height: Int = -1,
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

data class HighDynamicRangeConfig(
    var enabled: Boolean = true,
    var finalStretch: Boolean = false,
)

data class ColorStretchConfig(
    var enabled: Boolean = true,
    var iterations: Int = 4,
    var sigmoidMidpoint: Double = 0.25,
    var sigmoidFactor: Double = 6.0,
    var firstLinearMinPercentile: Double = 0.001,
    var firstLinearMaxPercentile: Double = 0.999,
    var linearMinPercentile: Double = 0.0001,
    var linearMaxPercentile: Double = 0.9999,
    var highDynamicRange: HighDynamicRangeConfig = HighDynamicRangeConfig(),
)

data class EnhanceConfig(
    var outputImageExtension: String = "tif",
    var enhancedDirectory: String = "enhanced",
    var debayer: DebayerConfig = DebayerConfig(),
    var crop: CropConfig = CropConfig(),
    var background: BackgroundConfig = BackgroundConfig(),
    var whitebalance: WhitebalanceConfig = WhitebalanceConfig(),
    var colorStretch: ColorStretchConfig = ColorStretchConfig(),
)

fun main(args: Array<String>) {
    val yaml = Yaml()
    val configFile = File("astro-enhance.yaml")

    val config = if (configFile.exists()) {
        configFile.inputStream().use { input ->
            yaml.loadAs(input, EnhanceConfig::class.java)
        }
    } else {
        EnhanceConfig()
    }

    astroEnhance(config, args[0])
}

fun astroEnhance(
    config: EnhanceConfig,
    inputFileName: String,
) {
    val currentDir = File(".")
    currentDir.resolve(config.enhancedDirectory).mkdirs()

    val inputFile = File(inputFileName)

    println("Reading $inputFile")
    var image = ImageReader.read(inputFile)

    val histogramWidth = 1024
    val histogramHeight = 400
    var stepIndex = 1

    fun step(name: String, stretchFunc: (Image) -> Image) {
        elapsed(name) {
            image = stretchFunc(image)
            ImageWriter.write(image, currentDir.resolve(config.enhancedDirectory).resolve("step_${stepIndex}_$name.${config.outputImageExtension}"))
            ImageWriter.write(image.histogramImage(histogramWidth, histogramHeight), currentDir.resolve(config.enhancedDirectory).resolve("histogram_step_${stepIndex}_$name.${config.outputImageExtension}"))
            stepIndex++
        }
    }

    if (config.crop.enabled) {
        step("crop") {
            val width = if (config.crop.width < 0) it.width - config.crop.x + config.crop.width else config.crop.width
            val height = if (config.crop.height < 0) it.height - config.crop.y + config.crop.height else config.crop.height
            it.crop(config.crop.x, config.crop.y, width, height)
        }
    }

    if (config.debayer.enabled) {
        step("debayering") {
            it.debayerCleanupBadPixels(config.debayer.bayerPattern)
        }
    }

    if (config.background.enabled) {
        step("background") {
            val fixPoints = image.createFixPointGrid(config.background.gridSize, config.background.gridSize)
            val background = it.interpolate(fixPoints, power = config.background.power)
            it - background + config.background.offset
        }
    }

    if (config.whitebalance.enabled) {
        step("whitebalance") {
            it.applyWhitebalance()
            it
        }
    }


    if (config.colorStretch.enabled) {
        step("stretch-first-linear") {
            it.stretchLinearPercentile(config.colorStretch.firstLinearMinPercentile, config.colorStretch.firstLinearMaxPercentile)
        }

        val hdrSourceImages = mutableListOf<Image>()

        for (stretchIndex in 1..config.colorStretch.iterations) {
            step("stretch$stretchIndex-sigmoid") {
                it.stretchSigmoid(config.colorStretch.sigmoidMidpoint, config.colorStretch.sigmoidFactor)
            }
            step("stretch$stretchIndex-linear") {
                it.stretchLinearPercentile(config.colorStretch.linearMinPercentile, config.colorStretch.linearMaxPercentile)
            }
            if (config.colorStretch.highDynamicRange.enabled) {
                hdrSourceImages.add(image)
            }
        }

        if (config.colorStretch.highDynamicRange.enabled) {
            step("hdr") {
                highDynamicRange(hdrSourceImages.map { { it } })
            }
            if (config.colorStretch.highDynamicRange.finalStretch) {
                step("stretch-last-sigmoid") {
                    it.stretchSigmoid(config.colorStretch.sigmoidMidpoint, config.colorStretch.sigmoidFactor)
                }
                step("stretch-last-linear") {
                    it.stretchLinearPercentile(config.colorStretch.linearMinPercentile, config.colorStretch.linearMaxPercentile)
                }
            }
        }
    }

    val enhancedFile = currentDir.resolve(config.enhancedDirectory).resolve("${inputFile.nameWithoutExtension}.${config.outputImageExtension}")
    println("Writing $inputFile")
    ImageWriter.write(image, enhancedFile)
}
