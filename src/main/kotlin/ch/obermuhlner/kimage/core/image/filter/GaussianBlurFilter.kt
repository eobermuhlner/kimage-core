package ch.obermuhlner.kimage.filter

import ch.obermuhlner.kimage.core.image.filter.MatrixImageFilter
import ch.obermuhlner.kimage.core.matrix.Matrix
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sqrt

class GaussianBlurFilter(
    private val radius: Int
) : MatrixImageFilter(
    { _, source ->
        blur(source, radius)
    }
) {

    private class SwapMatrix(var source: Matrix, var target: Matrix) {
        fun swap() {
            val tmp: Matrix = source
            source = target
            target = tmp
        }
    }

    companion object {
        fun blur(source: Matrix, radius: Int): Matrix {
            val temp = SwapMatrix(source.create(), source.create())

            temp.source.set(source)

            val boxSizes = boxSizesForGauss(radius.toDouble(), 3)
            for (boxSize in boxSizes) {
                val boxRadius = (ceil((boxSize - 1) / 2) + 0.5).toInt()
                boxBlur(temp.source, temp.target, boxRadius)
                temp.swap()
            }

            return temp.source
        }

        private fun boxBlur(source: Matrix, target: Matrix, boxRadius: Int) {
            val tempMatrix = source.create()
            boxBlurHorizontal(source, tempMatrix, boxRadius)
            boxBlurVertical(tempMatrix, target, boxRadius)
        }

        private fun boxBlurHorizontal(source: Matrix, target: Matrix, boxRadius: Int) {
            val kernelSize = boxRadius + boxRadius + 1

            for (row in 0 until source.rows) {
                val first = source[row, 0]
                val last = source[row, source.cols - 1]

                var leftCol = 0
                var rightCol = boxRadius
                var targetCol = 0

                var sum = first * (boxRadius + 1)
                for (col in 0 until boxRadius) {
                    sum += source[row, col]
                }
                for (col in 0..boxRadius) {
                    sum += source[row, rightCol++] - first
                    target[row, targetCol++] = sum / kernelSize
                }
                for (col in boxRadius + 1 until source.cols - boxRadius) {
                    sum += source[row, rightCol++]
                    sum -= source[row, leftCol++]
                    target[row, targetCol++] = sum / kernelSize
                }
                for (col in source.cols - boxRadius until source.cols) {
                    sum += last
                    sum -= source[row, leftCol++]
                    target[row, targetCol++] = sum / kernelSize
                }
            }
        }

        private fun boxBlurVertical(source: Matrix, target: Matrix, boxRadius: Int) {
            val kernelSize = boxRadius + boxRadius + 1

            for (col in 0 until source.cols) {
                val first = source[0, col]
                val last = source[source.rows - 1, col]

                var upperRow = 0
                var lowerRow = boxRadius
                var targetRow = 0

                var sum = first * (boxRadius + 1)
                for (row in 0 until boxRadius) {
                    sum += source[row, col]
                }
                for (row in 0..boxRadius) {
                    sum += source[lowerRow++, col] - first
                    target[targetRow++, col] = sum / kernelSize
                }
                for (row in boxRadius + 1 until source.rows - boxRadius) {
                    sum += source[lowerRow++, col]
                    sum -= source[upperRow++, col]

                    target[targetRow++, col] = sum / kernelSize
                }
                for (row in source.rows - boxRadius until source.rows) {
                    sum += last
                    sum -= source[upperRow++, col]

                    target[targetRow++, col] = sum / kernelSize
                }
            }
        }

        private fun boxSizesForGauss(sigma: Double, n: Int): DoubleArray {
            val wIdeal = sqrt((12 * sigma * sigma / n) + 1)
            var wl = floor(wIdeal)
            if (wl % 2 == 0.0) wl--
            val wu: Double = wl + 2
            val mIdeal: Double = ((12 * sigma * sigma) - (n * wl * wl) - (4 * n * wl) - (3 * n)) / (-4 * wl - 4)
            val m: Long = mIdeal.roundToLong()
            val sizes = DoubleArray(n)
            for (i in 0 until n) {
                sizes[i] = if (i < m) wl else wu
            }
            return sizes
        }
    }
}
