package ch.obermuhlner.kimage.core.image.io

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.io.dimg.DoubleImageIO
import ch.obermuhlner.kimage.core.image.io.json.toJson
import nom.tam.fits.Fits
import nom.tam.fits.FitsFactory
import nom.tam.util.BufferedFile
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier

object ImageWriter {
    fun getWriterFileSuffixes(): List<String> {
        val suffixes = ImageIO.getWriterFileSuffixes().toMutableSet()

        for (format in ImageFormat.entries) {
            for (extension in format.extensions) {
                suffixes += extension
            }
        }

        return suffixes.toList().sorted()
    }

    fun write(image: Image, output: File) {
        val name = output.name
        for (format in ImageFormat.entries) {
            for (extension in format.extensions) {
                if (name.length > extension.length && name.substring(name.length - extension.length).equals(extension, ignoreCase = true)) {
                    write(image, output, format)
                    return
                }
            }
        }
        write(image, output, ImageFormat.TIF)
    }

    fun write(image: Image, output: File, format: ImageFormat) {
        when (format) {
            ImageFormat.FITS -> {
                writeFits(image, output)
                return
            }
            ImageFormat.JSON -> {
                writeJson(image, output)
                return
            }
            ImageFormat.DIMG -> {
                DoubleImageIO.writeImage(image, output)
                return
            }
            else -> writeStandardImage(image, output, format)
        }
    }

    private fun writeStandardImage(image: Image, output: File, format: ImageFormat) {
        val bufferedImage = when (format) {
            ImageFormat.TIF, ImageFormat.PNG -> createBufferedImageUShort(image.width, image.height, image.channels.size)
            ImageFormat.JPG -> BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            else -> throw IllegalArgumentException("Unknown format: $format")
        }

        val color = DoubleArray(image.channels.size)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.getPixel(x, y, color)
                writePixelToBufferedImage(bufferedImage, x, y, color)
            }
        }

        ImageIO.write(bufferedImage, format.name.lowercase(), output)
    }

    private fun writePixelToBufferedImage(bufferedImage: BufferedImage, x: Int, y: Int, color: DoubleArray) {
        if (bufferedImage.type != BufferedImage.TYPE_INT_RGB) {
            val adjustedColor = adjustColorForBufferType(bufferedImage.raster.transferType, color)
            bufferedImage.raster.setPixel(x, y, adjustedColor)
        } else {
            val rgb = if (color.size == 1) {
                val gray = (color[0] * 255.0 + 0.5).toInt()
                (gray shl 16) or (gray shl 8) or gray
            } else {
                val r = (color[0] * 255.0 + 0.5).toInt() shl 16
                val g = (color[1] * 255.0 + 0.5).toInt() shl 8
                val b = (color[2] * 255.0 + 0.5).toInt()
                r or g or b
            }
            bufferedImage.setRGB(x, y, rgb)
        }
    }

    private fun adjustColorForBufferType(transferType: Int, color: DoubleArray): DoubleArray {
        return when (transferType) {
            DataBuffer.TYPE_USHORT -> color.map { (it * UShort.MAX_VALUE.toDouble() + 0.5).coerceIn(0.0, UShort.MAX_VALUE.toDouble()) }.toDoubleArray()
            DataBuffer.TYPE_SHORT -> color.map { ((it * Short.MAX_VALUE.toDouble() + 0.5) + Short.MAX_VALUE).coerceIn(0.0, 65535.0) }.toDoubleArray()
            DataBuffer.TYPE_INT -> color.map { (it * Int.MAX_VALUE.toDouble() + 0.5).coerceIn(0.0, Int.MAX_VALUE.toDouble()) }.toDoubleArray()
            DataBuffer.TYPE_BYTE -> color.map { (it * 255.0 + 0.5).coerceIn(0.0, 255.0) }.toDoubleArray()
            else -> color // No adjustment for unknown transfer types
        }
    }

    private fun writeJson(image: Image, output: File) {
        output.writeText(image.toJson())
    }

    private fun writeFits(image: Image, output: File) {
        val data = Array(image.channels.size) { channelIndex ->
            Array(image.height) { y ->
                FloatArray(image.width) { x ->
                    image[image.channels[channelIndex]][y, x].toFloat()
                }
            }
        }

        val fits = Fits()
        fits.addHDU(FitsFactory.hduFactory(data))

        val bufferedFile = BufferedFile(output, "rw")
        fits.write(bufferedFile)
        bufferedFile.close()
    }

    private fun createBufferedImageIntRGB(width: Int, height: Int): BufferedImage {
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    }

    private fun createBufferedImageUShort(width: Int, height: Int, channelCount: Int): BufferedImage {
        return if (channelCount == 1) {
            val specifier = ImageTypeSpecifier.createGrayscale(16, DataBuffer.TYPE_USHORT, false)
            specifier.createBufferedImage(width, height)
        } else {
            val specifier = ImageTypeSpecifier.createInterleaved(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                intArrayOf(0, 1, 2),
                DataBuffer.TYPE_USHORT,
                false,
                false
            )
            specifier.createBufferedImage(width, height)
        }
    }

    private fun createBufferedImageFloat(width: Int, height: Int): BufferedImage {
        val specifier = ImageTypeSpecifier.createInterleaved(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            intArrayOf(0, 1, 2),
            DataBuffer.TYPE_FLOAT,
            false,
            false
        )
        return specifier.createBufferedImage(width, height)
    }
}
