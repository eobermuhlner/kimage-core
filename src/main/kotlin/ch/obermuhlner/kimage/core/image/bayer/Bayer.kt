package ch.obermuhlner.kimage.core.image.bayer

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.values.asXY

enum class BayerPattern {
    RGGB,
    BGGR,
    GBRG,
    GRBG,
}

fun Image.bayer(pattern: BayerPattern = BayerPattern.RGGB): MatrixImage {
    val width = this.width
    val height = this.height
    val mosaicMatrixXY = DoubleMatrix.matrixOf(height, width).asXY()

    val redMatrix = this[Channel.Red]
    val greenMatrix = this[Channel.Green]
    val blueMatrix = this[Channel.Blue]

    val bayerMatrix = when (pattern) {
        BayerPattern.RGGB -> listOf(redMatrix, greenMatrix, greenMatrix, blueMatrix)
        BayerPattern.BGGR -> listOf(blueMatrix, greenMatrix, greenMatrix, redMatrix)
        BayerPattern.GBRG -> listOf(greenMatrix, blueMatrix, greenMatrix, redMatrix)
        BayerPattern.GRBG -> listOf(greenMatrix, redMatrix, greenMatrix, blueMatrix)
    }

    for (y in 0 until height) {
        for (x in 0 until width) {
            val bayerX = x % 2
            val bayerY = y % 2
            val bayerIndex = bayerX + bayerY * 2
            mosaicMatrixXY[x, y] = bayerMatrix[bayerIndex][y, x]
        }
    }

    return MatrixImage(mosaicMatrixXY.matrix)
}
