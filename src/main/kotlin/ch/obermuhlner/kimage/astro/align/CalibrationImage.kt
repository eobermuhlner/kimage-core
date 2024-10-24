package ch.obermuhlner.kimage.astro.align

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.stack.stack
import java.io.File

fun processCalibrationImages(
    imageDirectory: File,
    calibrationName: String,
    debayer: Boolean,
    bayerPattern: BayerPattern = BayerPattern.RGGB,
    inputImageExtension: String = "fit",
    outputImageExtension: String = "tif",
): Image? {
    val masterFileName = "master_$calibrationName.$outputImageExtension"
    val masterImageFile = imageDirectory.resolve(masterFileName)
    if (masterImageFile.isFile) {
        println("Loading calibration $calibrationName image: $masterImageFile")
        return ImageReader.read(masterImageFile)
    }

    val imageDirectoryFiles = imageDirectory.listFiles()
    if (imageDirectoryFiles == null || imageDirectoryFiles.isEmpty()) return null

    val imageSuppliers =
        imageDirectoryFiles
        .filter { file -> file.extension == inputImageExtension }
        .map { file ->
            {
                var img = ImageReader.read(file)
                if (debayer) {
                    val badPixels = img[Channel.Red].findBayerBadPixels()
                    img = img.debayer(bayerPattern, badpixelCoords = badPixels)
                }
                img
            }
        }
    val stackedImage = stack(imageSuppliers)

    println("Writing calibration $calibrationName image: $masterImageFile")
    ImageWriter.write(stackedImage, masterImageFile)
    return stackedImage
}
