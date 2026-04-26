package ch.obermuhlner.kimage.core.image.bayer

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.PointXY
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
    Monochrome,
    Nearest,
    Bilinear,
    AHD,
    GLI,
    AMaZE,
    VNG,
    PPG
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
    badpixelCoords: Set<PointXY> = emptySet(),
): MatrixImage {
    val (width, height) = when (interpolation) {
        DebayerInterpolation.SuperPixelHalf -> PointXY(this.width / 2, this.height / 2)
        else -> PointXY(this.width, this.height)
    }

    val (rX, rY) = when (pattern) {
        BayerPattern.RGGB -> PointXY(0, 0)
        BayerPattern.BGGR -> PointXY(1, 1)
        BayerPattern.GBRG -> PointXY(0, 1)
        BayerPattern.GRBG -> PointXY(1, 0)
    }
    val (g1X, g1Y) = when (pattern) {
        BayerPattern.RGGB -> PointXY(1, 0)
        BayerPattern.BGGR -> PointXY(1, 0)
        BayerPattern.GBRG -> PointXY(0, 0)
        BayerPattern.GRBG -> PointXY(0, 0)
    }
    val (g2X, g2Y) = when (pattern) {
        BayerPattern.RGGB -> PointXY(0, 1)
        BayerPattern.BGGR -> PointXY(0, 1)
        BayerPattern.GBRG -> PointXY(1, 1)
        BayerPattern.GRBG -> PointXY(1, 1)
    }
    val (bX, bY) = when (pattern) {
        BayerPattern.RGGB -> PointXY(1, 1)
        BayerPattern.BGGR -> PointXY(0, 0)
        BayerPattern.GBRG -> PointXY(1, 0)
        BayerPattern.GRBG -> PointXY(0, 1)
    }

    val mosaic = if (badpixelCoords.isEmpty()) {
        this[Channel.Gray]
    } else {
        this[Channel.Gray].cleanupBayerBadPixels(badpixelCoords)
    }
    val mosaicXY = mosaic.asXY()

    val widthHalf = mosaicXY.width / 2
    val heightHalf = mosaicXY.height / 2
    val mosaicRedMatrixXY = DoubleMatrix(heightHalf, widthHalf).asXY()
    val mosaicGreen1MatrixXY = DoubleMatrix(heightHalf, widthHalf).asXY()
    val mosaicGreen2MatrixXY = DoubleMatrix(heightHalf, widthHalf).asXY()
    val mosaicBlueMatrixXY = DoubleMatrix(heightHalf, widthHalf).asXY()
    val mosaicGrayMatrixXY = DoubleMatrix(heightHalf, widthHalf).asXY()

    for (y in 0 until this.height step 2) {
        for (x in 0 until this.width step 2) {
            val r = mosaicXY[x+rX, y+rY]
            val g1 = mosaicXY[x+g1X, y+g1Y]
            val g2 = mosaicXY[x+g2X, y+g2Y]
            val b = mosaicXY[x+bX, y+bY]
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
                    val r = mosaicRedMatrixXY[x, y]
                    val g1 = mosaicGreen1MatrixXY[x, y]
                    val g2 = mosaicGreen2MatrixXY[x, y]
                    val b = mosaicBlueMatrixXY[x, y]

                    redMatrixXY[x, y] = r
                    greenMatrixXY[x, y] = (g1+g2)/2
                    blueMatrixXY[x, y] = b
                }
            }
        }
        DebayerInterpolation.SuperPixel -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = mosaicRedMatrixXY[x/2, y/2]
                    val g1 = mosaicGreen1MatrixXY[x/2, y/2]
                    val g2 = mosaicGreen2MatrixXY[x/2, y/2]
                    val b = mosaicBlueMatrixXY[x/2, y/2]

                    redMatrixXY[x, y] = r
                    greenMatrixXY[x, y] = (g1+g2)/2
                    blueMatrixXY[x, y] = b
                }
            }
        }
        DebayerInterpolation.None -> {
            for (y in 0 until height step 2) {
                for (x in 0 until width step 2) {
                    val r = mosaicRedMatrixXY[x/2, y/2]
                    val g1 = mosaicGreen1MatrixXY[x/2, y/2]
                    val g2 = mosaicGreen2MatrixXY[x/2, y/2]
                    val b = mosaicBlueMatrixXY[x/2, y/2]

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
                    val r = mosaicRedMatrixXY[x/2, y/2]
                    val g1 = mosaicGreen1MatrixXY[x/2, y/2]
                    val g2 = mosaicGreen2MatrixXY[x/2, y/2]
                    val b = mosaicBlueMatrixXY[x/2, y/2]

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
        DebayerInterpolation.Monochrome -> {
            val mosaicBoundedXY = mosaic.asBoundedXY()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dx = x % 2
                    val dy = y % 2

                    val r: Double
                    val g: Double
                    val b: Double
                    if (dx == rX && dy == rY) {
                        r = mosaicBoundedXY[x, y]!!
                        g = listOf(mosaicBoundedXY[x-1, y], mosaicBoundedXY[x+1, y], mosaicBoundedXY[x, y-1], mosaicBoundedXY[x, y+1]).average()
                        b = listOf(mosaicBoundedXY[x-1, y-1], mosaicBoundedXY[x-1, y+1], mosaicBoundedXY[x+1, y-1], mosaicBoundedXY[x+1, y+1]).average()
                    } else if (dx == bX && dy == bY) {
                        r = listOf(mosaicBoundedXY[x-1, y-1], mosaicBoundedXY[x-1, y+1], mosaicBoundedXY[x+1, y-1], mosaicBoundedXY[x+1, y+1]).average()
                        g = listOf(mosaicBoundedXY[x-1, y], mosaicBoundedXY[x+1, y], mosaicBoundedXY[x, y-1], mosaicBoundedXY[x, y+1]).average()
                        b = mosaicBoundedXY[x, y]!!
                    } else {
                        g = mosaicBoundedXY[x, y]!!
                        if ((x-1) % 2 == rX) {
                            r = listOf(mosaicBoundedXY[x-1, y], mosaicBoundedXY[x+1, y]).average()
                            b = listOf(mosaicBoundedXY[x, y-1], mosaicBoundedXY[x, y+1]).average()
                        } else {
                            r = listOf(mosaicBoundedXY[x, y-1], mosaicBoundedXY[x, y+1]).average()
                            b = listOf(mosaicBoundedXY[x-1, y], mosaicBoundedXY[x+1, y]).average()
                        }
                    }

                    val gray = (r * red + g * green + b * blue) / 3.0
                    redMatrixXY[x, y] = gray * red
                    greenMatrixXY[x, y] = gray * green
                    blueMatrixXY[x, y] = gray * blue
                }
            }
        }
        DebayerInterpolation.Bilinear -> {
            val mosaicBoundedXY = mosaic.asBoundedXY()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dx = x % 2
                    val dy = y % 2

                    val r: Double
                    val g: Double
                    val b: Double
                    if (dx == rX && dy == rY) {
                        r = mosaicBoundedXY[x, y]!!
                        g = listOf(mosaicBoundedXY[x-1, y], mosaicBoundedXY[x+1, y], mosaicBoundedXY[x, y-1], mosaicBoundedXY[x, y+1]).average()
                        b = listOf(mosaicBoundedXY[x-1, y-1], mosaicBoundedXY[x-1, y+1], mosaicBoundedXY[x+1, y-1], mosaicBoundedXY[x+1, y+1]).average()
                    } else if (dx == bX && dy == bY) {
                        r = listOf(mosaicBoundedXY[x-1, y-1], mosaicBoundedXY[x-1, y+1], mosaicBoundedXY[x+1, y-1], mosaicBoundedXY[x+1, y+1]).average()
                        g = listOf(mosaicBoundedXY[x-1, y], mosaicBoundedXY[x+1, y], mosaicBoundedXY[x, y-1], mosaicBoundedXY[x, y+1]).average()
                        b = mosaicBoundedXY[x, y]!!
                    } else {
                        g = mosaicBoundedXY[x, y]!!
                        if ((x-1) % 2 == rX) {
                            r = listOf(mosaicBoundedXY[x-1, y], mosaicBoundedXY[x+1, y]).average()
                            b = listOf(mosaicBoundedXY[x, y-1], mosaicBoundedXY[x, y+1]).average()
                        } else {
                            r = listOf(mosaicBoundedXY[x, y-1], mosaicBoundedXY[x, y+1]).average()
                            b = listOf(mosaicBoundedXY[x-1, y], mosaicBoundedXY[x+1, y]).average()
                        }
                    }

                    redMatrixXY[x, y] = r * red
                    greenMatrixXY[x, y] = g * green
                    blueMatrixXY[x, y] = b * blue
                }
            }
        }
        DebayerInterpolation.GLI -> {
            val mosaicBoundedXY = mosaic.asBoundedXY()

            fun hasValue(x: Int, y: Int): Boolean = mosaicBoundedXY.isInBounds(x, y) && mosaicBoundedXY[x, y] != null

            fun avgNonNull(vararg coords: Pair<Int, Int>): Double {
                val values = coords.mapNotNull { (cx, cy) -> mosaicBoundedXY[cx, cy] }
                return if (values.isEmpty()) 0.0 else values.average()
            }

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dx = x % 2
                    val dy = y % 2

                    val r: Double
                    val g: Double
                    val b: Double

                    if (dx == rX && dy == rY) { // Red pixel
                        r = mosaicBoundedXY[x, y] ?: 0.0

                        val hasLeft = hasValue(x - 1, y)
                        val hasRight = hasValue(x + 1, y)
                        val hasTop = hasValue(x, y - 1)
                        val hasBottom = hasValue(x, y + 1)

                        val hGrad = if (hasLeft && hasRight) {
                            (mosaicBoundedXY[x - 1, y]!! - mosaicBoundedXY[x + 1, y]!!).absoluteValue
                        } else Double.MAX_VALUE
                        val vGrad = if (hasTop && hasBottom) {
                            (mosaicBoundedXY[x, y - 1]!! - mosaicBoundedXY[x, y + 1]!!).absoluteValue
                        } else Double.MAX_VALUE

                        g = if (hGrad <= vGrad) {
                            avgNonNull(x - 1 to y, x + 1 to y)
                        } else {
                            avgNonNull(x to y - 1, x to y + 1)
                        }

                        b = avgNonNull(
                            x - 1 to y - 1, x + 1 to y - 1,
                            x - 1 to y + 1, x + 1 to y + 1
                        )

                    } else if (dx == bX && dy == bY) { // Blue pixel
                        b = mosaicBoundedXY[x, y] ?: 0.0

                        val hasLeft = hasValue(x - 1, y)
                        val hasRight = hasValue(x + 1, y)
                        val hasTop = hasValue(x, y - 1)
                        val hasBottom = hasValue(x, y + 1)

                        val hGrad = if (hasLeft && hasRight) {
                            (mosaicBoundedXY[x - 1, y]!! - mosaicBoundedXY[x + 1, y]!!).absoluteValue
                        } else Double.MAX_VALUE
                        val vGrad = if (hasTop && hasBottom) {
                            (mosaicBoundedXY[x, y - 1]!! - mosaicBoundedXY[x, y + 1]!!).absoluteValue
                        } else Double.MAX_VALUE

                        g = if (hGrad <= vGrad) {
                            avgNonNull(x - 1 to y, x + 1 to y)
                        } else {
                            avgNonNull(x to y - 1, x to y + 1)
                        }

                        r = avgNonNull(
                            x - 1 to y - 1, x + 1 to y - 1,
                            x - 1 to y + 1, x + 1 to y + 1
                        )

                    } else { // Green pixel
                        g = mosaicBoundedXY[x, y] ?: 0.0

                        val hasLeft = hasValue(x - 1, y)
                        val hasRight = hasValue(x + 1, y)
                        val hasTop = hasValue(x, y - 1)
                        val hasBottom = hasValue(x, y + 1)

                        if ((x - 1) % 2 == rX) { // Green adjacent to red horizontally
                            r = if (hasLeft && hasRight) {
                                avgNonNull(x - 1 to y, x + 1 to y)
                            } else if (hasLeft) {
                                mosaicBoundedXY[x - 1, y]!!
                            } else if (hasRight) {
                                mosaicBoundedXY[x + 1, y]!!
                            } else {
                                0.0
                            }
                            b = if (hasTop && hasBottom) {
                                avgNonNull(x to y - 1, x to y + 1)
                            } else if (hasTop) {
                                mosaicBoundedXY[x, y - 1]!!
                            } else if (hasBottom) {
                                mosaicBoundedXY[x, y + 1]!!
                            } else {
                                0.0
                            }
                        } else { // Green adjacent to blue horizontally
                            b = if (hasLeft && hasRight) {
                                avgNonNull(x - 1 to y, x + 1 to y)
                            } else if (hasLeft) {
                                mosaicBoundedXY[x - 1, y]!!
                            } else if (hasRight) {
                                mosaicBoundedXY[x + 1, y]!!
                            } else {
                                0.0
                            }
                            r = if (hasTop && hasBottom) {
                                avgNonNull(x to y - 1, x to y + 1)
                            } else if (hasTop) {
                                mosaicBoundedXY[x, y - 1]!!
                            } else if (hasBottom) {
                                mosaicBoundedXY[x, y + 1]!!
                            } else {
                                0.0
                            }
                        }
                    }

                    redMatrixXY[x, y] = r * red
                    greenMatrixXY[x, y] = g * green
                    blueMatrixXY[x, y] = b * blue
                }
            }
        }
        DebayerInterpolation.AHD -> {
            val mosaicBoundedXY = mosaic.asBoundedXY()

            fun avgNonNull(vararg coords: Pair<Int, Int>): Double {
                val values = coords.mapNotNull { (cx, cy) -> mosaicBoundedXY[cx, cy] }
                return if (values.isEmpty()) 0.0 else values.average()
            }

            fun hasValue(x: Int, y: Int): Boolean = mosaicBoundedXY.isInBounds(x, y) && mosaicBoundedXY[x, y] != null

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dx = x % 2
                    val dy = y % 2

                    val r: Double
                    val g: Double
                    val b: Double

                    if (dx == rX && dy == rY) { // Red pixel position
                        // Known values
                        r = mosaicBoundedXY[x, y] ?: 0.0

                        // Interpolate missing green using gradient direction from adjacent green pixels
                        val hasLeft = hasValue(x - 1, y)
                        val hasRight = hasValue(x + 1, y)
                        val hasTop = hasValue(x, y - 1)
                        val hasBottom = hasValue(x, y + 1)

                        val hGrad = if (hasLeft && hasRight) {
                            (mosaicBoundedXY[x - 1, y]!! - mosaicBoundedXY[x + 1, y]!!).absoluteValue
                        } else Double.MAX_VALUE
                        val vGrad = if (hasTop && hasBottom) {
                            (mosaicBoundedXY[x, y - 1]!! - mosaicBoundedXY[x, y + 1]!!).absoluteValue
                        } else Double.MAX_VALUE

                        g = if (hGrad <= vGrad) {
                            avgNonNull(x - 1 to y, x + 1 to y)
                        } else {
                            avgNonNull(x to y - 1, x to y + 1)
                        }

                        // Interpolate missing blue via bilinear from diagonals (standard - no gradient direction)
                        b = avgNonNull(
                            x - 1 to y - 1, x + 1 to y - 1,
                            x - 1 to y + 1, x + 1 to y + 1
                        )

                    } else if (dx == bX && dy == bY) { // Blue pixel position
                        // Known values
                        b = mosaicBoundedXY[x, y] ?: 0.0

                        // Interpolate missing green using gradient direction from adjacent green pixels
                        val hasLeft = hasValue(x - 1, y)
                        val hasRight = hasValue(x + 1, y)
                        val hasTop = hasValue(x, y - 1)
                        val hasBottom = hasValue(x, y + 1)

                        val hGrad = if (hasLeft && hasRight) {
                            (mosaicBoundedXY[x - 1, y]!! - mosaicBoundedXY[x + 1, y]!!).absoluteValue
                        } else Double.MAX_VALUE
                        val vGrad = if (hasTop && hasBottom) {
                            (mosaicBoundedXY[x, y - 1]!! - mosaicBoundedXY[x, y + 1]!!).absoluteValue
                        } else Double.MAX_VALUE

                        g = if (hGrad <= vGrad) {
                            avgNonNull(x - 1 to y, x + 1 to y)
                        } else {
                            avgNonNull(x to y - 1, x to y + 1)
                        }

                        // Interpolate missing red via bilinear from diagonals (standard - no gradient direction)
                        r = avgNonNull(
                            x - 1 to y - 1, x + 1 to y - 1,
                            x - 1 to y + 1, x + 1 to y + 1
                        )

                    } else { // Green pixel position
                        // Known value
                        g = mosaicBoundedXY[x, y] ?: 0.0

                        if ((x - 1) % 2 == rX) { // Green adjacent to red horizontally (at red column)
                            // Interpolate red from left/right
                            r = avgNonNull(x - 1 to y, x + 1 to y)
                            // Interpolate blue from top/bottom
                            b = avgNonNull(x to y - 1, x to y + 1)
                        } else { // Green adjacent to blue horizontally (at blue column)
                            // Interpolate blue from left/right
                            b = avgNonNull(x - 1 to y, x + 1 to y)
                            // Interpolate red from top/bottom
                            r = avgNonNull(x to y - 1, x to y + 1)
                        }
                    }

                    redMatrixXY[x, y] = r * red
                    greenMatrixXY[x, y] = g * green
                    blueMatrixXY[x, y] = b * blue
                }
            }
        }
        DebayerInterpolation.AMaZE -> {
            val bmos = mosaic.asBoundedXY()

            fun cfa(x: Int, y: Int): Double = bmos[x, y] ?: 0.0
            fun inBounds(x: Int, y: Int): Boolean = bmos.isInBounds(x, y)
            fun isRSite(x: Int, y: Int): Boolean = x % 2 == rX && y % 2 == rY
            fun isBSite(x: Int, y: Int): Boolean = x % 2 == bX && y % 2 == bY

            val gPlane = Array(height) { DoubleArray(width) }
            val rPlane = Array(height) { DoubleArray(width) }
            val bPlane = Array(height) { DoubleArray(width) }

            // Fill known CFA values
            for (y in 0 until height) {
                for (x in 0 until width) {
                    when {
                        isRSite(x, y) -> rPlane[y][x] = cfa(x, y)
                        isBSite(x, y) -> bPlane[y][x] = cfa(x, y)
                        else          -> gPlane[y][x] = cfa(x, y)
                    }
                }
            }

            // Step 1: Interpolate green at R/B sites using gradient-weighted directional estimates
            // with Laplacian correction to account for local same-channel gradient
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (isRSite(x, y) || isBSite(x, y)) {
                        val p = cfa(x, y)

                        val canH = inBounds(x - 1, y) && inBounds(x + 1, y)
                        val canV = inBounds(x, y - 1) && inBounds(x, y + 1)

                        val gL = cfa(x - 1, y); val gR = cfa(x + 1, y)
                        val gU = cfa(x, y - 1); val gD = cfa(x, y + 1)
                        val pL2 = if (inBounds(x - 2, y)) cfa(x - 2, y) else p
                        val pR2 = if (inBounds(x + 2, y)) cfa(x + 2, y) else p
                        val pU2 = if (inBounds(x, y - 2)) cfa(x, y - 2) else p
                        val pD2 = if (inBounds(x, y + 2)) cfa(x, y + 2) else p

                        val gh: Double
                        val dh: Double
                        if (canH) {
                            gh = (gL + gR) / 2.0 + (2.0 * p - pL2 - pR2) / 4.0
                            dh = (gL - gR).absoluteValue + (p - pL2).absoluteValue + (p - pR2).absoluteValue
                        } else {
                            gh = if (inBounds(x - 1, y)) gL else gR
                            dh = Double.MAX_VALUE / 2.0
                        }

                        val gv: Double
                        val dv: Double
                        if (canV) {
                            gv = (gU + gD) / 2.0 + (2.0 * p - pU2 - pD2) / 4.0
                            dv = (gU - gD).absoluteValue + (p - pU2).absoluteValue + (p - pD2).absoluteValue
                        } else {
                            gv = if (inBounds(x, y - 1)) gU else gD
                            dv = Double.MAX_VALUE / 2.0
                        }

                        val wh = 1.0 / (1.0 + dh)
                        val wv = 1.0 / (1.0 + dv)
                        gPlane[y][x] = clamp((wh * gh + wv * gv) / (wh + wv), 0.0, 1.0)
                    }
                }
            }

            // Color-difference planes at known R/B sites (G now known everywhere after step 1)
            val grDiff = Array(height) { y -> DoubleArray(width) { x -> if (isRSite(x, y)) gPlane[y][x] - rPlane[y][x] else 0.0 } }
            val gbDiff = Array(height) { y -> DoubleArray(width) { x -> if (isBSite(x, y)) gPlane[y][x] - bPlane[y][x] else 0.0 } }

            // Step 2: Interpolate R/B at green sites using color-difference planes
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (!isRSite(x, y) && !isBSite(x, y)) {
                        val g = gPlane[y][x]

                        if (y % 2 == rY) { // Green in R-row: R neighbors are horizontal, B neighbors are vertical
                            val grDiffs = mutableListOf<Double>()
                            if (inBounds(x - 1, y) && isRSite(x - 1, y)) grDiffs += grDiff[y][x - 1]
                            if (inBounds(x + 1, y) && isRSite(x + 1, y)) grDiffs += grDiff[y][x + 1]
                            rPlane[y][x] = clamp(g - (if (grDiffs.isEmpty()) 0.0 else grDiffs.average()), 0.0, 1.0)

                            val gbDiffs = mutableListOf<Double>()
                            if (inBounds(x, y - 1) && isBSite(x, y - 1)) gbDiffs += gbDiff[y - 1][x]
                            if (inBounds(x, y + 1) && isBSite(x, y + 1)) gbDiffs += gbDiff[y + 1][x]
                            bPlane[y][x] = clamp(g - (if (gbDiffs.isEmpty()) 0.0 else gbDiffs.average()), 0.0, 1.0)
                        } else { // Green in B-row: B neighbors are horizontal, R neighbors are vertical
                            val gbDiffs = mutableListOf<Double>()
                            if (inBounds(x - 1, y) && isBSite(x - 1, y)) gbDiffs += gbDiff[y][x - 1]
                            if (inBounds(x + 1, y) && isBSite(x + 1, y)) gbDiffs += gbDiff[y][x + 1]
                            bPlane[y][x] = clamp(g - (if (gbDiffs.isEmpty()) 0.0 else gbDiffs.average()), 0.0, 1.0)

                            val grDiffs = mutableListOf<Double>()
                            if (inBounds(x, y - 1) && isRSite(x, y - 1)) grDiffs += grDiff[y - 1][x]
                            if (inBounds(x, y + 1) && isRSite(x, y + 1)) grDiffs += grDiff[y + 1][x]
                            rPlane[y][x] = clamp(g - (if (grDiffs.isEmpty()) 0.0 else grDiffs.average()), 0.0, 1.0)
                        }
                    }
                }
            }

            // Step 3: Interpolate opposite channel at R/B sites from diagonal color differences
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val g = gPlane[y][x]
                    if (isRSite(x, y)) {
                        val diffs = mutableListOf<Double>()
                        for ((dx, dy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) {
                            val cx = x + dx; val cy = y + dy
                            if (inBounds(cx, cy) && isBSite(cx, cy)) diffs += gbDiff[cy][cx]
                        }
                        bPlane[y][x] = clamp(g - (if (diffs.isEmpty()) 0.0 else diffs.average()), 0.0, 1.0)
                    } else if (isBSite(x, y)) {
                        val diffs = mutableListOf<Double>()
                        for ((dx, dy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) {
                            val cx = x + dx; val cy = y + dy
                            if (inBounds(cx, cy) && isRSite(cx, cy)) diffs += grDiff[cy][cx]
                        }
                        rPlane[y][x] = clamp(g - (if (diffs.isEmpty()) 0.0 else diffs.average()), 0.0, 1.0)
                    }
                }
            }

            // Copy to output
            for (y in 0 until height) {
                for (x in 0 until width) {
                    redMatrixXY[x, y] = rPlane[y][x] * red
                    greenMatrixXY[x, y] = gPlane[y][x] * green
                    blueMatrixXY[x, y] = bPlane[y][x] * blue
                }
            }
        }
        DebayerInterpolation.VNG -> {
            val bmos = mosaic.asBoundedXY()
            fun cfa(x: Int, y: Int): Double = bmos[x, y] ?: 0.0
            fun isRSite(x: Int, y: Int) = x % 2 == rX && y % 2 == rY
            fun isBSite(x: Int, y: Int) = x % 2 == bX && y % 2 == bY

            val dirs = arrayOf(
                0 to -1, 1 to -1, 1 to 0, 1 to 1,
                0 to 1, -1 to 1, -1 to 0, -1 to -1
            )

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val isR = isRSite(x, y)
                    val isB = isBSite(x, y)

                    val grads = DoubleArray(8)
                    val rEsts = DoubleArray(8)
                    val gEsts = DoubleArray(8)
                    val bEsts = DoubleArray(8)

                    for (i in dirs.indices) {
                        val (ddx, ddy) = dirs[i]
                        val nx1 = x + ddx; val ny1 = y + ddy
                        val nxN1 = x - ddx; val nyN1 = y - ddy
                        val nx2 = x + 2 * ddx; val ny2 = y + 2 * ddy

                        val crossGrad = if (bmos.isInBounds(nx1, ny1) && bmos.isInBounds(nxN1, nyN1))
                            (cfa(nx1, ny1) - cfa(nxN1, nyN1)).absoluteValue
                        else Double.MAX_VALUE / 4.0
                        val sameStep = if (bmos.isInBounds(nx2, ny2))
                            (cfa(x, y) - cfa(nx2, ny2)).absoluteValue
                        else 0.0
                        grads[i] = crossGrad + sameStep

                        val isAxisDir = ddx == 0 || ddy == 0
                        val perpDx = ddy
                        val perpDy = ddx

                        when {
                            isR -> {
                                rEsts[i] = cfa(x, y)
                                if (isAxisDir) {
                                    gEsts[i] = if (bmos.isInBounds(nx1, ny1)) cfa(nx1, ny1) else cfa(x, y)
                                    bEsts[i] = listOf(bmos[nx1 + perpDx, ny1 + perpDy], bmos[nx1 - perpDx, ny1 - perpDy]).average()
                                        .let { if (it.isNaN()) cfa(x, y) else it }
                                } else {
                                    bEsts[i] = if (bmos.isInBounds(nx1, ny1)) cfa(nx1, ny1) else cfa(x, y)
                                    gEsts[i] = listOf(bmos[x + ddx, y], bmos[x, y + ddy]).average()
                                        .let { if (it.isNaN()) cfa(x, y) else it }
                                }
                            }
                            isB -> {
                                bEsts[i] = cfa(x, y)
                                if (isAxisDir) {
                                    gEsts[i] = if (bmos.isInBounds(nx1, ny1)) cfa(nx1, ny1) else cfa(x, y)
                                    rEsts[i] = listOf(bmos[nx1 + perpDx, ny1 + perpDy], bmos[nx1 - perpDx, ny1 - perpDy]).average()
                                        .let { if (it.isNaN()) cfa(x, y) else it }
                                } else {
                                    rEsts[i] = if (bmos.isInBounds(nx1, ny1)) cfa(nx1, ny1) else cfa(x, y)
                                    gEsts[i] = listOf(bmos[x + ddx, y], bmos[x, y + ddy]).average()
                                        .let { if (it.isNaN()) cfa(x, y) else it }
                                }
                            }
                            else -> { // Green pixel
                                gEsts[i] = cfa(x, y)
                                if (isAxisDir) {
                                    when {
                                        bmos.isInBounds(nx1, ny1) && isRSite(nx1, ny1) -> {
                                            rEsts[i] = cfa(nx1, ny1)
                                            bEsts[i] = listOf(bmos[x + perpDx, y + perpDy], bmos[x - perpDx, y - perpDy]).average()
                                                .let { if (it.isNaN()) cfa(x, y) else it }
                                        }
                                        bmos.isInBounds(nx1, ny1) && isBSite(nx1, ny1) -> {
                                            bEsts[i] = cfa(nx1, ny1)
                                            rEsts[i] = listOf(bmos[x + perpDx, y + perpDy], bmos[x - perpDx, y - perpDy]).average()
                                                .let { if (it.isNaN()) cfa(x, y) else it }
                                        }
                                        else -> { rEsts[i] = cfa(x, y); bEsts[i] = cfa(x, y) }
                                    }
                                } else {
                                    // Diagonal from Green hits another Green; estimate R/B from axis neighbors
                                    var rSum = 0.0; var rCnt = 0; var bSum = 0.0; var bCnt = 0
                                    for ((px, py) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                                        if (bmos.isInBounds(px, py)) {
                                            when {
                                                isRSite(px, py) -> { rSum += cfa(px, py); rCnt++ }
                                                isBSite(px, py) -> { bSum += cfa(px, py); bCnt++ }
                                            }
                                        }
                                    }
                                    rEsts[i] = if (rCnt > 0) rSum / rCnt else cfa(x, y)
                                    bEsts[i] = if (bCnt > 0) bSum / bCnt else cfa(x, y)
                                }
                            }
                        }
                    }

                    val minGrad = grads.min()
                    val threshold = minGrad * 1.5

                    var rAcc = 0.0; var gAcc = 0.0; var bAcc = 0.0; var cnt = 0
                    for (i in dirs.indices) {
                        if (grads[i] <= threshold + 1e-10) {
                            rAcc += rEsts[i]; gAcc += gEsts[i]; bAcc += bEsts[i]; cnt++
                        }
                    }
                    if (cnt == 0) {
                        for (i in dirs.indices) { rAcc += rEsts[i]; gAcc += gEsts[i]; bAcc += bEsts[i] }
                        cnt = 8
                    }

                    redMatrixXY[x, y] = clamp(rAcc / cnt * red, 0.0, 1.0)
                    greenMatrixXY[x, y] = clamp(gAcc / cnt * green, 0.0, 1.0)
                    blueMatrixXY[x, y] = clamp(bAcc / cnt * blue, 0.0, 1.0)
                }
            }
        }
        DebayerInterpolation.PPG -> {
            val bmos = mosaic.asBoundedXY()
            fun cfa(x: Int, y: Int): Double = bmos[x, y] ?: 0.0
            fun inBounds(x: Int, y: Int): Boolean = bmos.isInBounds(x, y)
            fun isRSite(x: Int, y: Int) = x % 2 == rX && y % 2 == rY
            fun isBSite(x: Int, y: Int) = x % 2 == bX && y % 2 == bY

            val gPlane = Array(height) { DoubleArray(width) }
            val rPlane = Array(height) { DoubleArray(width) }
            val bPlane = Array(height) { DoubleArray(width) }

            // Step 1: Copy known CFA values to colour planes
            for (y in 0 until height) {
                for (x in 0 until width) {
                    when {
                        isRSite(x, y) -> rPlane[y][x] = cfa(x, y)
                        isBSite(x, y) -> bPlane[y][x] = cfa(x, y)
                        else -> gPlane[y][x] = cfa(x, y)
                    }
                }
            }

            // Step 2: Interpolate G at R/B sites using gradient-weighted Malvar formula
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (isRSite(x, y) || isBSite(x, y)) {
                        val p = cfa(x, y)
                        val gL = cfa(x - 1, y); val gR = cfa(x + 1, y)
                        val gU = cfa(x, y - 1); val gD = cfa(x, y + 1)
                        val pL2 = if (inBounds(x - 2, y)) cfa(x - 2, y) else p
                        val pR2 = if (inBounds(x + 2, y)) cfa(x + 2, y) else p
                        val pU2 = if (inBounds(x, y - 2)) cfa(x, y - 2) else p
                        val pD2 = if (inBounds(x, y + 2)) cfa(x, y + 2) else p

                        val canH = inBounds(x - 1, y) && inBounds(x + 1, y)
                        val canV = inBounds(x, y - 1) && inBounds(x, y + 1)

                        val gh: Double; val dh: Double
                        if (canH) {
                            gh = (gL + gR) / 2.0 + (2.0 * p - pL2 - pR2) / 4.0
                            dh = (gL - gR).absoluteValue + (p - pL2).absoluteValue + (p - pR2).absoluteValue
                        } else {
                            gh = if (inBounds(x - 1, y)) gL else gR
                            dh = Double.MAX_VALUE / 2.0
                        }

                        val gv: Double; val dv: Double
                        if (canV) {
                            gv = (gU + gD) / 2.0 + (2.0 * p - pU2 - pD2) / 4.0
                            dv = (gU - gD).absoluteValue + (p - pU2).absoluteValue + (p - pD2).absoluteValue
                        } else {
                            gv = if (inBounds(x, y - 1)) gU else gD
                            dv = Double.MAX_VALUE / 2.0
                        }

                        val wh = 1.0 / (1.0 + dh)
                        val wv = 1.0 / (1.0 + dv)
                        gPlane[y][x] = clamp((wh * gh + wv * gv) / (wh + wv), 0.0, 1.0)
                    }
                }
            }

            // Step 3: Interpolate R/B at Green sites using hue-transit (ratio) correction
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (!isRSite(x, y) && !isBSite(x, y)) {
                        val g = gPlane[y][x]
                        if (y % 2 == rY) { // Green in R-row: R is horizontal, B is vertical
                            val rRatios = mutableListOf<Double>()
                            for ((nx, ny) in listOf(x - 1 to y, x + 1 to y)) {
                                if (inBounds(nx, ny) && isRSite(nx, ny)) {
                                    val gAtR = gPlane[ny][nx]
                                    if (gAtR > 1e-10) rRatios.add(rPlane[ny][nx] / gAtR)
                                }
                            }
                            rPlane[y][x] = clamp(g * (if (rRatios.isEmpty()) 1.0 else rRatios.average()), 0.0, 1.0)

                            val bRatios = mutableListOf<Double>()
                            for ((nx, ny) in listOf(x to y - 1, x to y + 1)) {
                                if (inBounds(nx, ny) && isBSite(nx, ny)) {
                                    val gAtB = gPlane[ny][nx]
                                    if (gAtB > 1e-10) bRatios.add(bPlane[ny][nx] / gAtB)
                                }
                            }
                            bPlane[y][x] = clamp(g * (if (bRatios.isEmpty()) 1.0 else bRatios.average()), 0.0, 1.0)
                        } else { // Green in B-row: B is horizontal, R is vertical
                            val bRatios = mutableListOf<Double>()
                            for ((nx, ny) in listOf(x - 1 to y, x + 1 to y)) {
                                if (inBounds(nx, ny) && isBSite(nx, ny)) {
                                    val gAtB = gPlane[ny][nx]
                                    if (gAtB > 1e-10) bRatios.add(bPlane[ny][nx] / gAtB)
                                }
                            }
                            bPlane[y][x] = clamp(g * (if (bRatios.isEmpty()) 1.0 else bRatios.average()), 0.0, 1.0)

                            val rRatios = mutableListOf<Double>()
                            for ((nx, ny) in listOf(x to y - 1, x to y + 1)) {
                                if (inBounds(nx, ny) && isRSite(nx, ny)) {
                                    val gAtR = gPlane[ny][nx]
                                    if (gAtR > 1e-10) rRatios.add(rPlane[ny][nx] / gAtR)
                                }
                            }
                            rPlane[y][x] = clamp(g * (if (rRatios.isEmpty()) 1.0 else rRatios.average()), 0.0, 1.0)
                        }
                    }
                }
            }

            // Step 4: Interpolate opposite channel at R/B sites via diagonal hue-transit
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val g = gPlane[y][x]
                    if (isRSite(x, y)) {
                        val bRatios = mutableListOf<Double>()
                        for ((nx, ny) in listOf(x - 1 to y - 1, x + 1 to y - 1, x - 1 to y + 1, x + 1 to y + 1)) {
                            if (inBounds(nx, ny) && isBSite(nx, ny)) {
                                val gAtB = gPlane[ny][nx]
                                if (gAtB > 1e-10) bRatios.add(bPlane[ny][nx] / gAtB)
                            }
                        }
                        bPlane[y][x] = clamp(g * (if (bRatios.isEmpty()) 1.0 else bRatios.average()), 0.0, 1.0)
                    } else if (isBSite(x, y)) {
                        val rRatios = mutableListOf<Double>()
                        for ((nx, ny) in listOf(x - 1 to y - 1, x + 1 to y - 1, x - 1 to y + 1, x + 1 to y + 1)) {
                            if (inBounds(nx, ny) && isRSite(nx, ny)) {
                                val gAtR = gPlane[ny][nx]
                                if (gAtR > 1e-10) rRatios.add(rPlane[ny][nx] / gAtR)
                            }
                        }
                        rPlane[y][x] = clamp(g * (if (rRatios.isEmpty()) 1.0 else rRatios.average()), 0.0, 1.0)
                    }
                }
            }

            // Copy to output
            for (y in 0 until height) {
                for (x in 0 until width) {
                    redMatrixXY[x, y] = rPlane[y][x] * red
                    greenMatrixXY[x, y] = gPlane[y][x] * green
                    blueMatrixXY[x, y] = bPlane[y][x] * blue
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
): Set<PointXY> {
    val matrixXY = this.asXY()

    val width = matrixXY.width
    val height = matrixXY.height
    val result = mutableSetOf<PointXY>()
    val values = mutableListOf<Double>()

    for (y in 0 until height) {
        for (x in 0 until width) {
            values.clear()
            val value = matrixXY[x, y]

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

            val sigma = values.stddev()
            val sigmaCorrected = max(sigma, minSigma)
            val steepGradientsCount = values.count {
                (value - it).absoluteValue > sigmaCorrected * gradientThresholdFactor
            }

            if (steepGradientsCount >= values.size * steepCountThresholdFactor) {
                result.add(PointXY(x, y))
            }
        }
    }

    return result
}

fun Matrix.cleanupBayerBadPixels(
    badpixelCoords: Set<PointXY> = emptySet()
): Matrix {
    val mosaic = copy()
    val mosaicBoundedXY = mosaic.asBoundedXY()

    for (badpixelCoord in badpixelCoords) {
        val x = badpixelCoord.x
        val y = badpixelCoord.y

        val surroundingValues = mutableListOf<Double>()
        for (dy in -2..2 step 2) {
            for (dx in -2..2 step 2) {
                if ((dx != 0 && dy != 0) && mosaicBoundedXY.isInBounds(x + dx, y + dy) && !badpixelCoords.contains(PointXY(x + dx, y + dy))) {
                    surroundingValues.add(mosaicBoundedXY[x + dx, y + dy]!!)
                }
            }
        }

        mosaicBoundedXY[x, y] = surroundingValues.median()
    }
    return mosaic
}
