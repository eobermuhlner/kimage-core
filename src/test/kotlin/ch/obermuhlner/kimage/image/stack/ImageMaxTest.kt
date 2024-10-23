package ch.obermuhlner.kimage.image.stack

import ch.obermuhlner.kimage.core.image.stack.max
import ch.obermuhlner.kimage.core.image.times
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageMaxTest: AbstractImageProcessingTest() {

    @Test
    fun `should max images`() {
        val image = readTestImage()
        val halfImage = image * 0.5
        val quarterImage = image * 0.25

        assertImage(image, max(image, halfImage, quarterImage), "max1")
        assertImage(image, max(halfImage, image, quarterImage), "max2")
        assertImage(image, max(halfImage, quarterImage, image), "max3")
    }
}