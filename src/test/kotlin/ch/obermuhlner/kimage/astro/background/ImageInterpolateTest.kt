package ch.obermuhlner.kimage.astro.background

import ch.obermuhlner.kimage.astro.color.stretchLogarithmic
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.plus
import ch.obermuhlner.kimage.core.image.values.applyEach
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageInterpolateTest: AbstractImageProcessingTest() {

    @Test
    fun `should interpolate manual points`() {
        val image = readTestImage()

        val fixPoints = listOf(
            PointXY(10, 10),
            PointXY(10, image.height-10),
            PointXY(image.width-10, 10),
            PointXY(image.width-10, image.height-10),
            PointXY(image.width/2, image.height/2)
        )

        for (i in 1 .. fixPoints.size) {
            val fixPointValues = image.getFixPointValues(
                fixPoints.subList(0, i),
                estimateMedianRadiusForInterpolate(i, image.width, image.height)
            )

            val background = image.interpolate(
                fixPointValues
            ).let {
                assertReferenceImage("interpolate$i", it)
                it
            }

            val withoutBackgroundImage = image - background
            withoutBackgroundImage.applyEach { v -> clamp(v, 0.0, 1.0) }
            assertReferenceImage("withoutBackgroundImage$i", withoutBackgroundImage)
        }
    }


    @Test
    fun `should interpolate gradient test image with various automated grid points`() {
        val image = createGradientTestImage(600, 300)
        assertReferenceImage("orig", image)

        for (i in 1 .. 9) {
            val fixPoints = image.createFixPointGrid(i, i)

            val medianRadius = 50
            val power = 2.0
            val sigmaThreshold = 3.0
            val sigmaClippingMaxIterations = 5
            //val medianRadius = estimateMedianRadiusForInterpolate(fixPoints, image.width, image.height)
            //val power = estimatePowerForInterpolate(fixPoints, image.width, image.height)
            println("generated ${fixPoints.size} fix points")
            println("medianRadius: $medianRadius")
            println("power: $power")

            val fixPointValues = image.getFixPointValues(fixPoints.subList(0, i))

            val background = image.interpolate(
                fixPointValues,
                power
            ).let {
                assertReferenceImage("interpolate$i", it)
                it
            }

            val withoutBackgroundImage = image - background + 0.01
            withoutBackgroundImage.applyEach { v -> clamp(v, 0.0, 1.0) }
            assertReferenceImage("withoutBackgroundImage$i", withoutBackgroundImage)
        }
    }

    @Test
    fun `should interpolate M42 with various automated grid points`() {
        val image = readTestImage("small_M42.png")
        //val image = createGradientTestImage(600, 300)
        val stretchFactor = 1000.0
        assertReferenceImage("orig", image.stretchLogarithmic(stretchFactor))

        for (i in 1 .. 9) {
            val fixPointValues = image.getFixPointValues(image.createFixPointGrid(i, i))

            val medianRadius = 50
            val power = 2.0
            val sigmaThreshold = 3.0
            val sigmaClippingMaxIterations = 5
            //val medianRadius = estimateMedianRadiusForInterpolate(fixPoints, image.width, image.height)
            //val power = estimatePowerForInterpolate(fixPoints, image.width, image.height)
            println("generated ${fixPointValues.size} fix points")
            println("medianRadius: $medianRadius")
            println("power: $power")

            val background = image.interpolate(
                fixPointValues,
                power = power
            ).let {
                assertReferenceImage("interpolate$i", it.stretchLogarithmic(stretchFactor))
                it
            }

            val withoutBackgroundImage = image - background + 0.01
            withoutBackgroundImage.applyEach { v -> clamp(v, 0.0, 1.0) }
            assertReferenceImage("withoutBackgroundImage$i", withoutBackgroundImage.stretchLogarithmic(stretchFactor))
        }
    }
}
