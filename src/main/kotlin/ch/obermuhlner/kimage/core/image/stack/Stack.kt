package ch.obermuhlner.kimage.core.image.stack

import ch.obermuhlner.kimage.core.huge.HugeMultiDimensionalDoubleArray
import ch.obermuhlner.kimage.core.huge.HugeMultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.huge.MultiDimensionalDoubleArray
import ch.obermuhlner.kimage.core.huge.MultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.huge.SimpleMultiDimensionalDoubleArray
import ch.obermuhlner.kimage.core.huge.SimpleMultiDimensionalFloatArray
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
import ch.obermuhlner.kimage.core.matrix.stack.maxInPlace
import java.io.Closeable
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

fun max(firstImage: Image, vararg otherImages: Image): Image {
    return MatrixImage(firstImage.width, firstImage.height, firstImage.channels) { channel, _, _ ->
        max(firstImage[channel], *otherImages.map { it[channel] }.toTypedArray())
    }
}

fun maxInPlace(result: MatrixImage, other: Image) {
    for (channel in result.channels) {
        maxInPlace(result[channel], other[channel])
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
    SmartMax,
    WinsorizedSigmaClipMedian,
    WinsorizedSigmaClipAverage,
    SigmaClipWeightedMedian,
    Drizzle,
}

enum class StackPrecision {
    /** Store pixel values as 32-bit float. Half the disk/memory of Double; ~7 significant digits. */
    Float,
    /** Store pixel values as 64-bit double. Full precision; ~15 significant digits. */
    Double,
}

/**
 * Configuration for the [stack] function.
 *
 * @param algorithm         stacking algorithm to apply per pixel
 * @param kappa             sigma-clip threshold (used by sigma-clip and winsorize variants)
 * @param iterations        sigma-clip iteration count
 * @param precision         whether to store intermediate pixel values as Float or Double
 * @param tempDir           directory for memory-mapped temp files; null = system temp dir
 * @param maxDiskSpaceBytes maximum bytes allowed for memory-mapped temp files.
 *   The required disk space is calculated as `numImages × numChannels × numPixels × bytesPerElement`.
 *   When the requirement exceeds this limit the implementation falls back to tile-based processing:
 *   each image is loaded in full but only the relevant strip of pixels is processed at a time,
 *   using in-memory buffers sized to stay within [maxDiskSpaceBytes].
 *   Set to 0 to always use tile-based (row-by-row).
 */
data class StackConfig(
    val algorithm: StackAlgorithm = StackAlgorithm.Median,
    val kappa: Double = 2.0,
    val iterations: Int = 10,
    val precision: StackPrecision = StackPrecision.Float,
    val tempDir: File? = null,
    val maxDiskSpaceBytes: Long = Long.MAX_VALUE,
)

// ── Internal pixel buffer abstraction ────────────────────────────────────────

private interface PixelBuffer : Closeable {
    fun store(imageIndex: Int, channelIndex: Int, pixelIndex: Int, value: Double)
    fun load(imageIndex: Int, channelIndex: Int, pixelIndex: Int): Double
}

private class FloatPixelBuffer(private val arr: MultiDimensionalFloatArray) : PixelBuffer {
    override fun store(i0: Int, i1: Int, i2: Int, v: Double) { arr[i0, i1, i2] = v.toFloat() }
    override fun load(i0: Int, i1: Int, i2: Int): Double = arr[i0, i1, i2].toDouble()
    override fun close() = arr.close()
}

private class DoublePixelBuffer(private val arr: MultiDimensionalDoubleArray) : PixelBuffer {
    override fun store(i0: Int, i1: Int, i2: Int, v: Double) { arr[i0, i1, i2] = v }
    override fun load(i0: Int, i1: Int, i2: Int): Double = arr[i0, i1, i2]
    override fun close() = arr.close()
}

private fun makeBuffer(precision: StackPrecision, numImages: Int, numChannels: Int, numPixels: Int, mmap: Boolean, tempDir: File?): PixelBuffer =
    when (precision) {
        StackPrecision.Float -> FloatPixelBuffer(
            if (mmap) HugeMultiDimensionalFloatArray(numImages, numChannels, numPixels, tempDir = tempDir)
            else SimpleMultiDimensionalFloatArray(numImages, numChannels, numPixels)
        )
        StackPrecision.Double -> DoublePixelBuffer(
            if (mmap) HugeMultiDimensionalDoubleArray(numImages, numChannels, numPixels, tempDir = tempDir)
            else SimpleMultiDimensionalDoubleArray(numImages, numChannels, numPixels)
        )
    }

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Stack a list of images using the given [StackConfig].
 *
 * The required disk space is calculated upfront.  If it fits within [StackConfig.maxDiskSpaceBytes]
 * all images are loaded into a single memory-mapped buffer and processed in one pass.
 * Otherwise the image set is processed in tiles: each image is loaded in full for each tile,
 * but only the relevant pixel range is kept in memory at a time.
 */
fun stack(
    imageSuppliers: List<() -> Image>,
    config: StackConfig = StackConfig(),
): Image {
    val baseImage: Image = imageSuppliers[0]()
    val channels = baseImage.channels
    val numImages = imageSuppliers.size
    val numChannels = channels.size
    val numPixels = baseImage.width * baseImage.height
    val bytesPerElement = if (config.precision == StackPrecision.Float) 4L else 8L
    val requiredDiskBytes = numImages.toLong() * numChannels * numPixels * bytesPerElement

    val useMmap = requiredDiskBytes <= config.maxDiskSpaceBytes
    val tileSize: Int = when {
        useMmap -> numPixels
        config.maxDiskSpaceBytes <= 0L -> baseImage.width  // row-by-row
        else -> (config.maxDiskSpaceBytes / (numImages.toLong() * numChannels * bytesPerElement))
                 .coerceAtLeast(1L).toInt()
    }

    val sigmaClipHistogram = Histogram(numImages + 1)
    val stackingMethod = buildDoubleStackingMethod(config.algorithm, config.kappa, config.iterations, sigmaClipHistogram)
    val values = DoubleArray(numImages)

    val resultImage = MatrixImage(baseImage.width, baseImage.height, channels)

    var pixelStart = 0
    while (pixelStart < numPixels) {
        val pixelEnd = minOf(pixelStart + tileSize, numPixels)
        val tileLength = pixelEnd - pixelStart

        makeBuffer(config.precision, numImages, numChannels, tileLength, true, config.tempDir).use { buf ->
            // Load phase: store pixel data for every image in this tile
            for (imageIndex in imageSuppliers.indices) {
                val image = if (imageIndex == 0) baseImage
                            else imageSuppliers[imageIndex]().crop(0, 0, baseImage.width, baseImage.height)
                for (channelIndex in channels.indices) {
                    val matrix = image[channels[channelIndex]]
                    for (tileIndex in 0 until tileLength) {
                        buf.store(imageIndex, channelIndex, tileIndex, matrix[pixelStart + tileIndex])
                    }
                }
            }

            // Process phase: apply stacking algorithm per pixel
            for (channelIndex in channels.indices) {
                val resultMatrix = resultImage[channels[channelIndex]]
                for (tileIndex in 0 until tileLength) {
                    for (imageIndex in 0 until numImages) {
                        values[imageIndex] = buf.load(imageIndex, channelIndex, tileIndex)
                    }
                    resultMatrix[pixelStart + tileIndex] = stackingMethod(values)
                }
            }
        }

        pixelStart = pixelEnd
    }

    return resultImage
}

/**
 * Backward-compatible overload. Prefer [stack] with [StackConfig] for new code.
 */
fun stack(
    imageSuppliers: List<() -> Image>,
    algorithm: StackAlgorithm = StackAlgorithm.Median,
    kappa: Double = 2.0,
    iterations: Int = 10,
    multiDimensionalFloatArraySupplier: (Int, Int, Int) -> MultiDimensionalFloatArray = { dim1, dim2, dim3 -> HugeMultiDimensionalFloatArray(dim1, dim2, dim3) }
): Image = stack(imageSuppliers, StackConfig(algorithm = algorithm, kappa = kappa, iterations = iterations))

// ── Algorithm builder ─────────────────────────────────────────────────────────

private fun buildDoubleStackingMethod(
    algorithm: StackAlgorithm,
    kappa: Double,
    iterations: Int,
    sigmaClipHistogram: Histogram,
): (DoubleArray) -> Double = when (algorithm) {
    StackAlgorithm.Median -> { arr -> arr.medianInplace() }
    StackAlgorithm.Average -> { arr -> arr.average() }
    StackAlgorithm.Max -> { arr -> arr.max() }
    StackAlgorithm.Min -> { arr -> arr.min() }

    StackAlgorithm.SigmaClipMedian -> { arr ->
        val n = arr.sigmaClipInplace(kappa, iterations, histogram = sigmaClipHistogram)
        arr.medianInplace(0, n)
    }
    StackAlgorithm.SigmaClipAverage -> { arr ->
        val n = arr.sigmaClipInplace(kappa, iterations, histogram = sigmaClipHistogram)
        arr.average(0, n)
    }
    StackAlgorithm.SigmaWinsorizeMedian -> { arr ->
        arr.sigmaWinsorizeInplace(kappa)
        arr.medianInplace()
    }
    StackAlgorithm.SigmaWinsorizeAverage -> { arr ->
        arr.sigmaWinsorizeInplace(kappa)
        arr.average()
    }
    StackAlgorithm.SmartMax -> { arr ->
        val n = arr.huberWinsorizedSigmaClipInplace(kappa, iterations, histogram = sigmaClipHistogram)
        arr.sort(0, n)
        arr[n - 1]
    }
    StackAlgorithm.WinsorizedSigmaClipMedian -> { arr ->
        val n = arr.huberWinsorizedSigmaClipInplace(kappa, iterations, histogram = sigmaClipHistogram)
        arr.medianInplace(0, n)
    }
    StackAlgorithm.WinsorizedSigmaClipAverage -> { arr ->
        val n = arr.huberWinsorizedSigmaClipInplace(kappa, iterations, histogram = sigmaClipHistogram)
        arr.average(0, n)
    }
    StackAlgorithm.SigmaClipWeightedMedian -> { arr ->
        val n = arr.sigmaClipInplace(kappa, iterations, histogram = sigmaClipHistogram)
        val med = arr.median(0, n)
        val sigma = arr.stddev(StandardDeviation.Population, 0, n)
        arr.weightedAverage({ _, v ->
            if (sigma == 0.0) 1.0 else 1.0 / sqrt(abs(v - med) / sigma + 1)
        }, 0, n)
    }
    StackAlgorithm.Drizzle -> error("Drizzle algorithm must be invoked via drizzle() directly, not stack()")
}
