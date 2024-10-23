package ch.obermuhlner.kimage.image.filter

import ch.obermuhlner.kimage.core.image.filter.KernelFilter
import ch.obermuhlner.kimage.core.image.filter.Shape
import ch.obermuhlner.kimage.core.image.filter.averageFilter
import ch.obermuhlner.kimage.core.image.filter.boxBlur3Filter
import ch.obermuhlner.kimage.core.image.filter.edgeDetectionCrossFilter
import ch.obermuhlner.kimage.core.image.filter.edgeDetectionDiagonalFilter
import ch.obermuhlner.kimage.core.image.filter.edgeDetectionStrongFilter
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur3Filter
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur5Filter
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur7Filter
import ch.obermuhlner.kimage.core.image.filter.gaussianBlurFilter
import ch.obermuhlner.kimage.core.image.filter.kernelFilter
import ch.obermuhlner.kimage.core.image.filter.medianFilter
import ch.obermuhlner.kimage.core.image.filter.medianPixelFilter
import ch.obermuhlner.kimage.core.image.filter.sharpenFilter
import ch.obermuhlner.kimage.core.image.filter.slowMedianFilter
import ch.obermuhlner.kimage.core.image.filter.sobelFilter
import ch.obermuhlner.kimage.core.image.filter.sobel3Filter
import ch.obermuhlner.kimage.core.image.filter.sobel5Filter
import ch.obermuhlner.kimage.core.image.filter.unsharpMaskFilter
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageFilterTest : AbstractImageProcessingTest() {

    @Test
    fun `should apply box blur filter`() {
        val image = readTestImage()

        val boxBlurImage = image.boxBlur3Filter()
        assertReferenceImage("boxBlur3Filter()", boxBlurImage)
    }

    @Test
    fun `should apply gaussian blur 3 filter`() {
        val image = readTestImage()

        val gaussianBlurImage = image.gaussianBlur3Filter()
        assertReferenceImage("gaussianBlur3Filter()", gaussianBlurImage)
    }

    @Test
    fun `should apply gaussian blur 5 filter`() {
        val image = readTestImage()

        val gaussianBlurImage = image.gaussianBlur5Filter()
        assertReferenceImage("gaussianBlur5Filter()", gaussianBlurImage)
    }

    @Test
    fun `should apply gaussian blur 7 filter`() {
        val image = readTestImage()

        val gaussianBlurImage = image.gaussianBlur7Filter()
        assertReferenceImage("gaussianBlur7Filter()", gaussianBlurImage)
    }

    @Test
    fun `should apply gaussian blur filter with specified radius`() {
        val image = readTestImage()
        val radius = 5

        val blurredImage = image.gaussianBlurFilter(radius)
        assertReferenceImage("gaussianBlur(radius=5)", blurredImage)
    }

    @Test
    fun `should apply average filter with specified radius and shape`() {
        val image = readTestImage()
        val radius = 3

        val averageImage = image.averageFilter(radius)
        assertReferenceImage("averageFilter(radius=3,shape=Square)", averageImage)
    }

    @Test
    fun `should apply median filter with specified radius`() {
        val image = readTestImage()
        val radius = 4

        val medianImage = image.medianFilter(radius)
        assertReferenceImage("medianFilter(radius=4)", medianImage)
    }

    @Test
    fun `should apply slow median filter with specified radius`() {
        val image = readTestImage()
        val radius = 4

        val medianImage = image.slowMedianFilter(radius, Shape.Circle)
        assertReferenceImage("slowMedianFilter(radius=4,Circle)", medianImage)

        for (shape in Shape.entries) {
            val medianImage = image.slowMedianFilter(radius, shape)
            assertReferenceImage("slowMedianFilter(radius=4,$shape)", medianImage)

            val medianRecursiveImage = image.slowMedianFilter(radius, shape, true)
            assertReferenceImage("slowMedianFilter(radius=4,$shape,recursive=true)", medianRecursiveImage)
        }
    }

    @Test
    fun `should apply median pixel filter with specified radius`() {
        val image = readTestImage()
        val radius = 4

        val medianImage = image.medianPixelFilter(radius)
        assertReferenceImage("medianPixelFilter(radius=4)", medianImage)
    }

    @Test
    fun `should apply unsharp mask filter with specified radius and strength`() {
        val image = readTestImage()
        val radius = 2
        val strength = 1.5

        val unsharpImage = image.unsharpMaskFilter(radius, strength)
        assertReferenceImage("unsharpMaskFilter(radius=2,strength=1.5)", unsharpImage)
    }

    @Test
    fun `should apply sharpen filter`() {
        val image = readTestImage()

        val sharpenedImage = image.sharpenFilter()
        assertReferenceImage("sharpenFilter()", sharpenedImage)
    }

    @Test
    fun `should apply edge detection strong filter`() {
        val image = readTestImage()

        val edgeImage = image.edgeDetectionStrongFilter()
        assertReferenceImage("edgeDetectionStrongFilter()", edgeImage)
    }

    @Test
    fun `should apply edge detection cross filter`() {
        val image = readTestImage()

        val edgeImage = image.edgeDetectionCrossFilter()
        assertReferenceImage("edgeDetectionCrossFilter()", edgeImage)
    }

    @Test
    fun `should apply edge detection diagonal filter`() {
        val image = readTestImage()

        val edgeImage = image.edgeDetectionDiagonalFilter()
        assertReferenceImage("edgeDetectionDiagonalFilter()", edgeImage)
    }

    @Test
    fun `should apply sobel filter with different kernels`() {
        val image = readTestImage()

        val sobelImage35 = image.sobelFilter(KernelFilter.SobelHorizontal3, KernelFilter.SobelVertical5)
        assertReferenceImage("sobelFilter(Sobel3,Sobel5)", sobelImage35)

        val sobelImage53 = image.sobelFilter(KernelFilter.SobelHorizontal5, KernelFilter.SobelVertical3)
        assertReferenceImage("sobelFilter(Sobel5,Sobel3)", sobelImage53)
    }

    @Test
    fun `should apply sobel3 filter`() {
        val image = readTestImage()

        val sobelImage = image.sobel3Filter()
        assertReferenceImage("sobel3Filter()", sobelImage)
    }

    @Test
    fun `should apply sobel5 filter`() {
        val image = readTestImage()

        val sobelImage = image.sobel5Filter()
        assertReferenceImage("sobel5Filter()", sobelImage)
    }

    @Test
    fun `should apply kernel filter with custom kernel`() {
        val image = readTestImage()
        val kernel = DoubleMatrix.matrixOf(3, 3,
            0.0, -1.0, 0.0,
            -1.0, 5.0, -1.0,
            0.0, -1.0, 0.0
        )

        val filteredImage = image.kernelFilter(kernel)
        assertReferenceImage("kernelFilter(custom)", filteredImage)
    }
}
