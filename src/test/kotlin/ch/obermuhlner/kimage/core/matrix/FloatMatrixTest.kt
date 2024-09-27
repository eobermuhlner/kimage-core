package ch.obermuhlner.kimage.core.matrix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloatMatrixTest {

    @Test
    fun `test matrix creation with constructor`() {
        val matrix = FloatMatrix(2, 3)
        assertEquals(2, matrix.rows)
        assertEquals(3, matrix.cols)
        assertEquals(6, matrix.size)
        for (index in 0 until matrix.size) {
            assertEquals(0.0f, matrix.get(index).toFloat())
        }
    }

    @Test
    fun `test matrix creation with init function`() {
        val matrix = FloatMatrix(2, 2) { row, col -> (row + col).toFloat() }
        assertEquals(0.0f, matrix[0, 0].toFloat())
        assertEquals(1.0f, matrix[0, 1].toFloat())
        assertEquals(1.0f, matrix[1, 0].toFloat())
        assertEquals(2.0f, matrix[1, 1].toFloat())
    }

    @Test
    fun `test matrix creation with factory method`() {
        val matrix = FloatMatrix.matrixOf(2, 2, 1.0f, 2.0f, 3.0f, 4.0f)
        assertEquals(1.0f, matrix[0, 0].toFloat())
        assertEquals(2.0f, matrix[0, 1].toFloat())
        assertEquals(3.0f, matrix[1, 0].toFloat())
        assertEquals(4.0f, matrix[1, 1].toFloat())
    }

    @Test
    fun `test get and set element by index`() {
        val matrix = FloatMatrix(2, 2)
        matrix.set(0, 1.0)
        matrix.set(3, 4.0)
        assertEquals(1.0, matrix.get(0))
        assertEquals(4.0, matrix.get(3))
    }

    @Test
    fun `test get and set element by row and column`() {
        val matrix = FloatMatrix(2, 2)
        matrix[0, 0] = 1.0
        matrix[1, 1] = 4.0
        assertEquals(1.0, matrix[0, 0])
        assertEquals(4.0, matrix[1, 1])
    }

    @Test
    fun `test matrix addition`() {
        val matrixA = FloatMatrix.matrixOf(2, 2, 1.0f, 2.0f, 3.0f, 4.0f)
        val matrixB = FloatMatrix.matrixOf(2, 2, 4.0f, 3.0f, 2.0f, 1.0f)
        val result = matrixA + matrixB
        assertEquals(5.0, result[0, 0])
        assertEquals(5.0, result[0, 1])
        assertEquals(5.0, result[1, 0])
        assertEquals(5.0, result[1, 1])
    }

    @Test
    fun `test scalar multiplication`() {
        val matrix = FloatMatrix.matrixOf(2, 2, 1.0f, 2.0f, 3.0f, 4.0f)
        val result = matrix * 2.0
        assertEquals(2.0, result[0, 0])
        assertEquals(4.0, result[0, 1])
        assertEquals(6.0, result[1, 0])
        assertEquals(8.0, result[1, 1])
    }

    @Test
    fun `test matrix multiplication`() {
        val matrixA = FloatMatrix.matrixOf(2, 3,
            1.0f, 2.0f, 3.0f,
            4.0f, 5.0f, 6.0f)
        val matrixB = FloatMatrix.matrixOf(3, 2,
            7.0f, 8.0f,
            9.0f, 10.0f,
            11.0f, 12.0f)
        val result = matrixA * matrixB
        assertEquals(58.0, result[0, 0])
        assertEquals(64.0, result[0, 1])
        assertEquals(139.0, result[1, 0])
        assertEquals(154.0, result[1, 1])
    }

    @Test
    fun `test contentEquals`() {
        val matrixA = FloatMatrix.matrixOf(2, 2, 1.0f, 2.0f, 3.0f, 4.0f)
        val matrixB = FloatMatrix.matrixOf(2, 2, 1.0f, 2.0f, 3.0f, 4.0f)
        val matrixC = FloatMatrix.matrixOf(2, 2, 4.0f, 3.0f, 2.0f, 1.0f)
        assertTrue(matrixA.contentEquals(matrixB))
        assertFalse(matrixA.contentEquals(matrixC))
    }
}
