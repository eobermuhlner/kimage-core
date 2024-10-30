package ch.obermuhlner.kimage.astro

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.astro.align.applyTransformationToImage
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.createDebugImageFromTransformedStars
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.astro.align.formatTransformation
import ch.obermuhlner.kimage.astro.align.processCalibrationImages
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.debayerCleanupBadPixels
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.div
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
import ch.obermuhlner.kimage.core.image.stack.stack
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.math.average
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.matrix.linearalgebra.invert
import ch.obermuhlner.kimage.core.matrix.values.values
import ch.obermuhlner.kimage.util.elapsed
import java.io.File
import kotlin.math.min

// 20h29m34.8s 62d59m38.9s

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
    val debayerCalibrationFrames: Boolean = true
    val debayerLightFrames: Boolean = true
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

    println("### Processing calibration images ...")

    var bias = elapsed("Processing bias frames") { processCalibrationImages(currentDir.resolve(biasDirectory), "bias", debayerCalibrationFrames) }
    var flat = elapsed("Processing flat frames") { normalizeImage(processCalibrationImages(currentDir.resolve(flatDirectory), "flat", debayerCalibrationFrames)) }
    var darkflat = elapsed("Processing darkflat frames") { processCalibrationImages(currentDir.resolve(darkflatDirectory), "darkflat", debayerCalibrationFrames) }
    var dark = elapsed("Processing dark frames") { processCalibrationImages(currentDir.resolve(darkDirectory), "dark", debayerCalibrationFrames) }

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

    val inputFiles = files.filter { it.extension == inputImageExtension }.filterNotNull()

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
                            val delta = background - lowestBackground
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

    var referenceImageProcessed = false
    var referenceStars: List<Star> = emptyList<Star>()
    var referenceImageWidth = 0
    var referenceImageHeight = 0
    val alignedFiles = calibratedFiles.mapNotNull { calibratedFile ->
        val outputFile = currentDir.resolve(alignedDirectory).resolve("${calibratedFile.nameWithoutExtension}.$outputImageExtension")
        if (outputFile.exists()) {
            return@mapNotNull outputFile
        }

        println("Loading $calibratedFile")
        var light = elapsed("Reading calibrated frame") { ImageReader.read(calibratedFile) }

        val alignedImage = if (!referenceImageProcessed) {
            referenceImageProcessed = true
            elapsed("Finding reference stars") {
                referenceStars = findStars(light, starTheshold)
                referenceImageWidth = light.width
                referenceImageHeight = light.height
            }
            light
        } else {
            val imageStars = elapsed("Finding image stars") { findStars(light, starTheshold) }
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
                    //for debugging: createDebugImageFromTransformedStars(imageStars, transform, light.width, light.height)
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
    if (!outputFile.exists()) {
        val alignedFileSuppliers = alignedFiles.map {
            {
                println("Loading $it")
                ImageReader.read(it)
            }
        }
        // FIXME do not use Max
        val stackedImage = elapsed("Stacking images") { stack(alignedFileSuppliers, algorithm = StackAlgorithm.Max) }

        println("Saving $outputFile")
        ImageWriter.write(stackedImage, outputFile)
    } else {
        println("Stacked image already exists: $outputFile")
    }
}
