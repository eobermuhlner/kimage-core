package ch.obermuhlner.kimage.image.crop

import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.crop.cropCenter
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImageCropTest: AbstractImageProcessingTest() {

    @Test
    fun `should crop image with specified bounds`() {
        val image = readTestImage()

        image.crop(5, 10, 15, 20).let {
            assertEquals(15, it.width)
            assertEquals(20, it.height)
            assertReferenceImage("crop(5,10,15,20)", it)
        }
    }

    @Test
    fun `should crop image center with specified radius`() {
        val image = readTestImage()

        val radius = 10
        image.cropCenter(radius).let {
            assertEquals(radius * 2, it.width)
            assertEquals(radius * 2, it.height)
            assertReferenceImage("cropCenter(radius=10)", it)
        }
    }

    @Test
    fun `should crop image center with specified radii`() {
        val image = readTestImage()

        val radiusX = 10
        val radiusY = 15
        image.cropCenter(radiusX = radiusX, radiusY = radiusY).let {
            assertEquals(radiusX * 2, it.width)
            assertEquals(radiusY * 2, it.height)
            assertReferenceImage("cropCenter(radiusX=10, radiusY=15)", it)
        }
    }

    @Test
    fun `should throw exception when cropping out of bounds in strict mode`() {
        val image = readTestImage()

        assertThrows(IllegalArgumentException::class.java) {
            image.crop(-10, -10, 100, 100, strictClipping = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            image.crop(0, 0, image.width + 10, image.height + 10, strictClipping = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            image.crop(image.width - 5, image.height - 5, 10, 10, strictClipping = true)
        }
    }

    @Test
    fun `should handle cropping exactly on image boundaries`() {
        val image = readTestImage()

        val croppedImage = image.crop(0, 0, image.width, image.height, strictClipping = true)
        assertEquals(image.width, croppedImage.width)
        assertEquals(image.height, croppedImage.height)
        assertReferenceImage("crop(0,0,width,height)", croppedImage)
        assertImage(image, croppedImage)
    }

    @Test
    fun `should allow cropping out of bounds in non-strict mode`() {
        val image = readTestImage()

        val croppedImage = image.crop(-10, -10, 100, 100, strictClipping = false)
        assertEquals(100, croppedImage.width)  // Assuming image width is 80
        assertEquals(100, croppedImage.height) // Assuming image height is 80
        assertReferenceImage("crop(-10,-10,100,100,strictClipping=false)", croppedImage)
    }
}
