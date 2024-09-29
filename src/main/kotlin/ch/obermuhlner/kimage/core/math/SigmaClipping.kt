package ch.obermuhlner.kimage.core.math

import kotlin.math.abs

fun FloatArray.sigmaClip(kappa: Float = 2f, iterations: Int = 1, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population, center: (array: FloatArray, offset: Int, length: Int) -> Float = FloatArray::median, keepLast: Boolean = true, histogram: Histogram? = null): FloatArray {
    val array = copyOfRange(offset, offset+length)
    val clippedLength = array.sigmaClipInplace(kappa, iterations, 0, length, standardDeviationType, center, keepLast, histogram)
    return array.copyOfRange(0, clippedLength)
}

fun DoubleArray.sigmaClip(kappa: Double = 2.0, iterations: Int = 1, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population, center: (array: DoubleArray, offset: Int, length: Int) -> Double = DoubleArray::median, keepLast: Boolean = true, histogram: Histogram? = null): DoubleArray {
    val array = copyOfRange(offset, offset+length)
    val clippedLength = array.sigmaClipInplace(kappa, iterations, 0, length, standardDeviationType, center, keepLast, histogram)
    return array.copyOfRange(0, clippedLength)
}

fun FloatArray.sigmaClip(low: Float, high: Float, offset: Int = 0, length: Int = size-offset, keepLast: Boolean = true, histogram: Histogram? = null): FloatArray {
    val array = copyOfRange(offset, offset+length)
    val clippedLength = array.sigmaClipInplace(low, high, 0, length, keepLast)
    return array.copyOfRange(0, clippedLength)
}

fun FloatArray.sigmaClipInplace(kappa: Float = 2f, iterations: Int = 1, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population, center: (array: FloatArray, offset: Int, length: Int) -> Float = FloatArray::median, keepLast: Boolean = true, histogram: Histogram? = null): Int {
    var currentLength = length

    for (i in 0 until iterations) {
        val sigma = stddev(standardDeviationType, offset, currentLength)
        val mid = center(this, offset, currentLength)

        val low = mid - kappa * sigma
        val high = mid + kappa * sigma

        currentLength = sigmaClipInplace(low, high, offset, currentLength, keepLast)
    }
    histogram?.add(currentLength)

    return currentLength
}

fun DoubleArray.sigmaClipInplace(kappa: Double = 2.0, iterations: Int = 1, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population, center: (array: DoubleArray, offset: Int, length: Int) -> Double = DoubleArray::median, keepLast: Boolean = true, histogram: Histogram? = null): Int {
    var currentLength = length

    for (i in 0 until iterations) {
        val sigma = stddev(standardDeviationType, offset, currentLength)
        val mid = center(this, offset, currentLength)

        val low = mid - kappa * sigma
        val high = mid + kappa * sigma

        currentLength = sigmaClipInplace(low, high, offset, currentLength, keepLast)
    }
    histogram?.add(currentLength)

    return currentLength
}

fun FloatArray.sigmaClipInplace(low: Float, high: Float, offset: Int = 0, length: Int = size-offset, keepLast: Boolean = true): Int {
    var targetLength = 0
    for (source in offset until (offset+length)) {
        if (this[source] in low..high) {
            this[offset + targetLength++] = this[source]
        }
    }

    if (keepLast && length > 0 && targetLength == 0) {
        val center = low + (high - low) / 2
        var best = this[offset]
        for (source in offset+1 until (offset + length)) {
            if (abs(center - this[source]) < abs(center - best)) {
                best = this[source]
            }
        }
        this[offset] = best
        targetLength = 1
    }

    return targetLength
}

fun DoubleArray.sigmaClipInplace(low: Double, high: Double, offset: Int = 0, length: Int = size - offset, keepLast: Boolean = true): Int {
    var targetLength = 0
    for (source in offset until (offset+length)) {
        if (this[source] in low..high) {
            this[offset + targetLength++] = this[source]
        }
    }

    if (keepLast && length > 0 && targetLength == 0) {
        val center = low + (high - low) / 2
        var best = this[offset]
        for (source in offset+1 until (offset + length)) {
            if (abs(center - this[source]) < abs(center - best)) {
                best = this[source]
            }
        }
        this[offset] = best
        targetLength = 1
    }

    return targetLength
}

fun FloatArray.huberWinsorizeInplace(offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population): FloatArray {
    val huberEpsilon = 0.0005
    val huberKappa = 1.5f
    val huberSigmaFactor = 1.345f
    var median = medianInplace(offset, length)
    var sigma = stddev(standardDeviationType, offset, length)
    do {
        val low = median - sigma * huberKappa
        val high = median + sigma * huberKappa
        winsorizeInplace(low, high, offset, length)

        median = medianInplace(offset, length)
        val lastSigma = sigma
        sigma = huberSigmaFactor * stddev(standardDeviationType, offset, length)
        val change = abs(sigma - lastSigma) / lastSigma
    } while (change > huberEpsilon)

    return this
}

fun FloatArray.huberWinsorizedSigmaClipInplace(kappa: Float, iterations: Int = 1, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population, histogram: Histogram? = null): Int {
    huberWinsorizeInplace(offset, length, standardDeviationType)
    return sigmaClipInplace(kappa, iterations, offset, length, standardDeviationType, histogram = histogram)
}

fun FloatArray.huberWinsorizedSigmaClip(kappa: Float, iterations: Int = 1, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population, histogram: Histogram? = null): FloatArray {
    val array = copyOfRange(offset, offset+length)
    val clippedLength = array.huberWinsorizedSigmaClipInplace(kappa, iterations, 0, length, standardDeviationType, histogram)
    return array.copyOfRange(0, clippedLength)
}

fun FloatArray.sigmaWinsorize(kappa: Float, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population): FloatArray {
    val array = copyOfRange(offset, offset+length)

    return array.sigmaWinsorizeInplace(kappa, 0, length, standardDeviationType)
}

fun DoubleArray.sigmaWinsorize(kappa: Double, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population): DoubleArray {
    val array = copyOfRange(offset, offset+length)

    return array.sigmaWinsorizeInplace(kappa, 0, length, standardDeviationType)
}

fun FloatArray.sigmaWinsorizeInplace(kappa: Float, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population): FloatArray {
    val sigma = stddev(standardDeviationType, offset, length)

    val m = median(offset, length)

    val low = m - kappa * sigma
    val high = m + kappa * sigma

    return winsorizeInplace(low, high, offset, length)
}

fun DoubleArray.sigmaWinsorizeInplace(kappa: Double, offset: Int = 0, length: Int = size-offset, standardDeviationType: StandardDeviation = StandardDeviation.Population): DoubleArray {
    val sigma = stddev(standardDeviationType, offset, length)

    val m = median(offset, length)

    val low = m - kappa * sigma
    val high = m + kappa * sigma

    return winsorizeInplace(low, high, offset, length)
}

fun FloatArray.winsorizeInplace(lowThreshold: Float, highThreshold: Float, offset: Int = 0, length: Int = size-offset): FloatArray {
    for (i in offset until (offset+length)) {
        this[i] = clamp(this[i], lowThreshold, highThreshold)
    }
    return this
}

fun DoubleArray.winsorizeInplace(lowThreshold: Double, highThreshold: Double, offset: Int = 0, length: Int = size-offset): DoubleArray {
    for (i in offset until (offset+length)) {
        this[i] = clamp(this[i], lowThreshold, highThreshold)
    }
    return this
}
