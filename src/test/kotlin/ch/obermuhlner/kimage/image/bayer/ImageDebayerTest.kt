package ch.obermuhlner.kimage.image.bayer

import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.DebayerInterpolation
import ch.obermuhlner.kimage.core.image.bayer.bayer
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageDebayerTest : AbstractImageProcessingTest() {
    @Test
    fun `should debayer image with bayer pattern and interpolation`() {
        val image = readTestImage("flowers.bmp")
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

}