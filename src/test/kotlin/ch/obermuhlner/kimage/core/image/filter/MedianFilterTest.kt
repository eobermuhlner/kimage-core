package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MedianFilterTest {

    @Test
    fun `median filter handles single pixel matrix`() {
        val matrix = DoubleMatrix.matrixOf(1, 1, 5.0)
        val result = MedianFilter.medianMatrix(matrix, 1, Shape.Square, false)
        assertEquals(5.0, result[0, 0])
    }

    @Test
    fun `median filter handles odd kernel size`() {
        val matrix = DoubleMatrix.matrixOf(3, 3,
            1.0, 2.0, 3.0,
            4.0, 5.0, 6.0,
            7.0, 8.0, 9.0)
        val result = MedianFilter.medianMatrix(matrix, 1, Shape.Square, false)
        assertEquals(5.0, result[1, 1])
    }
}