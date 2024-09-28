package ch.obermuhlner.kimage.core.matrix.linearalgebra

import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixInvertTest {

    @Test
    fun testInvert_identityMatrix() {
        val identityMatrix = DoubleMatrix.identity(3)
        val invertedMatrix = identityMatrix.invert()

        assertNotNull(invertedMatrix, "Inversion of identity matrix should not return null")
        assertTrue(identityMatrix.contentEquals(invertedMatrix!!), "Inversion of an identity matrix should return the same identity matrix")
    }

    @Test
    fun testInvert_squareMatrix() {
        val matrix = DoubleMatrix.matrixOf(3, 3,
            4.0, 7.0, 2.0,
            3.0, 6.0, 1.0,
            2.0, 5.0, 8.0
        )

        val invertedMatrix = matrix.invert()
        assertNotNull(invertedMatrix, "Inversion should not return null")

        // Multiply original matrix by its inverse, expecting to get the identity matrix
        val result = matrix * invertedMatrix!!

        val identityMatrix = DoubleMatrix.identity(3)
        assertTrue(identityMatrix.contentEquals(result), "Matrix multiplied by its inverse should be the identity matrix")
    }

    @Test
    fun testInvert_nonInvertibleMatrix() {
        val matrix = DoubleMatrix.matrixOf(3, 3,
            1.0, 2.0, 3.0,
            1.0, 2.0, 3.0,
            1.0, 2.0, 3.0
        )

        val invertedMatrix = matrix.invert()

        assertNull(invertedMatrix, "Inversion of a non-invertible matrix should return null")
    }
}
