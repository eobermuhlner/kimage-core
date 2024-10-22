package ch.obermuhlner.kimage.core.image.bayer

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.math.average
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.values.asBoundedXY
import ch.obermuhlner.kimage.core.matrix.values.asXY
import kotlin.math.absoluteValue
import kotlin.math.max

enum class DebayerInterpolation {
    None,
    SuperPixel,
    SuperPixelHalf,
    Nearest,
    Bilinear
}

fun Image.debayerCleanupBadPixels(
    pattern: BayerPattern = BayerPattern.RGGB,
    interpolation: DebayerInterpolation = DebayerInterpolation.Bilinear,
    red: Double = 1.0,
    green: Double = 1.0,
    blue: Double = 1.0,
    minSigma: Double = 0.01,
    gradientThresholdFactor: Double = 10.0,
    steepCountThresholdFactor: Double = 0.75
): MatrixImage {
    val mosaic = if (this.hasChannel(Channel.Red)) {
        this[Channel.Red]
    } else {
        this[Channel.Gray]
    }
    val badPixels = mosaic.findBayerBadPixels(minSigma, gradientThresholdFactor, steepCountThresholdFactor)
    return debayer(pattern, interpolation, red, green, blue, badPixels)
}


fun Image.debayer(
    pattern: BayerPattern = BayerPattern.RGGB,
    interpolation: DebayerInterpolation = DebayerInterpolation.Bilinear,
    red: Double = 1.0,
    green: Double = 1.0,
    blue: Double = 1.0,
    badpixelCoords: Set<Pair<Int, Int>> = emptySet(),
): MatrixImage {
    val (width, height) = when (interpolation) {
        DebayerInterpolation.SuperPixelHalf -> Pair(this.width / 2, this.height / 2)
        else -> Pair(this.width, this.height)
    }

    val (rX, rY) = when (pattern) {
        BayerPattern.RGGB -> Pair(0, 0)
        BayerPattern.BGGR -> Pair(1, 1)
        BayerPattern.GBRG -> Pair(0, 1)
        BayerPattern.GRBG -> Pair(1, 0)
    }
    val (g1X, g1Y) = when (pattern) {
        BayerPattern.RGGB -> Pair(1, 0)
        BayerPattern.BGGR -> Pair(1, 0)
        BayerPattern.GBRG -> Pair(0, 0)
        BayerPattern.GRBG -> Pair(0, 0)
    }
    val (g2X, g2Y) = when (pattern) {
        BayerPattern.RGGB -> Pair(0, 1)
        BayerPattern.BGGR -> Pair(0, 1)
        BayerPattern.GBRG -> Pair(1, 1)
        BayerPattern.GRBG -> Pair(1, 1)
    }
    val (bX, bY) = when (pattern) {
        BayerPattern.RGGB -> Pair(1, 1)
        BayerPattern.BGGR -> Pair(0, 0)
        BayerPattern.GBRG -> Pair(1, 0)
        BayerPattern.GRBG -> Pair(0, 1)
    }

    val mosaic = if (badpixelCoords.isEmpty()) {
        this[Channel.Gray]
    } else {
        this[Channel.Gray].cleanupBayerBadPixels(badpixelCoords)
    }
    val mosaicXY = mosaic.asBoundedXY()

    val widthHalf = mosaicXY.width / 2
    val heightHalf = mosaicXY.height / 2
    val mosaicRedMatrixXY = DoubleMatrix(heightHalf, widthHalf).asBoundedXY()
    val mosaicGreen1MatrixXY = DoubleMatrix(heightHalf, widthHalf).asBoundedXY()
    val mosaicGreen2MatrixXY = DoubleMatrix(heightHalf, widthHalf).asBoundedXY()
    val mosaicBlueMatrixXY = DoubleMatrix(heightHalf, widthHalf).asBoundedXY()
    val mosaicGrayMatrixXY = DoubleMatrix(heightHalf, widthHalf).asBoundedXY()

    for (y in 0 until this.height step 2) {
        for (x in 0 until this.width step 2) {
            val r = mosaicXY[x+rX, y+rY]!!
            val g1 = mosaicXY[x+g1X, y+g1Y]!!
            val g2 = mosaicXY[x+g2X, y+g2Y]!!
            val b = mosaicXY[x+bX, y+bY]!!
            val gray = (r + r + g1 + g2 + b + b) / 6

            mosaicRedMatrixXY[x/2, y/2] = r
            mosaicGreen1MatrixXY[x/2, y/2] = g1
            mosaicGreen2MatrixXY[x/2, y/2] = g2
            mosaicBlueMatrixXY[x/2, y/2] = b
            mosaicGrayMatrixXY[x/2, y/2] = gray
        }
    }

    mosaicRedMatrixXY.matrix.applyEach { v -> v * red  }
    mosaicGreen1MatrixXY.matrix.applyEach { v -> v * green  }
    mosaicGreen2MatrixXY.matrix.applyEach { v -> v * green  }
    mosaicBlueMatrixXY.matrix.applyEach { v -> v * blue  }

    val redMatrixXY = DoubleMatrix.matrixOf(height, width).asBoundedXY()
    val greenMatrixXY = DoubleMatrix.matrixOf(height, width).asBoundedXY()
    val blueMatrixXY = DoubleMatrix.matrixOf(height, width).asBoundedXY()

    when (interpolation) {
        DebayerInterpolation.SuperPixelHalf -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = mosaicRedMatrixXY[x, y]!!
                    val g1 = mosaicGreen1MatrixXY[x, y]!!
                    val g2 = mosaicGreen2MatrixXY[x, y]!!
                    val b = mosaicBlueMatrixXY[x, y]!!

                    redMatrixXY[x, y] = r
                    greenMatrixXY[x, y] = (g1+g2)/2
                    blueMatrixXY[x, y] = b
                }
            }
        }
        DebayerInterpolation.SuperPixel -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = mosaicRedMatrixXY[x/2, y/2]!!
                    val g1 = mosaicGreen1MatrixXY[x/2, y/2]!!
                    val g2 = mosaicGreen2MatrixXY[x/2, y/2]!!
                    val b = mosaicBlueMatrixXY[x/2, y/2]!!

                    redMatrixXY[x, y] = r
                    greenMatrixXY[x, y] = (g1+g2)/2
                    blueMatrixXY[x, y] = b
                }
            }
        }
        DebayerInterpolation.None -> {
            for (y in 0 until height step 2) {
                for (x in 0 until width step 2) {
                    val r = mosaicRedMatrixXY[x/2, y/2]!!
                    val g1 = mosaicGreen1MatrixXY[x/2, y/2]!!
                    val g2 = mosaicGreen2MatrixXY[x/2, y/2]!!
                    val b = mosaicBlueMatrixXY[x/2, y/2]!!

                    redMatrixXY[x+rX, y+rY] = r
                    greenMatrixXY[x+g1X, y+g1Y] = g1
                    greenMatrixXY[x+g2X, y+g2Y] = g2
                    blueMatrixXY[x+bX, y+bY] = b
                }
            }
        }
        DebayerInterpolation.Nearest -> {
            for (y in 0 until height step 2) {
                for (x in 0 until width step 2) {
                    val r = mosaicRedMatrixXY[x / 2, y / 2]!!
                    val g1 = mosaicGreen1MatrixXY[x / 2, y / 2]!!
                    val g2 = mosaicGreen2MatrixXY[x / 2, y / 2]!!
                    val b = mosaicBlueMatrixXY[x / 2, y / 2]!!

                    redMatrixXY[x + 0, y + 0] = r
                    redMatrixXY[x+1, y + 0] = r
                    redMatrixXY[x + 0, y+1] = r
                    redMatrixXY[x+1, y+1] = r
                    blueMatrixXY[x + 0, y + 0] = b
                    blueMatrixXY[x+1, y + 0] = b
                    blueMatrixXY[x + 0, y+1] = b
                    blueMatrixXY[x+1, y+1] = b
                    greenMatrixXY[x + 0, y + 0] = g1
                    greenMatrixXY[x+1, y + 0] = g1
                    greenMatrixXY[x + 0, y+1] = g2
                    greenMatrixXY[x+1, y+1] = g2
                }
            }
        }
        DebayerInterpolation.Bilinear -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dx = x % 2
                    val dy = y % 2

                    val r: Double
                    val g: Double
                    val b: Double
                    if (dx == rX && dy == rY) {
                        r = mosaicXY[x, y]!!
                        g = listOf(mosaicXY[x-1, y], mosaicXY[x+1, y], mosaicXY[x, y-1], mosaicXY[x, y+1]).average()
                        b = listOf(mosaicXY[x-1, y-1], mosaicXY[x-1, y+1], mosaicXY[x+1, y-1], mosaicXY[x+1, y+1]).average()
                    } else if (dx == bX && dy == bY) {
                        r = listOf(mosaicXY[x-1, y-1], mosaicXY[x-1, y+1], mosaicXY[x+1, y-1], mosaicXY[x+1, y+1]).average()
                        g = listOf(mosaicXY[x-1, y], mosaicXY[x+1, y], mosaicXY[x, y-1], mosaicXY[x, y+1]).average()
                        b = mosaicXY[x, y]!!
                    } else {
                        g = mosaicXY[x, y]!!
                        if ((x-1) % 2 == rX) {
                            r = listOf(mosaicXY[x-1, y], mosaicXY[x+1, y]).average()
                            b = listOf(mosaicXY[x, y-1], mosaicXY[x, y+1]).average()
                        } else {
                            r = listOf(mosaicXY[x, y-1], mosaicXY[x, y+1]).average()
                            b = listOf(mosaicXY[x-1, y], mosaicXY[x+1, y]).average()
                        }
                    }

                    redMatrixXY[x, y] = r * red
                    greenMatrixXY[x, y] = g * green
                    blueMatrixXY[x, y] = b * blue
                }
            }
        }
    }

    if (red > 1.0) {
        redMatrixXY.matrix.applyEach { v -> clamp(v, 0.0, 1.0) }
    }
    if (green > 1.0) {
        greenMatrixXY.matrix.applyEach { v -> clamp(v, 0.0, 1.0) }
    }
    if (blue > 1.0) {
        blueMatrixXY.matrix.applyEach { v -> clamp(v, 0.0, 1.0) }
    }

    return MatrixImage(width, height,
        Channel.Red to redMatrixXY.matrix,
        Channel.Green to greenMatrixXY.matrix,
        Channel.Blue to blueMatrixXY.matrix)
}

fun Matrix.findBayerBadPixels(
    minSigma: Double = 0.01,
    gradientThresholdFactor: Double = 10.0,
    steepCountThresholdFactor: Double = 0.75
): Set<Pair<Int, Int>> {
    val matrixXY = this.asXY()

    val width = matrixXY.width
    val height = matrixXY.height
    val result = mutableSetOf<Pair<Int, Int>>()

    for (y in 0 until height) {
        for (x in 0 until width) {
            val value = matrixXY[x, y]
            val values = mutableListOf<Double>()

            fun addValueIfInBounds(x: Int, y: Int) {
                if (matrixXY.isInBounds(x, y)) {
                    values.add(matrixXY[x, y])
                }
            }

            for (dy in -2 .. 2 step 2) {
                for (dx in -2 .. 2 step 2) {
                    if (dx != 0 || dy != 0) {
                        addValueIfInBounds(x+dx, y+dy)
                    }
                }
            }
//            addValueIfInBounds(x-2, y)
//            addValueIfInBounds(x+2, y)
//            addValueIfInBounds(x, y-2)
//            addValueIfInBounds(x, y+2)

            val sigma = values.stddev()
            //val sigma = values.medianAbsoluteDeviation()

            val sigmaCorrected = max(sigma, minSigma)
            val steepGradientsCount = values.count {
                (value - it).absoluteValue > sigmaCorrected * gradientThresholdFactor
            }

            if (steepGradientsCount >= values.size * steepCountThresholdFactor) {
                result.add(Pair(x, y))
            }
        }
    }

    return result
}

fun Matrix.cleanupBayerBadPixels(
    badpixelCoords: Set<Pair<Int, Int>> = emptySet()
): Matrix {
    val mosaic = copy()
    val mosaicXY = mosaic.asBoundedXY()

    for (badpixelCoord in badpixelCoords) {
        val x = badpixelCoord.first
        val y = badpixelCoord.second

        val surroundingValues = mutableListOf<Double>()
        for (dy in -2..2 step 2) {
            for (dx in -2..2 step 2) {
                if ((dx != 0 && dy != 0) && mosaicXY.isInBounds(x + dx, y + dy) && !badpixelCoords.contains(Pair(x + dx, y + dy))) {
                    surroundingValues.add(mosaicXY[x + dx, y + dy]!!)
                }
            }
        }

        mosaicXY[x, y] = surroundingValues.median()
    }
    return mosaic
}
