package ch.obermuhlner.kimage.astro.color

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.copy
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.math.Histogram
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.medianAndMedianAbsoluteDeviation
import ch.obermuhlner.kimage.core.math.sigmaClip
import ch.obermuhlner.kimage.core.math.sigmoidLike
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.matrix.values.values
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.ln
import kotlin.math.pow

fun Image.stretchLinearFactor(factor: Double): Image {
    return stretch { v -> (v * factor).coerceIn(0.0, 1.0) }
}

fun Image.stretchLinearPercentile(
    minPercentile: Double = 0.001,
    maxPercentile: Double = 0.999,
    histogram: Histogram = histogram()
): Image {
    val minValue = histogram.estimatePercentile(minPercentile)
    val maxValue = histogram.estimatePercentile(maxPercentile)
    return stretchLinear(minValue, maxValue)
}

fun Image.histogram(): Histogram {
    val histogram = Histogram()
    this.channels.forEach { channel -> histogram.add(this[channel]) }
    return histogram
}

fun Image.stretchLinearSigmaClipped(
    minKappa: Double = -1.0,
    maxKappa: Double = 3.0,
): Image {
    val sigmaClipped = this.values().toList().toDoubleArray().sigmaClip()
    val median = sigmaClipped.median()
    val sigma = sigmaClipped.stddev()
    val minValue = median + minKappa * sigma
    val maxValue = median + maxKappa * sigma
    return stretchLinear(minValue, maxValue)
}

fun Image.stretchLinear(
    minValue: Double = this.values().min(),
    maxValue: Double = this.values().max()
): Image {
    val range = maxValue - minValue
    return stretch { v -> ((v - minValue) / range).coerceIn(0.0, 1.0) }
}

fun Image.stretchLogarithmic(base: Double = 10.0): Image {
    val scaleFactor = 1 / ln(base)
    return stretch { v -> (ln(1 + v * (base - 1)) * scaleFactor).coerceIn(0.0, 1.0) }
}

fun Image.stretchExponential(exp: Double = 0.1): Image {
    return stretch { v -> (v.pow(exp)).coerceIn(0.0, 1.0) }
}

fun Image.stretchExponentialPercentile(
    minPercentile: Double = 0.001,
    maxPercentile: Double = 0.999,
    minExp: Double = 0.1,
    maxExp: Double = 1.0,
    histogram: Histogram = histogram()
): Image {
    val minValue = histogram.estimatePercentile(minPercentile)
    val maxValue = histogram.estimatePercentile(maxPercentile)
    val dynamicRange = maxValue - minValue
    val exp = if (dynamicRange < 0.5) {
        minExp + ((0.5 - dynamicRange) * (maxExp - minExp) * 2)
    } else {
        minExp
    }
    return stretchExponential(exp)
}

fun Image.stretchExponentialMedian(
    histogram: Histogram = histogram()
): Image {
    val medianValue = histogram.estimatePercentile(50.0)

    // Calculate exponent based on median value and a target contrast level
    val contrastFactor = 1.5
    val exp = if (medianValue > 0.0) contrastFactor / medianValue else 2.0

    // Apply exponential stretch with calculated exponent
    return stretch { v -> (v.pow(exp)).coerceIn(0.0, 1.0) }
}

fun Image.stretchSigmoid(midpoint: Double = 0.01, strength: Double = 10.0): Image {
    return stretch { v ->
        sigmoidLike(v, midpoint, strength).coerceIn(0.0, 1.0)
    }
}

fun Image.stretchSigmoidLike(midpoint: Double = 0.5, strength: Double = 2.0): Image {
    return stretch { v ->
        sigmoidLike(v, midpoint, strength).coerceIn(0.0, 1.0)
    }
}

fun Image.stretchAsinh(beta: Double = 5.0): Image {
    val scale = asinh(beta)
    return stretch { v -> (asinh(v * beta) / scale).coerceIn(0.0, 1.0) }
}

fun Image.stretchAsinhPercentile(
    minPercentile: Double = 0.001,
    maxPercentile: Double = 0.999,
    histogram: Histogram = histogram()
): Image {
    val minValue = histogram.estimatePercentile(minPercentile)
    val maxValue = histogram.estimatePercentile(maxPercentile)
    return stretchAsinh(1.0 / (maxValue - minValue))
}


fun Image.stretchSTF(shadows: Double = 0.01, highlights: Double = 0.99, midtones: Double = 0.5, target: Double = 0.25): Image {
    val minVal = this.values().min()
    val maxVal = this.values().max()
    val scale = maxVal - minVal

    return stretch { v ->
        val normalized = (v - minVal) / scale
        if (normalized < shadows) {
            0.0
        } else if (normalized > highlights) {
            1.0
        } else {
            val t = (normalized - shadows) / (highlights - shadows)
            ((t.pow(midtones) * (1 - target)) + target).coerceIn(0.0, 1.0)
        }
    }
}

fun Image.stretchAutoSTF(
    shadowClipping: Double = 2.8,
    targetBackground: Double = 0.1,
    perChannel: Boolean = false,
): Image {
    return if (perChannel) {
        MatrixImage(width, height, channels) { channel, _, _ ->
            val (c0, m) = autoStfParams(this[channel].values().toList(), shadowClipping, targetBackground)
            val result = this[channel].copy()
            result.applyEach { v -> autoStfApply(c0, m, v) }
            result
        }
    } else {
        val (c0, m) = autoStfParams(this.values().toList(), shadowClipping, targetBackground)
        stretch { v -> autoStfApply(c0, m, v) }
    }
}

private fun autoStfParams(values: Iterable<Double>, shadowClipping: Double, targetBackground: Double): Pair<Double, Double> {
    val (median, mad) = values.medianAndMedianAbsoluteDeviation()
    val sigma = 1.4826 * mad
    val c0 = (median - shadowClipping * sigma).coerceIn(0.0, 1.0)
    val range = (1.0 - c0).coerceAtLeast(1e-10)
    val x = ((median - c0) / range).coerceIn(0.0, 1.0)
    val m = if (x <= 0.0 || x >= 1.0) {
        0.5
    } else {
        val tb = targetBackground
        (x * (tb - 1.0) / ((2.0 * x - 1.0) * tb - x)).coerceIn(0.001, 0.999)
    }
    return Pair(c0, m)
}

private fun autoStfApply(c0: Double, m: Double, v: Double): Double {
    val x = ((v - c0).coerceAtLeast(0.0) / (1.0 - c0).coerceAtLeast(1e-10)).coerceIn(0.0, 1.0)
    return mtfTransfer(m, x)
}

private fun mtfTransfer(m: Double, x: Double): Double {
    if (x <= 0.0) return 0.0
    if (x >= 1.0) return 1.0
    if (m == 0.5) return x
    return ((m - 1.0) * x / ((2.0 * m - 1.0) * x - m)).coerceIn(0.0, 1.0)
}

fun Image.stretch(func: (Double) -> Double): Image {
    val result = this.copy()
    result.applyEach { v -> func(v) }
    return result
}
