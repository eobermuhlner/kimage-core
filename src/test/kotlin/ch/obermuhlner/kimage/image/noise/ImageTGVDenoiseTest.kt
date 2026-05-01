package ch.obermuhlner.kimage.image.noise

import ch.obermuhlner.kimage.core.image.noise.tgvDenoise
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageTGVDenoiseTest : AbstractImageProcessingTest() {

    @Test
    fun testTGVDenoise() {
        val image = createGradientTestImage(60, 30)

        assertReferenceImage("tgvDenoise_default", image.tgvDenoise())
        assertReferenceImage("tgvDenoise_strong", image.tgvDenoise(lambda = 0.5))
        assertReferenceImage("tgvDenoise_weak", image.tgvDenoise(lambda = 500.0))
        assertReferenceImage("tgvDenoise_highAlpha", image.tgvDenoise(alpha0 = 2.0, alpha1 = 4.0))
        assertReferenceImage("tgvDenoise_fewIter", image.tgvDenoise(iterations = 20))
    }
}
