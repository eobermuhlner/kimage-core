package ch.obermuhlner.kimage.core.matrix.check

import ch.obermuhlner.kimage.core.matrix.Matrix

fun Matrix.checkRow(row: Int) {
    checkRow("row", row)
}

fun Matrix.checkCol(col: Int) {
    checkCol("col", col)
}

fun Matrix.checkRow(name: String, row: Int) {
    checkRow(name, row, rows)
}

fun Matrix.checkCol(name: String, col: Int) {
    checkCol(name, col, cols)
}

fun Matrix.checkSquare() {
    require(this.rows == this.cols) {
        "rows $rows != columns $cols"
    }
}

fun checkRow(name: String, row: Int, rows: Int) {
    require(row >= 0) { "$name < 0 : $row" }
    require(row < rows) { "$name >= $rows : $row" }
}

fun checkCol(name: String, col: Int, cols: Int) {
    require(col >= 0) { "$name < 0 : $col" }
    require(col < cols) { "$name >= $cols : $col" }
}

fun Matrix.checkSameSize(other: Matrix) {
    require(rows == other.rows && cols == other.cols) {
        "Matrix size mismatch: (${rows}x${cols}) vs (${other.rows}x${other.cols})"
    }
}

fun Matrix.checkColsOtherRows(other: Matrix) {
    require(cols == other.rows) { "columns != other.rows : $cols != ${other.rows}" }
}
