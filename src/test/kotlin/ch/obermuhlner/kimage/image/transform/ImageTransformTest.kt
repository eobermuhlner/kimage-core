package ch.obermuhlner.kimage.image.transform

import ch.obermuhlner.kimage.core.image.transform.mirrorX
import ch.obermuhlner.kimage.core.image.transform.mirrorY
import ch.obermuhlner.kimage.core.image.transform.rotateLeft
import ch.obermuhlner.kimage.core.image.transform.rotateRight
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageTransformTest: AbstractImageProcessingTest() {

    @Test
    fun `should mirrorX image`() {
        val image = readTestImage()

        image.mirrorX().let {
            assertEquals(image.width, it.width)
            assertEquals(image.height, it.height)
            assertReferenceImage("mirrorX", it)
        }
    }

    @Test
    fun `should mirrorY image`() {
        val image = readTestImage()

        image.mirrorY().let {
            assertEquals(image.width, it.width)
            assertEquals(image.height, it.height)
            assertReferenceImage("mirrorY", it)
        }
    }

    @Test
    fun `should rotateLeft image`() {
        val image = readTestImage()

        image.rotateLeft().let {
            assertEquals(image.width, it.height)
            assertEquals(image.height, it.width)
            assertReferenceImage("rotateLeft", it)
        }
    }

    @Test
    fun `should rotateRight image`() {
        val image = readTestImage()

        image.rotateRight().let {
            assertEquals(image.width, it.height)
            assertEquals(image.height, it.width)
            assertReferenceImage("rotateRight", it)
        }
    }
}