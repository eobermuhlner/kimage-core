package ch.obermuhlner.kimage.core.image.noise

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.filter.slowMedianFilter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.image.values.applyEach

fun Image.reduceNoiseUsingMedianTransform(radius: Int = 1, threshold: Double = 0.001): Image {
    val medianImage = this.slowMedianFilter(radius)
    val diffImage = this - medianImage
    diffImage.applyEach { v ->
        if (v > threshold) {
            v - threshold
        } else if (v < -threshold) {
            v + threshold
        } else {
            0.0
        }
    }

    return medianImage + diffImage
}