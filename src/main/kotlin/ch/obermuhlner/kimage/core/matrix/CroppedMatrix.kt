package ch.obermuhlner.kimage.core.matrix

import ch.obermuhlner.kimage.core.matrix.check.checkCol
import ch.obermuhlner.kimage.core.matrix.check.checkRow
import java.util.Objects

class CroppedMatrix(
    private val matrix: Matrix,
    private val offsetRow: Int,
    private val offsetCol: Int,
    override val rows: Int,
    override val cols: Int,
    private val strictClipping: Boolean = true
) : Matrix {

    override fun create(newRows: Int, newCols: Int): Matrix {
        return matrix.create(newRows, newCols)
    }

    override fun get(index: Int): Double {
        val row = index / cols
        val col = index % cols
        return get(row, col)
    }

    override fun set(index: Int, value: Double) {
        val row = index / cols
        val col = index % cols
        set(row, col, value)
    }

    override fun get(row: Int, col: Int): Double {
        return matrix[innerRow(row), innerCol(col)]
    }

    override fun set(row: Int, col: Int, value: Double) {
        matrix[innerRow(row), innerCol(col)] = value
    }

    private fun innerRow(row: Int): Int {
        if (strictClipping) {
            checkRow(row, rows)
        }
        return row + offsetRow
    }

    private fun innerCol(col: Int): Int {
        if (strictClipping) {
            checkCol(col, cols)
        }
        return col + offsetCol
    }

    override fun equals(other: Any?): Boolean = (other is Matrix) && contentEquals(other)

    override fun hashCode(): Int {
        return Objects.hash(rows, cols, contentHashCode())
    }

    override fun toString(): String {
        return "CroppedMatrix($rows, $cols, offset=($offsetRow, $offsetCol), $matrix)"
    }

}