package ch.obermuhlner.kimage.core.matrix

import java.util.Objects

class FloatMatrix(override val rows: Int, override val cols: Int) : Matrix {

    private val data = FloatArray(rows * cols)

    constructor(rows: Int, cols: Int, init: (row: Int, col: Int) -> Float): this(rows, cols) {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                setValue(indexOf(row, col), init(row, col))
            }
        }
    }

    override fun create(newRows: Int, newCols: Int): Matrix {
        return FloatMatrix(newRows, newCols)
    }

    override fun copy(): Matrix {
        val m = FloatMatrix(rows, cols)
        System.arraycopy(this.data, 0, m.data, 0, this.data.size)
        return m
    }

    override fun get(index: Int): Double {
        return data[index].toDouble()
    }

    override fun set(index: Int, value: Double) {
        data[index] = value.toFloat()
    }

    private fun setValue(index: Int, value: Float) {
        data[index] = value
    }

    override fun equals(other: Any?): Boolean = (other is Matrix) && contentEquals(other)

    override fun hashCode(): Int {
        return Objects.hash(rows, cols, data.contentHashCode())
    }

    override fun toString(): String {
        return "FloatMatrix($rows, $cols)"
    }

    companion object {
        fun matrixOf(rows: Int, cols: Int, vararg values: Float): Matrix {
            val m = FloatMatrix(rows, cols)
            val limit = minOf(values.size, m.size)
            for (index in 0 until limit) {
                m.setValue(index, values[index])
            }
            return m
        }

        fun matrixOf(rows: Int, cols: Int, init: (row: Int, col: Int) -> Float): Matrix {
            return FloatMatrix(rows, cols, init)
        }


        fun identity(size: Int): Matrix {
            val m = FloatMatrix(size, size)
            for (i in 0 until size) {
                m[i, i] = 1.0
            }
            return m
        }

    }
}