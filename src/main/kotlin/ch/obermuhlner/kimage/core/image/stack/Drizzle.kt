package ch.obermuhlner.kimage.core.image.stack

import ch.obermuhlner.kimage.core.huge.HugeMultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.FloatMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.math.average
import ch.obermuhlner.kimage.core.math.sigmaClipInplace
import ch.obermuhlner.kimage.core.math.sigmaWinsorizeInplace
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class DrizzleKernel { Square, Gaussian }

enum class DrizzleRejection { None, SigmaClip, Winsorize }

data class DrizzleCropConfig(
    var enabled: Boolean = false,
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 0,
    var height: Int = 0,
)

data class DrizzleConfig(
    var scale: Double = 2.0,
    var pixfrac: Double = 0.7,
    var kernel: DrizzleKernel = DrizzleKernel.Square,
    var rejection: DrizzleRejection = DrizzleRejection.None,
    var kappa: Double = 2.0,
    var iterations: Int = 5,
    var crop: DrizzleCropConfig = DrizzleCropConfig(),
)

/**
 * Drizzle (Variable-Pixel Linear Reconstruction) image combination.
 *
 * Each frame is forward-mapped onto the output grid using its affine transformation matrix
 * (the forward map from input → reference frame, as returned by calculateTransformationMatrix).
 * Pixel flux is distributed across output cells weighted by the fractional area of overlap.
 *
 * When rejection != None, each frame is drizzled independently (two-pass) so hot pixels
 * and cosmic rays are excluded before the final combine.
 *
 * Images are loaded lazily one at a time so arbitrarily large frame sets do not require
 * all images to reside in the heap simultaneously. [onImageLoaded] is called with each
 * image immediately after it is loaded, before the reference is released.
 */
fun drizzle(
    frames: List<Pair<() -> Image, Matrix>>,
    config: DrizzleConfig = DrizzleConfig(),
    onImageLoaded: ((Image) -> Unit)? = null,
): Image {
    require(frames.isNotEmpty()) { "frames must not be empty" }

    return if (config.rejection == DrizzleRejection.None) {
        drizzleSinglePass(frames, config, onImageLoaded)
    } else {
        drizzleTwoPass(frames, config, onImageLoaded)
    }
}

/** Convenience overload that accepts pre-loaded images (wraps each in a supplier). */
fun drizzle(
    frames: List<Pair<Image, Matrix>>,
    config: DrizzleConfig = DrizzleConfig(),
): Image = drizzle(frames.map { (img, m) -> ({ img } to m) }, config)

// ── Single-pass (no rejection) ────────────────────────────────────────────────

private fun drizzleSinglePass(frames: List<Pair<() -> Image, Matrix>>, config: DrizzleConfig, onImageLoaded: ((Image) -> Unit)?): Image {
    val firstImage = frames[0].first()
    onImageLoaded?.invoke(firstImage)
    val channels = firstImage.channels
    val cropOffsetX = if (config.crop.enabled) config.crop.x.toDouble() else 0.0
    val cropOffsetY = if (config.crop.enabled) config.crop.y.toDouble() else 0.0
    val outW = ceil((if (config.crop.enabled) config.crop.width else firstImage.width) * config.scale).toInt()
    val outH = ceil((if (config.crop.enabled) config.crop.height else firstImage.height) * config.scale).toInt()
    val centerX = firstImage.width / 2.0
    val centerY = firstImage.height / 2.0
    val halfSize = config.pixfrac * config.scale / 2.0

    val fluxAccum = Array(channels.size) { FloatMatrix(outH, outW) }
    // Weight is channel-independent: same spatial overlap for all channels
    val weightAccum = FloatMatrix(outH, outW)

    fun accumulate(image: Image, transformationMatrix: Matrix) {
        forEachOverlap(image, transformationMatrix, config.kernel, centerX, centerY, config.scale, halfSize, cropOffsetX, cropOffsetY, outW, outH) { oy, ox, xIn, yIn, w ->
            weightAccum[oy, ox] += w
            for (ci in channels.indices) {
                fluxAccum[ci][oy, ox] += image.getPixel(xIn, yIn, channels[ci]) * w
            }
        }
    }

    accumulate(firstImage, frames[0].second)
    for (i in 1 until frames.size) {
        val image = frames[i].first()
        onImageLoaded?.invoke(image)
        accumulate(image, frames[i].second)
    }

    val resultMatrices = channels.mapIndexed { ci, _ ->
        FloatMatrix(outH, outW) { row, col ->
            val w = weightAccum[row, col]
            if (w > 0.0) (fluxAccum[ci][row, col] / w).toFloat() else 0.0f
        }
    }
    return MatrixImage(outW, outH, channels, resultMatrices)
}

// ── Two-pass (with rejection) ─────────────────────────────────────────────────

private fun drizzleTwoPass(frames: List<Pair<() -> Image, Matrix>>, config: DrizzleConfig, onImageLoaded: ((Image) -> Unit)?): Image {
    val firstImage = frames[0].first()
    onImageLoaded?.invoke(firstImage)
    val channels = firstImage.channels
    val cropOffsetX = if (config.crop.enabled) config.crop.x.toDouble() else 0.0
    val cropOffsetY = if (config.crop.enabled) config.crop.y.toDouble() else 0.0
    val outW = ceil((if (config.crop.enabled) config.crop.width else firstImage.width) * config.scale).toInt()
    val outH = ceil((if (config.crop.enabled) config.crop.height else firstImage.height) * config.scale).toInt()
    val centerX = firstImage.width / 2.0
    val centerY = firstImage.height / 2.0
    val halfSize = config.pixfrac * config.scale / 2.0
    val numPixels = outW * outH

    // Per-frame flux:   [frameIndex, channelIndex, pixelIndex]
    // Per-frame weight: [frameIndex, 0,            pixelIndex]  (channel-independent)
    val perFrameFlux   = HugeMultiDimensionalFloatArray(frames.size, channels.size, numPixels)
    val perFrameWeight = HugeMultiDimensionalFloatArray(frames.size, 1, numPixels)

    fun accumulateFrame(fi: Int, image: Image, transformationMatrix: Matrix) {
        forEachOverlap(image, transformationMatrix, config.kernel, centerX, centerY, config.scale, halfSize, cropOffsetX, cropOffsetY, outW, outH) { oy, ox, xIn, yIn, w ->
            val pixelIndex = oy * outW + ox
            // Weight accumulated once per spatial contribution (not per channel)
            perFrameWeight[fi, 0, pixelIndex] = perFrameWeight[fi, 0, pixelIndex] + w.toFloat()
            for (ci in channels.indices) {
                val flux = image.getPixel(xIn, yIn, channels[ci])
                perFrameFlux[fi, ci, pixelIndex] = perFrameFlux[fi, ci, pixelIndex] + (flux * w).toFloat()
            }
        }
    }

    // Pass 1: drizzle each frame into its own per-frame accumulator (load one image at a time)
    accumulateFrame(0, firstImage, frames[0].second)
    for (fi in 1 until frames.size) {
        val image = frames[fi].first()
        onImageLoaded?.invoke(image)
        accumulateFrame(fi, image, frames[fi].second)
    }

    // Pass 2: for each output pixel, collect per-frame normalised values, reject, average
    val values = FloatArray(frames.size)
    val resultMatrices = channels.map { FloatMatrix(outH, outW) }

    for (ci in channels.indices) {
        val resultMatrix = resultMatrices[ci]
        for (pixelIndex in 0 until numPixels) {
            var count = 0
            for (fi in frames.indices) {
                val w = perFrameWeight[fi, 0, pixelIndex]
                if (w > 0f) {
                    values[count++] = perFrameFlux[fi, ci, pixelIndex] / w
                }
            }
            if (count == 0) {
                resultMatrix[pixelIndex] = 0.0
                continue
            }
            val kept = when (config.rejection) {
                DrizzleRejection.SigmaClip -> values.sigmaClipInplace(config.kappa.toFloat(), config.iterations, 0, count)
                DrizzleRejection.Winsorize -> { values.sigmaWinsorizeInplace(config.kappa.toFloat(), 0, count); count }
                DrizzleRejection.None      -> count
            }
            resultMatrix[pixelIndex] = values.average(0, kept).toDouble()
        }
    }

    return MatrixImage(outW, outH, channels, resultMatrices)
}

// ── Forward-mapping loop: calls onHit for each (output pixel, input pixel) overlap ──

private inline fun forEachOverlap(
    image: Image,
    transformationMatrix: Matrix,
    kernel: DrizzleKernel,
    centerX: Double,
    centerY: Double,
    scale: Double,
    halfSize: Double,
    cropOffsetX: Double,
    cropOffsetY: Double,
    outW: Int,
    outH: Int,
    onHit: (oy: Int, ox: Int, xIn: Int, yIn: Int, w: Double) -> Unit,
) {
    val m00 = transformationMatrix[0, 0]; val m01 = transformationMatrix[0, 1]; val m02 = transformationMatrix[0, 2]
    val m10 = transformationMatrix[1, 0]; val m11 = transformationMatrix[1, 1]; val m12 = transformationMatrix[1, 2]

    for (yIn in 0 until image.height) {
        val dy = yIn.toDouble() - centerY
        for (xIn in 0 until image.width) {
            val dx = xIn.toDouble() - centerX

            val xOut = (m00 * dx + m01 * dy + m02 + centerX - cropOffsetX) * scale
            val yOut = (m10 * dx + m11 * dy + m12 + centerY - cropOffsetY) * scale

            val oyMin = floor(yOut - halfSize).toInt()
            val oyMax = ceil(yOut + halfSize).toInt()
            val oxMin = floor(xOut - halfSize).toInt()
            val oxMax = ceil(xOut + halfSize).toInt()

            for (oy in oyMin..oyMax) {
                if (oy < 0 || oy >= outH) continue
                for (ox in oxMin..oxMax) {
                    if (ox < 0 || ox >= outW) continue

                    val w = kernelWeight(kernel, xOut, yOut, ox, oy, halfSize)
                    if (w > 0.0) onHit(oy, ox, xIn, yIn, w)
                }
            }
        }
    }
}

// ── Kernel weight functions ───────────────────────────────────────────────────

private fun kernelWeight(
    kernel: DrizzleKernel,
    xOut: Double,
    yOut: Double,
    ox: Int,
    oy: Int,
    halfSize: Double,
): Double = when (kernel) {
    DrizzleKernel.Square -> {
        val overlapX = max(0.0, min(xOut + halfSize, ox + 1.0) - max(xOut - halfSize, ox.toDouble()))
        val overlapY = max(0.0, min(yOut + halfSize, oy + 1.0) - max(yOut - halfSize, oy.toDouble()))
        overlapX * overlapY
    }
    DrizzleKernel.Gaussian -> {
        val sigma = halfSize / 2.0
        if (sigma <= 0.0) {
            if (ox == xOut.toInt() && oy == yOut.toInt()) 1.0 else 0.0
        } else {
            val dx = xOut - (ox + 0.5)
            val dy = yOut - (oy + 0.5)
            exp(-(dx * dx + dy * dy) / (2.0 * sigma * sigma))
        }
    }
}
