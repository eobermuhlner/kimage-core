package ch.obermuhlner.kimage.core.image.whitebalance

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.matrix.values.values
import kotlin.math.max

fun Image.applyWhitebalance() {
    val redMatrix = this[Channel.Red]
    val greenMatrix = this[Channel.Green]
    val blueMatrix = this[Channel.Blue]

    val redMedian = redMatrix.values().median()
    val greenMedian = greenMatrix.values().median()
    val blueMedian = blueMatrix.values().median()

    val maxFactor = max(redMedian, max(greenMedian, blueMedian))
    val redFactor = maxFactor / redMedian
    val greenFactor = maxFactor / greenMedian
    val blueFactor = maxFactor / blueMedian

    redMatrix.applyEach { v -> v * redFactor  }
    greenMatrix.applyEach { v -> v * greenFactor  }
    blueMatrix.applyEach { v -> v * blueFactor  }
}

