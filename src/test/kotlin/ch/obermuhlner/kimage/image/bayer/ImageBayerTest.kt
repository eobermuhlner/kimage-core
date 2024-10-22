package ch.obermuhlner.kimage.image.bayer

import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.bayer
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageBayerTest : AbstractImageProcessingTest() {

    @Test
    fun `should bayer image`() {
        for (bayerPattern in BayerPattern.entries) {
            assertReferenceImage(
                "lena_$bayerPattern",
                readTestImage("lena512.png").bayer(bayerPattern)
            )
        }
    }
}