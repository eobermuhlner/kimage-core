package ch.obermuhlner.kimage.image.bayer

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.bayer.DebayerInterpolation
import ch.obermuhlner.kimage.core.image.bayer.bayer
import ch.obermuhlner.kimage.core.image.bayer.debayer
import ch.obermuhlner.kimage.core.image.bayer.findBayerBadPixels
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.values.asXY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class ImageBayerDebayerTest {

    @Test
    fun `should bayer image`() {
        val width = 50
        val height = 30

        val image = createTestImage(width, height)
        ImageWriter.write(image, File("before_bayer.tif"))

        val bayeredImage = image.bayer()
        val stuckX = 2
        val stuckY = 2
        bayeredImage[Channel.Red].asXY()[stuckX, stuckY] = 1.0
        bayeredImage[Channel.Green].asXY()[stuckX, stuckY] = 1.0
        bayeredImage[Channel.Blue].asXY()[stuckX, stuckY] = 1.0
        ImageWriter.write(bayeredImage, File("after_bayer.tif"))

        val bayeredColorImage = bayeredImage.debayer(interpolation = DebayerInterpolation.None)
        ImageWriter.write(bayeredColorImage, File("after_bayer_color.tif"))

        val stuckPixels = bayeredImage[Channel.Red].findBayerBadPixels()
        println(stuckPixels)
        assertEquals(setOf(Pair(2,2)), stuckPixels)

        val debayeredImage = bayeredImage.debayer(badpixelCoords = stuckPixels)
        ImageWriter.write(debayeredImage, File("after_debayer.tif"))

        assertEquals(setOf<Pair<Int,Int>>(), debayeredImage.bayer()[Channel.Red].findBayerBadPixels())
    }

    private fun createTestImage(width: Int, height: Int): Image {
        return MatrixImage(width, height,
            Channel.Red to DoubleMatrix.matrixOf(height, width) { row, col -> col.toDouble() / width },
            Channel.Green to DoubleMatrix.matrixOf(height, width) { row, col -> row.toDouble() / height },
            Channel.Blue to DoubleMatrix.matrixOf(height, width) { row, col -> ((width - col).toDouble() / width + (height - row).toDouble() / height) / 2.0 },
        )
    }
}
