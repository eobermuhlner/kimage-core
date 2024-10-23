package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.matrix.Matrix

class AverageFilter(
    private val radius: Int,
    private val shape: Shape = Shape.Square
) : MatrixImageFilter(
    { _, source ->
        averageMatrix(source, radius, shape)
    }
) {

    companion object {
        fun averageMatrix(source: Matrix, radius: Int, shape: Shape): Matrix {
            val target = source.create()
            val kernelSize = radius+radius+1
            val n = kernelSize * kernelSize
            val values = DoubleArray(n)

            for (row in 0 until source.rows) {
                for (col in 0 until source.cols) {
                    target[row, col] = average(source, row, col, radius, shape, values)
                }
            }
            return target
        }

        private fun average(matrix: Matrix, row: Int, col: Int, radius: Int, shape: Shape, values: DoubleArray): Double {
            var n = 0

            var sum = 0.0
            when (shape) {
                Shape.Cross -> {
                    for (dr in -radius..radius) {
                        sum += matrix[row + dr, col]
                        n++
                    }
                    for (dc in -radius until 0) {
                        sum += matrix[row, col + dc]
                        n++
                    }
                    for (dc in 1 .. radius) {
                        sum += matrix[row, col + dc]
                        n++
                    }
                }
                Shape.DiagonalCross -> {
                    values[n++] = matrix[row, col]
                    for (r in 1..radius) {
                        sum += matrix[row + r, col + r]
                        sum += matrix[row - r, col + r]
                        sum += matrix[row + r, col - r]
                        sum += matrix[row - r, col - r]
                        n += 4
                    }
                }
                Shape.Star -> {
                    values[n++] = matrix[row, col]
                    for (r in 1..radius) {
                        sum += matrix[row, col + r]
                        sum += matrix[row, col - r]
                        sum += matrix[row + r, col]
                        sum += matrix[row - r, col]

                        sum += matrix[row + r, col + r]
                        sum += matrix[row - r, col + r]
                        sum += matrix[row + r, col - r]
                        sum += matrix[row - r, col - r]
                        n += 8
                    }
                }
                else -> {
                    val horizontalRadius = shape.horizontalRadius(radius)
                    val verticalRadius = shape.verticalRadius(radius)
                    for (dr in -verticalRadius .. verticalRadius) {
                        for (dc in -horizontalRadius .. horizontalRadius) {
                            if (shape.isInside(dc, dr, radius)) {
                                sum += matrix[row + dr, col + dc]
                                n++
                            }
                        }
                    }
                }
            }

            return sum / n
        }
    }
}