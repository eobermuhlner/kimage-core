package ch.obermuhlner.kimage.core.matrix.filter

import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix

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

fun Matrix.richardsonLucyDeconvolution(kernel: Matrix, iterations: Int = 20): Matrix {
    val blurred = this.convolute(kernel)
    val kernelFlipped = DoubleMatrix.matrixOf(kernel.rows, kernel.cols) { row, col ->
        kernel[kernel.rows - 1 - row, kernel.cols - 1 - col]
    }

    var estimate = this.copy()

    for (i in 0 until iterations) {
        val convolved = estimate.convolute(kernel)
        val ratio = DoubleMatrix.matrixOf(rows, cols) { row, col ->
            val c = convolved[row, col]
            if (c < 1e-10) {
                (blurred[row, col] / 1e-10)
            } else {
                blurred[row, col] / c
            }
        }
        val correction = ratio.convolute(kernelFlipped)
        
        val newEstimate = DoubleMatrix.matrixOf(rows, cols) { row, col ->
            estimate[row, col] * correction[row, col]
        }
        newEstimate.applyEach { v -> clamp(v, 0.0, 1.0) }
        estimate = newEstimate
    }

    return estimate
}
