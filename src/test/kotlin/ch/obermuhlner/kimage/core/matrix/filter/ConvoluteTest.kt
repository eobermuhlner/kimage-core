package ch.obermuhlner.kimage.core.matrix.filter

import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConvoluteTest {

    @Test
    fun `test convolute with identity kernel`() {
        val matrix = DoubleMatrix.matrixOf(3, 3,
            0.1, 0.2, 0.3,
            0.4, 0.5, 0.6,
            0.7, 0.8, 0.9
        )
        val identityKernel = DoubleMatrix.matrixOf(1, 1, 1.0)

        val result = matrix.convolute(identityKernel)

        assertEquals(matrix.rows, result.rows)
        assertEquals(matrix.cols, result.cols)
        for (row in 0 until matrix.rows) {
            for (col in 0 until matrix.cols) {
                assertEquals(matrix[row, col], result[row, col], 1e-10)
            }
        }
    }

    @Test
    fun `test convolute produces output of correct size`() {
        val matrix = DoubleMatrix.matrixOf(5, 5, 0.5)
        val kernel = DoubleMatrix.matrixOf(3, 3,
            1.0, 1.0, 1.0,
            1.0, 1.0, 1.0,
            1.0, 1.0, 1.0
        )

        val result = matrix.convolute(kernel)

        assertEquals(matrix.rows, result.rows)
        assertEquals(matrix.cols, result.cols)
    }

    @Test
    fun `test convoluteSeparable matches convolute for Gaussian kernel`() {
        val size = 5
        val center = size / 2
        val sigma = 1.5
        val kernel = DoubleMatrix.matrixOf(size, size) { row, col ->
            val dx = row - center
            val dy = col - center
            kotlin.math.exp(-(dx*dx + dy*dy) / (2 * sigma * sigma))
        }

        val matrix = DoubleMatrix.matrixOf(5, 5) { row, col ->
            ((row * 5 + col + 1).toDouble() / 25.0)
        }

        val kernel1D = DoubleArray(size) { kernel[center, it] }

        val resultConvolute = matrix.convoluteUnclamped(kernel)
        val resultSeparable = matrix.convoluteSeparable(kernel1D)

        for (row in 0 until matrix.rows) {
            for (col in 0 until matrix.cols) {
                assertEquals(resultConvolute[row, col], resultSeparable[row, col], 1e-6,
                    "Mismatch at ($row, $col): ${resultConvolute[row, col]} vs ${resultSeparable[row, col]}")
            }
        }
    }

    @Test
    fun `test convoluteSeparable with identity kernel`() {
        val matrix = DoubleMatrix.matrixOf(3, 3,
            0.1, 0.2, 0.3,
            0.4, 0.5, 0.6,
            0.7, 0.8, 0.9
        )
        val identityKernel = doubleArrayOf(1.0)

        val result = matrix.convoluteSeparable(identityKernel)

        for (row in 0 until matrix.rows) {
            for (col in 0 until matrix.cols) {
                assertEquals(matrix[row, col], result[row, col], 1e-10)
            }
        }
    }

    @Test
    fun `test convoluteSeparable produces output of correct size`() {
        val matrix = DoubleMatrix.matrixOf(5, 5, 0.5)
        val kernel = doubleArrayOf(1.0, 1.0, 1.0)

        val result = matrix.convoluteSeparable(kernel)

        assertEquals(matrix.rows, result.rows)
        assertEquals(matrix.cols, result.cols)
    }

    @Test
    fun `test richardsonLucyDeconvolution with identity returns same`() {
        val matrix = DoubleMatrix.matrixOf(5, 5) { row, col ->
            (row * 5 + col + 1).toDouble() / 25.0
        }
        val identityKernel = DoubleMatrix.matrixOf(1, 1, 1.0)

        val result = matrix.richardsonLucyDeconvolution(identityKernel, iterations = 5)

        for (row in 0 until matrix.rows) {
            for (col in 0 until matrix.cols) {
                assertEquals(matrix[row, col], result[row, col], 1e-6,
                    "Mismatch at ($row, $col)")
            }
        }
    }

    @Test
    fun `test richardsonLucyDeconvolution produces valid output for Gaussian`() {
        val size = 5
        val center = size / 2
        val sigma = 1.0
        val kernel = DoubleMatrix.matrixOf(size, size) { row, col ->
            val dx = row - center
            val dy = col - center
            kotlin.math.exp(-(dx*dx + dy*dy) / (2 * sigma * sigma))
        }

        val matrix = DoubleMatrix.matrixOf(10, 10) { row, col ->
            ((row + col) % 5 + 1).toDouble() / 5.0
        }

        val result = matrix.richardsonLucyDeconvolution(kernel, iterations = 3)

        assertEquals(matrix.rows, result.rows)
        assertEquals(matrix.cols, result.cols)

        for (row in 0 until result.rows) {
            for (col in 0 until result.cols) {
                assertTrue(result[row, col].isFinite())
            }
        }
    }

    @Test
    fun `test richardsonLucyDeconvolution handles non-Gaussian kernel`() {
        val kernel = DoubleMatrix.matrixOf(3, 3,
            -1.0, 0.0, 1.0,
            -2.0, 0.0, 2.0,
            -1.0, 0.0, 1.0
        )

        val matrix = DoubleMatrix.matrixOf(5, 5) { row, col ->
            ((row + col) % 5 + 1).toDouble() / 5.0
        }
        val result = matrix.richardsonLucyDeconvolution(kernel, iterations = 1)

        assertNotNull(result)
        assertEquals(matrix.rows, result.rows)
        assertEquals(matrix.cols, result.cols)
    }
}
