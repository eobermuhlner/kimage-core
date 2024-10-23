package ch.obermuhlner.kimage.image

import ch.obermuhlner.kimage.core.image.MatrixImage
import org.junit.jupiter.api.Test

class ImageProcessingTest: AbstractImageProcessingTest() {
    @Test
    fun `should assert images`() {
        val image = MatrixImage(5, 20)
        assertImage(image, image)
    }

    @Test
    fun `should load test images`() {
        assertReferenceImage("default", readTestImage())
    }
}