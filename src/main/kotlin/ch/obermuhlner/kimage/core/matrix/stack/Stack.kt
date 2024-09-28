package ch.obermuhlner.kimage.core.matrix.stack

import ch.obermuhlner.kimage.core.matrix.Matrix
import kotlin.math.max

fun Matrix.max(other: Matrix): Matrix {
    val m = create()
    m.applyEach { row, col, value ->
        max(value, other[row, col])
    }
    return m
}
