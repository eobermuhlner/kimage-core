package ch.obermuhlner.kimage.image.scale

import ch.obermuhlner.kimage.core.image.scaling.scaleBy
import ch.obermuhlner.kimage.core.image.scaling.scaleTo
import ch.obermuhlner.kimage.core.matrix.scaling.Scaling
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageScaleTest: AbstractImageProcessingTest() {

    @Test
    fun `should scaleTo image`() {
        val image = readTestImage("monarch.bmp")

        assertImage(image, image.scaleTo(image.width, image.height, scaling = Scaling.Nearest)) // only Nearest guarantees unchanged values
        assertReferenceImage("scaleTo(w,h,Bilinear)", image.scaleTo(image.width, image.height, scaling = Scaling.Bilinear))
        assertReferenceImage("scaleTo(w,h,Bicubic)", image.scaleTo(image.width, image.height, scaling = Scaling.Bicubic))

        image.scaleTo(100, 200).let {
            assertEquals(100, it.width)
            assertEquals(200, it.height)
            assertReferenceImage("scaleBy(100,200)", it)
        }
    }

    @Test
    fun `should scaleBy image`() {
        val image = readTestImage("monarch_small.png")

        assertImage(image, image.scaleBy(1.0, 1.0, scaling = Scaling.Nearest)) // only Nearest guarantees unchanged values
        assertReferenceImage("scaleBy(1,1,Bilinear)", image.scaleBy(1.0, 1.0, scaling = Scaling.Bilinear))
        assertReferenceImage("scaleBy(1,1,Bicubic)", image.scaleBy(1.0, 1.0, scaling = Scaling.Bicubic))

        assertReferenceImage("scaleBy(1.2,0.7)", image.scaleBy(1.2, 0.7))

        for (scaling in Scaling.entries) {
            assertReferenceImage("scaleBy(2,4,$scaling)", image.scaleBy(2.0, 4.0, scaling = scaling))
        }
    }
}