package ch.obermuhlner.kimage.astro.color

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.copy
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.math.Histogram
import ch.obermuhlner.kimage.core.math.sigmoidLike
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

private fun Image.histogram(): Histogram {
    val histogram = Histogram()
    this.channels.forEach { channel -> histogram.add(this[channel]) }
    return histogram
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

fun Image.stretchAsinh(strength: Double = 1.0): Image {
    return stretch { v -> (asinh(v * strength)).coerceIn(0.0, 1.0) }
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

fun Image.stretch(func: (Double) -> Double): Image {
    val result = this.copy()
    result.applyEach { v -> func(v) }
    return result
}
