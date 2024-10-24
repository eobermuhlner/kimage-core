package ch.obermuhlner.kimage.core.image.io

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.io.dimg.DoubleImageIO
import ch.obermuhlner.kimage.core.image.io.json.JsonImageParser
import ch.obermuhlner.kimage.core.image.io.json.JsonTokenizer
import ch.obermuhlner.kimage.core.matrix.FloatMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import nom.tam.fits.BasicHDU
import nom.tam.fits.Fits
import nom.tam.fits.ImageHDU
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.io.File
import javax.imageio.ImageIO

object ImageReader {

    fun read(file: File): Image {
        return when (file.extension.lowercase()) {
            "fits", "fit" -> readFits(file)
            "json" -> readJson(file)
            "dimg" -> DoubleImageIO.readImage(file)
            else -> readStandardImage(file)
        }
    }

    private fun readStandardImage(file: File): MatrixImage {
        val image = ImageIO.read(file)
            ?: throw RuntimeException("Failed to read image: $file")

        return when (image.type) {
            BufferedImage.TYPE_BYTE_GRAY -> readGrayImage(image, 255.0)
            BufferedImage.TYPE_USHORT_GRAY -> readGrayImage(image, 65535.0)
            BufferedImage.TYPE_CUSTOM -> readCustomImage(image)
            else -> readColorImage(image)
        }
    }

    private fun readGrayImage(image: BufferedImage, maxValue: Double): MatrixImage {
        val matrixImage = MatrixImage(image.width, image.height, Channel.Gray)
        val grayMatrix = matrixImage[Channel.Gray]
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val gray = image.raster.getSample(x, y, 0) / maxValue
                grayMatrix[y, x] = gray
            }
        }
        return matrixImage
    }

    private fun readColorImage(image: BufferedImage): MatrixImage {
        val matrixImage = MatrixImage(image.width, image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                val color = doubleArrayOf(
                    (argb shr 16 and 0xff) / 255.0,
                    (argb shr 8 and 0xff) / 255.0,
                    (argb and 0xff) / 255.0
                )
                matrixImage.setPixel(x, y, color)
            }
        }
        return matrixImage
    }

    private fun readCustomImage(image: BufferedImage): MatrixImage {
        val color = DoubleArray(3)
        val matrixImage = MatrixImage(image.width, image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.raster.getPixel(x, y, color)

                val maxValue = when (image.raster.transferType) {
                    DataBuffer.TYPE_USHORT -> 65535.0
                    DataBuffer.TYPE_SHORT -> 65535.0
                    DataBuffer.TYPE_INT -> Int.MAX_VALUE.toDouble()
                    DataBuffer.TYPE_BYTE -> 255.0
                    else -> 1.0 // Fallback, avoid division by zero
                }

                for (i in color.indices) {
                    color[i] = if (image.raster.transferType == DataBuffer.TYPE_SHORT)
                        (color[i] + 32768) / maxValue
                    else
                        color[i] / maxValue
                }

                matrixImage.setPixel(x, y, color)
            }
        }
        return matrixImage
    }

    private fun readJson(file: File): Image {
        return JsonImageParser(JsonTokenizer(file.readText())).parseImage()
    }

    private fun readFits(file: File): Image {
        val fits = Fits(file)
        val hdu = fits.getHDU(0)
        if (hdu is ImageHDU) {
            val matrices = mutableListOf<Matrix>()
            var width: Int = 0
            var height: Int = 0
            when (hdu.axes.size) {
                2 -> {
                    height = hdu.axes[0]
                    width = hdu.axes[1]

                    val matrix = FloatMatrix(width, height)

                    matrices += when (hdu.bitPix) {
                        BasicHDU.BITPIX_BYTE -> {
                            val data = hdu.kernel as Array<ByteArray>
                            matrix.applyEach { row, col, _ ->
                                scaleFitsValue(data[row][col].toDouble(), hdu)
                            }
                            matrix
                        }
                        BasicHDU.BITPIX_SHORT -> {
                            val data = hdu.kernel as Array<ShortArray>
                            matrix.applyEach { row, col, _ ->
                                scaleFitsValue(data[row][col].toDouble(), hdu)
                            }
                            matrix
                        }
                        BasicHDU.BITPIX_INT -> {
                            val data = hdu.kernel as Array<IntArray>
                            matrix.applyEach { row, col, _ ->
                                scaleFitsValue(data[row][col].toDouble(), hdu)
                            }
                            matrix
                        }
                        BasicHDU.BITPIX_LONG -> {
                            val data = hdu.kernel as Array<LongArray>
                            matrix.applyEach { row, col, _ ->
                                scaleFitsValue(data[row][col].toDouble(), hdu)
                            }
                            matrix
                        }
                        BasicHDU.BITPIX_FLOAT -> {
                            val data = hdu.kernel as Array<FloatArray>
                            matrix.applyEach { row, col, _ ->
                                scaleFitsValue(data[row][col].toDouble(), hdu)
                            }
                            matrix
                        }
                        BasicHDU.BITPIX_DOUBLE -> {
                            val data = hdu.kernel as Array<DoubleArray>
                            matrix.applyEach { row, col, _ ->
                                scaleFitsValue(data[row][col].toDouble(), hdu)
                            }
                            matrix
                        }
                        else -> throw IllegalArgumentException("Unknown bits per pixel: ${hdu.bitPix}")
                    }
                }
                3 -> {
                    val channels = hdu.axes[0]
                    height = hdu.axes[1]
                    width = hdu.axes[2]

                    for (channelIndex in 0 until channels) {
                        val matrix = FloatMatrix(height, width)

                        matrices += when (hdu.bitPix) {
                            BasicHDU.BITPIX_BYTE -> {
                                val data = hdu.kernel as Array<Array<ByteArray>>
                                matrix.applyEach { row, col, _ ->
                                    scaleFitsValue(data[channelIndex][row][col].toDouble(), hdu)
                                }
                                matrix
                            }
                            BasicHDU.BITPIX_SHORT -> {
                                val data = hdu.kernel as Array<Array<ShortArray>>
                                matrix.applyEach { row, col, _ ->
                                    scaleFitsValue(data[channelIndex][row][col].toDouble(), hdu)
                                }
                                matrix
                            }
                            BasicHDU.BITPIX_INT -> {
                                val data = hdu.kernel as Array<Array<IntArray>>
                                matrix.applyEach { row, col, _ ->
                                    scaleFitsValue(data[channelIndex][row][col].toDouble(), hdu)
                                }
                                matrix
                            }
                            BasicHDU.BITPIX_LONG -> {
                                val data = hdu.kernel as Array<Array<LongArray>>
                                matrix.applyEach { row, col, _ ->
                                    scaleFitsValue(data[channelIndex][row][col].toDouble(), hdu)
                                }
                                matrix
                            }
                            BasicHDU.BITPIX_FLOAT -> {
                                val data = hdu.kernel as Array<Array<FloatArray>>
                                matrix.applyEach { row, col, _ ->
                                    scaleFitsValue(data[channelIndex][row][col].toDouble(), hdu)
                                }
                                matrix
                            }
                            BasicHDU.BITPIX_DOUBLE -> {
                                val data = hdu.kernel as Array<Array<DoubleArray>>
                                matrix.applyEach { row, col, _ ->
                                    scaleFitsValue(data[channelIndex][row][col].toDouble(), hdu)
                                }
                                matrix
                            }
                            else -> throw IllegalArgumentException("Unknown bits per pixel: ${hdu.bitPix}")
                        }
                    }
                }
            }

            return when (matrices.size) {
                1 -> MatrixImage(width, height,
                    Channel.Gray to matrices[0])
                3 -> MatrixImage(width, height,
                    Channel.Red to matrices[0],
                    Channel.Green to matrices[1],
                    Channel.Blue to matrices[2])
                4 -> MatrixImage(width, height,
                    Channel.Red to matrices[0],
                    Channel.Green to matrices[1],
                    Channel.Blue to matrices[2],
                    Channel.Alpha to matrices[3])
                else -> throw java.lang.IllegalArgumentException("Unknown number of channels in fits: ${matrices.size}")
            }
        }

        throw IllegalArgumentException("Unknown FITS")
    }

    private fun scaleFitsValue(value: Double, hdu: BasicHDU<*>): Double {
        return if (hdu.minimumValue != hdu.maximumValue) {
            hdu.bZero + (value - hdu.minimumValue) / (hdu.maximumValue - hdu.minimumValue) * hdu.bScale
        } else {
            val scaledValue = hdu.bZero + value * hdu.bScale
            when (hdu.bitPix) {
                BasicHDU.BITPIX_BYTE -> scaledValue / (256.0 - 1)
                BasicHDU.BITPIX_SHORT -> scaledValue / (65536.0 - 1)
                BasicHDU.BITPIX_INT -> scaledValue / (4294967296.0 - 1)
                BasicHDU.BITPIX_LONG -> scaledValue / (18446744073709551616.0 - 1)
                BasicHDU.BITPIX_FLOAT -> scaledValue
                else -> throw RuntimeException("Unknown bitpix: " + hdu.bitPix)
            }
        }
    }
}
