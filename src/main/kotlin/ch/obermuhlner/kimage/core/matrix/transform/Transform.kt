package ch.obermuhlner.kimage.core.matrix.transform

import ch.obermuhlner.kimage.core.matrix.Matrix

fun Matrix.rotateLeft(): Matrix {
    val m = create(cols, rows)

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            m[cols - col - 1, row] = this[row, col]
        }
    }

    return m
}

fun Matrix.rotateRight(): Matrix {
    val m = create(cols, rows)

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            m[col, rows - row - 1] = this[row, col]
        }
    }

    return m
}

fun Matrix.mirrorX(): Matrix {
    val m = create(rows, cols)

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            m[row, cols - col - 1] = this[row, col]
        }
    }

    return m
}

fun Matrix.mirrorY(): Matrix {
    val m = create(rows, cols)

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            m[rows - row - 1, col] = this[row, col]
        }
    }

    return m
}
