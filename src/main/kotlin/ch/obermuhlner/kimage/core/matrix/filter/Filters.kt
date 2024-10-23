package ch.obermuhlner.kimage.core.matrix.filter

import ch.obermuhlner.kimage.core.image.filter.AverageFilter
import ch.obermuhlner.kimage.core.image.filter.FastMedianFilter
import ch.obermuhlner.kimage.core.image.filter.KernelFilter
import ch.obermuhlner.kimage.core.image.filter.MedianFilter
import ch.obermuhlner.kimage.core.image.filter.Shape
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.filter.GaussianBlurFilter
import kotlin.math.sqrt

fun Matrix.gaussianBlurFilter(radius: Int): Matrix = GaussianBlurFilter.blur(this, radius)

fun Matrix.averageFilter(radius: Int, shape: Shape = Shape.Square): Matrix = AverageFilter.averageMatrix(this, radius, shape)

fun Matrix.medianFilter(radius: Int, recursive: Boolean = false): Matrix = FastMedianFilter.fastMedianMatrix(this, radius, recursive)

fun Matrix.slowMedianFilter(radius: Int, shape: Shape = Shape.Square): Matrix = MedianFilter.medianMatrix(this, radius, shape)

fun Matrix.unsharpMaskFilter(radius: Int, strength: Double): Matrix {
    val blurred = this.gaussianBlurFilter(radius)
    val m = (this - blurred) * strength
    val m2 = (this + m)
    m2.applyEach { v -> clamp(v, 0.0, 1.0) }
    return m2
}

fun Matrix.sharpenFilter(): Matrix = this.convolute(KernelFilter.Sharpen)

fun Matrix.unsharpMaskFilter(): Matrix = this.convolute(KernelFilter.UnsharpMask)

fun Matrix.edgeDetectionStrongFilter(): Matrix = this.convolute(KernelFilter.EdgeDetectionStrong)

fun Matrix.edgeDetectionCrossFilter(): Matrix = this.convolute(KernelFilter.EdgeDetectionCross)

fun Matrix.edgeDetectionDiagonalFilter(): Matrix = this.convolute(KernelFilter.EdgeDetectionDiagonal)

fun Matrix.sobelFilter(sobelHorizontal: Matrix = KernelFilter.SobelHorizontal3, sobelVertical: Matrix = KernelFilter.SobelVertical3): Matrix {
    val gCol = this.convolute(sobelHorizontal)
    val gRow = this.convolute(sobelVertical)

    return DoubleMatrix.matrixOf(rows, cols) { row, col ->
        val pCol = gCol[row, col]
        val pRow = gRow[row, col]
        clamp(sqrt(pCol*pCol + pRow*pRow), 0.0, 1.0)
    }
}

fun Matrix.sobelFilter3() = this.sobelFilter(KernelFilter.SobelHorizontal3, KernelFilter.SobelVertical3)

fun Matrix.sobelFilter5() = this.sobelFilter(KernelFilter.SobelHorizontal5, KernelFilter.SobelVertical5)