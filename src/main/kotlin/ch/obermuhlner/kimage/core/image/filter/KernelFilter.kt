package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.filter.convolute

class KernelFilter(
    private val kernel: Matrix
) : MatrixImageFilter(
    { _, source ->
        source.convolute(kernel)
    }
) {

    companion object {
        val EdgeDetectionStrong = DoubleMatrix.matrixOf(
            3, 3,
            -1.0, -1.0, -1.0,
            -1.0, 8.0, -1.0,
            -1.0, -1.0, -1.0)

        val EdgeDetectionCross = DoubleMatrix.matrixOf(
                3, 3,
                0.0, -1.0, 0.0,
                -1.0, 4.0, -1.0,
                0.0, -1.0, 0.0)

        val EdgeDetectionDiagonal = DoubleMatrix.matrixOf(
                3, 3,
                -1.0, 0.0, -1.0,
                0.0, 4.0, 0.0,
                -1.0, 0.0, -1.0)

        val Sharpen = DoubleMatrix.matrixOf(
                3, 3,
                0.0, -1.0, 0.0,
                -1.0, 5.0, -1.0,
                0.0, -1.0, 0.0)

        val BoxBlur3 = DoubleMatrix.matrixOf(
                3, 3,
                1.0, 1.0, 1.0,
                1.0, 1.0, 1.0,
                1.0, 1.0, 1.0) / 9.0

        val GaussianBlur3 = DoubleMatrix.matrixOf(
                3, 3,
                1.0, 2.0, 1.0,
                2.0, 4.0, 2.0,
                1.0, 2.0, 1.0) / 16.0

        val GaussianBlur5 = DoubleMatrix.matrixOf(
                5, 5,
                1.0, 4.0, 6.0, 4.0, 1.0,
                4.0, 16.0, 24.0, 16.0, 4.0,
                16.0, 24.0, 36.0, 24.0, 16.0,
                4.0, 16.0, 24.0, 16.0, 4.0,
                1.0, 4.0, 6.0, 4.0, 1.0) / 256.0

        val GaussianBlur7 = DoubleMatrix.matrixOf(
            7, 7,
            0.0, 0.0, 1.0, 2.0, 1.0, 0.0, 0.0,
            0.0, 3.0, 13.0, 22.0, 13.0, 3.0, 0.0,
            1.0, 13.0, 59.0, 97.0, 59.0, 13.0, 1.0,
            2.0, 22.0, 97.0, 159.0, 97.0, 22.0, 2.0,
            1.0, 13.0, 59.0, 97.0, 59.0, 13.0, 1.0,
            0.0, 3.0, 13.0, 22.0, 13.0, 3.0, 0.0,
            0.0, 0.0, 1.0, 2.0, 1.0, 0.0, 0.0,) / 1003.0


        val UnsharpMask = DoubleMatrix.matrixOf(
                5, 5,
                1.0, 4.0, 6.0, 4.0, 1.0,
                4.0, 16.0, 24.0, 16.0, 4.0,
                16.0, 24.0, -476.0, 24.0, 16.0,
                4.0, 16.0, 24.0, 16.0, 4.0,
                1.0, 4.0, 6.0, 4.0, 1.0) / -256.0

        val Emboss = DoubleMatrix.matrixOf(
                3, 3,
                -2.0, -1.0, 0.0,
                -1.0, 1.0, 1.0,
                0.0, 1.0, 2.0)

        val SobelHorizontal3 = DoubleMatrix.matrixOf(3, 3,
            1.0, 0.0, -1.0,
            2.0, 0.0, -2.0,
            1.0, 0.0, -1.0)

        val SobelVertical3 = DoubleMatrix.matrixOf(3, 3,
            1.0, 2.0, 1.0,
            0.0, 0.0, 0.0,
            -1.0, -2.0, -1.0)

        val SobelHorizontal5 = DoubleMatrix.matrixOf(5, 5,
            2.0, 1.0, 0.0, -1.0, -2.0,
            2.0, 1.0, 0.0, -1.0, -2.0,
            4.0, 2.0, 0.0, -2.0, -4.0,
            2.0, 1.0, 0.0, -1.0, -2.0,
            2.0, 1.0, 0.0, -1.0, -2.0)

        val SobelVertical5 = DoubleMatrix.matrixOf(5, 5,
            2.0, 2.0, 4.0, 2.0, 2.0,
            1.0, 1.0, 2.0, 1.0, 1.0,
            0.0, 0.0, 0.0, 0.0, 0.0,
            -1.0, -1.0, -2.0, -1.0, -1.0,
            -2.0, -2.0, -4.0, -2.0, -2.0)

    }
}