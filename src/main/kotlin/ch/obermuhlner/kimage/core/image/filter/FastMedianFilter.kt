package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.math.Histogram
import ch.obermuhlner.kimage.core.matrix.Matrix

class FastMedianFilter(
    private val radius: Int,
    recursive: Boolean = false
) : MatrixImageFilter(
    { _, source ->
        fastMedianMatrix(source, radius, recursive)
    }
) {

    companion object {
        fun fastMedianMatrix(source: Matrix, radius: Int, recursive: Boolean = false): Matrix {
            val sourceCopy = if (recursive) source.copy() else source
            val target = source.create()
            val kernelSize = radius+radius+1

            val histogram = Histogram()

            histogram.add(sourceCopy, -radius, -radius, kernelSize, kernelSize)

            for (row in 0 until source.rows) {
                val forward = row % 2 == 0
                val colRange = if (forward) 0 until source.cols else source.cols-1 downTo 0
                for (col in colRange) {
                    val medianValue = histogram.estimateMedian()
                    if (recursive) {
                        histogram.remove(sourceCopy[row, col])
                        sourceCopy[row, col] = medianValue
                        histogram.add(medianValue)
                    }
                    target[row, col] = medianValue
                    if (forward) {
                        if (col < source.cols - 1) {
                            // move right
                            histogram.remove(sourceCopy, row-radius, col-radius, kernelSize, 1)
                            histogram.add(sourceCopy, row-radius, col+radius+1, kernelSize, 1)
                        } else {
                            // move down
                            histogram.remove(sourceCopy, row-radius, col-radius, 1, kernelSize)
                            histogram.add(sourceCopy, row+radius+1, col-radius, 1, kernelSize)
                        }
                    } else {
                        if (col > 0) {
                            // move left
                            histogram.remove(sourceCopy, row-radius, col+radius, kernelSize, 1)
                            histogram.add(sourceCopy, row-radius, col-radius-1, kernelSize, 1)
                        } else {
                            // move down
                            histogram.remove(sourceCopy, row-radius, col-radius, 1, kernelSize)
                            histogram.add(sourceCopy, row+radius+1, col-radius, 1, kernelSize)
                        }
                    }
                }
            }
            return target
        }
    }
}