package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.filter.richardsonLucyDeconvolution
import ch.obermuhlner.kimage.core.matrix.filter.sobelFilter
import ch.obermuhlner.kimage.core.matrix.filter.unsharpMaskFilter
import ch.obermuhlner.kimage.core.matrix.filter.wienerDeconvolution
import ch.obermuhlner.kimage.filter.GaussianBlurFilter

fun Image.boxBlur3Filter(): Image = this.kernelFilter(KernelFilter.BoxBlur3)

fun Image.gaussianBlur3Filter(): Image = this.kernelFilter(KernelFilter.GaussianBlur3)

fun Image.gaussianBlur5Filter(): Image = this.kernelFilter(KernelFilter.GaussianBlur5)

fun Image.gaussianBlur7Filter(): Image = this.kernelFilter(KernelFilter.GaussianBlur7)

fun Image.embossFilter(): Image = this.kernelFilter(KernelFilter.Emboss)

fun Image.laplacianFilter(): Image = this.kernelFilter(KernelFilter.Laplacian)

fun Image.edgeEnhancementFilter(): Image = this.kernelFilter(KernelFilter.EdgeEnhancement)

fun Image.motionBlurFilter(): Image = this.kernelFilter(KernelFilter.MotionBlur)

fun Image.highPassFilter(): Image = this.kernelFilter(KernelFilter.HighPass)

fun Image.gaussianBlurFilter(radius: Int): Image = GaussianBlurFilter(radius).filter(this)

fun Image.averageFilter(radius: Int, shape: Shape = Shape.Square): Image = AverageFilter(radius, shape).filter(this)

fun Image.medianFilter(radius: Int, recursive: Boolean = false): Image = FastMedianFilter(radius, recursive).filter(this)

fun Image.medianPixelFilter(radius: Int): Image = FastMedianFilter(radius).filter(this)

fun Image.slowMedianFilter(radius: Int, shape: Shape = Shape.Square, recursive: Boolean = false): Image = MedianFilter(radius, shape, recursive).filter(this)

fun Image.unsharpMaskFilter(radius: Int, strength: Double): Image = MatrixImageFilter({ _, matrix -> matrix.unsharpMaskFilter(radius, strength) }).filter(this)

fun Image.kernelFilter(kernel: Matrix): Image = KernelFilter(kernel).filter(this)

fun Image.sharpenFilter(): Image = this.kernelFilter(KernelFilter.Sharpen)

fun Image.edgeDetectionStrongFilter(): Image = this.kernelFilter(KernelFilter.EdgeDetectionStrong)

fun Image.edgeDetectionCrossFilter(): Image = this.kernelFilter(KernelFilter.EdgeDetectionCross)

fun Image.edgeDetectionDiagonalFilter(): Image = this.kernelFilter(KernelFilter.EdgeDetectionDiagonal)

fun Image.sobelFilter(sobelHorizontal: Matrix = KernelFilter.SobelHorizontal3, sobelVertical: Matrix = KernelFilter.SobelVertical3): Image
    = MatrixImageFilter(
        { _, matrix ->
            matrix.sobelFilter(sobelHorizontal, sobelVertical)
        }
    ).filter(this)

fun Image.sobel3Filter() = this.sobelFilter(KernelFilter.SobelHorizontal3, KernelFilter.SobelVertical3)

fun Image.sobel5Filter() = this.sobelFilter(KernelFilter.SobelHorizontal5, KernelFilter.SobelVertical5)

fun Image.richardsonLucyDeconvolution(psfSigma: Double = 1.5, iterations: Int = 20): Image {
    // Kernel size must be odd and large enough to capture the Gaussian
    val minSize = (psfSigma * 6).toInt().coerceAtLeast(7)
    val size = if (minSize % 2 == 0) minSize + 1 else minSize
    val center = size / 2
    val psf = DoubleMatrix.matrixOf(size, size) { row, col ->
        val dx = row - center
        val dy = col - center
        val dist2 = dx * dx + dy * dy
        kotlin.math.exp(-dist2 / (2 * psfSigma * psfSigma))
    }
    // Normalize PSF so its values sum to 1.0
    val sum = (0 until psf.rows).sumOf { row -> (0 until psf.cols).sumOf { col -> psf[row, col] } }
    val normalizedPsf = DoubleMatrix.matrixOf(size, size) { row, col -> psf[row, col] / sum }
    return this.richardsonLucyDeconvolution(normalizedPsf, iterations)
}

fun Image.richardsonLucyDeconvolution(psf: Matrix, iterations: Int = 20): Image = MatrixImageFilter(
    { _, matrix -> matrix.richardsonLucyDeconvolution(psf, iterations) }
).filter(this)

fun Image.wienerDeconvolution(psfSigma: Double = 1.5, iterations: Int = 10, noiseLevel: Double = 0.01): Image {
    val minSize = (psfSigma * 6).toInt().coerceAtLeast(7)
    val size = if (minSize % 2 == 0) minSize + 1 else minSize
    val center = size / 2
    val psf = DoubleMatrix.matrixOf(size, size) { row, col ->
        val dx = row - center
        val dy = col - center
        val dist2 = dx * dx + dy * dy
        kotlin.math.exp(-dist2 / (2 * psfSigma * psfSigma))
    }
    // Normalize PSF so its values sum to 1.0
    val sum = (0 until psf.rows).sumOf { row -> (0 until psf.cols).sumOf { col -> psf[row, col] } }
    val normalizedPsf = DoubleMatrix.matrixOf(size, size) { row, col -> psf[row, col] / sum }
    return this.wienerDeconvolution(normalizedPsf, iterations, noiseLevel)
}

fun Image.wienerDeconvolution(psf: Matrix, iterations: Int = 10, noiseLevel: Double = 0.01): Image = MatrixImageFilter(
    { _, matrix -> matrix.wienerDeconvolution(psf, iterations, noiseLevel) }
).filter(this)
