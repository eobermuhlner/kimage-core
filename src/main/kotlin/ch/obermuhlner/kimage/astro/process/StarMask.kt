package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.image.filter.gaussianBlurFilter
import ch.obermuhlner.kimage.core.image.filter.medianFilter
import ch.obermuhlner.kimage.core.math.Histogram
import ch.obermuhlner.kimage.core.matrix.FloatMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.erode.dilate
import ch.obermuhlner.kimage.core.matrix.erode.erode
import kotlin.math.sqrt

enum class StarMaskAlgorithm {
    /** FWHM-based ellipses drawn for each star found by findStars (original behaviour). */
    FwhmEllipse,
    /** Morphological white top-hat: original − open(original, disk). */
    WhiteTopHat,
    /** Image minus a heavily blurred version of itself, then threshold. */
    GaussianBlurDiff,
    /** Difference of two Gaussian-blurred versions at different scales, then threshold. */
    DifferenceOfGaussians,
    /** BFS region-growing from findStars peaks until pixel drops below background fraction. */
    RegionGrowing,
    /** 2D image-moment (weighted variance) PSF fit per star from findStars for accurate radii. */
    GaussianPsfFit,
    /** Per-pixel: flag pixels whose value exceeds kappa × local median. */
    AdaptiveLocalThreshold,
    /** Pixels above the Kth percentile of the channel histogram. */
    LuminancePercentile,
}

fun buildStarMask(image: Image, stars: () -> List<Star>, cfg: ExtractStarsConfig): Matrix =
    when (cfg.starMaskAlgorithm) {
        StarMaskAlgorithm.FwhmEllipse ->
            buildFwhmEllipseMask(image, stars(), cfg.factor, cfg.softMaskBlurRadius)
        StarMaskAlgorithm.WhiteTopHat ->
            buildWhiteTopHatMask(image, cfg.diskRadius, cfg.maskThreshold, cfg.softMaskBlurRadius, cfg.channel)
        StarMaskAlgorithm.GaussianBlurDiff ->
            buildGaussianBlurDiffMask(image, cfg.blurRadius, cfg.maskThreshold, cfg.softMaskBlurRadius, cfg.channel)
        StarMaskAlgorithm.DifferenceOfGaussians ->
            buildDogMask(image, cfg.dogRadius1, cfg.dogRadius2, cfg.maskThreshold, cfg.softMaskBlurRadius, cfg.channel)
        StarMaskAlgorithm.RegionGrowing ->
            buildRegionGrowingMask(image, stars(), cfg.growthFactor, cfg.softMaskBlurRadius, cfg.channel)
        StarMaskAlgorithm.GaussianPsfFit ->
            buildGaussianPsfFitMask(image, stars(), cfg.factor, cfg.softMaskBlurRadius, cfg.channel)
        StarMaskAlgorithm.AdaptiveLocalThreshold ->
            buildAdaptiveLocalThresholdMask(image, cfg.windowRadius, cfg.kappa, cfg.softMaskBlurRadius, cfg.channel)
        StarMaskAlgorithm.LuminancePercentile ->
            buildLuminancePercentileMask(image, cfg.percentile, cfg.softMaskBlurRadius, cfg.channel)
    }

private fun applyBlur(mask: Matrix, blurRadius: Int): Matrix {
    if (blurRadius <= 0) return mask
    return MatrixImage(mask).gaussianBlurFilter(blurRadius)[Channel.Red]
}

private fun buildFwhmEllipseMask(image: Image, stars: List<Star>, factor: Double, blurRadius: Int): Matrix {
    val maskMatrix = FloatMatrix(image.height, image.width) { _, _ -> 0f }
    val width = image.width
    val height = image.height
    for (star in stars) {
        val radiusX = (star.fwhmX * factor / 2 + 0.5).toInt().coerceAtLeast(1)
        val radiusY = (star.fwhmY * factor / 2 + 0.5).toInt().coerceAtLeast(1)
        val cx = star.intX
        val cy = star.intY
        for (y in (cy - radiusY)..(cy + radiusY)) {
            for (x in (cx - radiusX)..(cx + radiusX)) {
                if (x in 0 until width && y in 0 until height) {
                    val dx = (x - cx).toDouble() / radiusX
                    val dy = (y - cy).toDouble() / radiusY
                    if (dx * dx + dy * dy <= 1.0) {
                        maskMatrix[y, x] = 1.0
                    }
                }
            }
        }
    }
    return applyBlur(maskMatrix, blurRadius)
}

private fun buildWhiteTopHatMask(image: Image, diskRadius: Int, maskThreshold: Double, blurRadius: Int, channel: Channel): Matrix {
    val gray = image[channel]
    val opened = gray.erode(diskRadius).dilate(diskRadius)
    val topHat = gray - opened
    val binaryMask = gray.create()
    binaryMask.applyEach { row, col, _ ->
        if (topHat[row, col] >= maskThreshold) 1.0 else 0.0
    }
    return applyBlur(binaryMask, blurRadius)
}

private fun buildGaussianBlurDiffMask(image: Image, blurRadius: Int, maskThreshold: Double, softBlurRadius: Int, channel: Channel): Matrix {
    val gray = image[channel]
    val blurred = image.gaussianBlurFilter(blurRadius)[channel]
    val diff = gray - blurred
    val binaryMask = gray.create()
    binaryMask.applyEach { row, col, _ ->
        if (diff[row, col] >= maskThreshold) 1.0 else 0.0
    }
    return applyBlur(binaryMask, softBlurRadius)
}

private fun buildDogMask(image: Image, dogRadius1: Int, dogRadius2: Int, maskThreshold: Double, blurRadius: Int, channel: Channel): Matrix {
    val blurred1 = image.gaussianBlurFilter(dogRadius1)[channel]
    val blurred2 = image.gaussianBlurFilter(dogRadius2)[channel]
    val dog = blurred1 - blurred2
    val binaryMask = blurred1.create()
    binaryMask.applyEach { row, col, _ ->
        if (dog[row, col] >= maskThreshold) 1.0 else 0.0
    }
    return applyBlur(binaryMask, blurRadius)
}

private fun buildRegionGrowingMask(image: Image, stars: List<Star>, growthFactor: Double, blurRadius: Int, channel: Channel): Matrix {
    val gray = image[channel]
    val width = image.width
    val height = image.height

    val histogram = Histogram()
    histogram.add(gray)
    val background = histogram.estimateMedian()

    val maskMatrix = FloatMatrix(height, width) { _, _ -> 0f }
    val visited = Array(height) { BooleanArray(width) }
    val queue = ArrayDeque<PointXY>()

    for (star in stars) {
        val cx = star.intX
        val cy = star.intY
        if (cx !in 0 until width || cy !in 0 until height || visited[cy][cx]) continue
        val peak = gray[cy, cx]
        val growthThreshold = background + growthFactor * (peak - background)
        visited[cy][cx] = true
        queue.add(PointXY(cx, cy))
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            maskMatrix[y, x] = 1.0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height && !visited[ny][nx] && gray[ny, nx] >= growthThreshold) {
                        visited[ny][nx] = true
                        queue.add(PointXY(nx, ny))
                    }
                }
            }
        }
    }

    return applyBlur(maskMatrix, blurRadius)
}

private fun buildGaussianPsfFitMask(image: Image, stars: List<Star>, factor: Double, blurRadius: Int, channel: Channel): Matrix {
    val gray = image[channel]
    val width = image.width
    val height = image.height
    val maskMatrix = FloatMatrix(height, width) { _, _ -> 0f }

    for (star in stars) {
        val cx = star.intX
        val cy = star.intY
        val windowR = (maxOf(star.fwhmX, star.fwhmY) * 3).toInt().coerceAtLeast(3).coerceAtMost(50)

        // Estimate local background as the minimum pixel value in the window,
        // then subtract it so background pixels contribute zero weight to the moments.
        var localBg = Double.MAX_VALUE
        for (dy in -windowR..windowR) {
            for (dx in -windowR..windowR) {
                val nx = cx + dx; val ny = cy + dy
                if (nx in 0 until width && ny in 0 until height)
                    localBg = minOf(localBg, gray[ny, nx])
            }
        }
        if (localBg == Double.MAX_VALUE) localBg = 0.0

        var sumW = 0.0
        var sumWX2 = 0.0
        var sumWY2 = 0.0
        for (dy in -windowR..windowR) {
            for (dx in -windowR..windowR) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until width && ny in 0 until height) {
                    val w = (gray[ny, nx] - localBg).coerceAtLeast(0.0)
                    sumW += w
                    sumWX2 += w * dx * dx
                    sumWY2 += w * dy * dy
                }
            }
        }

        val sigmaX = if (sumW > 0) sqrt(sumWX2 / sumW) else 1.0
        val sigmaY = if (sumW > 0) sqrt(sumWY2 / sumW) else 1.0
        val fwhmX = 2.355 * sigmaX
        val fwhmY = 2.355 * sigmaY

        val radiusX = (fwhmX * factor / 2 + 0.5).toInt().coerceAtLeast(1)
        val radiusY = (fwhmY * factor / 2 + 0.5).toInt().coerceAtLeast(1)

        for (dy in -radiusY..radiusY) {
            for (dx in -radiusX..radiusX) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until width && ny in 0 until height) {
                    val px = dx.toDouble() / radiusX
                    val py = dy.toDouble() / radiusY
                    if (px * px + py * py <= 1.0) {
                        maskMatrix[ny, nx] = 1.0
                    }
                }
            }
        }
    }

    return applyBlur(maskMatrix, blurRadius)
}

private fun buildAdaptiveLocalThresholdMask(image: Image, windowRadius: Int, kappa: Double, blurRadius: Int, channel: Channel): Matrix {
    val gray = image[channel]
    val localMedian = image.medianFilter(windowRadius)[channel]
    val binaryMask = gray.create()
    binaryMask.applyEach { row, col, _ ->
        val local = localMedian[row, col]
        val pixel = gray[row, col]
        if (local > 0.0 && pixel > local * kappa) 1.0 else 0.0
    }
    return applyBlur(binaryMask, blurRadius)
}

private fun buildLuminancePercentileMask(image: Image, percentile: Double, blurRadius: Int, channel: Channel): Matrix {
    val gray = image[channel]
    val histogram = Histogram()
    histogram.add(gray)
    val threshold = histogram.estimatePercentile(percentile)
    val binaryMask = gray.create()
    binaryMask.applyEach { row, col, _ ->
        if (gray[row, col] >= threshold) 1.0 else 0.0
    }
    return applyBlur(binaryMask, blurRadius)
}
