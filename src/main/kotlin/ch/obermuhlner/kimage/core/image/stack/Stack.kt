package ch.obermuhlner.kimage.core.image.stack

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.stack.max

fun Image.max(other: Image): Image {
    return MatrixImage(width, height, channels) { channel, width, height ->
        this[channel].max(other[channel])
    }
}