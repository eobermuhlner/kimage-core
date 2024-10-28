package ch.obermuhlner.kimage.astro.color

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.copy
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.image.values.values
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

fun Image.stretchNormalize(): Image {
    val minVal = this.values().min()
    val maxVal = this.values().max()

    val result = this.copy()
    result.applyEach { v ->
        (v - minVal) / (maxVal - minVal)
    }
    return result
}

fun Image.stretchLinear(factor: Double): Image {
    return stretch { v -> (v * factor).coerceIn(0.0, 1.0) }
}

fun Image.stretchLogarithmic(base: Double = 10.0): Image {
    val scaleFactor = 1 / ln(base)
    return stretch { v -> (ln(1 + v * (base - 1)) * scaleFactor).coerceIn(0.0, 1.0) }
}

fun Image.stretchExponential(exp: Double = 2.0): Image {
    return stretch { v -> (v.pow(exp)).coerceIn(0.0, 1.0) }
}

fun Image.stretchSigmoid(midpoint: Double = 0.5, factor: Double = 10.0): Image {
    return stretch { v ->
        (1 / (1 + exp(-factor * (v - midpoint)))).coerceIn(0.0, 1.0)
    }
}

fun Image.stretchAsinh(factor: Double = 1.0): Image {
    return stretch { v -> (Math.log(v + Math.sqrt(v * v + 1.0)) * factor).coerceIn(0.0, 1.0) }
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
