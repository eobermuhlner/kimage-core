package ch.obermuhlner.kimage.image.filter

import ch.obermuhlner.kimage.core.image.filter.KernelFilter
import ch.obermuhlner.kimage.core.image.filter.Shape
import ch.obermuhlner.kimage.core.image.filter.averageFilter
import ch.obermuhlner.kimage.core.image.filter.boxBlur3Filter
import ch.obermuhlner.kimage.core.image.filter.edgeDetectionCrossFilter
import ch.obermuhlner.kimage.core.image.filter.edgeDetectionDiagonalFilter
import ch.obermuhlner.kimage.core.image.filter.edgeDetectionStrongFilter
import ch.obermuhlner.kimage.core.image.filter.edgeEnhancementFilter
import ch.obermuhlner.kimage.core.image.filter.embossFilter
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur3Filter
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur5Filter
import ch.obermuhlner.kimage.core.image.filter.gaussianBlur7Filter
import ch.obermuhlner.kimage.core.image.filter.gaussianBlurFilter
import ch.obermuhlner.kimage.core.image.filter.highPassFilter
import ch.obermuhlner.kimage.core.image.filter.kernelFilter
import ch.obermuhlner.kimage.core.image.filter.laplacianFilter
import ch.obermuhlner.kimage.core.image.filter.medianFilter
import ch.obermuhlner.kimage.core.image.filter.medianPixelFilter
import ch.obermuhlner.kimage.core.image.filter.motionBlurFilter
import ch.obermuhlner.kimage.core.image.filter.sharpenFilter
import ch.obermuhlner.kimage.core.image.filter.slowMedianFilter
import ch.obermuhlner.kimage.core.image.filter.sobel3Filter
import ch.obermuhlner.kimage.core.image.filter.sobel5Filter
import ch.obermuhlner.kimage.core.image.filter.sobelFilter
import ch.obermuhlner.kimage.core.image.filter.unsharpMaskFilter
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class ImageFilterTest : AbstractImageProcessingTest() {

    @Test
    fun `should apply box blur filter`() {
        val image = readTestImage()

        image.boxBlur3Filter().let {
            assertReferenceImage("boxBlur3Filter()", it)
        }
    }

    @Test
    fun `should apply emboss filter`() {
        val image = readTestImage()

        image.embossFilter().let {
            assertReferenceImage("embossFilter()", it)
        }
    }

    @Test
    fun `should apply laplacian filter`() {
        val image = readTestImage()

        image.laplacianFilter().let {
            assertReferenceImage("laplacianFilter()", it)
        }
    }

    @Test
    fun `should apply edge enhancement filter`() {
        val image = readTestImage()

        image.edgeEnhancementFilter().let {
            assertReferenceImage("edgeEnhancementFilter()", it)
        }
    }

    @Test
    fun `should apply motion blur filter`() {
        val image = readTestImage()

        image.motionBlurFilter().let {
            assertReferenceImage("motionBlurFilter()", it)
        }
    }

    @Test
    fun `should apply high pass filter`() {
        val image = readTestImage()

        image.highPassFilter().let {
            assertReferenceImage("highPassFilter()", it)
        }
    }

    @Test
    fun `should apply gaussian blur 3 filter`() {
        val image = readTestImage()

        image.gaussianBlur3Filter().let {
            assertReferenceImage("gaussianBlur3Filter()", it)
        }
    }

    @Test
    fun `should apply gaussian blur 5 filter`() {
        val image = readTestImage()

        image.gaussianBlur5Filter().let {
            assertReferenceImage("gaussianBlur5Filter()", it)
        }
    }

    @Test
    fun `should apply gaussian blur 7 filter`() {
        val image = readTestImage()

        image.gaussianBlur7Filter().let {
            assertReferenceImage("gaussianBlur7Filter()", it)
        }
    }

    @Test
    fun `should apply gaussian blur filter with specified radius`() {
        val image = readTestImage()
        val radius = 5

        image.gaussianBlurFilter(radius).let {
            assertReferenceImage("gaussianBlur(radius=5)", it)
        }
    }

    @Test
    fun `should apply average filter with specified radius and shape`() {
        val image = readTestImage()
        val radius = 3

        image.averageFilter(radius).let {
            assertReferenceImage("averageFilter(radius=3,shape=Square)", it)
        }
    }

    @Test
    fun `should apply median filter with specified radius`() {
        val image = readTestImage()
        val radius = 4

        image.medianFilter(radius).let {
            assertReferenceImage("medianFilter(radius=4)", it)
        }
    }

    @Test
    fun `should apply slow median filter with specified radius`() {
        val image = readTestImage()
        val radius = 4

        for (shape in Shape.entries) {
            image.slowMedianFilter(radius, shape).let {
                assertReferenceImage("slowMedianFilter(radius=4,$shape)", it)
            }

            image.slowMedianFilter(radius, shape, true).let {
                assertReferenceImage("slowMedianFilter(radius=4,$shape,recursive=true)", it)
            }
        }
    }

    @Test
    fun `should apply median pixel filter with specified radius`() {
        val image = readTestImage()
        val radius = 4

        image.medianPixelFilter(radius).let {
            assertReferenceImage("medianPixelFilter(radius=4)", it)
        }
    }

    @Test
    fun `should apply unsharp mask filter with specified radius and strength`() {
        val image = readTestImage()
        val radius = 2
        val strength = 1.5

        image.unsharpMaskFilter(radius, strength).let {
            assertReferenceImage("unsharpMaskFilter(radius=2,strength=1.5)", it)
        }
    }

    @Test
    fun `should apply sharpen filter`() {
        val image = readTestImage()

        image.sharpenFilter().let {
            assertReferenceImage("sharpenFilter()", it)
        }
    }

    @Test
    fun `should apply edge detection strong filter`() {
        val image = readTestImage()

        image.edgeDetectionStrongFilter().let {
            assertReferenceImage("edgeDetectionStrongFilter()", it)
        }
    }

    @Test
    fun `should apply edge detection cross filter`() {
        val image = readTestImage()

        image.edgeDetectionCrossFilter().let {
            assertReferenceImage("edgeDetectionCrossFilter()", it)
        }
    }

    @Test
    fun `should apply edge detection diagonal filter`() {
        val image = readTestImage()

        image.edgeDetectionDiagonalFilter().let {
            assertReferenceImage("edgeDetectionDiagonalFilter()", it)
        }
    }

    @Test
    fun `should apply sobel filter with different kernels`() {
        val image = readTestImage()

        image.sobelFilter(KernelFilter.SobelHorizontal3, KernelFilter.SobelVertical5).let {
            assertReferenceImage("sobelFilter(Sobel3,Sobel5)", it)
        }

        image.sobelFilter(KernelFilter.SobelHorizontal5, KernelFilter.SobelVertical3).let {
            assertReferenceImage("sobelFilter(Sobel5,Sobel3)", it)
        }
    }

    @Test
    fun `should apply sobel3 filter`() {
        val image = readTestImage()

        image.sobel3Filter().let {
            assertReferenceImage("sobel3Filter()", it)
        }
    }

    @Test
    fun `should apply sobel5 filter`() {
        val image = readTestImage()

        image.sobel5Filter().let {
            assertReferenceImage("sobel5Filter()", it)
        }
    }

    @Test
    fun `should apply kernel filter with custom kernel`() {
        val image = readTestImage()
        val kernel = DoubleMatrix.matrixOf(3, 3,
            0.0, -1.0, 0.0,
            -1.0, 5.0, -1.0,
            0.0, -1.0, 0.0
        )

        image.kernelFilter(kernel).let {
            assertReferenceImage("kernelFilter(custom)", it)
        }
    }
}
