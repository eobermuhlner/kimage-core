package ch.obermuhlner.kimage.core.matrix.filter

import ch.obermuhlner.kimage.core.math.clamp
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
