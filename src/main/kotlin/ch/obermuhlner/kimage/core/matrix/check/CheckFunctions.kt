package ch.obermuhlner.kimage.core.matrix.check

import ch.obermuhlner.kimage.core.matrix.Matrix

fun Matrix.checkRows(rows: Int) {
    require(rows >= 0) { "rows < 0 : $rows" }
}

fun Matrix.checkCols(cols: Int) {
    require(cols >= 0) { "columns < 0 : $cols" }
}

fun Matrix.checkSquare(matrix: Matrix) {
    require(matrix.rows == matrix.cols) {
        "rows " + matrix.rows.toString() + " != columns " + matrix.cols
    }
}

fun Matrix.checkCol(matrix: Matrix, col: Int) {
    checkCol(matrix.cols, "column", col)
}

fun Matrix.checkRow(matrix: Matrix, name: String, row: Int) {
    checkRow(matrix.rows, name, row)
}

fun Matrix.checkRow(rows: Int, row: Int) {
    checkRow(rows, "row", row)
}

fun Matrix.checkCol(cols: Int, col: Int) {
    checkCol(cols, "column", col)
}

fun Matrix.checkRow(rows: Int, name: String, row: Int) {
    require(row >= 0) { "$name < 0 : $row" }
    require(row < rows) { "$name >= $rows : $row" }
}

fun Matrix.checkCol(cols: Int, name: String, col: Int) {
    require(col >= 0) { "$name < 0 : $col" }
    require(col < cols) { "$name >= $cols : $col" }
}

fun Matrix.checkSameSize(matrix: Matrix, other: Matrix) {
    require(matrix.rows == other.rows && matrix.cols == other.cols) {
        "Matrix size mismatch: (${matrix.rows}x${matrix.cols}) vs (${other.rows}x${other.cols})"
    }
}

fun Matrix.checkColsOtherRows(matrix: Matrix, other: Matrix) {
    require(matrix.cols == other.rows) { "columns != other.rows : ${matrix.cols} != ${other.rows}" }
}
