package ch.obermuhlner.kimage.core.matrix.erode

import ch.obermuhlner.kimage.core.matrix.Matrix

fun Matrix.dilate(kernelRadius: Int = 1): Matrix {
    val m = create(rows, cols)

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            var maxValue = -Double.MAX_VALUE

            for (kRow in -kernelRadius..kernelRadius) {
                for (kCol in -kernelRadius..kernelRadius) {
                    maxValue = maxOf(maxValue, this[row + kRow, col + kCol])
                }
            }

            m[row, col] = maxValue
        }
    }

    return m
}
