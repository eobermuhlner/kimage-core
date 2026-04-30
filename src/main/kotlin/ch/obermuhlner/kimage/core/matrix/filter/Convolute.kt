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

// Wiener deconvolution in the frequency domain:
//   F̂(ω) = H*(ω) · G(ω) / (|H(ω)|² + noiseLevel)
// where H is the OTF (FFT of PSF) and G is the observed (blurred) image.
// The `iterations` parameter is kept for API compatibility but is not used.
@Suppress("UNUSED_PARAMETER")
fun Matrix.wienerDeconvolution(kernel: Matrix, iterations: Int = 10, noiseLevel: Double = 0.01): Matrix {
    val fftRows = nextPow2(rows + kernel.rows - 1)
    val fftCols = nextPow2(cols + kernel.cols - 1)

    // Zero-pad observed image into frequency-domain buffers
    val gRe = Array(fftRows) { row -> DoubleArray(fftCols) { col -> if (row < rows && col < cols) this[row, col] else 0.0 } }
    val gIm = Array(fftRows) { DoubleArray(fftCols) }

    // Place PSF with its centre circularly shifted to (0,0) for linear-phase alignment
    val hRe = Array(fftRows) { DoubleArray(fftCols) }
    val hIm = Array(fftRows) { DoubleArray(fftCols) }
    val kCR = kernel.rows / 2
    val kCC = kernel.cols / 2
    for (kr in 0 until kernel.rows) {
        for (kc in 0 until kernel.cols) {
            val r = (kr - kCR + fftRows) % fftRows
            val c = (kc - kCC + fftCols) % fftCols
            hRe[r][c] = kernel[kr, kc]
        }
    }

    fft2D(gRe, gIm)
    fft2D(hRe, hIm)

    // Apply Wiener formula in-place, reusing gRe/gIm for the result
    for (row in 0 until fftRows) {
        for (col in 0 until fftCols) {
            val hr = hRe[row][col]; val hi = hIm[row][col]
            val gr = gRe[row][col]; val gi = gIm[row][col]
            val denom = hr * hr + hi * hi + noiseLevel
            // H_conj * G = (hr - i·hi)(gr + i·gi)
            gRe[row][col] = (hr * gr + hi * gi) / denom
            gIm[row][col] = (hr * gi - hi * gr) / denom
        }
    }

    fft2D(gRe, gIm, inverse = true)

    return DoubleMatrix.matrixOf(rows, cols) { row, col ->
        clamp(gRe[row][col], 0.0, 1.0)
    }
}
