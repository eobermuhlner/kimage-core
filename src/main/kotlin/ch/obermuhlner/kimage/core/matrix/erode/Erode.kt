package ch.obermuhlner.kimage.core.matrix.erode

import ch.obermuhlner.kimage.core.matrix.Matrix

fun Matrix.erode(kernelRadius: Int = 1): Matrix {
    val m = create(rows, cols)

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            var minValue = Double.MAX_VALUE

            for (kRow in -kernelRadius..kernelRadius) {
                for (kCol in -kernelRadius..kernelRadius) {
                    minValue = minOf(minValue, this[row+kRow, col+kCol])
                }
            }

            m[row, col] = minValue
        }
    }

    return m
}

fun Matrix.erode(kernel: Matrix, strength: Double = 1.0, repeat: Int = 1): Matrix {
    var m1 = create(rows, cols)
    m1.applyEach { row, col, _ ->  this[row, col] }
    var m2 = create(rows, cols)

    val kernelCenterRow = kernel.rows / 2
    val kernelCenterCol = kernel.cols / 2

    for (i in 0 until repeat) {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                var minValue = Double.MAX_VALUE

                for (kRow in 0..kernel.rows) {
                    for (kCol in 0..kernel.cols) {
                        if (kernel[kRow, kCol] >= 1.0) {
                            minValue = minOf(minValue, m1[row + kRow - kernelCenterRow, col + kCol - kernelCenterCol])
                        }
                    }
                }

                m2[row, col] = m1[row, col] * (1.0 - strength) + minValue * strength
            }
        }

        val tmp = m1
        m1 = m2
        m2 = tmp
    }

    return m1
}
