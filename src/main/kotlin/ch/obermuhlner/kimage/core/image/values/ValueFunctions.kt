package ch.obermuhlner.kimage.core.image.values

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage

fun Image.onEach(func: (value: Double) -> Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels) { channel, _, _ ->
            val m = this[channel].copy()
            m.applyEach(func)
            m
        }
}
