package ch.obermuhlner.kimage.core.image.stack

import ch.obermuhlner.kimage.core.huge.HugeMultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.huge.MultiDimensionalFloatArray
import java.io.File
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
 *
 * @param maxDiskSpaceBytes maximum bytes to use for temp mmap files in the two-pass path.
 *   When the required storage would exceed this limit, a tile-based approach is used instead,
 *   trading I/O (images are re-loaded per tile) for disk space. Use [Long.MAX_VALUE] to always
 *   use mmap (default), or `0`/`1` to force single-row tiles.
 */
fun drizzle(
    frames: List<Pair<() -> Image, Matrix>>,
    config: DrizzleConfig = DrizzleConfig(),
    tempDir: File? = null,
    maxDiskSpaceBytes: Long = Long.MAX_VALUE,
    onImageLoaded: ((Image) -> Unit)? = null,
): Image {
    require(frames.isNotEmpty()) { "frames must not be empty" }

    return if (config.rejection == DrizzleRejection.None) {
        drizzleSinglePass(frames, config, onImageLoaded)
    } else {
        drizzleTwoPass(frames, config, tempDir, maxDiskSpaceBytes, onImageLoaded)
    }
}

/** Convenience overload that accepts pre-loaded images (wraps each in a supplier). */
fun drizzle(
    frames: List<Pair<Image, Matrix>>,
    config: DrizzleConfig = DrizzleConfig(),
    tempDir: File? = null,
    maxDiskSpaceBytes: Long = Long.MAX_VALUE,
): Image = drizzle(frames.map { (img, m) -> ({ img } to m) }, config, tempDir, maxDiskSpaceBytes)

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

private fun drizzleTwoPass(
    frames: List<Pair<() -> Image, Matrix>>,
    config: DrizzleConfig,
    tempDir: File?,
    maxDiskSpaceBytes: Long,
    onImageLoaded: ((Image) -> Unit)?,
): Image {
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

    // Per-frame flux:   [frameIndex, channelIndex, pixelIndex]
    // Per-frame weight: [frameIndex, 0,            pixelIndex]  (channel-independent)
    // Each element is a Float (4 bytes)
    val requiredBytes = frames.size.toLong() * (channels.size + 1) * outW.toLong() * outH.toLong() * 4L

    return if (requiredBytes <= maxDiskSpaceBytes) {
        drizzleTwoPassFull(frames, firstImage, config, tempDir, channels, outW, outH, centerX, centerY, halfSize, cropOffsetX, cropOffsetY, onImageLoaded)
    } else {
        // Tile height: how many output rows fit within the disk budget
        val tileHeight = if (maxDiskSpaceBytes <= 0L) {
            1
        } else {
            (maxDiskSpaceBytes / (frames.size.toLong() * (channels.size + 1) * outW.toLong() * 4L)).toInt().coerceAtLeast(1)
        }
        drizzleTwoPassTiled(frames, firstImage, config, tempDir, channels, outW, outH, centerX, centerY, halfSize, cropOffsetX, cropOffsetY, tileHeight, onImageLoaded)
    }
}

private fun drizzleTwoPassFull(
    frames: List<Pair<() -> Image, Matrix>>,
    firstImage: Image,
    config: DrizzleConfig,
    tempDir: File?,
    channels: List<ch.obermuhlner.kimage.core.image.Channel>,
    outW: Int,
    outH: Int,
    centerX: Double,
    centerY: Double,
    halfSize: Double,
    cropOffsetX: Double,
    cropOffsetY: Double,
    onImageLoaded: ((Image) -> Unit)?,
): Image {
    val numPixels = outW * outH
    val perFrameFlux   = HugeMultiDimensionalFloatArray(frames.size, channels.size, numPixels, tempDir = tempDir)
    val perFrameWeight = HugeMultiDimensionalFloatArray(frames.size, 1, numPixels, tempDir = tempDir)

    try {
        fun accumulateFrame(fi: Int, image: Image, transformationMatrix: Matrix) {
            forEachOverlap(image, transformationMatrix, config.kernel, centerX, centerY, config.scale, halfSize, cropOffsetX, cropOffsetY, outW, outH) { oy, ox, xIn, yIn, w ->
                val pixelIndex = oy * outW + ox
                perFrameWeight[fi, 0, pixelIndex] = perFrameWeight[fi, 0, pixelIndex] + w.toFloat()
                for (ci in channels.indices) {
                    val flux = image.getPixel(xIn, yIn, channels[ci])
                    perFrameFlux[fi, ci, pixelIndex] = perFrameFlux[fi, ci, pixelIndex] + (flux * w).toFloat()
                }
            }
        }

        accumulateFrame(0, firstImage, frames[0].second)
        for (fi in 1 until frames.size) {
            val image = frames[fi].first()
            onImageLoaded?.invoke(image)
            accumulateFrame(fi, image, frames[fi].second)
        }

        return combinePerFrameBuffers(frames.size, channels, outW, outH, config, perFrameFlux, perFrameWeight)
    } finally {
        perFrameFlux.close()
        perFrameWeight.close()
    }
}

private fun drizzleTwoPassTiled(
    frames: List<Pair<() -> Image, Matrix>>,
    firstImage: Image,
    config: DrizzleConfig,
    tempDir: File?,
    channels: List<ch.obermuhlner.kimage.core.image.Channel>,
    outW: Int,
    outH: Int,
    centerX: Double,
    centerY: Double,
    halfSize: Double,
    cropOffsetX: Double,
    cropOffsetY: Double,
    tileHeight: Int,
    onImageLoaded: ((Image) -> Unit)?,
): Image {
    val resultMatrices = channels.map { FloatMatrix(outH, outW) }
    val values = FloatArray(frames.size)

    var yStart = 0
    while (yStart < outH) {
        val yEnd = min(yStart + tileHeight, outH)
        val tilePixels = outW * (yEnd - yStart)

        HugeMultiDimensionalFloatArray(frames.size, channels.size, tilePixels, tempDir = tempDir).use { perFrameFlux ->
        HugeMultiDimensionalFloatArray(frames.size, 1, tilePixels, tempDir = tempDir).use { perFrameWeight ->

        // For each frame: load image (reuse firstImage for fi=0), accumulate into tile buffers
        for (fi in frames.indices) {
            val image: Image
            if (fi == 0) {
                image = firstImage
                // onImageLoaded was already called for firstImage before drizzleTwoPass dispatched
            } else {
                image = frames[fi].first()
                // Only call onImageLoaded on the first tile to avoid duplicate notifications
                if (yStart == 0) onImageLoaded?.invoke(image)
            }

            forEachOverlap(image, frames[fi].second, config.kernel, centerX, centerY, config.scale, halfSize, cropOffsetX, cropOffsetY, outW, outH) { oy, ox, xIn, yIn, w ->
                if (oy < yStart || oy >= yEnd) return@forEachOverlap
                val tilePixelIndex = (oy - yStart) * outW + ox
                perFrameWeight[fi, 0, tilePixelIndex] = perFrameWeight[fi, 0, tilePixelIndex] + w.toFloat()
                for (ci in channels.indices) {
                    val flux = image.getPixel(xIn, yIn, channels[ci])
                    perFrameFlux[fi, ci, tilePixelIndex] = perFrameFlux[fi, ci, tilePixelIndex] + (flux * w).toFloat()
                }
            }
        }

        // Combine tile
        for (ci in channels.indices) {
            val resultMatrix = resultMatrices[ci]
            for (tilePixelIndex in 0 until tilePixels) {
                val oy = yStart + tilePixelIndex / outW
                val ox = tilePixelIndex % outW

                var count = 0
                for (fi in frames.indices) {
                    val w = perFrameWeight[fi, 0, tilePixelIndex]
                    if (w > 0f) {
                        values[count++] = perFrameFlux[fi, ci, tilePixelIndex] / w
                    }
                }
                if (count == 0) {
                    resultMatrix[oy, ox] = 0.0
                    continue
                }
                val kept = when (config.rejection) {
                    DrizzleRejection.SigmaClip -> values.sigmaClipInplace(config.kappa.toFloat(), config.iterations, 0, count)
                    DrizzleRejection.Winsorize -> { values.sigmaWinsorizeInplace(config.kappa.toFloat(), 0, count); count }
                    DrizzleRejection.None      -> count
                }
                resultMatrix[oy, ox] = values.average(0, kept).toDouble()
            }
        }

        }} // close perFrameWeight and perFrameFlux
        yStart = yEnd
    }

    return MatrixImage(outW, outH, channels, resultMatrices)
}

// ── Shared per-frame combine (used by full mmap path) ─────────────────────────

private fun combinePerFrameBuffers(
    numFrames: Int,
    channels: List<ch.obermuhlner.kimage.core.image.Channel>,
    outW: Int,
    outH: Int,
    config: DrizzleConfig,
    perFrameFlux: MultiDimensionalFloatArray,
    perFrameWeight: MultiDimensionalFloatArray,
): Image {
    val values = FloatArray(numFrames)
    val resultMatrices = channels.map { FloatMatrix(outH, outW) }
    val numPixels = outW * outH

    for (ci in channels.indices) {
        val resultMatrix = resultMatrices[ci]
        for (pixelIndex in 0 until numPixels) {
            var count = 0
            for (fi in 0 until numFrames) {
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

            val xOut = (m00 * dx + m01 * dy + m02 + centerX - cropOffsetX) * scale + (scale - 1.0) / 2.0
            val yOut = (m10 * dx + m11 * dy + m12 + centerY - cropOffsetY) * scale + (scale - 1.0) / 2.0

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
