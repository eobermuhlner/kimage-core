package ch.obermuhlner.kimage.core.image

import ch.obermuhlner.kimage.core.huge.HugeFloatArray
import ch.obermuhlner.kimage.core.math.Histogram
import ch.obermuhlner.kimage.core.math.StandardDeviation
import ch.obermuhlner.kimage.core.math.average
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.huberWinsorizedSigmaClipInplace
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.medianInplace
import ch.obermuhlner.kimage.core.math.sigmaClipInplace
import ch.obermuhlner.kimage.core.math.sigmaWinsorizeInplace
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.math.weightedAverage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import java.util.*
import kotlin.math.*

// exaggerates low values but never reaches 1.0
fun exaggerate(x: Double): Double = -1/(x+0.5)+2

fun Image.histogramImage(
    histogramWidth: Int,
    histogramHeight: Int,
    scaleYFunction: (Double) -> Double = { x: Double -> x },
    histogramFunction: (Channel) -> Histogram = { Histogram(histogramWidth) },
    channels: List<Channel> = this.channels,
    ignoreMinMaxBins: Boolean = true
): Image {
    val result = MatrixImage(histogramWidth, histogramHeight)

    val channelHistograms = mutableMapOf<Channel, Histogram>()
    var maxCount = 0
    for (channel in channels) {
        val histogram = histogramFunction(channel)
        channelHistograms[channel] = histogram

        histogram.add(this[channel])
        maxCount = max(maxCount, histogram.max(ignoreMinMaxBins))
    }

    val max = scaleYFunction(maxCount.toDouble())
    for (channel in channels) {
        val histogram = channelHistograms[channel]!!

        for (x in 0 until histogramWidth) {
            val histY = min(histogramHeight, (histogramHeight.toDouble() * scaleYFunction(histogram[x].toDouble()) / max).toInt())
            for (y in (histogramHeight-histY) until histogramHeight) {
                result[channel][x, y] = 1.0
            }
        }
    }

    return result
}

fun Image.copy(): Image {
    return MatrixImage(this.width, this.height, this.channels) { channel, _, _ ->
        this[channel].copy()
    }
}