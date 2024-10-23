package ch.obermuhlner.kimage.core.image.crop

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.matrix.crop.CroppedMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix

fun Image.crop(croppedX: Int, croppedY: Int, croppedWidth: Int, croppedHeight: Int, strictClipping: Boolean = true): Image {
    return CroppedImage(this, croppedX, croppedY, croppedWidth, croppedHeight, strictClipping)
}

fun Image.cropCenter(radius: Int, croppedCenterX: Int = width / 2, croppedCenterY: Int = height / 2, strictClipping: Boolean = true): Image {
    return cropCenter(radius, radius, croppedCenterX, croppedCenterY, strictClipping)
}

fun Image.cropCenter(radiusX: Int, radiusY: Int, croppedCenterX: Int = width / 2, croppedCenterY: Int = height / 2, strictClipping: Boolean = true): Image {
    return crop(croppedCenterX - radiusX, croppedCenterY - radiusY, radiusX*2, radiusY*2, strictClipping)
}

class CroppedImage(
    private val image: Image,
    private val offsetX: Int,
    private val offsetY: Int,
    override val width: Int,
    override val height: Int,
    private val strictClipping: Boolean = true
): Image {

    init {
        if (strictClipping) {
            require(offsetX >= 0) { "offsetX must be >= 0: $offsetX" }
            require(offsetY >= 0) { "offsetY must be >= 0: $offsetY" }
            require(offsetX < image.width) { "offsetX must be < image.width: $offsetX" }
            require(offsetY < image.height) { "offsetY must be < image.height: $offsetY" }
            require(width >= 0) { "width must be >= 0: $width" }
            require(height >= 0) { "width must be >= 0: $height" }
            require(width <= image.width-offsetX) { "width must be <= image.width - offsetX: $width" }
            require(height <= image.height-offsetY) { "width must be <= image.height - offsetX: $height" }
        }
    }

    override val channels: List<Channel>
        get() = image.channels

    override fun getPixel(x: Int, y: Int, color: DoubleArray): DoubleArray {
        return image.getPixel(innerX(x), innerY(y), color)
    }

    override fun getPixel(x: Int, y: Int, targetChannels: List<Channel>, color: DoubleArray) {
        return image.getPixel(innerX(x), innerY(y), targetChannels, color)
    }

    override fun getPixel(x: Int, y: Int, channel: Channel): Double {
        return image.getPixel(innerX(x), innerY(y), channel)
    }

    override fun setPixel(x: Int, y: Int, color: DoubleArray) {
        image.setPixel(innerX(x), innerY(y), color)
    }

    override fun setPixel(x: Int, y: Int, channel: Channel, color: Double) {
        image.setPixel(innerX(x), innerY(y), channel, color)
    }

    override fun getMatrix(channel: Channel): Matrix {
        return CroppedMatrix(image.getMatrix(channel), offsetY, offsetX, height, width)
    }

    override fun toString(): String {
        return "CroppedImage($offsetX, $offsetY, $width, $height, $image)"
    }

    private fun innerX(x: Int) = if (strictClipping) {
        boundedX(x) + offsetX
    } else {
        x + offsetX
    }

    private fun innerY(y: Int) = if (strictClipping) {
        boundedY(y) + offsetY
    } else {
        y + offsetY
    }
}