package ch.obermuhlner.kimage.core.image

data class PointXY(val x: Int, val y: Int)

data class DoublePointXY(val x: Double, val y: Double) {
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())

    val intX: Int get() = (x + 0.5).toInt()
    val intY: Int get() = (y + 0.5).toInt()
}