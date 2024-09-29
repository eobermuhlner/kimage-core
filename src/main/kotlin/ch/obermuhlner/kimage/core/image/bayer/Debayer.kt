package ch.obermuhlner.kimage.core.image.bayer

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.values.asXY

enum class DebayerInterpolation {
    None,
    SuperPixel,
    SuperPixelHalf,
    Nearest,
    Bilinear
}
fun Image.debayer(
    pattern: BayerPattern = BayerPattern.RGGB,
    interpolation: DebayerInterpolation = DebayerInterpolation.Bilinear,
    red: Double = 1.0,
    green: Double = 1.0,
    blue: Double = 1.0,
    badpixelCoords: Set<Pair<Int, Int>> = emptySet()
): MatrixImage {
    val (width, height) = when (interpolation) {
        DebayerInterpolation.SuperPixelHalf -> Pair(this.width / 2, this.height / 2)
        else -> Pair(this.width, this.height)
    }

    val (rX, rY) = when (pattern) {
        BayerPattern.RGGB -> Pair(0, 0)
        BayerPattern.BGGR -> Pair(1, 1)
        BayerPattern.GBRG -> Pair(0, 1)
        BayerPattern.GRBG -> Pair(0, 1)
        else -> throw java.lang.IllegalArgumentException("Unknown pattern: $pattern")
    }
    val (g1X, g1Y) = when (pattern) {
        BayerPattern.RGGB -> Pair(0, 1)
        BayerPattern.BGGR -> Pair(0, 1)
        BayerPattern.GBRG -> Pair(0, 0)
        BayerPattern.GRBG -> Pair(0, 0)
        else -> throw java.lang.IllegalArgumentException("Unknown pattern: $pattern")
    }
    val (g2X, g2Y) = when (pattern) {
        BayerPattern.RGGB -> Pair(1, 0)
        BayerPattern.BGGR -> Pair(1, 0)
        BayerPattern.GBRG -> Pair(1, 1)
        BayerPattern.GRBG -> Pair(1, 1)
        else -> throw java.lang.IllegalArgumentException("Unknown pattern: $pattern")
    }
    val (bX, bY) = when (pattern) {
        BayerPattern.RGGB -> Pair(1, 1)
        BayerPattern.BGGR -> Pair(0, 0)
        BayerPattern.GBRG -> Pair(0, 1)
        BayerPattern.GRBG -> Pair(1, 0)
        else -> throw java.lang.IllegalArgumentException("Unknown pattern: $pattern")
    }

    val mosaic = this[Channel.Gray].asXY()

    for (badpixelCoord in badpixelCoords) {
        val x = badpixelCoord.first
        val y = badpixelCoord.second

        val surroundingValues = mutableListOf<Double>()
        for (dy in -2..2 step 4) {
            for (dx in -2..2 step 4) {
                if (mosaic.isInBounds(x + dx, y + dy) && !badpixelCoords.contains(Pair(x + dx, y + dy))) {
                    surroundingValues.add(mosaic[x + dx, y + dy])
                }
            }
        }

        mosaic[x, y] = surroundingValues.median()
    }

    val mosaicRedMatrix = DoubleMatrix(mosaic.width / 2, mosaic.height / 2)
    val mosaicGreen1Matrix = DoubleMatrix(mosaic.width / 2, mosaic.height / 2)
    val mosaicGreen2Matrix = DoubleMatrix(mosaic.width / 2, mosaic.height / 2)
    val mosaicBlueMatrix = DoubleMatrix(mosaic.width / 2, mosaic.height / 2)
    val mosaicGrayMatrix = DoubleMatrix(mosaic.width / 2, mosaic.height / 2)

    for (y in 0 until this.height step 2) {
        for (x in 0 until this.width step 2) {
            val r = mosaic[x+rX, y+rY]
            val g1 = mosaic[x+g1X, y+g1Y]
            val g2 = mosaic[x+g2X, y+g2Y]
            val b = mosaic[x+bX, y+bY]
            val gray = (r + r + g1 + g2 + b + b) / 6

            mosaicRedMatrix[x/2, y/2] = r
            mosaicGreen1Matrix[x/2, y/2] = g1
            mosaicGreen2Matrix[x/2, y/2] = g2
            mosaicBlueMatrix[x/2, y/2] = b
            mosaicGrayMatrix[x/2, y/2] = gray
        }
    }

    val redFactor = 1.0 / red
    val greenFactor = 1.0 / green
    val blueFactor = 1.0 / blue

    val redOffset = 0.0
    val greenOffset = 0.0
    val blueOffset = 0.0

    mosaicRedMatrix.applyEach { v -> (v - redOffset) * redFactor  }
    mosaicGreen1Matrix.applyEach { v -> (v - greenOffset) * greenFactor  }
    mosaicGreen2Matrix.applyEach { v -> (v - greenOffset) * greenFactor  }
    mosaicBlueMatrix.applyEach { v -> (v - blueOffset) * blueFactor  }

    val redMatrix = DoubleMatrix.matrixOf(width, height)
    val greenMatrix = DoubleMatrix.matrixOf(width, height)
    val blueMatrix = DoubleMatrix.matrixOf(width, height)

    when (interpolation) {
        DebayerInterpolation.SuperPixelHalf, DebayerInterpolation.SuperPixel -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = mosaicRedMatrix[x, y]
                    val g1 = mosaicGreen1Matrix[x, y]
                    val g2 = mosaicGreen2Matrix[x, y]
                    val b = mosaicBlueMatrix[x, y]

                    redMatrix[x, y] = r
                    greenMatrix[x, y] = (g1+g2)/2
                    blueMatrix[x, y] = b
                }
            }
        }
        DebayerInterpolation.None -> {
            for (y in 0 until height step 2) {
                for (x in 0 until width step 2) {
                    val r = mosaicRedMatrix[x/2, y/2]
                    val g1 = mosaicGreen1Matrix[x/2, y/2]
                    val g2 = mosaicGreen2Matrix[x/2, y/2]
                    val b = mosaicBlueMatrix[x/2, y/2]

                    redMatrix[x+rX, y+rY] = r
                    greenMatrix[x+g1X, y+g1Y] = g1
                    greenMatrix[x+g2X, y+g2Y] = g2
                    blueMatrix[x+bX, y+bY] = b
                }
            }
        }
        DebayerInterpolation.Nearest -> {
            for (y in 0 until height step 2) {
                for (x in 0 until width step 2) {
                    val r = mosaicRedMatrix[x / 2, y / 2]
                    val g1 = mosaicGreen1Matrix[x / 2, y / 2]
                    val g2 = mosaicGreen2Matrix[x / 2, y / 2]
                    val b = mosaicBlueMatrix[x / 2, y / 2]

                    redMatrix[x + 0, y + 0] = r
                    redMatrix[x + 1, y + 0] = r
                    redMatrix[x + 0, y + 1] = r
                    redMatrix[x + 1, y + 1] = r
                    blueMatrix[x + 0, y + 0] = b
                    blueMatrix[x + 1, y + 0] = b
                    blueMatrix[x + 0, y + 1] = b
                    blueMatrix[x + 1, y + 1] = b
                    greenMatrix[x + 0, y + 0] = g1
                    greenMatrix[x + 1, y + 0] = g1
                    greenMatrix[x + 0, y + 1] = g2
                    greenMatrix[x + 1, y + 1] = g2
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
                        r = mosaic[x, y]
                        g = (mosaic[x - 1, y] + mosaic[x + 1, y] + mosaic[x,
                            y - 1
                        ] + mosaic[x, y + 1]) / 4
                        b = (mosaic[x - 1, y - 1] + mosaic[x - 1,
                            y + 1
                        ] + mosaic[x + 1, y - 1] + mosaic[x + 1, y + 1]) / 4
                    } else if (dx == bX && dy == bY) {
                        r = (mosaic[x - 1, y - 1] + mosaic[x - 1, y + 1] + mosaic[x + 1,
                            y - 1
                        ] + mosaic[x + 1, y + 1]) / 4
                        g = (mosaic[x - 1, y] + mosaic[x + 1, y] + mosaic[x,
                            y - 1
                        ] + mosaic[x, y + 1]) / 4
                        b = mosaic[x, y]
                    } else {
                        g = mosaic[x, y]
                        if ((x - 1) % 2 == rX) {
                            r = (mosaic[x - 1, y] + mosaic[x + 1, y]) / 2
                            b = (mosaic[x, y - 1] + mosaic[x, y + 1]) / 2
                        } else {
                            r = (mosaic[x, y - 1] + mosaic[x, y + 1]) / 2
                            b = (mosaic[x - 1, y] + mosaic[x + 1, y]) / 2
                        }
                    }

                    redMatrix[x, y] = (r - redOffset) * redFactor
                    greenMatrix[x, y] = (g - greenOffset) * greenFactor
                    blueMatrix[x, y] = (b - blueOffset) * blueFactor
                }
            }
        }
    }

    return MatrixImage(width, height,
        Channel.Red to redMatrix,
        Channel.Green to greenMatrix,
        Channel.Blue to blueMatrix)
}
