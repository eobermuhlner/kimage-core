package ch.obermuhlner.kimage.image

import ch.obermuhlner.kimage.core.image.Image
import org.junit.jupiter.api.Test

class ImageReadWriteTest: AbstractImageProcessingTest() {

    @Test
    fun `should read rgb image in different formats`() {
        val image = readTestImage()

        assertReferenceImage("jpg", image, "jpg", 0.8)
        assertReferenceImage("png", image, "png", 1E-10)
        assertReferenceImage("tif", image, "tif", 1E-10)
        assertReferenceImage("fits", image, "fits", 1E-10)
        assertReferenceImage("gif", image, "gif", 1E-10)
        assertReferenceImage("bmp", image, "bmp", 1E-10)
    }
}