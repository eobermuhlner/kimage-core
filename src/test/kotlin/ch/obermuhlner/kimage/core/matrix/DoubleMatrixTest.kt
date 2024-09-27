package ch.obermuhlner.kimage.core.matrix

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DoubleMatrixTest {

    @Test
    fun `test matrix creation with constructor`() {
        val matrix = DoubleMatrix(2, 3)
        assertEquals(2, matrix.rows)
        assertEquals(3, matrix.cols)
        assertEquals(6, matrix.size)
        for (index in 0 until matrix.size) {
            assertEquals(0.0, matrix[index])
        }
    }

    @Test
    fun `test matrix creation with init function`() {
        val matrix = DoubleMatrix(2, 2) { row, col -> (row + col).toDouble() }
        assertEquals(0.0, matrix[0, 0])
        assertEquals(1.0, matrix[0, 1])
        assertEquals(1.0, matrix[1, 0])
        assertEquals(2.0, matrix[1, 1])
    }

    @Test
    fun `test matrix creation with factory method`() {
        val matrix = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        assertEquals(1.0, matrix[0, 0])
        assertEquals(2.0, matrix[0, 1])
        assertEquals(3.0, matrix[1, 0])
        assertEquals(4.0, matrix[1, 1])
    }

    @Test
    fun `test get and set element by index`() {
        val matrix = DoubleMatrix(2, 2)
        matrix[0] = 1.0
        matrix[3] = 4.0
        assertEquals(1.0, matrix[0])
        assertEquals(4.0, matrix[3])
    }

    @Test
    fun `test get and set element by row and column`() {
        val matrix = DoubleMatrix(2, 2)
        matrix[0, 0] = 1.0
        matrix[1, 1] = 4.0
        assertEquals(1.0, matrix[0, 0])
        assertEquals(4.0, matrix[1, 1])
    }

    @Test
    fun `test out-of-bounds access`() {
        val matrix = DoubleMatrix(2, 2)
        assertThrows<IllegalArgumentException> { matrix[-1, 0] }
        assertThrows<IllegalArgumentException> { matrix[0, -1] }
        assertThrows<IllegalArgumentException> { matrix[2, 0] }
        assertThrows<IllegalArgumentException> { matrix[0, 2] }
    }

    @Test
    fun `test matrix addition`() {
        val matrixA = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 4.0, 3.0, 2.0, 1.0)
        val result = matrixA + matrixB
        assertEquals(5.0, result[0, 0])
        assertEquals(5.0, result[0, 1])
        assertEquals(5.0, result[1, 0])
        assertEquals(5.0, result[1, 1])
    }

    @Test
    fun `test matrix addition assignment`() {
        val matrixA = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 4.0, 3.0, 2.0, 1.0)
        matrixA += matrixB
        assertEquals(5.0, matrixA[0, 0])
        assertEquals(5.0, matrixA[0, 1])
        assertEquals(5.0, matrixA[1, 0])
        assertEquals(5.0, matrixA[1, 1])
    }

    @Test
    fun `test matrix subtraction`() {
        val matrixA = DoubleMatrix.matrixOf(2, 2, 5.0, 5.0, 5.0, 5.0)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val result = matrixA - matrixB
        assertEquals(4.0, result[0, 0])
        assertEquals(3.0, result[0, 1])
        assertEquals(2.0, result[1, 0])
        assertEquals(1.0, result[1, 1])
    }

    @Test
    fun `test matrix subtraction assignment`() {
        val matrixA = DoubleMatrix.matrixOf(2, 2, 5.0, 5.0, 5.0, 5.0)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        matrixA -= matrixB
        assertEquals(4.0, matrixA[0, 0])
        assertEquals(3.0, matrixA[0, 1])
        assertEquals(2.0, matrixA[1, 0])
        assertEquals(1.0, matrixA[1, 1])
    }

    @Test
    fun `test matrix multiplication`() {
        val matrixA = DoubleMatrix.matrixOf(2, 3,
            1.0, 2.0, 3.0,
            4.0, 5.0, 6.0)
        val matrixB = DoubleMatrix.matrixOf(3, 2,
            7.0, 8.0,
            9.0, 10.0,
            11.0, 12.0)
        val result = matrixA * matrixB
        assertEquals(58.0, result[0, 0])
        assertEquals(64.0, result[0, 1])
        assertEquals(139.0, result[1, 0])
        assertEquals(154.0, result[1, 1])
    }

    @Test
    fun `test scalar multiplication`() {
        val matrix = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val result = matrix * 2.0
        assertEquals(2.0, result[0, 0])
        assertEquals(4.0, result[0, 1])
        assertEquals(6.0, result[1, 0])
        assertEquals(8.0, result[1, 1])
    }

    @Test
    fun `test scalar multiplication assignment`() {
        val matrix = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        matrix *= 2.0
        assertEquals(2.0, matrix[0, 0])
        assertEquals(4.0, matrix[0, 1])
        assertEquals(6.0, matrix[1, 0])
        assertEquals(8.0, matrix[1, 1])
    }

    @Test
    fun `test scalar division`() {
        val matrix = DoubleMatrix.matrixOf(2, 2, 2.0, 4.0, 6.0, 8.0)
        val result = matrix / 2.0
        assertEquals(1.0, result[0, 0])
        assertEquals(2.0, result[0, 1])
        assertEquals(3.0, result[1, 0])
        assertEquals(4.0, result[1, 1])
    }

    @Test
    fun `test scalar division assignment`() {
        val matrix = DoubleMatrix.matrixOf(2, 2, 2.0, 4.0, 6.0, 8.0)
        matrix /= 2.0
        assertEquals(1.0, matrix[0, 0])
        assertEquals(2.0, matrix[0, 1])
        assertEquals(3.0, matrix[1, 0])
        assertEquals(4.0, matrix[1, 1])
    }

    @Test
    fun `test element-wise multiplication`() {
        val matrixA = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 2.0, 3.0, 4.0, 5.0)
        val result = matrixA elementTimes matrixB
        assertEquals(2.0, result[0, 0])
        assertEquals(6.0, result[0, 1])
        assertEquals(12.0, result[1, 0])
        assertEquals(20.0, result[1, 1])
    }

    @Test
    fun `test element-wise division`() {
        val matrixA = DoubleMatrix.matrixOf(2, 2, 2.0, 6.0, 12.0, 20.0)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 2.0, 3.0, 4.0, 5.0)
        val result = matrixA elementDiv matrixB
        assertEquals(1.0, result[0, 0])
        assertEquals(2.0, result[0, 1])
        assertEquals(3.0, result[1, 0])
        assertEquals(4.0, result[1, 1])
    }

    @Test
    fun `test matrix copy`() {
        val matrix = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val copy = matrix.copy()
        assertTrue(matrix !== copy)
        assertTrue(matrix.contentEquals(copy))
    }

    @Test
    fun `test contentEquals`() {
        val matrixA = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val matrixC = DoubleMatrix.matrixOf(2, 2, 4.0, 3.0, 2.0, 1.0)
        assertTrue(matrixA.contentEquals(matrixB))
        assertFalse(matrixA.contentEquals(matrixC))
    }

    @Test
    fun `test equals and hashCode`() {
        val matrixA = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        val matrixC = DoubleMatrix.matrixOf(2, 2, 4.0, 3.0, 2.0, 1.0)
        assertEquals(matrixA, matrixB)
        assertEquals(matrixA.hashCode(), matrixB.hashCode())
        assertNotEquals(matrixA, matrixC)
        assertNotEquals(matrixA.hashCode(), matrixC.hashCode())
    }

    @Test
    fun `test set matrix within another matrix`() {
        val matrixA = DoubleMatrix(4, 4)
        val matrixB = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        matrixA.set(matrixB, offsetRow = 1, offsetCol = 1)
        assertEquals(1.0, matrixA[1, 1])
        assertEquals(2.0, matrixA[1, 2])
        assertEquals(3.0, matrixA[2, 1])
        assertEquals(4.0, matrixA[2, 2])
    }

    @Test
    fun `test applyEach function`() {
        val matrix = DoubleMatrix.matrixOf(2, 2, 1.0, 2.0, 3.0, 4.0)
        matrix.applyEach { value -> value * 2 }
        assertEquals(2.0, matrix[0, 0])
        assertEquals(4.0, matrix[0, 1])
        assertEquals(6.0, matrix[1, 0])
        assertEquals(8.0, matrix[1, 1])
    }

    @Test
    fun `test applyEach with row and column`() {
        val matrix = DoubleMatrix(2, 2)
        matrix.applyEach { row, col, _ -> (row + col).toDouble() }
        assertEquals(0.0, matrix[0, 0])
        assertEquals(1.0, matrix[0, 1])
        assertEquals(1.0, matrix[1, 0])
        assertEquals(2.0, matrix[1, 1])
    }

    @Test
    fun `test matrix multiplication dimension mismatch`() {
        val matrixA = DoubleMatrix(2, 3)
        val matrixB = DoubleMatrix(2, 2)
        val exception = assertThrows<IllegalArgumentException> {
            matrixA * matrixB
        }
        assertEquals("columns != other.rows : 3 != 2", exception.message)
    }

    @Test
    fun `test matrix addition dimension mismatch`() {
        val matrixA = DoubleMatrix(2, 2)
        val matrixB = DoubleMatrix(3, 2)
        val exception = assertThrows<IllegalArgumentException> {
            matrixA + matrixB
        }
        assertEquals("Matrix size mismatch: (2x2) vs (3x2)", exception.message)
    }
}
