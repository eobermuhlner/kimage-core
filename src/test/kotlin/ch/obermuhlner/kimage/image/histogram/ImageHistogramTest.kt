package ch.obermuhlner.kimage.image.histogram

import ch.obermuhlner.kimage.core.image.histogram.histogramImage
import ch.obermuhlner.kimage.core.image.transform.mirrorX
import ch.obermuhlner.kimage.core.image.transform.mirrorY
import ch.obermuhlner.kimage.core.image.transform.rotateLeft
import ch.obermuhlner.kimage.core.image.transform.rotateRight
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageHistogramTest: AbstractImageProcessingTest() {

    @Test
    fun `should create histogram image`() {
        val image = readTestImage("flowers.bmp")

        image.histogramImage(200, 100).let {
            assertReferenceImage("histogramImage", it)
        }
    }
}