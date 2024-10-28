package ch.obermuhlner.kimage.image.bayer

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.bayer.DebayerInterpolation
import ch.obermuhlner.kimage.core.image.bayer.bayer
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.matrix.values.asXY
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class ImageBayerDebayerTest: AbstractImageProcessingTest() {

    @Test
    fun `should bayer then debayer an image`() {
        val width = 50
        val height = 30

        val image = createGradientTestImage(width, height)

        assertReferenceImage("before_bayer", image)

        val bayeredImage = image.bayer()

        fun simulateStuckPixel(stuckX: Int, stuckY: Int, value: Double) {
            bayeredImage[Channel.Red].asXY()[stuckX, stuckY] = value
            bayeredImage[Channel.Green].asXY()[stuckX, stuckY] = value
            bayeredImage[Channel.Blue].asXY()[stuckX, stuckY] = value
        }

        simulateStuckPixel(2, 2, 1.0)
        simulateStuckPixel(5, 3, 0.0)
        simulateStuckPixel(width-5, 2, 1.0)
        ImageWriter.write(bayeredImage, File("after_bayer.tif"))

        val bayeredColorImage = bayeredImage.debayer(interpolation = DebayerInterpolation.None)
        assertReferenceImage("after_bayer_color", bayeredColorImage)

        val stuckPixels = bayeredImage[Channel.Red].findBayerBadPixels()
        println(stuckPixels)
        assertEquals(
            setOf(
                Pair(2,2),
                Pair(5,3),
                Pair(width-5, 2)
            ), stuckPixels)

        val debayeredImage = bayeredImage.debayer(badpixelCoords = stuckPixels)
        assertReferenceImage("after_debayer", debayeredImage)

        assertEquals(setOf<Pair<Int,Int>>(), debayeredImage.bayer()[Channel.Red].findBayerBadPixels())
    }
}
