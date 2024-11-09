package ch.obermuhlner.kimage.core.image.noise

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.math.sigmoid
import ch.obermuhlner.kimage.core.math.sigmoidLike
import ch.obermuhlner.kimage.core.matrix.filter.slowMedianFilter

fun thresholdHard(v: Double, threshold: Double): Double {
    return when {
        v > threshold -> v
        v < -threshold -> v
        else -> 0.0
    }
}

fun thresholdSoft(v: Double, threshold: Double): Double {
    return when {
        v > threshold -> v - threshold
        v < -threshold -> v + threshold
        else -> 0.0
    }
}

fun thresholdSigmoid(v: Double, threshold: Double, midpoint: Double = 0.5, strength: Double = 2.0): Double {
    return when (v) {
        in 0.0..threshold -> sigmoid(v / threshold, midpoint, strength) * threshold
        in -threshold..0.0 -> sigmoid(-v / threshold, midpoint, strength) * -threshold
        else -> v
    }
}

fun thresholdSigmoidLike(v: Double, threshold: Double, midpoint: Double = 0.5, strength: Double = 2.0): Double {
    return when (v) {
        in 0.0..threshold -> sigmoidLike(v / threshold, midpoint, strength) * threshold
        in -threshold..0.0 -> sigmoidLike(-v / threshold, midpoint, strength) * -threshold
        else -> v
    }
}

fun Image.reduceNoiseUsingMultiScaleMedianTransform(
    thresholds: List<Double>,
    thresholding: (v: Double, threshold: Double) -> Double = { v, threshold -> thresholdSigmoid(v, threshold) },
): Image {
    val red = this[Channel.Red]
    val green = this[Channel.Green]
    val blue = this[Channel.Blue]

    var gray = this[Channel.Gray]

    for (thresholdIndex in thresholds.indices) {
        val threshold = thresholds[thresholdIndex]
        val radius = thresholdIndex + 1
        val medianGray = gray.slowMedianFilter(radius)
        val medianRed = red.slowMedianFilter(radius)
        val medianGreen = green.slowMedianFilter(radius)
        val medianBlue = blue.slowMedianFilter(radius)

        val diffGray = gray - medianGray

        for (i in 0 until diffGray.size) {
            val v = diffGray[i]
            val adjustment = thresholding(v, threshold)
            diffGray[i] = adjustment
            red[i] = (medianRed[i] + adjustment).coerceIn(0.0, 1.0)
            green[i] = (medianGreen[i] + adjustment).coerceIn(0.0, 1.0)
            blue[i] = (medianBlue[i] + adjustment).coerceIn(0.0, 1.0)
        }

        gray = medianGray + diffGray
    }

    return MatrixImage(
        this.width,
        this.height,
        Channel.Red to red,
        Channel.Green to green,
        Channel.Blue to blue
    )
}
