package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.matrix.Matrix


class FastMedianPixelFilter(
    private val radius: Int,
    private val medianChannel: Channel = Channel.Luminance
) : Filter<Image> {

    override fun filter(source: Image): Image {
        val matrix = source[medianChannel]

        return fastMedianPixel(matrix, source, radius)
    }

    class Histogram(private val binCount: Int = 256) {
        private val bins = IntArray(binCount)
        private val sampleRow = IntArray(binCount)
        private val sampleCol = IntArray(binCount)
        private var n = 0

        fun clear() {
            for (i in bins.indices) {
                bins[i] = 0
            }
            n = 0
        }

        fun add(matrix: Matrix, rowStart: Int, colStart: Int, rows: Int, cols: Int) {
            for (row in rowStart until rowStart+rows) {
                for (col in colStart until colStart+cols) {
                    add(matrix[row, col], row, col)
                }
            }
        }

        fun remove(matrix: Matrix, rowStart: Int, colStart: Int, rows: Int, cols: Int) {
            for (row in rowStart until rowStart+rows) {
                for (col in colStart until colStart+cols) {
                    remove(matrix[row, col])
                }
            }
        }

        fun add(value: Double, row: Int, col: Int) {
            val index = clamp((value * binCount).toInt(), 0, binCount - 1)
            bins[index]++
            sampleCol[index] = col
            sampleRow[index] = row
            n++
        }

        fun remove(value: Double) {
            val index = clamp((value * binCount).toInt(), 0, binCount - 1)
            bins[index]--
            n--
        }

        fun estimateMean(): Double {
            var sum = 0.0
            for (i in bins.indices) {
                sum += bins[i] * (i.toDouble() + 0.5) / (binCount - 1).toDouble()
            }
            return sum / n
        }

        fun estimateMedian(): Double {
            val nHalf = n / 2
            var cumulativeN = 0
            for (i in bins.indices) {
                if (cumulativeN + bins[i] >= nHalf) {
                    val lowerLimit = i.toDouble() / (binCount - 1).toDouble()
                    val width = 1.0 / (binCount - 1).toDouble()
                    return lowerLimit + (nHalf - cumulativeN) / bins[i].toDouble() * width
                }
                cumulativeN += bins[i]
            }
            return 0.0
        }

        fun estimateMedianPixelIndex(): Pair<Int, Int> {
            val nHalf = n / 2
            var cumulativeN = 0
            for (i in bins.indices) {
                if (cumulativeN + bins[i] >= nHalf) {
                    return Pair(sampleCol[i], sampleRow[i])
                }
                cumulativeN += bins[i]
            }
            return Pair(0, 0)
        }
    }

    companion object {
        fun fastMedianPixel(matrix: Matrix, image: Image, radius: Int): Image {
            val target = MatrixImage(image.width, image.height, image.channels)
            val kernelSize = radius+radius+1

            val histogram = Histogram()
            val pixel = DoubleArray(image.channels.size)

            histogram.add(matrix, -radius, -radius, kernelSize, kernelSize)

            for (row in 0 until matrix.rows) {
                val forward = row % 2 == 0
                val colRange = if (forward) 0 until matrix.cols else matrix.cols-1 downTo 0
                for (col in colRange) {
                    val medianPixelXY = histogram.estimateMedianPixelIndex()
                    image.getPixel(medianPixelXY.first, medianPixelXY.second, pixel)
                    target.setPixel(col, row, pixel)

                    if (forward) {
                        if (col < matrix.cols - 1) {
                            // move right
                            histogram.remove(matrix, row-radius, col-radius, kernelSize, 1)
                            histogram.add(matrix, row-radius, col+radius+1, kernelSize, 1)
                        } else {
                            // move down
                            histogram.remove(matrix, row-radius, col-radius, 1, kernelSize)
                            histogram.add(matrix, row+radius+1, col-radius, 1, kernelSize)
                        }
                    } else {
                        if (col > 0) {
                            // move left
                            histogram.remove(matrix, row-radius, col+radius, kernelSize, 1)
                            histogram.add(matrix, row-radius, col-radius-1, kernelSize, 1)
                        } else {
                            // move down
                            histogram.remove(matrix, row-radius, col-radius, 1, kernelSize)
                            histogram.add(matrix, row+radius+1, col-radius, 1, kernelSize)
                        }
                    }
                }
            }
            return target
        }
    }
}