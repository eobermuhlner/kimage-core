package ch.obermuhlner.kimage.core.image.whitebalance

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.image.copy
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.medianInplace
import ch.obermuhlner.kimage.core.matrix.values.values
import kotlin.math.max

fun Image.whitebalanceGlobal(valueRangeMin: Double = 0.0, valueRangeMax: Double = 1.0): Image {
    val result = this.copy()
    applyWhitebalanceGlobal(valueRangeMin, valueRangeMax)
    return result
}

fun Image.applyWhitebalanceGlobal(valueRangeMin: Double = 0.0, valueRangeMax: Double = 1.0) {
    val redMatrix = this[Channel.Red]
    val greenMatrix = this[Channel.Green]
    val blueMatrix = this[Channel.Blue]

    val redMedian = redMatrix.values().filter { it in valueRangeMin..valueRangeMax }.median()
    val greenMedian = greenMatrix.values().filter { it in valueRangeMin..valueRangeMax }.median()
    val blueMedian = blueMatrix.values().filter { it in valueRangeMin..valueRangeMax }.median()

    applyWhitebalance(redMedian, greenMedian, blueMedian)
}

fun Image.whitebalanceLocal(fixPoints: List<PointXY>, medianRadius: Int): Image {
    val result = this.copy()
    applyWhitebalanceLocal(fixPoints, medianRadius)
    return result
}

fun Image.applyWhitebalanceLocal(fixPoints: List<PointXY>, medianRadius: Int) {
    val redMatrix = this[Channel.Red]
    val greenMatrix = this[Channel.Green]
    val blueMatrix = this[Channel.Blue]

    val redValues = mutableListOf<Double>()
    val greenValues = mutableListOf<Double>()
    val blueValues = mutableListOf<Double>()
    val squareSize = medianRadius+1+medianRadius
    fixPoints.forEach {
        redValues.addAll(redMatrix.crop(it.y-medianRadius, it.x-medianRadius, squareSize, squareSize).values())
        greenValues.addAll(greenMatrix.crop(it.y-medianRadius, it.x-medianRadius, squareSize, squareSize).values())
        blueValues.addAll(blueMatrix.crop(it.y-medianRadius, it.x-medianRadius, squareSize, squareSize).values())
    }

    val redMedian = redValues.medianInplace()
    val greenMedian = greenValues.medianInplace()
    val blueMedian = blueValues.medianInplace()

    applyWhitebalance(redMedian, greenMedian, blueMedian)
}

fun Image.whitebalance(redMedian: Double, greenMedian: Double, blueMedian: Double): Image {
    val result = this.copy()
    result.applyWhitebalance(redMedian, greenMedian, blueMedian)
    return result
}

fun Image.applyWhitebalance(redMedian: Double, greenMedian: Double, blueMedian: Double) {
    val redMatrix = this[Channel.Red]
    val greenMatrix = this[Channel.Green]
    val blueMatrix = this[Channel.Blue]

    val maxFactor = max(redMedian, max(greenMedian, blueMedian))
    val redFactor = maxFactor / redMedian
    val greenFactor = maxFactor / greenMedian
    val blueFactor = maxFactor / blueMedian

    redMatrix.applyEach { v -> v * redFactor  }
    greenMatrix.applyEach { v -> v * greenFactor  }
    blueMatrix.applyEach { v -> v * blueFactor  }
}

