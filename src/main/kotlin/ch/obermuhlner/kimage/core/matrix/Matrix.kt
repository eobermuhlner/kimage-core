package ch.obermuhlner.kimage.core.matrix

import ch.obermuhlner.kimage.core.matrix.check.checkCol
import ch.obermuhlner.kimage.core.matrix.check.checkColsOtherRows
import ch.obermuhlner.kimage.core.matrix.check.checkRow
import ch.obermuhlner.kimage.core.matrix.check.checkSameSize
import kotlin.math.abs

interface Matrix {
    val rows: Int
    val cols: Int

    val size: Int
        get() = rows * cols

    operator fun get(index: Int): Double
    operator fun set(index: Int, value: Double)

    operator fun get(row: Int, col: Int): Double {
        val index = indexOf(row, col)
        return this[index]
    }

    operator fun set(row: Int, col: Int, value: Double) {
        val index = indexOf(row, col)
        this[index] = value
    }

    fun set(other: Matrix, offsetRow: Int = 0, offsetCol: Int = 0) {
        for (row in 0 until other.rows) {
            for (col in 0 until other.cols) {
                this[row + offsetRow, col + offsetCol] = other[row, col]
            }
        }
    }

    fun indexOf(row: Int, col: Int): Int {
        checkRow(row, rows)
        checkCol(col, cols)
        return row * cols + col
    }

    operator fun plus(other: Matrix): Matrix {
        checkSameSize(this, other)

        val m = create()
        for (index in 0 until size) {
            m[index] = this[index] + other[index]
        }
        return m
    }

    operator fun plusAssign(other: Matrix) {
        checkSameSize(this, other)

        for (index in 0 until size) {
            this[index] += other[index]
        }
    }

    operator fun minus(other: Matrix): Matrix {
        checkSameSize(this, other)

        val m = create()
        for (index in 0 until size) {
            m[index] = this[index] - other[index]
        }
        return m
    }

    operator fun minusAssign(other: Matrix) {
        checkSameSize(this, other)

        for (index in 0 until size) {
            this[index] -= other[index]
        }
    }

    operator fun times(other: Matrix): Matrix {
        checkColsOtherRows(this, other)

        val m = create(this.rows, other.cols)
        for (row in 0 until rows) {
            for (otherCol in 0 until other.cols) {
                var sum = 0.0
                for (col in 0 until cols) {
                    sum += this[row, col] * other[col, otherCol]
                }
                m[row, otherCol] = sum
            }
        }
        return m
    }

    operator fun times(other: Double): Matrix {
        val m = create()
        for (index in 0 until size) {
            m[index] = this[index] * other
        }
        return m
    }

    operator fun timesAssign(other: Double) {
        applyEach { value ->
            value * other
        }
    }

    operator fun div(other: Double): Matrix {
        val m = create()
        for (index in 0 until size) {
            m[index] = this[index] / other
        }
        return m
    }

    operator fun divAssign(other: Double) {
        applyEach { value ->
            value / other
        }
    }

    operator fun minusAssign(other: Double) {
        applyEach { value ->
            value - other
        }
    }

    infix fun elementPlus(other: Double): Matrix {
        val m = create()
        for (index in 0 until size) {
            m[index] = this[index] + other
        }
        return m
    }

    infix fun elementMinus(other: Double): Matrix {
        val m = create()
        for (index in 0 until size) {
            m[index] = this[index] - other
        }
        return m
    }

    infix fun elementTimes(other: Matrix): Matrix {
        checkSameSize(this, other)
        val m = create()
        for (index in 0 until size) {
            m[index] = this[index] * other[index]
        }
        return m
    }

    infix fun elementDiv(other: Matrix): Matrix {
        checkSameSize(this, other)
        val m = create()
        for (index in 0 until size) {
            m[index] = this[index] / other[index]
        }
        return m
    }

    fun transpose(): Matrix {
        val m = create(rows, cols)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                m[col, row] = this[row, col]
            }
        }

        return m
    }

    fun create(newRows: Int = rows, newCols: Int = cols): Matrix

    fun copy(): Matrix {
        val m = create()
        m.set(this)
        return m
    }

    fun applyEach(func: (Double) -> Double) {
        for (index in 0 until size) {
            this[index] = func.invoke(this[index])
        }
    }

    fun applyEach(func: (row: Int, col: Int, value: Double) -> Double) {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                this[row, col] = func.invoke(row, col, this[row, col])
            }
        }
    }

    fun contentToString(multiline: Boolean = false): String {
        val str = StringBuilder()

        str.append("[")
        for (row in 0 until rows) {
            if (row != 0) {
                str.append(" ")
            }
            str.append("[")
            for (col in 0 until cols) {
                if (col != 0) {
                    str.append(" ,")
                }
                str.append(this[row, col])
            }
            str.append("]")
            if (multiline && row != rows - 1) {
                str.appendLine()
            }
        }
        str.append("]")
        if (multiline) {
            str.appendLine()
        }

        return str.toString()
    }

    fun contentEquals(other: Matrix, epsilon: Double = 1E-10): Boolean {
        if (rows != other.rows || cols != other.cols) {
            return false
        }

        for (index in 0 until size) {
            if (abs(this[index] - other[index]) > epsilon) {
                return false
            }
        }

        return true
    }

    fun contentHashCode(): Int {
        var result = rows
        result = 31 * result + cols
        for (index in 0 until size) {
            result = 31 * result + this[index].hashCode()
        }
        return result
    }
}