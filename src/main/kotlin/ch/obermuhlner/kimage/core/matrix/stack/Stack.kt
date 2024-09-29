package ch.obermuhlner.kimage.core.matrix.stack

import ch.obermuhlner.kimage.core.matrix.Matrix
import kotlin.math.max

fun max(firstMatrix: Matrix, vararg otherMatrices: Matrix): Matrix {
    val m = firstMatrix.create()

    m.applyEach { row, col, _ ->
        var maxValue = firstMatrix[row, col]
        for (matrix in otherMatrices) {
            maxValue = max(maxValue, matrix[row, col])
        }
        maxValue
    }

    return m
}