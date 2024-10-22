package ch.obermuhlner.kimage.core.image

import ch.obermuhlner.kimage.core.matrix.FloatMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix

class MatrixImage(
        width: Int,
        height: Int,
        channels: List<Channel>,
        channelMatrices: List<Matrix>)
    : AbstractImage(width, height, channels) {

    private val data = Array(channels.size) { channelMatrices[it] }

    constructor(
            width: Int,
            height: Int)
            : this(width, height, Channel.Red, Channel.Green, Channel.Blue)

    constructor(
            width: Int,
            height: Int,
            vararg channels: Channel)
            : this(width, height, channels.toList())

    constructor(
            width: Int,
            height: Int,
            vararg channels: Pair<Channel, Matrix>)
            : this(width, height, channels.map { it.first }, channels.map { it.second })

    constructor(
        gray: Matrix
    )
            : this(gray.cols, gray.rows, Channel.Red to gray, Channel.Green to gray, Channel.Blue to gray)

    constructor(
        red: Matrix, green: Matrix, blue: Matrix)
            : this(red.cols, red.rows, Channel.Red to red, Channel.Green to green, Channel.Blue to blue)

    constructor(
            width: Int,
            height: Int,
            channels: List<Channel>,
            matrixFunc: (channel: Channel, rows: Int, cols: Int) -> Matrix = { _, _, _ -> FloatMatrix(height, width) })
            : this(width, height, channels, channels.map { matrixFunc.invoke(it, height, width) })

    constructor(image: Image) : this(image.width, image.height) {
        this.setPixels(image)
    }


    override fun getPixel(x: Int, y: Int, channel: Channel): Double {
        val channelIndex = channelIndex(channel)
        if (channelIndex < 0) {
            return convertPixelToChannel(x, y, channel)
        }
        return data[channelIndex][y, x]
    }

    override fun setPixel(x: Int, y: Int, channel: Channel, color: Double) {
        data[channelIndex(channel)][y, x] = color
    }

    override fun getMatrix(channel: Channel): Matrix {
        val idx = channelIndex(channel)
        return if (idx >= 0) {
            data[idx]
        } else {
            super.getMatrix(channel)
        }
    }
}