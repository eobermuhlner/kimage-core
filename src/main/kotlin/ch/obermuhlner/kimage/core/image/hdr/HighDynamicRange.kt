package ch.obermuhlner.kimage.core.image.hdr

import ch.obermuhlner.kimage.core.huge.HugeMultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.huge.MultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.filter.KernelFilter
import ch.obermuhlner.kimage.core.math.weightedAverage
import ch.obermuhlner.kimage.core.matrix.filter.convolute
import ch.obermuhlner.kimage.core.matrix.filter.gaussianBlurFilter
import kotlin.math.exp
import kotlin.math.pow

fun highDynamicRange(
    imageSuppliers: List<() -> Image>,
    saturationBlurRadius: Int = 3,
    contrastWeight: Double = 0.2,
    saturationWeight: Double = 0.1,
    exposureWeight: Double = 1.0,
    multiDimensionalFloatArraySupplier: (Int, Int, Int) -> MultiDimensionalFloatArray = { dim1, dim2, dim3 -> HugeMultiDimensionalFloatArray(dim1, dim2, dim3) },
): Image {
    var baseImage = imageSuppliers[0]()
    val channels = baseImage.channels

    val weightChannelIndex = channels.size
    val hugeMatrixChannelCount = weightChannelIndex + 1

    val huge = multiDimensionalFloatArraySupplier(imageSuppliers.size, hugeMatrixChannelCount, baseImage.width * baseImage.height)

    for (imageIndex in imageSuppliers.indices) {
        val image = if (imageIndex == 0) {
            baseImage
        } else {
            imageSuppliers[imageIndex]()
        }

        for (channelIndex in channels.indices) {
            val matrix = image[channels[channelIndex]]
            for (matrixIndex in 0 until matrix.size) {
                huge[imageIndex, channelIndex, matrixIndex] = matrix[matrixIndex].toFloat()
            }
        }

        val luminanceMatrix = image[Channel.Luminance]
        val saturationMatrix = image[Channel.Saturation].gaussianBlurFilter(saturationBlurRadius)
        val contrastMatrix = luminanceMatrix.convolute(KernelFilter.EdgeDetectionStrong)

        for (matrixIndex in 0 until luminanceMatrix.size) {
            val wellExposed = exp(-(luminanceMatrix[matrixIndex] - 0.5).pow(2)/0.08)
            val contrast = contrastMatrix[matrixIndex]
            val saturation = saturationMatrix[matrixIndex]
            val weight = contrast.pow(1.0) * contrastWeight +
                    saturation.pow(1.0) * saturationWeight +
                    wellExposed.pow(0.2) * exposureWeight
            huge[imageIndex, weightChannelIndex, matrixIndex] = weight.toFloat()
        }
    }

    val stackingMethod: (FloatArray, FloatArray) -> Float = { weightValues, values ->
        values.weightedAverage({ i, _ ->
            weightValues[i]
        })
    }

    val resultImage = MatrixImage(baseImage.width, baseImage.height, channels)
    val values = FloatArray(imageSuppliers.size)
    val weightValues = FloatArray(imageSuppliers.size)
    for (channelIndex in channels.indices) {
        val channel = channels[channelIndex]
        val matrix = baseImage[channel]
        val resultMatrix = resultImage[channel]
        for (matrixIndex in 0 until matrix.size) {
            for (imageIndex in imageSuppliers.indices) {
                values[imageIndex] = huge[imageIndex, channelIndex, matrixIndex]
                weightValues[imageIndex] = huge[imageIndex, weightChannelIndex, matrixIndex]
            }

            val stackedValue = stackingMethod(weightValues, values)
            resultMatrix[matrixIndex] = stackedValue.toDouble()
        }
    }

    return resultImage
}