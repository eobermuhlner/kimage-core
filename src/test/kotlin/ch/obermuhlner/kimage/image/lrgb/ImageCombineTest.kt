package ch.obermuhlner.kimage.image.lrgb

import ch.obermuhlner.kimage.core.image.bayer.DebayerInterpolation.Monochrome
import ch.obermuhlner.kimage.core.image.bayer.bayer
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.lrgb.combineGray
import ch.obermuhlner.kimage.core.image.lrgb.replaceBrightness
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageCombineTest : AbstractImageProcessingTest() {
    @Test
    fun `should combine debayered monochrome with rgb image`() {
        val image = readTestImage().bayer()

        val monochromeImage = image.debayer(interpolation = Monochrome)
        val rgbImage = image.debayer()

        val resultImage = rgbImage.combineGray(monochromeImage, 0.5)
        assertReferenceImage("combineGray_0.5", resultImage)
    }

    @Test
    fun `should replace brightness debayered monochrome in rgb image`() {
        val image = readTestImage().bayer()

        val monochromeImage = image.debayer(interpolation = Monochrome)
        val rgbImage = image.debayer()

        assertReferenceImage("replaceBrightness_1.0", rgbImage.replaceBrightness(monochromeImage, 1.0))
        assertReferenceImage("replaceBrightness_0.9", rgbImage.replaceBrightness(monochromeImage, 0.9))
        assertReferenceImage("replaceBrightness_0.5", rgbImage.replaceBrightness(monochromeImage, 0.5))
        assertReferenceImage("replaceBrightness_0.1", rgbImage.replaceBrightness(monochromeImage, 0.1))
    }
}