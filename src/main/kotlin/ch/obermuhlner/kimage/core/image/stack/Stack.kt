package ch.obermuhlner.kimage.core.image.stack

import ch.obermuhlner.kimage.core.huge.HugeMultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.huge.MultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.math.Histogram
import ch.obermuhlner.kimage.core.math.StandardDeviation
import ch.obermuhlner.kimage.core.math.average
import ch.obermuhlner.kimage.core.math.huberWinsorizedSigmaClipInplace
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.math.medianInplace
import ch.obermuhlner.kimage.core.math.sigmaClipInplace
import ch.obermuhlner.kimage.core.math.sigmaWinsorizeInplace
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.math.weightedAverage
import ch.obermuhlner.kimage.core.matrix.stack.max
import kotlin.math.abs
import kotlin.math.sqrt

fun max(firstImage: Image, vararg otherImages: Image): Image {
    return MatrixImage(firstImage.width, firstImage.height, firstImage.channels) { channel, _, _ ->
        max(firstImage[channel], *otherImages.map { it[channel] }.toTypedArray())
    }
}

enum class StackAlgorithm {
    Median,
    Average,
    Max,
    Min,
    SigmaClipMedian,
    SigmaClipAverage,
    SigmaWinsorizeMedian,
    SigmaWinsorizeAverage,
    WinsorizedSigmaClipMedian,
    WinsorizedSigmaClipAverage,
    SigmaClipWeightedMedian,
}

fun stack(
    imageSuppliers: List<() -> Image>,
    algorithm: StackAlgorithm = StackAlgorithm.Median,
    kappa: Double = 2.0,
    iterations: Int = 10,
    multiDimensionalFloatArraySupplier: (Int, Int, Int) -> MultiDimensionalFloatArray = { dim1, dim2, dim3 -> HugeMultiDimensionalFloatArray(dim1, dim2, dim3) }
): Image {
    val baseImage: Image = imageSuppliers[0]()
    val channels = baseImage.channels
    val huge = multiDimensionalFloatArraySupplier(imageSuppliers.size, channels.size, baseImage.width * baseImage.height)

    for (imageIndex in imageSuppliers.indices) {
        val image = if (imageIndex == 0) {
            baseImage
        } else {
            imageSuppliers[imageIndex]().crop(0, 0, baseImage.width, baseImage.height)
        }

        for (channelIndex in channels.indices) {
            val matrix = image[channels[channelIndex]]
            for (matrixIndex in 0 until matrix.size) {
                huge[imageIndex, channelIndex, matrixIndex] = matrix[matrixIndex].toFloat()
            }
        }
    }

    val sigmaClipHistogram = Histogram(imageSuppliers.size + 1)

    val stackingMethod: (FloatArray) -> Float = when (algorithm) {
        StackAlgorithm.Median -> { array -> array.median() }
        StackAlgorithm.Average -> { array -> array.average() }
        StackAlgorithm.Max -> { array -> array.maxOrNull() ?: 0.0f }
        StackAlgorithm.Min -> { array -> array.minOrNull() ?: 0.0f }
        StackAlgorithm.SigmaClipMedian -> { array ->
            val clippedLength = array.sigmaClipInplace(kappa.toFloat(), iterations, histogram = sigmaClipHistogram)
            array.medianInplace(0, clippedLength)
        }

        StackAlgorithm.SigmaClipAverage -> { array ->
            val clippedLength = array.sigmaClipInplace(kappa.toFloat(), iterations, histogram = sigmaClipHistogram)
            array.average(0, clippedLength)
        }

        StackAlgorithm.SigmaWinsorizeMedian -> { array ->
            array.sigmaWinsorizeInplace(kappa.toFloat())
            array.medianInplace()
        }

        StackAlgorithm.SigmaWinsorizeAverage -> { array ->
            array.sigmaWinsorizeInplace(kappa.toFloat())
            array.average()
        }

        StackAlgorithm.WinsorizedSigmaClipMedian -> { array ->
            val clippedLength = array.huberWinsorizedSigmaClipInplace(
                kappa = kappa.toFloat(),
                iterations,
                histogram = sigmaClipHistogram
            )
            array.medianInplace(0, clippedLength)
        }

        StackAlgorithm.WinsorizedSigmaClipAverage -> { array ->
            val clippedLength = array.huberWinsorizedSigmaClipInplace(
                kappa = kappa.toFloat(),
                iterations,
                histogram = sigmaClipHistogram
            )
            array.average(0, clippedLength)
        }

        StackAlgorithm.SigmaClipWeightedMedian -> { array ->
            val clippedLength = array.sigmaClipInplace(kappa.toFloat(), iterations, histogram = sigmaClipHistogram)
            val median = array.median(0, clippedLength)
            val sigma = array.stddev(StandardDeviation.Population, 0, clippedLength)
            array.weightedAverage({ _, v ->
                if (sigma == 0.0f) {
                    1.0f
                } else {
                    val x = abs(v - median) / sigma
                    1 / (sqrt(x + 1))
                }
            }, 0, clippedLength)
        }
    }

    val resultImage = MatrixImage(baseImage.width, baseImage.height, channels)
    val values = FloatArray(imageSuppliers.size)
    for (channelIndex in channels.indices) {
        val channel = channels[channelIndex]
        val matrix = baseImage[channel]
        val resultMatrix = resultImage[channel]
        for (matrixIndex in 0 until matrix.size) {
            for (imageIndex in imageSuppliers.indices) {
                values[imageIndex] = huge[imageIndex, channelIndex, matrixIndex]
            }

            val stackedValue = stackingMethod(values)
            resultMatrix[matrixIndex] = stackedValue.toDouble()
        }
    }
    return resultImage
}
