package ch.obermuhlner.kimage.core.matrix.values

import ch.obermuhlner.kimage.core.matrix.Matrix

fun Matrix.values(): Iterable<Double> = object : Iterable<Double> {
    override operator fun iterator(): Iterator<Double> {
        return object : Iterator<Double> {
            var index = 0
            override fun hasNext(): Boolean = index < size
            override fun next(): Double = get(index++)
        }
    }
}

open class MatrixXY(val matrix: Matrix) {
    val width: Int
        get() = matrix.cols

    val height: Int
        get() = matrix.rows

    operator fun get(x: Int, y: Int): Double = matrix[y, x]

    operator fun set(x: Int, y: Int, value: Double) = matrix.set(y, x, value)

    fun set(other: Matrix, offsetRow: Int = 0, offsetCol: Int = 0) {
        for (row in 0 until other.rows) {
            for (col in 0 until other.cols) {
                this[row + offsetRow, col + offsetCol] = other[row, col]
            }
        }
    }

    fun isInBounds(x: Int, y: Int) = matrix.isInBounds(y, x)

    override fun toString(): String {
        return "MatrixXY($width, $height)"
    }
}

class BoundedMatrixXY(val matrix: Matrix) {
    val width: Int
        get() = matrix.cols

    val height: Int
        get() = matrix.rows

    operator fun get(x: Int, y: Int): Double? {
        return if (isInBounds(x, y)) {
            matrix[y, x]
        } else {
            null
        }
    }

    operator fun set(x: Int, y: Int, value: Double) {
        if (isInBounds(x, y)) {
            matrix[y, x] = value
        }
    }

    fun set(other: Matrix, offsetRow: Int = 0, offsetCol: Int = 0) {
        for (row in 0 until other.rows) {
            for (col in 0 until other.cols) {
                this[row + offsetRow, col + offsetCol] = other[row, col]
            }
        }
    }

    fun isInBounds(x: Int, y: Int) = matrix.isInBounds(y, x)

    override fun toString(): String {
        return "BoundedMatrixXY($width, $height)"
    }
}

fun Matrix.asXY() = MatrixXY(this)

fun Matrix.asBoundedXY() = BoundedMatrixXY(this)
