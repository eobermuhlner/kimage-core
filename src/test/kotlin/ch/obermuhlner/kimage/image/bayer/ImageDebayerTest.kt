package ch.obermuhlner.kimage.image.bayer

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.DebayerInterpolation
import ch.obermuhlner.kimage.core.image.bayer.bayer
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.values.values
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageDebayerTest : AbstractImageProcessingTest() {
    @Test
    fun `should debayer image with bayer pattern and interpolation`() {
        val image = readTestImage()
        assertReferenceImage("default", image.bayer().debayer())

        for (bayerPattern in BayerPattern.entries) {
            val bayerImage = image.bayer(bayerPattern)
            for (interpolation in DebayerInterpolation.entries) {
                val debayerImage = bayerImage.debayer(bayerPattern, interpolation)
                assertReferenceImage("${bayerPattern.name}_${interpolation.name}", debayerImage)
            }
        }
    }

    @Test
    fun `should debayer image with rgb factors`() {
        val bayerImage = readTestImage().bayer()

        assertReferenceImage("r0", bayerImage.debayer(red = 0.0))
        assertReferenceImage("g0", bayerImage.debayer(green = 0.0))
        assertReferenceImage("b0", bayerImage.debayer(blue = 0.0))
        assertReferenceImage("rgb0.5", bayerImage.debayer(red = 0.5, green = 0.5, blue = 0.5))
        assertReferenceImage("r2", bayerImage.debayer(red = 2.0))
        assertReferenceImage("g2", bayerImage.debayer(green = 2.0))
        assertReferenceImage("b2", bayerImage.debayer(blue = 2.0))
    }

    @Test
    fun `should GLI debayer edge pixels without artifacts`() {
        val grayMatrix = DoubleMatrix(6, 6)
        for (y in 0 until 6) {
            for (x in 0 until 6) {
                grayMatrix[y, x] = (x + y * 6 + 1).toDouble() / 36.0
            }
        }
        val bayerImage = MatrixImage(6, 6, Channel.Gray to grayMatrix)

        val debayered = bayerImage.debayer(BayerPattern.RGGB, DebayerInterpolation.GLI)

        val allRedPixels = debayered[Channel.Red].values().toList()
        val allGreenPixels = debayered[Channel.Green].values().toList()
        val allBluePixels = debayered[Channel.Blue].values().toList()

        val minRed = allRedPixels.minOrNull() ?: 0.0
        val minGreen = allGreenPixels.minOrNull() ?: 0.0
        val minBlue = allBluePixels.minOrNull() ?: 0.0

        assertAll(
            { assertTrue(minRed > 0.0, "Red channel has edge artifacts near 0") },
            { assertTrue(minGreen > 0.0, "Green channel has edge artifacts near 0") },
            { assertTrue(minBlue > 0.0, "Blue channel has edge artifacts near 0") }
        )
    }

}