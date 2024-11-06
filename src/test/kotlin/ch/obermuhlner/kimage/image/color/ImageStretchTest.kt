package ch.obermuhlner.kimage.image.color

import ch.obermuhlner.kimage.astro.color.stretch
import ch.obermuhlner.kimage.astro.color.stretchAsinh
import ch.obermuhlner.kimage.astro.color.stretchAsinhPercentile
import ch.obermuhlner.kimage.astro.color.stretchExponential
import ch.obermuhlner.kimage.astro.color.stretchExponentialMedian
import ch.obermuhlner.kimage.astro.color.stretchExponentialPercentile
import ch.obermuhlner.kimage.astro.color.stretchLinear
import ch.obermuhlner.kimage.astro.color.stretchLinearFactor
import ch.obermuhlner.kimage.astro.color.stretchLinearPercentile
import ch.obermuhlner.kimage.astro.color.stretchLogarithmic
import ch.obermuhlner.kimage.astro.color.stretchSTF
import ch.obermuhlner.kimage.astro.color.stretchSigmoid
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageStretchTest : AbstractImageProcessingTest() {

    @Test
    fun testStretch() {
        val image = readTestImage("small_M42.png")

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
    }
}