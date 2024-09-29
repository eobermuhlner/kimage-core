package ch.obermuhlner.kimage.core.image.scaling

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.scaling.Scaling
import ch.obermuhlner.kimage.core.matrix.scaling.scaleTo
import kotlin.math.min

fun Image.scaleBy(
    scaleX: Double,
    scaleY: Double,
    offsetX: Double = 0.0,
    offsetY: Double = 0.0,
    scaling: Scaling = Scaling.Bicubic
): Image {
    val newWidth = (width * scaleX).toInt()
    val newHeight = (height * scaleY).toInt()
    return scaleTo(newWidth, newHeight, offsetX, offsetY, scaling)
}

fun Image.scaleTo(
    newWidth: Int,
    newHeight: Int,
    offsetX: Double = 0.0,
    offsetY: Double = 0.0,
    scaling: Scaling = Scaling.Bicubic
): Image {
    return MatrixImage(
        newWidth,
        newHeight,
        this.channels) { channel, _, _ ->
        this[channel].scaleTo(newHeight, newWidth, offsetY, offsetX, scaling)
    }
}

fun Image.scaleToKeepRatio(
    newWidth: Int,
    newHeight: Int,
    offsetX: Double = 0.0,
    offsetY: Double = 0.0,
    scaling: Scaling = Scaling.Bicubic
): Image {
    val ratio = this.width.toDouble() / this.height
    val correctedNewWidth = min(newWidth, (newHeight*ratio).toInt())
    val correctedNewHeight = min(newHeight, (newWidth/ratio).toInt())
    return scaleTo(correctedNewWidth, correctedNewHeight, offsetX, offsetY, scaling)
}

