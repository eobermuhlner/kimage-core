package ch.obermuhlner.kimage.core.image

import ch.obermuhlner.kimage.core.math.Histogram
import kotlin.math.*

// exaggerates low values but never reaches 1.0
fun exaggerate(x: Double): Double = -1/(x+0.5)+2

fun Image.copy(): Image {
    return MatrixImage(this.width, this.height, this.channels) { channel, _, _ ->
        this[channel].copy()
    }
}