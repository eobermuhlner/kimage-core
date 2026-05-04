package ch.obermuhlner.kimage.astro.align

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FindStarsTest {

    @Test
    fun `findStars with flat background returns only actual peaks`() {
        // 10x10 image: two bright peaks on a uniform background
        // Bug: all background pixels satisfy "no neighbor strictly greater", so all become
        // local maxima and cluster into a spurious third star.
        val matrix = DoubleMatrix.matrixOf(10, 10) { _, _ -> 0.3 }
        matrix[2, 2] = 0.9  // star 1 (row=2, col=2 → y=2, x=2)
        matrix[7, 7] = 0.8  // star 2
        val image = MatrixImage(10, 10, listOf(Channel.Gray), listOf(matrix))

        val stars = findStars(image, threshold = 0.1)

        assertEquals(2, stars.size, "flat background should not produce spurious stars")
        assertEquals(0.9, stars[0].brightness, 1e-9)
        assertEquals(0.8, stars[1].brightness, 1e-9)
    }

    @Test
    fun `findStars isolated single peak is detected correctly`() {
        val matrix = DoubleMatrix.matrixOf(5, 5)
        matrix[2, 2] = 0.8
        val image = MatrixImage(5, 5, listOf(Channel.Gray), listOf(matrix))

        val stars = findStars(image, threshold = 0.5)

        assertEquals(1, stars.size)
        assertEquals(2.0, stars[0].x)
        assertEquals(2.0, stars[0].y)
    }

    @Test
    fun `findStars with low threshold on large flat image completes quickly`() {
        // 500x500 image with a uniform background — at low threshold all interior pixels
        // were previously flagged as local maxima, causing O(n*m) BFS work.
        val matrix = DoubleMatrix.matrixOf(500, 500) { _, _ -> 0.5 }
        matrix[100, 100] = 0.9
        matrix[300, 300] = 0.8
        val image = MatrixImage(500, 500, listOf(Channel.Gray), listOf(matrix))

        val start = System.currentTimeMillis()
        val stars = findStars(image, threshold = 0.1)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed < 5000, "findStars should complete in < 5 s on a flat image, took ${elapsed} ms")
        assertEquals(2, stars.size, "only real peaks should be returned")
    }
}
