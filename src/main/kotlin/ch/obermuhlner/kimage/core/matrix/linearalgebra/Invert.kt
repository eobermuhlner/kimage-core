package ch.obermuhlner.kimage.core.matrix.linearalgebra

import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.check.checkRow
import ch.obermuhlner.kimage.core.matrix.check.checkSquare
import kotlin.math.abs

fun Matrix.invert(): Matrix? {
    //return this.invertUsingDeterminant()
    return this.invertUsingGaussianElimination()
}

fun Matrix.invertUsingGaussianElimination(): Matrix? {
    checkSquare()

    val m = create(rows, cols * 2)
    m.set(this, 0, 0)
    m.set(DoubleMatrix.identity(rows), 0, cols)

    val invertible = m.gaussianElimination()
    if (!invertible) {
        return null
    }

    return m.crop(0, cols, rows, cols)
}

fun Matrix.gaussianElimination(reducedEchelonForm: Boolean = true): Boolean {
    val EPSILON = 1e-10
    var pivotRow = 0
    var pivotCol = 0

    while (pivotRow < rows && pivotCol < cols) {
        // Find the pivot row
        var maxRow = pivotRow
        var maxVal = abs(this[maxRow, pivotCol])
        for (row in pivotRow + 1 until rows) {
            val valRow = abs(this[row, pivotCol])
            if (valRow > maxVal && valRow > EPSILON) {
                maxVal = valRow
                maxRow = row
            }
        }
        if (maxVal < EPSILON) {
            // Cannot find a valid pivot in this column, move to the next column
            pivotCol++
            continue
        }
        // Swap the current row with the pivot row
        swapRow(pivotRow, maxRow)

        // Normalize the pivot row
        val pivotDivisor = this[pivotRow, pivotCol]
        if (abs(pivotDivisor) < EPSILON) return false
        for (col in 0 until cols) {
            this[pivotRow, col] /= pivotDivisor
        }

        // Eliminate entries in other rows
        for (row in 0 until rows) {
            if (row != pivotRow) {
                val factor = this[row, pivotCol]
                if (abs(factor) > EPSILON) {
                    for (col in 0 until cols) {
                        this[row, col] -= this[pivotRow, col] * factor
                    }
                }
            }
        }
        pivotCol++
        pivotRow++
    }

    // After elimination, check if the left side is an identity matrix
    for (row in 0 until rows) {
        for (col in 0 until cols / 2) { // Assuming width is doubled for augmented matrix
            val expected = if (col == row) 1.0 else 0.0
            if (abs(this[row, col] - expected) > EPSILON) {
                return false // Matrix is singular
            }
        }
    }
    return true // Matrix is invertible
}

private fun Matrix.swapRow(row1: Int, row2: Int) {
    checkRow("row1", row1)
    checkRow("row2", row2)

    if (row1 == row2) {
        return
    }
    for (col in 0 until cols) {
        val tmp = this[row1, col]
        this[row1, col] = this[row2, col]
        this[row2, col] = tmp
    }
}

fun Matrix.invertUsingDeterminant(): Matrix? {
    val determinant = determinant()
    if (determinant == 0.0) {
        return null
    }

    val adjoint = create(rows, cols)

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            adjoint[col, row] = cofactor(row, col)
        }
    }

    return adjoint * (1.0 / determinant)
}
