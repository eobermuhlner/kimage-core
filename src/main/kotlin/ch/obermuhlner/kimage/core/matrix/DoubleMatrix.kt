package ch.obermuhlner.kimage.core.matrix

import java.util.Objects

class DoubleMatrix(override val rows: Int, override val cols: Int) : Matrix {

    private val data = DoubleArray(rows * cols)

    constructor(rows: Int, cols: Int, init: (row: Int, col: Int) -> Double): this(rows, cols) {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                this[row, col] = init(row, col)
            }
        }
    }

    override fun create(newRows: Int, newCols: Int): Matrix {
        return DoubleMatrix(newRows, newCols)
    }

    override fun copy(): Matrix {
        val m = DoubleMatrix(rows, cols)
        System.arraycopy(this.data, 0, m.data, 0, this.data.size)
        return m
    }

    override fun get(index: Int): Double {
        return data[index]
    }

    override fun set(index: Int, value: Double) {
        data[index] = value
    }

    override fun equals(other: Any?): Boolean = (other is Matrix) && contentEquals(other)

    override fun hashCode(): Int {
        return Objects.hash(rows, cols, data.contentHashCode())
    }

    override fun toString(): String {
        return "DoubleMatrix($rows, $cols)"
    }

    companion object {
        fun matrixOf(rows: Int, cols: Int, vararg values: Double): Matrix {
            val m = DoubleMatrix(rows, cols)
            val limit = minOf(values.size, m.size)
            for (index in 0 until limit) {
                m[index] = values[index]
            }
            return m
        }

        fun matrixOf(rows: Int, cols: Int, init: (row: Int, col: Int) -> Double): Matrix {
            return DoubleMatrix(rows, cols, init)
        }

        fun matrixOf(rows: Int, cols: Int, init: (index: Int) -> Double): Matrix {
            val m = DoubleMatrix(rows, cols)
            for (index in 0 until m.size) {
                m[index] = init(index)
            }
            return m
        }

        fun identity(size: Int): Matrix {
            val m = DoubleMatrix(size, size)
            for (i in 0 until size) {
                m[i, i] = 1.0
            }
            return m
        }
    }
}