package ch.obermuhlner.kimage.astro.background

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.filter.gaussianBlurFilter
import ch.obermuhlner.kimage.core.matrix.values.values
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class FixPointValue(val fixPoint: PointXY, val value: Double)

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

fun sigmaClipFixPoints(
    fixPoints: List<PointXY>,
    fixValues: List<Double>,
    sigmaThreshold: Double = 3.0,
    maxIterations: Int = 5
): Pair<List<PointXY>, List<Double>> {
    require(fixPoints.size == fixValues.size) { "Fix points and values must have the same size" }

    var clippedFixPoints = fixPoints
    var clippedFixValues = fixValues

    for (iteration in 1..maxIterations) {
        val mean = clippedFixValues.average()
        val stdDev = clippedFixValues.stddev()

        // Remove points that are more than sigmaThreshold * standard deviation away from the mean
        val filtered = clippedFixPoints.zip(clippedFixValues).filter { (_, value) ->
            val deviation = Math.abs(value - mean)
            deviation <= sigmaThreshold * stdDev
        }

        // If no points were filtered out in this iteration, we stop
        if (filtered.size == clippedFixPoints.size) break

        // Update fix points and values with the filtered set
        clippedFixPoints = filtered.map { it.first }
        clippedFixValues = filtered.map { it.second }
    }

    return Pair(clippedFixPoints, clippedFixValues)
}

fun estimateMedianRadiusForInterpolate(fixPoints: List<PointXY>, imageWidth: Int, imageHeight: Int): Int {
    return min(imageWidth, imageHeight) / max(sqrt(fixPoints.size.toDouble()).toInt()+1, 2)
}

fun estimatePowerForInterpolate(fixPoints: List<PointXY>, imageWidth: Int, imageHeight: Int): Double {
    val numFixPoints = fixPoints.size
    val imageSizeFactor = (imageWidth * imageHeight).toDouble().pow(0.25) // Image size factor for large images
    val densityFactor = (imageWidth * imageHeight) / numFixPoints.toDouble() // Density factor: area per fix point

    // Power increases with image size and sparsity of fix points
    val basePower = 2.0 // Base power value for moderate smoothing
    val sizeAdjustment = imageSizeFactor * 0.0001 // Adjust based on image size
    val densityAdjustment = 1.0 / (densityFactor + 1).pow(0.5) // Adjust based on fix point density

    return basePower + sizeAdjustment * densityAdjustment
}

fun Image.interpolate(
    fixPoints: List<PointXY> = createFixPointGrid(),
    medianRadius: Int = estimateMedianRadiusForInterpolate(fixPoints, this.width, this.height),
    power: Double = estimatePowerForInterpolate(fixPoints, this.width, this.height),
    sigmaThreshold: Double = 3.0,
    maxIterations: Int = 5,
): Image {
    return MatrixImage(
        width,
        height,
        this.channels) { channel, _, _ ->
        val fixValues = fixPoints.map { this[channel].medianAround(it.y, it.x, medianRadius) }
        val (bestFixPoints, bestFixValues) = sigmaClipFixPoints(fixPoints, fixValues, sigmaThreshold, maxIterations)
        this[channel].interpolate(
            bestFixPoints,
            bestFixValues,
            power = power)
    }
}

fun Image.medianAround(x: Int, y: Int, radius: Int = 10): Double {
    return crop(x - radius, y - radius, radius+radius+1, radius+radius+1, false).values().median()
}

fun Matrix.medianAround(x: Int, y: Int, radius: Int = 10): Double {
    return crop(y - radius, x - radius, radius+radius+1, radius+radius+1, false).values().median()
}

fun Image.interpolate(
    fixPoints: List<PointXY>,
    fixValues: List<Double>,
    power: Double = 2.0,
): Image {
    return MatrixImage(
        width,
        height,
        this.channels) { channel, _, _ ->
        this[channel].interpolate(fixPoints,  fixValues, power)
    }
}

fun Matrix.interpolate(
    fixPoints: List<PointXY>,
    fixValues: List<Double>,
    power: Double = 2.0,
    gaussianBlurRadius: Int = 0
): Matrix {
    val m = create()

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            m[row, col] = interpolate(col, row, fixPoints, fixValues, power)
        }
    }

    if (gaussianBlurRadius > 0) {
        return m.gaussianBlurFilter(gaussianBlurRadius)
    }

    return m
}

private fun interpolate(
    x: Int,
    y: Int,
    fixPoints: List<PointXY>,
    fixValues: List<Double>,
    power: Double = 2.0,
    radius: Double = 100.0,
    epsilon: Double = 1e-10,
): Double {
    require(fixPoints.size == fixValues.size) { "fixPoints and fixValues must have the same size" }
    require(fixPoints.isNotEmpty()) { "fixPoints must not be empty" }

    val distances = fixPoints.map { (fixX, fixY) ->
        val dX = (x - fixX).toDouble()
        val dY = (y - fixY).toDouble()
        sqrt(dX * dX + dY * dY)
    }

    val weights = DoubleArray(fixPoints.size)
    var totalWeight = 0.0

    for (i in fixPoints.indices) {
        val distance = distances[i]
        //val weight = 1.0 / (distance + epsilon).pow(power)
        val weight = exp(-(((distance + epsilon) / radius).pow(power)))
        weights[i] = weight
        totalWeight += weight
    }

    // Compute the interpolated value by weighting the k nearest fix values
    var interpolatedValue = 0.0
    for (i in fixPoints.indices) {
        val fixValue = fixValues[i]
        val normalizedWeight = weights[i] / totalWeight
        interpolatedValue += fixValue * normalizedWeight
    }

    return interpolatedValue
}
