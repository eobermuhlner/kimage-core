package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.filter.sobelFilter
import ch.obermuhlner.kimage.core.matrix.filter.unsharpMaskFilter
import ch.obermuhlner.kimage.filter.GaussianBlurFilter

fun Image.boxBlur3Filter(): Image = this.kernelFilter(KernelFilter.BoxBlur3)

fun Image.gaussianBlur3Filter(): Image = this.kernelFilter(KernelFilter.GaussianBlur3)

fun Image.gaussianBlur5Filter(): Image = this.kernelFilter(KernelFilter.GaussianBlur5)

fun Image.gaussianBlur7Filter(): Image = this.kernelFilter(KernelFilter.GaussianBlur7)

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
