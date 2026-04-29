package ch.obermuhlner.kimage.core.matrix.filter

import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

fun Matrix.convolute(kernel: Matrix): Matrix {
    val m = create()
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            var value = 0.0
            for (kernelRow in 0 until kernel.rows) {
                for (kernelCol in 0 until kernel.cols) {
                    val pixel = this[row - kernel.rows/2 + kernelRow, col - kernel.cols/2 + kernelCol]
                    value += pixel * kernel[kernelRow, kernelCol]
                }
            }
            m[row, col] = clamp(value, 0.0, 1.0)
        }
    }
    return m
}

fun Matrix.convoluteUnclamped(kernel: Matrix): Matrix {
    val m = create()
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            var value = 0.0
            for (kernelRow in 0 until kernel.rows) {
                for (kernelCol in 0 until kernel.cols) {
                    val pixel = this[row - kernel.rows/2 + kernelRow, col - kernel.cols/2 + kernelCol]
                    value += pixel * kernel[kernelRow, kernelCol]
                }
            }
            m[row, col] = value
        }
    }
    return m
}

fun Matrix.convoluteSeparable(kernel1D: DoubleArray): Matrix {
    val half = kernel1D.size / 2
    val temp = create()
    val m = create()

    // Horizontal pass
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            var value = 0.0
            for (k in kernel1D.indices) {
                value += this[row, col - half + k] * kernel1D[k]
            }
            temp[row, col] = value
        }
    }

    // Vertical pass
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            var value = 0.0
            for (k in kernel1D.indices) {
                value += temp[row - half + k, col] * kernel1D[k]
            }
            m[row, col] = value
        }
    }

    return m
}

private fun isGaussianKernel(kernel: Matrix): Boolean {
    if (kernel.rows != kernel.cols || kernel.rows % 2 == 0) return false
    val center = kernel.rows / 2
    if (kernel[center, center] <= 0.0) return false

    // Check separability: kernel must equal outer product of its center row
    val profile = DoubleArray(kernel.cols) { kernel[center, it] }
    for (row in 0 until kernel.rows) {
        for (col in 0 until kernel.cols) {
            val expected = profile[row] * profile[col] / profile[center]
            if (abs(kernel[row, col] - expected) > 1e-6) return false
        }
    }
    return true
}

fun Matrix.richardsonLucyDeconvolution(kernel: Matrix, iterations: Int = 20): Matrix {
    val kernel1D = if (isGaussianKernel(kernel)) {
        val center = kernel.rows / 2
        DoubleArray(kernel.cols) { kernel[center, it] }
    } else {
        null
    }

    // The input matrix IS the blurred image - no need to convolve it
    val blurred = this

    val kernelFlipped = if (kernel1D == null) {
        DoubleMatrix.matrixOf(kernel.rows, kernel.cols) { row, col ->
            kernel[kernel.rows - 1 - row, kernel.cols - 1 - col]
        }
    } else null

    var estimate = this.copy()

    for (i in 0 until iterations) {
        val convolved = if (kernel1D != null) estimate.convoluteSeparable(kernel1D) else estimate.convoluteUnclamped(kernel)
        val ratio = DoubleMatrix.matrixOf(rows, cols) { row, col ->
            val c = convolved[row, col]
            if (c < 1e-10) {
                (blurred[row, col] / 1e-10)
            } else {
                blurred[row, col] / c
            }
        }
        val correction = if (kernel1D != null) ratio.convoluteSeparable(kernel1D) else ratio.convoluteUnclamped(kernelFlipped!!)

        val newEstimate = DoubleMatrix.matrixOf(rows, cols) { row, col ->
            estimate[row, col] * correction[row, col]
        }
        estimate = newEstimate
    }

    // Only clamp at the end
    estimate.applyEach { v -> clamp(v, 0.0, 1.0) }
    return estimate
}

fun Matrix.wienerDeconvolution(kernel: Matrix, iterations: Int = 10, noiseLevel: Double = 0.01): Matrix {
    val kernel1D = if (isGaussianKernel(kernel)) {
        val center = kernel.rows / 2
        DoubleArray(kernel.cols) { kernel[center, it] }
    } else {
        null
    }

    val blurred = this
    val kernelFlipped = if (kernel1D == null) {
        DoubleMatrix.matrixOf(kernel.rows, kernel.cols) { row, col ->
            kernel[kernel.rows - 1 - row, kernel.cols - 1 - col]
        }
    } else null

    var estimate = this.copy()

    for (i in 0 until iterations) {
        val convolved = if (kernel1D != null) estimate.convoluteSeparable(kernel1D) else estimate.convolute(kernel)
        val ratio = DoubleMatrix.matrixOf(rows, cols) { row, col ->
            val c = convolved[row, col]
            val b = blurred[row, col]
            if (c < 1e-10) {
                b / 1e-10
            } else {
                b / c
            }
        }
        val correction = if (kernel1D != null) ratio.convoluteSeparable(kernel1D) else ratio.convolute(kernelFlipped!!)

        val newEstimate = DoubleMatrix.matrixOf(rows, cols) { row, col ->
            val est = estimate[row, col]
            val corr = correction[row, col]
            val denominator = corr * corr + noiseLevel
            est * (corr / denominator)
        }
        newEstimate.applyEach { v -> clamp(v, 0.0, 1.0) }
        estimate = newEstimate
    }

    return estimate
}
