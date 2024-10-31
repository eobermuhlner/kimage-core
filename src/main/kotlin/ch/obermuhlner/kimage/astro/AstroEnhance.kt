package ch.obermuhlner.kimage.astro

import ch.obermuhlner.kimage.astro.background.createFixPointGrid
import ch.obermuhlner.kimage.astro.background.interpolate
import ch.obermuhlner.kimage.astro.color.stretchAsinhPercentile
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchSigmoid
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.debayerCleanupBadPixels
import ch.obermuhlner.kimage.core.image.hdr.highDynamicRange
import ch.obermuhlner.kimage.core.image.histogram.histogramImage
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.matrix.values.values
import ch.obermuhlner.kimage.util.elapsed
import java.io.File
import kotlin.math.max

fun main(args: Array<String>) {
    astroEnhance(args)
}

fun Image.applyWhitebalance() {
    val redMatrix = this[Channel.Red]
    val greenMatrix = this[Channel.Green]
    val blueMatrix = this[Channel.Blue]

    val redMedian = redMatrix.values().median()
    val greenMedian = greenMatrix.values().median()
    val blueMedian = blueMatrix.values().median()

    val maxFactor = max(redMedian, max(greenMedian, blueMedian))
    val redFactor = maxFactor / redMedian
    val greenFactor = maxFactor / greenMedian
    val blueFactor = maxFactor / blueMedian

    redMatrix.applyEach { v -> v * redFactor  }
    greenMatrix.applyEach { v -> v * greenFactor  }
    blueMatrix.applyEach { v -> v * blueFactor  }
}

fun astroEnhance(args: Array<String>) {
    val outputImageExtension = "tif"
    val enhancedDirectory = "enhanced"
    val debayer = true
    val bayerPattern = BayerPattern.RGGB
    val removeBackground = true
    val backgroundGridSize = 2
    val backgroundPower = 1.5
    val whitebalance = true
    val stretchColors = true
    val stretchIterations = 4
    val stretchSigmoidMidpoint = 0.25
    val stretchSigmoidFactor = 6.0
    val stretchLinearMinPercentile = 0.001
    val stretchLinearMaxPercentile = 0.999
    val hdr = true

    val currentDir = File(".")
    currentDir.resolve(enhancedDirectory).mkdirs()

    val inputFile = File(args[0])

    println("Reading $inputFile")
    var image = ImageReader.read(inputFile)

    val histogramWidth = 1024
    val histogramHeight = 400
    var stepIndex = 1

    fun step(name: String, stretchFunc: (Image) -> Image) {
        elapsed(name) {
            image = stretchFunc(image)
            ImageWriter.write(image, currentDir.resolve(enhancedDirectory).resolve("step_${stepIndex}_$name.$outputImageExtension"))
            ImageWriter.write(image.histogramImage(histogramWidth, histogramHeight), currentDir.resolve(enhancedDirectory).resolve("histogram_step_${stepIndex}_$name.$outputImageExtension"))
            stepIndex++
        }
    }

    if (debayer) {
        step("debayering") {
            it.debayerCleanupBadPixels(bayerPattern)
        }
    }

    if (removeBackground) {
        step("background") {
            val fixPoints = image.createFixPointGrid(backgroundGridSize, backgroundGridSize)
            val background = it.interpolate(fixPoints, power = backgroundPower)
            it - background + 0.001
        }
    }

    if (whitebalance) {
        step("whitebalance") {
            it.applyWhitebalance()
            it
        }
    }


    if (stretchColors) {
        val hdrSourceImages = mutableListOf<Image>()

        for (stretchIndex in 1..stretchIterations) {
            step("stretch$stretchIndex-sigmoid") {
                it.stretchSigmoid(stretchSigmoidMidpoint, stretchSigmoidFactor)
            }
            step("stretch$stretchIndex-linear") {
                it.stretchLinearPercentile(stretchLinearMinPercentile, stretchLinearMaxPercentile)
            }
            if (hdr) {
                hdrSourceImages.add(image)
            }
        }

        if (hdr) {
            step("hdr") {
                highDynamicRange(hdrSourceImages.map { { it } })
            }
            step("stretch-sigmoid") {
                it.stretchSigmoid(stretchSigmoidMidpoint, stretchSigmoidFactor)
            }
            step("stretch-linear") {
                it.stretchLinearPercentile(stretchLinearMinPercentile, stretchLinearMaxPercentile)
            }
        }
    }

    val enhancedFile = currentDir.resolve(enhancedDirectory).resolve("${inputFile.nameWithoutExtension}.$outputImageExtension")
    println("Writing $inputFile")
    ImageWriter.write(image, enhancedFile)
}
