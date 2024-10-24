package ch.obermuhlner.kimage.image

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.Matrix
import org.junit.jupiter.api.Test

class ImageChannelTest: AbstractImageProcessingTest() {

    @Test
    fun `should get channels from rgb image`() {
        val image = readTestImage()

        assertReferenceImage("gray", toGrayMatrixImage(image[Channel.Gray]))
        assertReferenceImage("hue", toGrayMatrixImage(image[Channel.Hue]))
        assertReferenceImage("saturation", toGrayMatrixImage(image[Channel.Saturation]))
        assertReferenceImage("brightness", toGrayMatrixImage(image[Channel.Brightness]))
        assertReferenceImage("luminance", toGrayMatrixImage(image[Channel.Luminance]))
        assertReferenceImage("red", toGrayMatrixImage(image[Channel.Red]))
        assertReferenceImage("green", toGrayMatrixImage(image[Channel.Green]))
        assertReferenceImage("blue", toGrayMatrixImage(image[Channel.Blue]))
    }

    @Test
    fun `should get channels from hsv image`() {
        val rgbImage = readTestImage()
        val image = MatrixImage(rgbImage.width, rgbImage.height,
            Channel.Hue to rgbImage[Channel.Hue],
            Channel.Saturation to rgbImage[Channel.Saturation],
            Channel.Brightness to rgbImage[Channel.Brightness])

        assertReferenceImage("gray", toGrayMatrixImage(image[Channel.Gray]))
        assertReferenceImage("hue", toGrayMatrixImage(image[Channel.Hue]))
        assertReferenceImage("saturation", toGrayMatrixImage(image[Channel.Saturation]))
        assertReferenceImage("brightness", toGrayMatrixImage(image[Channel.Brightness]))
        assertReferenceImage("luminance", toGrayMatrixImage(image[Channel.Luminance]))
        assertReferenceImage("red", toGrayMatrixImage(image[Channel.Red]))
        assertReferenceImage("green", toGrayMatrixImage(image[Channel.Green]))
        assertReferenceImage("blue", toGrayMatrixImage(image[Channel.Blue]))

        val againRgbImage = MatrixImage(image.width, image.height,
            Channel.Red to image[Channel.Red],
            Channel.Green to image[Channel.Green],
            Channel.Blue to image[Channel.Blue])
        assertImage(rgbImage, againRgbImage)
    }

    @Test
    fun `should get channels from gray image`() {
        val image = toGrayMatrixImage(readTestImage()[Channel.Gray])

        assertReferenceImage("gray", toGrayMatrixImage(image[Channel.Gray]))
        assertReferenceImage("hue", toGrayMatrixImage(image[Channel.Hue]))
        assertReferenceImage("saturation", toGrayMatrixImage(image[Channel.Saturation]))
        assertReferenceImage("brightness", toGrayMatrixImage(image[Channel.Brightness]))
        assertReferenceImage("luminance", toGrayMatrixImage(image[Channel.Luminance]))
        assertReferenceImage("red", toGrayMatrixImage(image[Channel.Red]))
        assertReferenceImage("green", toGrayMatrixImage(image[Channel.Green]))
        assertReferenceImage("blue", toGrayMatrixImage(image[Channel.Blue]))
    }

    private fun toGrayMatrixImage(matrix: Matrix): MatrixImage {
        return MatrixImage(matrix.cols, matrix.rows,
            Channel.Gray to matrix)
    }
}