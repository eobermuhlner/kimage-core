package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.matrix.Matrix
import java.util.*

class MedianFilter(
    private val radius: Int,
    private val shape: Shape = Shape.Square,
    recursive: Boolean = false
) : MatrixImageFilter(
    { _, source ->
        medianMatrix(source, radius, shape, recursive)
    }
) {

    companion object {
        fun medianMatrix(source: Matrix, radius: Int, shape: Shape, recursive: Boolean = false): Matrix {
            val sourceCopy = if (recursive) source.copy() else source
            val target = source.create()
            val kernelSize = radius+radius+1
            val n = kernelSize * kernelSize
            val values = DoubleArray(n)

            for (row in 0 until source.rows) {
                for (col in 0 until source.cols) {
                    val medianValue = median(sourceCopy, row, col, radius, shape, values)
                    if (recursive) {
                        sourceCopy[row, col] = medianValue
                    }
                    target[row, col] = medianValue
                }
            }
            return target
        }

        private fun median(matrix: Matrix, row: Int, col: Int, radius: Int, shape: Shape, values: DoubleArray): Double {
            var n = 0

            when (shape) {
                Shape.Cross -> {
                    for (dr in -radius..radius) {
                        values[n++] = matrix[row + dr, col]
                    }
                    for (dc in -radius until 0) {
                        values[n++] = matrix[row, col + dc]
                    }
                    for (dc in 1 .. radius) {
                        values[n++] = matrix[row, col + dc]
                    }
                }
                Shape.DiagonalCross -> {
                    values[n++] = matrix[row, col]
                    for (r in 1..radius) {
                        values[n++] = matrix[row + r, col + r]
                        values[n++] = matrix[row + r, col - r]
                        values[n++] = matrix[row - r, col + r]
                        values[n++] = matrix[row - r, col - r]
                    }
                }
                Shape.Star -> {
                    values[n++] = matrix[col, row]
                    for (r in 1..radius) {
                        values[n++] = matrix[row + r, col]
                        values[n++] = matrix[row - r, col]
                        values[n++] = matrix[row, col + r]
                        values[n++] = matrix[row, col - r]

                        values[n++] = matrix[row + r, col + r]
                        values[n++] = matrix[row + r, col - r]
                        values[n++] = matrix[row - r, col + r]
                        values[n++] = matrix[row - r, col - r]
                    }
                }
                else -> {
                    val horizontalRadius = shape.horizontalRadius(radius)
                    val verticalRadius = shape.verticalRadius(radius)
                    for (dr in -verticalRadius .. verticalRadius) {
                        for (dc in -horizontalRadius .. horizontalRadius) {
                            if (shape.isInside(dr, dc, radius)) {
                                values[n++] = matrix[row + dr, col + dc]
                            }
                        }
                    }
                }
            }

            Arrays.sort(values,0, n)

            return if (n % 2 == 0) {
                (values[n/2] + values[n/2+1]) / 2
            } else {
                values[n/2]
            }
        }
    }
}