package ch.obermuhlner.kimage.astro.align

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyTransformationTest {

    private fun uniformImage(width: Int, height: Int, value: Double = 0.5): MatrixImage {
        val m = DoubleMatrix.matrixOf(height, width) { _, _ -> value }
        return MatrixImage(width, height, Channel.Red to m, Channel.Green to m, Channel.Blue to m)
    }

    @Test
    fun `applyTransformationToImage with identity matrix preserves last column and row`() {
        val width = 10
        val height = 10
        val image = uniformImage(width, height)

        val identity = DoubleMatrix.matrixOf(3, 3,
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )
        val result = applyTransformationToImage(image, identity)

        for (y in 0 until height) {
            assertEquals(0.5, result[width - 1, y, Channel.Red], 1e-5,
                "Last column at y=$y should not be black")
        }
        for (x in 0 until width) {
            assertEquals(0.5, result[x, height - 1, Channel.Red], 1e-5,
                "Last row at x=$x should not be black")
        }
    }

    @Test
    fun `applyTransformationToImage interior pixels are preserved by identity`() {
        val width = 10
        val height = 10
        val image = uniformImage(width, height)
        val identity = DoubleMatrix.matrixOf(3, 3,
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )
        val result = applyTransformationToImage(image, identity)

        for (y in 0 until height - 1) {
            for (x in 0 until width - 1) {
                assertEquals(0.5, result[x, y, Channel.Red], 1e-5, "Pixel [$x,$y] should be 0.5")
            }
        }
    }
}
