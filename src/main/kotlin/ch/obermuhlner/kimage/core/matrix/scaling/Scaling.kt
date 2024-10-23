package ch.obermuhlner.kimage.core.matrix.scaling

import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.mixBilinear
import ch.obermuhlner.kimage.core.math.mixCubicHermite
import ch.obermuhlner.kimage.core.matrix.Matrix

enum class Scaling {
    Nearest,
    Bilinear,
    Bicubic
}

fun Matrix.scaleBy(scaleRows: Double, scaleCols: Double, offsetRow: Double = 0.0, offsetCol: Double = 0.0, scaling: Scaling = Scaling.Bicubic): Matrix {
    val newRows = (rows * scaleRows).toInt()
    val newCols = (cols * scaleCols).toInt()

    return scaleTo(newRows, newCols, offsetRow, offsetCol, scaling)
}

fun Matrix.scaleTo(newRows: Int, newCols: Int, offsetRow: Double = 0.0, offsetCol: Double = 0.0, scaling: Scaling = Scaling.Bicubic): Matrix {
    return when (scaling) {
        Scaling.Nearest -> scaleNearestTo(newRows, newCols, offsetRow, offsetCol)
        Scaling.Bilinear -> scaleBilinearTo(newRows, newCols, offsetRow, offsetCol)
        Scaling.Bicubic -> scaleBicubicTo(newRows, newCols, offsetRow, offsetCol)
    }
}

private fun Matrix.scaleNearestTo(newRows: Int, newCols: Int, offsetRow: Double = 0.0, offsetCol: Double = 0.0): Matrix {
    val m = create(newRows, newCols)
    for (newRow in 0 until newRows) {
        for (newCol in 0 until newCols) {
            val oldRow = (newRow.toDouble() / newRows * rows + offsetRow + 0.5).toInt()
            val oldCol = (newCol.toDouble() / newCols * cols + offsetCol + 0.5).toInt()

            val newValue = this[oldRow, oldCol]
            m[newRow, newCol] = newValue
        }
    }

    return m
}

private fun Matrix.scaleBilinearTo(newRows: Int, newCols: Int, offsetRow: Double = 0.0, offsetCol: Double = 0.0): Matrix {
    val m = create(newRows, newCols)
    for (newRow in 0 until newRows) {
        for (newCol in 0 until newCols) {
            val oldRow = newRow.toDouble() / newRows * (rows - 1) + offsetRow + 0.5
            val oldCol = newCol.toDouble() / newCols * (cols - 1) + offsetCol + 0.5
            val oldRowInt = oldRow.toInt()
            val oldColInt = oldCol.toInt()
            val oldRowFract = oldRow - oldRowInt
            val oldColFract = oldCol - oldColInt

            val v00 = this[oldRowInt, oldColInt]
            val v01 = this[oldRowInt + 1, oldColInt]
            val v10 = this[oldRowInt, oldColInt + 1]
            val v11 = this[oldRowInt + 1, oldColInt + 1]

            val newValue = mixBilinear(v00, v01, v10, v11, oldRowFract, oldColFract)

            m[newRow, newCol] = newValue
        }
    }

    return m
}

private fun Matrix.scaleBicubicTo(newRows: Int, newCols: Int, offsetRow: Double = 0.0, offsetCol: Double = 0.0): Matrix {
    val m = create(newRows, newCols)
    for (newRow in 0 until newRows) {
        for (newCol in 0 until newCols) {
            val oldRow = newRow.toDouble() / newRows * (rows - 1) + offsetRow + 0.5
            val oldCol = newCol.toDouble() / newCols * (cols - 1) + offsetCol + 0.5
            val oldRowInt = oldRow.toInt()
            val oldColInt = oldCol.toInt()
            val oldRowFract = oldRow - oldRowInt
            val oldColFract = oldCol - oldColInt

            val v00 = this[oldRowInt - 1, oldColInt - 1]
            val v10 = this[oldRowInt - 1, oldColInt + 0]
            val v20 = this[oldRowInt - 1, oldColInt + 1]
            val v30 = this[oldRowInt - 1, oldColInt + 2]

            val v01 = this[oldRowInt + 0, oldColInt - 1]
            val v11 = this[oldRowInt + 0, oldColInt + 0]
            val v21 = this[oldRowInt + 0, oldColInt + 1]
            val v31 = this[oldRowInt + 0, oldColInt + 2]

            val v02 = this[oldRowInt + 1, oldColInt - 1]
            val v12 = this[oldRowInt + 1, oldColInt + 0]
            val v22 = this[oldRowInt + 1, oldColInt + 1]
            val v32 = this[oldRowInt + 1, oldColInt + 2]

            val v03 = this[oldRowInt + 2, oldColInt - 1]
            val v13 = this[oldRowInt + 2, oldColInt + 0]
            val v23 = this[oldRowInt + 2, oldColInt + 1]
            val v33 = this[oldRowInt + 2, oldColInt + 2]

            val col0 = mixCubicHermite(v00, v10, v20, v30, oldRowFract)
            val col1 = mixCubicHermite(v01, v11, v21, v31, oldRowFract)
            val col2 = mixCubicHermite(v02, v12, v22, v32, oldRowFract)
            val col3 = mixCubicHermite(v03, v13, v23, v33, oldRowFract)
            val newValue = mixCubicHermite(col0, col1, col2, col3, oldColFract)

            m[newRow, newCol] = clamp(newValue, 0.0, 1.0)
        }
    }

    return m
}
