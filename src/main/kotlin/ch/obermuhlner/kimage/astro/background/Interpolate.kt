package ch.obermuhlner.kimage.astro.background

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.values.values
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class PointXYValue(val point: PointXY, val value: Double)

fun Image.createFixPointGrid(
    horizontal: Int = 3,
    vertical: Int = 3
): List<PointXY> {
    val result = mutableListOf<PointXY>()

    val stepX = this.width / horizontal
    val stepY = this.height / vertical

    for (i in 0 until horizontal) {
        for (j in 0 until vertical) {
            val x = i * stepX + stepX/2
            val y = j * stepY + stepY/2
            result.add(PointXY(x, y))
        }
    }

    return result
}

fun Image.createFixPointFourCorners(
    distance: Int
): List<PointXY> {
    return listOf(
        PointXY(distance, distance),
        PointXY(width - distance, distance),
        PointXY(distance, height - distance),
        PointXY(width - distance, height - distance),
    )
}

fun Image.createFixPointEightCorners(
    distance: Int
): List<PointXY> {
    val centerX = width / 2
    val centerY = height / 2
    return listOf(
        PointXY(distance, distance),
        PointXY(centerX, distance),
        PointXY(width - distance, distance),
        PointXY(distance, centerY),
        PointXY(width - distance, centerY),
        PointXY(distance, height - distance),
        PointXY(centerX, height - distance),
        PointXY(width - distance, height - distance),
    )
}

fun Image.getFixPointValues(
    fixPoints: List<PointXY>,
    medianRadius: Int = estimateMedianRadiusForInterpolate(fixPoints.size, this.width, this.height)
): Map<Channel, List<PointXYValue>> {
    val result = mutableMapOf<Channel, List<PointXYValue>>()
    for (channel in this.channels) {
        result[channel] = fixPoints.map { PointXYValue(it, medianAround(it.x, it.y, medianRadius)) }
    }
    return result
}

fun sigmaClipFixPointValues(
    fixPointValues: Map<Channel, List<PointXYValue>>,
    sigmaThreshold: Double = 3.0,
    maxIterations: Int = 5
): Map<Channel, List<PointXYValue>> {
    val result = mutableMapOf<Channel, List<PointXYValue>>()
    for (channel in fixPointValues.keys) {
        result[channel] = sigmaClipFixPointValues(fixPointValues[channel]!!, sigmaThreshold, maxIterations)
    }
    return result
}

fun sigmaClipFixPointValues(
    fixPointValues: List<PointXYValue>,
    sigmaThreshold: Double = 3.0,
    maxIterations: Int = 5
): List<PointXYValue> {
    var clippedFixPointValues = fixPointValues

    for (iteration in 1..maxIterations) {
        val values = clippedFixPointValues.map{ it.value }
        val mean = values.average()
        val stdDev = values.stddev()

        // Remove points that are more than sigmaThreshold * standard deviation away from the mean
        val filtered = clippedFixPointValues.filter {
            val deviation = abs(it.value - mean)
            deviation <= sigmaThreshold * stdDev
        }

        // If no points were filtered out in this iteration, we stop
        if (filtered.size == clippedFixPointValues.size) break

        clippedFixPointValues = filtered
    }

    return clippedFixPointValues
}

fun estimateMedianRadiusForInterpolate(fixPointsCount: Int, imageWidth: Int, imageHeight: Int): Int {
    return min(imageWidth, imageHeight) / max(sqrt(fixPointsCount.toDouble()).toInt()+1, 2)
}

fun estimatePowerForInterpolate(fixPointsCount: Int, imageWidth: Int, imageHeight: Int): Double {
    val imageSizeFactor = (imageWidth * imageHeight).toDouble().pow(0.25) // Image size factor for large images
    val densityFactor = (imageWidth * imageHeight) / fixPointsCount.toDouble() // Density factor: area per fix point

    // Power increases with image size and sparsity of fix points
    val basePower = 2.0 // Base power value for moderate smoothing
    val sizeAdjustment = imageSizeFactor * 0.0001 // Adjust based on image size
    val densityAdjustment = 1.0 / (densityFactor + 1).pow(0.5) // Adjust based on fix point density

    return basePower + sizeAdjustment * densityAdjustment
}

fun Image.medianAround(x: Int, y: Int, radius: Int = 10): Double {
    return crop(x - radius, y - radius, radius+radius+1, radius+radius+1, false).values().median()
}

fun Matrix.medianAround(x: Int, y: Int, radius: Int = 10): Double {
    return crop(y - radius, x - radius, radius+radius+1, radius+radius+1, false).values().median()
}

fun Image.interpolate(
    fixPointValues: Map<Channel, List<PointXYValue>>,
    power: Double = estimatePowerForInterpolate(fixPointValues.size, this.width, this.height),
): Image {
    return MatrixImage(
        width,
        height,
        this.channels) { channel, _, _ ->
        this[channel].interpolate(
            fixPointValues[channel]!!,
            power)
    }
}
fun Matrix.interpolate(
    fixPointValues: List<PointXYValue>,
    power: Double = 2.0,
): Matrix {
    val m = create()

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            m[row, col] = interpolate(col, row, fixPointValues, power)
        }
    }

    return m
}

private fun interpolate(
    x: Int,
    y: Int,
    fixPointValues: List<PointXYValue>,
    power: Double = 2.0,
    radius: Double = 100.0,
    epsilon: Double = 1e-10,
): Double {
    require(fixPointValues.isNotEmpty()) { "fixPoints must not be empty" }

    val distances = fixPointValues.map {
        val dX = (x - it.point.x).toDouble()
        val dY = (y - it.point.y).toDouble()
        sqrt(dX * dX + dY * dY)
    }

    val weights = DoubleArray(fixPointValues.size)
    var totalWeight = 0.0

    for (i in fixPointValues.indices) {
        val distance = distances[i]
        //val weight = 1.0 / (distance + epsilon).pow(power)
        val weight = exp(-(((distance + epsilon) / radius).pow(power)))
        weights[i] = weight
        totalWeight += weight
    }

    // Compute the interpolated value by weighting the k nearest fix values
    var interpolatedValue = 0.0
    for (i in fixPointValues.indices) {
        val fixValue = fixPointValues[i].value
        val normalizedWeight = weights[i] / totalWeight
        interpolatedValue += fixValue * normalizedWeight
    }

    return interpolatedValue
}
