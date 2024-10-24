package ch.obermuhlner.kimage.core.image.transform

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.transform.mirrorX
import ch.obermuhlner.kimage.core.matrix.transform.mirrorY
import ch.obermuhlner.kimage.core.matrix.transform.rotateLeft
import ch.obermuhlner.kimage.core.matrix.transform.rotateRight

fun Image.rotateLeft(): Image {
    return MatrixImage(
        this.height,
        this.width,
        this.channels) { channel, _, _ ->
        this[channel].rotateLeft()
    }
}

fun Image.rotateRight(): Image {
    return MatrixImage(
        this.height,
        this.width,
        this.channels) { channel, _, _ ->
        this[channel].rotateRight()
    }
}

fun Image.mirrorX(): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels) { channel, _, _ ->
        this[channel].mirrorX()
    }
}

fun Image.mirrorY(): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels) { channel, _, _ ->
        this[channel].mirrorY()
    }
}

