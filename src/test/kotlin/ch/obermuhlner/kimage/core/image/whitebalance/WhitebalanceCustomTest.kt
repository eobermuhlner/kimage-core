package ch.obermuhlner.kimage.core.image.whitebalance

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhitebalanceCustomTest {

    private fun imageWithChannels(r: Double, g: Double, b: Double): MatrixImage {
        val red   = DoubleMatrix.matrixOf(1, 1, r)
        val green = DoubleMatrix.matrixOf(1, 1, g)
        val blue  = DoubleMatrix.matrixOf(1, 1, b)
        return MatrixImage(red, green, blue)
    }

    @Test
    fun `applyWhitebalanceCustom multiplies each channel by its factor`() {
        val image = imageWithChannels(0.5, 0.5, 0.5)
        image.applyWhitebalanceCustom(redFactor = 1.2, greenFactor = 1.0, blueFactor = 0.8)
        assertEquals(0.6, image[Channel.Red][0, 0], 1e-9)
        assertEquals(0.5, image[Channel.Green][0, 0], 1e-9)
        assertEquals(0.4, image[Channel.Blue][0, 0], 1e-9)
    }

    @Test
    fun `applyWhitebalanceCustom with all factors 1 leaves image unchanged`() {
        val image = imageWithChannels(0.3, 0.5, 0.7)
        image.applyWhitebalanceCustom(1.0, 1.0, 1.0)
        assertEquals(0.3, image[Channel.Red][0, 0], 1e-9)
        assertEquals(0.5, image[Channel.Green][0, 0], 1e-9)
        assertEquals(0.7, image[Channel.Blue][0, 0], 1e-9)
    }
}
