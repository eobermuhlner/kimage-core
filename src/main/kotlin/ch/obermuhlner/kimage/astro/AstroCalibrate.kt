package ch.obermuhlner.kimage.astro

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.astro.align.applyTransformationToImage
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.astro.align.formatTransformation
import ch.obermuhlner.kimage.astro.align.processCalibrationImages
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.div
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.stack.stack
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.math.average
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.matrix.linearalgebra.invert
import ch.obermuhlner.kimage.core.matrix.values.values
import java.io.File
import kotlin.math.min

fun main(args: Array<String>) {
    astroCalibrate(args)
}

fun normalizeImage(image: Image?): Image? {
    if (image == null) return null

    for (channel in image.channels) {
        val m = image[channel]
        val average = m.values().average()
        m.applyEach { v -> v / average }
    }

    return image
}

fun astroCalibrate(args: Array<String>) {
    val inputImageExtension: String = "fit"
    val debayer: Boolean = true
    val bayerPattern: BayerPattern = BayerPattern.RGGB
    val biasDirectory: String = "../bias"
    val flatDirectory: String = "../flat"
    val darkflatDirectory: String = "../darkflat"
    val darkDirectory: String = "../dark"
    val calibratedDirectory: String = "calibrated"
    val alignedDirectory: String = "aligned"
    val stackedDirectory: String = "stacked"
    val outputImageExtension: String = "tif"
    val normalizeBackground = true
    val starTheshold = 0.2
    val maxStars = 100
    val positionTolerance = 2.0


    val currentDir = File(".")
    var bias = processCalibrationImages(currentDir.resolve(biasDirectory), "bias", debayer)
    var flat = normalizeImage(processCalibrationImages(currentDir.resolve(flatDirectory), "flat", debayer))
    var darkflat = processCalibrationImages(currentDir.resolve(darkflatDirectory), "darkflat", debayer)
    var dark = processCalibrationImages(currentDir.resolve(darkDirectory), "dark", debayer)

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

    val inputFiles = files.filter { it.extension == inputImageExtension }

    val minBackground = mutableMapOf<Channel, Double>()
    if (normalizeBackground) {
        println()
        println("### Normalizing backgrounds ...")

        inputFiles.forEach { inputFile ->
            println("Loading $inputFile")
            var light = ImageReader.read(inputFile)
            if (debayer) {
                light = light.debayer(bayerPattern)
            }

            for (channel in light.channels) {
                val median = light[channel].values().median()
                println("$channel: $median")
                minBackground[channel] = minBackground[channel]?.let { min(it, median) } ?: median
            }
        }

        println()
        println("Minimum background:")
        minBackground.forEach { (channel, median) ->
            println("$channel: $median")
        }
    }

    println()
    println("### Calibrating images ...")

    val calibratedFiles = inputFiles.map { inputFile ->
        println("Loading $inputFile")
        var light = ImageReader.read(inputFile)
        if (debayer) {
            light = light.debayer(bayerPattern)
        }

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
                    val delta = background - lowestBackground
                    light[channel].applyEach { v -> v - delta }
                }
            }
        }

        light.applyEach { v -> clamp(v, 0.0, 1.0) }

        val outputFile = currentDir.resolve(calibratedDirectory).resolve("${inputFile.nameWithoutExtension}.$outputImageExtension")
        println("Saving $outputFile")
        ImageWriter.write(light, outputFile)

        outputFile
    }

    println()
    println("### Aligning images ...")

    var referenceImageProcessed = false
    var referenceStars: List<Star> = emptyList<Star>()
    val alignedFiles = calibratedFiles.mapNotNull { calibratedFile ->
        println("Loading $calibratedFile")
        var light = ImageReader.read(calibratedFile)

        val alignedImage = if (!referenceImageProcessed) {
            referenceImageProcessed = true
            referenceStars = findStars(light, starTheshold)
            light
        } else {
            val imageStars = findStars(light, starTheshold)
            val transform = calculateTransformationMatrix(
                referenceStars.take(maxStars),
                imageStars.take(maxStars),
                positionTolerance = positionTolerance
            )
            if (transform != null) {
                println(formatTransformation(decomposeTransformationMatrix(transform)))

                applyTransformationToImage(light, transform.invert()!!)
            } else {
                null
            }
        }

        if (alignedImage != null) {
            val outputFile = currentDir.resolve(alignedDirectory).resolve("${calibratedFile.nameWithoutExtension}.$outputImageExtension")
            println("Saving $outputFile")
            ImageWriter.write(alignedImage, outputFile)

            outputFile
        } else {
            null
        }
    }

    println()
    println("### Stacking images ...")

    val alignedFileSuppliers = alignedFiles.map {
        {
            println("Loading $it")
            ImageReader.read(it)
        }
    }
    val stackedImage = stack(alignedFileSuppliers)

    val outputFile = currentDir.resolve(stackedDirectory).resolve("${inputFiles[0].nameWithoutExtension}_stacked_${alignedFiles.size}.$outputImageExtension")
    println("Saving $outputFile")
    ImageWriter.write(stackedImage, outputFile)
}
