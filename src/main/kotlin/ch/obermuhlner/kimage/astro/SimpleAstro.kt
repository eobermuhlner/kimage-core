package ch.obermuhlner.kimage.astro

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.astro.align.applyTransformationToImage
import ch.obermuhlner.kimage.astro.align.applyTransformationToStars
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.copyOnlyStars
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.astro.align.formatTransformation
import ch.obermuhlner.kimage.astro.color.stretchAsinh
import ch.obermuhlner.kimage.astro.color.stretchLinear
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.bayer.DebayerInterpolation
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.copy
import ch.obermuhlner.kimage.core.image.filter.medianFilter
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
import ch.obermuhlner.kimage.core.image.stack.stack
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.util.elapsed
import java.io.File

fun main(args: Array<String>) {
    alignStarImages(
        "/home/ero/astro/2024-10-29 Verzasca M31 M33 Alnitak/Alnitak/astro-process/calibrated/Light_Alnitak_180.0s_Bin1_533MC_gain100_20241030-020832_-10.0C_0001.tif",
        "/home/ero/astro/2024-10-29 Verzasca M31 M33 Alnitak/Alnitak/astro-process/calibrated/Light_Alnitak_180.0s_Bin1_533MC_gain100_20241030-024405_-10.0C_0012.tif",
    )

//    markStars("test-input/Alnitak.tif")

//    multiScaleMedianTransform("test-input/Alnitak_enhanced.tif")

//    interpolateImage("test-input/M42.tif")

//    debayerImage("images/calibrate/dark/Dark_60.0s_Bin1_533MC_gain100_20230522-203021_0001.fit")
//    debayerImage("images/calibrate/flat/Flat_110.0ms_Bin1_533MC_gain100_20240917-204051_-4.0C_0001.fit")
//    debayerImage("images/calibrate/M11/Light_M11_180.0s_Bin1_533MC_gain100_20240827-212432_-10.0C_0001.fit")
//    debayerImage("images/calibrate/M33/Light_M33_60.0s_Bin1_533MC_gain100_20241005-211237_-10.1C_0001.fit")

//    alignStarImages(
//        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212432_-10.0C_0001.tif",
//        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212734_-10.0C_0002.tif",
//        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213108_-10.1C_0003.tif",
//        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213410_-10.0C_0004.tif",
//        "images/calibrate/M11/debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213734_-10.0C_0005.tif",
//        //"images/calibrate/M11/crop_debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212432_-10.0C_0001.tif",
//    )

//    alignStarImages(
//        "images/calibrate/M16/debayer_Light_M16_0001.tif",
//        "images/calibrate/M16/debayer_Light_M16_0002.tif",
//        "images/calibrate/M16/debayer_Light_M16_0003.tif",
//        "images/calibrate/M16/debayer_Light_M16_0004.tif",
//        "images/calibrate/M16/debayer_Light_M16_0005.tif",
//    )

//    alignStarImages(
//        "aligned_debayer_Light_M16_0001.tif",
//        "aligned_debayer_Light_M16_0002.tif",
//        "aligned_debayer_Light_M16_0003.tif",
//        "aligned_debayer_Light_M16_0004.tif",
//        "aligned_debayer_Light_M16_0005.tif",
//    )

//    stackImages(
//        "aligned_debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212432_-10.0C_0001.tif",
//        "aligned_debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-212734_-10.0C_0002.tif",
//        "aligned_debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213108_-10.1C_0003.tif",
//        "aligned_debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213410_-10.0C_0004.tif",
//        "aligned_debayer_Light_M11_180.0s_Bin1_533MC_gain100_20240827-213734_-10.0C_0005.tif",
//    )
}

private fun markStars(imageName: String) {
    println(imageName)

    val image = elapsed("Loaded $imageName") {
        ImageReader.read(File(imageName))
    }
    val stars = elapsed("Find stars") {
        val stars = findStars(image, 0.2)
        println("Found ${stars.size} stars")
        stars
    }
    elapsed("Write marked stars") {
        writeStarAnnotationImage(image, stars, File("stars_marked.tif"))
    }
    val starImage = elapsed("Only stars") {
        copyOnlyStars(image, stars)
    }
    elapsed("Write star image") {
        ImageWriter.write(starImage, File("stars_only.tif"))
    }
    elapsed("Write starless image stretched") {
        val starlessImage = image - starImage
        ImageWriter.write(starlessImage.stretchLinearPercentile(0.01, 0.99), File("starless_stretched.tif"))
    }
}

private fun multiScaleMedianTransform(imageName: String) {
    println(imageName)

    val image = elapsed("Loaded $imageName") {
        ImageReader.read(File(imageName))
    }

    var currentImage = image
    for (r in listOf(1, 3, 7)) {
        elapsed("radius = $r") {
            val medianImage = elapsed("median $r") {
                currentImage.medianFilter(r)
            }
            val diffImage = elapsed("diff") {
                currentImage - medianImage
            }
            val stretchedDiffImage = elapsed("stretched") {
                diffImage.stretchLinear()
            }
            elapsed("write") {
                ImageWriter.write(stretchedDiffImage, File("medianDiff${r}.tif"))
            }

            currentImage = medianImage
        }
    }

    ImageWriter.write(currentImage.stretchLinear(), File("medianDiff-rest.tif"))
}

private fun interpolateImage(imageName: String) {
    val stretchFactor = 0.05

    println(imageName)
    val image = elapsed("Loaded $imageName") {
        ImageReader.read(File(imageName))
    }
    val stretchedImage = elapsed("Stretch image") {
        image.stretchAsinh()
    }
    elapsed("Write stretched image") {
        ImageWriter.write(stretchedImage, File("stretched_M42.tif"))
    }
//    val fixPoints = elapsed("Calculated fixpoints") {
//        image.findFixPoints(9, 9)
//    }
//    val background = elapsed("Interpolated background") {
//        image.interpolate(fixPoints)
//    }
//    elapsed("Write background") {
//        ImageWriter.write(background, File("background_M42.tif"))
//    }
//    val stretchedBackground = elapsed("Stretch background") {
//        background.stretch { v -> asinhStretchFunction(v, stretchFactor) }
//    }
//    elapsed("Write stretched background") {
//        ImageWriter.write(stretchedBackground, File("background_stretched_M42.tif"))
//    }
}

private fun debayerImage(imageName: String) {
    println(imageName)
    val bayeredImage = ImageReader.read(File(imageName))
    val stuckPixels = bayeredImage[Channel.Red].findBayerBadPixels(gradientThresholdFactor = 10.0, steepCountThresholdFactor = 1.0)
    println("Found ${stuckPixels.size} stuck pixels")
    if (stuckPixels.size < 50) {
        println(stuckPixels)
    }

    val bayeredColorImage = bayeredImage.debayer(interpolation = DebayerInterpolation.None)
    ImageWriter.write(bayeredColorImage, File(imageName).withNamePrefix("bayered_color_").withExtension("tif"))

    if (stuckPixels.size > 0 && stuckPixels.size < 1000) {
        val zoomMatrixRed = DoubleMatrix(5*stuckPixels.size, 5)
        val zoomMatrixGreen = DoubleMatrix(5*stuckPixels.size, 5)
        val zoomMatrixBlue = DoubleMatrix(5*stuckPixels.size, 5)
        var stuckPixelIndex = 0
        for (stuckPixel in stuckPixels) {
            zoomMatrixRed.set(
                bayeredColorImage[Channel.Red].crop(stuckPixel.y-2, stuckPixel.x-2, 5, 5),
                5*stuckPixelIndex, 0)
            zoomMatrixGreen.set(
                bayeredColorImage[Channel.Green].crop(stuckPixel.y-2, stuckPixel.x-2, 5, 5),
                5*stuckPixelIndex, 0)
            zoomMatrixBlue.set(
                bayeredColorImage[Channel.Blue].crop(stuckPixel.y-2, stuckPixel.x-2, 5, 5),
                5*stuckPixelIndex, 0)
            stuckPixelIndex++
        }
        val zoomImage = MatrixImage(zoomMatrixRed.cols, zoomMatrixRed.rows,
            Channel.Red to zoomMatrixRed,
            Channel.Green to zoomMatrixGreen,
            Channel.Blue to zoomMatrixBlue)
        ImageWriter.write(zoomImage, File(imageName).withNamePrefix("zoom_").withExtension("tif"))
    }

    val debayeredImage = bayeredImage.debayer(badpixelCoords = stuckPixels)
    ImageWriter.write(debayeredImage, File(imageName).withNamePrefix("debayered_").withExtension("tif"))

    val debugImage = MatrixImage(bayeredImage.width, bayeredImage.height,
        Channel.Red to bayeredImage[Channel.Gray].copy(),
        Channel.Green to bayeredImage[Channel.Gray].copy(),
        Channel.Blue to bayeredImage[Channel.Gray].copy())
    stuckPixels.forEach { (x, y) ->
        debugImage[Channel.Red][y-1, x] = 1.0
        debugImage[Channel.Green][y-1, x] = 0.0
        debugImage[Channel.Blue][y-1, x] = 0.0

        debugImage[Channel.Red][y+1, x] = 1.0
        debugImage[Channel.Green][y+1, x] = 0.0
        debugImage[Channel.Blue][y+1, x] = 0.0

        debugImage[Channel.Red][y, x-1] = 1.0
        debugImage[Channel.Green][y, x-1] = 0.0
        debugImage[Channel.Blue][y, x-1] = 0.0

        debugImage[Channel.Red][y, x+1] = 1.0
        debugImage[Channel.Green][y, x+1] = 0.0
        debugImage[Channel.Blue][y, x+1] = 0.0

    }
    ImageWriter.write(debugImage, File(imageName).withNamePrefix("debug_").withExtension("tif"))
}

private fun File.withExtension(extension: String): File {
    return File("${this.nameWithoutExtension}.$extension")
}

private fun File.withNamePrefix(prefix: String): File {
    return File("$prefix${this.name}")
}

private fun stackImages(vararg fileNames: String) {
    val stacked = stack(
        fileNames.map {
            {
                println("Loading $it")
                ImageReader.read(File(it))
            }
        },
        StackAlgorithm.Max
    )
    ImageWriter.write(stacked, File("stacked.tif"))
}

private fun alignStarImages(
    vararg imageFileNames: String
) {
    val starTheshold = 0.2
    val maxStars = 100
    val positionTolerance = 2.0

    val referenceImageFile = File(imageFileNames.first())
    val referenceImage = ImageReader.read(referenceImageFile)

    val referenceOutputFileName = "aligned_${referenceImageFile.nameWithoutExtension}.tif"
    val referenceOutputFile = File(referenceOutputFileName)

    val referenceStars = findStars(referenceImage, starTheshold)
    println("Found ${referenceStars.size} reference stars")
    writeStarAnnotationImage(referenceImage, referenceStars, File("stars_0.tif"))

    for (i in 0 until imageFileNames.size) {
        val otherImageFileName = imageFileNames[i]
        println()

        val otherImageFile = File(otherImageFileName)
        println("Loading $otherImageFile")
        val otherImage = ImageReader.read(otherImageFile)

        val otherStars = findStars(otherImage, starTheshold)
        println("Found ${otherStars.size} stars")
        writeStarAnnotationImage(otherImage, otherStars, File("stars_$i.tif"))

        val transform = calculateTransformationMatrix(
            referenceStars.take(maxStars),
            otherStars.take(maxStars),
            referenceImage.width,
            referenceImage.height,
            positionTolerance = positionTolerance,
        )
        if (transform != null) {
            println(formatTransformation(decomposeTransformationMatrix(transform)))

            val alignedOtherImage = applyTransformationToImage(otherImage, transform)
            val transformedOtherStars = applyTransformationToStars(otherStars, transform, referenceImage.width, referenceImage.height)
            writeStarAnnotationImage(referenceImage, transformedOtherStars, File("transformed_stars_$i.tif"))

            val debugImage = MatrixImage(referenceImage.width, referenceImage.height,
                Channel.Red to otherImage[Channel.Gray],
                Channel.Green to referenceImage[Channel.Gray],
                Channel.Blue to alignedOtherImage[Channel.Gray])
            ImageWriter.write(debugImage, File("debug_${otherImageFile.nameWithoutExtension}.tif"))


            val outputFileName = "aligned_${otherImageFile.nameWithoutExtension}.tif"
            val outputFile = File(outputFileName)
            println("Writing $outputFile")
            ImageWriter.write(alignedOtherImage, outputFile)
        }
    }

}

private fun writeStarAnnotationImage(image: Image, stars: List<Star>, file: File) {
    val annotatedImage = image.copy()
    for (star in stars) {
        annotatedImage[Channel.Red][star.y, star.x] = star.brightness
        annotatedImage[Channel.Green][star.y, star.x] = 0.0
        annotatedImage[Channel.Blue][star.y, star.x] = 0.0
    }
    ImageWriter.write(annotatedImage, file)
}
