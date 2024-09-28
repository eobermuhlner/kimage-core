package ch.obermuhlner.kimage.astro

import ch.obermuhlner.kimage.astro.align.applyTransformationToImage
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.astro.align.formatTransformation
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.matrix.linearalgebra.invert
import java.io.File

/*
debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213108_-10.1C_0003
debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213410_-10.0C_0004
debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213734_-10.0C_0005
crop_debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212432_-10.0C_0001
 */

fun main(args: Array<String>) {
    val referenceImageFile = File("images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212432_-10.0C_0001.tif")
    val referenceImage = ImageReader.read(referenceImageFile)

    val referenceOutputFileName = "aligned_${referenceImageFile.nameWithoutExtension}.tif"
    val referenceOutputFile = File(referenceOutputFileName)
    println("Writing $referenceOutputFile")
    ImageWriter.write(referenceImage, referenceOutputFile)

    val starTheshold = 0.2
    val referenceStars = findStars(referenceImage, starTheshold)
    println("Found ${referenceStars.size} reference stars")

    for (otherImageFileName in listOf(
        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212734_-10.0C_0002.tif",
        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213108_-10.1C_0003.tif",
        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213410_-10.0C_0004.tif",
        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213734_-10.0C_0005.tif",
        //"images/calibrate/M11/crop_debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212432_-10.0C_0001.tif",
    )) {
        val otherImageFile = File(otherImageFileName)
        println("Loading $otherImageFile")
        val otherImage = ImageReader.read(otherImageFile)

        val otherStars = findStars(otherImage, starTheshold)
        println("Found ${otherStars.size} stars")

        val maxStars = 100
        val transform = calculateTransformationMatrix(referenceStars.take(maxStars), otherStars.take(maxStars))
        if (transform != null) {
            println(formatTransformation(decomposeTransformationMatrix(transform)))

            val alignedOtherImage = applyTransformationToImage(otherImage, transform)

            val outputFileName = "aligned_${otherImageFile.nameWithoutExtension}.tif"
            val outputFile = File(outputFileName)
            println("Writing $outputFile")
            ImageWriter.write(alignedOtherImage, outputFile)
        }
        println()
    }

}