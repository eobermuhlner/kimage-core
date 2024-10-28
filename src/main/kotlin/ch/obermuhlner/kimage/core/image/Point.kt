package ch.obermuhlner.kimage.core.image

data class PointXY(val x: Int, val y: Int) {
    fun toPointRowCol() = PointRowCol(y, x)
}

data class PointRowCol(val row: Int, val col: Int) {
    fun toPointXY() = PointXY(col, row)
}