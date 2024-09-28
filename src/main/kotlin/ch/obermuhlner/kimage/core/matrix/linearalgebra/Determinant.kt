package ch.obermuhlner.kimage.core.matrix.linearalgebra

import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.check.checkSquare

fun Matrix.determinant(): Double {
    checkSquare()

    // Base case for 2x2 matrix
    if (rows == 2) {
        return this[0, 0] * this[1, 1] - this[0, 1] * this[1, 0]
    }

    // Recursive case for larger matrices
    var det = 0.0
    for (col in 0 until cols) {
        det += this[0, col] * cofactor(0, col)
    }
    return det
}

fun Matrix.cofactor(row: Int, col: Int): Double {
    val minor = subMatrix(row, col)
    return (if ((row + col) % 2 == 0) 1.0 else -1.0) * minor.determinant()
}

fun Matrix.subMatrix(excludeRow: Int, excludeCol: Int): Matrix {
    val m = create(rows - 1, cols - 1)
    var destRow = 0
    for (row in 0 until rows) {
        if (row == excludeRow) continue
        var destCol = 0
        for (col in 0 until cols) {
            if (col == excludeCol) continue
            m[destRow, destCol] = this[row, col]
            destCol++
        }
        destRow++
    }
    return m
}
