package ch.obermuhlner.kimage.image.color

import ch.obermuhlner.kimage.astro.color.stretch
import ch.obermuhlner.kimage.astro.color.stretchAsinh
import ch.obermuhlner.kimage.astro.color.stretchAsinhPercentile
import ch.obermuhlner.kimage.astro.color.stretchAutoSTF
import ch.obermuhlner.kimage.astro.color.stretchExponential
import ch.obermuhlner.kimage.astro.color.stretchExponentialMedian
import ch.obermuhlner.kimage.astro.color.stretchExponentialPercentile
import ch.obermuhlner.kimage.astro.color.stretchGHS
import ch.obermuhlner.kimage.astro.color.stretchLinear
import ch.obermuhlner.kimage.astro.color.stretchLinearFactor
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchLogarithmic
import ch.obermuhlner.kimage.astro.color.stretchMasked
import ch.obermuhlner.kimage.astro.color.stretchSTF
import ch.obermuhlner.kimage.astro.color.stretchSigmoid
import ch.obermuhlner.kimage.astro.color.stretchSigmoidLike
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageStretchTest : AbstractImageProcessingTest() {

    @Test
    fun testStretch() {
        val image = createGradientTestImage(60, 30)

        assertReferenceImage("stretchLinear", image.stretchLinear())
        assertReferenceImage("stretchLinearPercentile", image.stretchLinearPercentile())
        assertReferenceImage("stretchLinearFactor", image.stretchLinearFactor(2.0))
        assertReferenceImage("stretchAsinh", image.stretchAsinh())
        assertReferenceImage("stretchAsinhPercentile", image.stretchAsinhPercentile())
        assertReferenceImage("stretchExponential", image.stretchExponential(0.1))
        assertReferenceImage("stretchExponentialPercentile", image.stretchExponentialPercentile())
        assertReferenceImage("stretchExponentialMedian", image.stretchExponentialMedian())
        assertReferenceImage("stretchSigmoid", image.stretchSigmoid())
        assertReferenceImage("stretchLogarithmic", image.stretchLogarithmic())
        assertReferenceImage("stretchSTF", image.stretchSTF())
        assertReferenceImage("stretchGHS", image.stretchGHS())
        assertReferenceImage("stretchGHS_D3_b3_SP0.2_LP0.05_HP0.9", image.stretchGHS(D = 3.0, b = 3.0, SP = 0.2, LP = 0.05, HP = 0.9))
        assertReferenceImage("stretchGHS_b0_linear", image.stretchGHS(D = 5.0, b = 0.0, SP = 0.1))
        assertReferenceImage("stretchMasked_sigmoid", image.stretchMasked(0.5) { it.stretchSigmoidLike() })
        assertReferenceImage("stretchMasked_ghs", image.stretchMasked(0.5) { it.stretchGHS() })
        assertReferenceImage("stretchMasked_asinh", image.stretchMasked(0.5) { it.stretchAsinh() })
        assertReferenceImage("stretchMasked_threshold0.3", image.stretchMasked(0.3) { it.stretchSigmoidLike() })
        assertReferenceImage("stretchMasked_threshold0.7", image.stretchMasked(0.7) { it.stretchSigmoidLike() })
    }

    @Test
    fun testStretchAutoSTF() {
        val image = createGradientTestImage(60, 30)

        assertReferenceImage("stretchAutoSTF", image.stretchAutoSTF())
        assertReferenceImage("stretchAutoSTF_perChannel", image.stretchAutoSTF(perChannel = true))
        assertReferenceImage("stretchAutoSTF_custom", image.stretchAutoSTF(shadowClipping = 1.5, targetBackground = 0.15))
    }
}