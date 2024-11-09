package ch.obermuhlner.kimage.core.image.histogram

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.math.Histogram
import kotlin.math.max
import kotlin.math.min

fun Image.histogramImage(
    histogramWidth: Int,
    histogramHeight: Int,
    scaleYFunction: (Double) -> Double = { x: Double -> x },
    histogramFunction: (Channel) -> Histogram = { Histogram(histogramWidth) },
    channels: List<Channel> = this.channels,
    ignoreMinMaxBins: Boolean = true
): Image {
    val result = MatrixImage(histogramWidth, histogramHeight)

    for (gridX in 1 .. 9) {
        val x = result.width * gridX / 10
        for (y in 0 until result.height) {
            result[Channel.Red][y, x] = 0.3
            result[Channel.Green][y, x] = 0.3
            result[Channel.Blue][y, x] = 0.3
        }
    }

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
                result[channel][y, x] = 1.0
            }
        }
    }

    return result
}
