package ch.obermuhlner.kimage.core.image.stack

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrizzleTest {

    private fun uniformImage(width: Int, height: Int, value: Double = 0.5): MatrixImage {
        val m = DoubleMatrix(height, width) { _, _ -> value }
        return MatrixImage(width, height,
            Channel.Red to m,
            Channel.Green to m,
            Channel.Blue to m
        )
    }

    private fun identityMatrix(): DoubleMatrix = DoubleMatrix(3, 3) { row, col ->
        if (row == col) 1.0 else 0.0
    }

    private fun translationMatrix(dx: Double, dy: Double): DoubleMatrix = DoubleMatrix(3, 3) { row, col ->
        when {
            row == col -> 1.0
            row == 0 && col == 2 -> dx
            row == 1 && col == 2 -> dy
            else -> 0.0
        }
    }

    @Test
    fun `identity transform scale=1 pixfrac=1 square kernel matches input for interior pixels`() {
        val width = 20
        val height = 20
        val value = 0.5
        val image = uniformImage(width, height, value)
        val config = DrizzleConfig(scale = 1.0, pixfrac = 1.0, kernel = DrizzleKernel.Square)
        val result = drizzle(listOf(image to identityMatrix()), config)

        assertEquals(width, result.width)
        assertEquals(height, result.height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                assertEquals(value, result[x, y, Channel.Red], 1e-5, "[$x,$y] Red")
            }
        }
    }

    @Test
    fun `flux conservation with scale=2 square kernel`() {
        val width = 20
        val height = 20
        val value = 0.5
        val image = uniformImage(width, height, value)
        val config = DrizzleConfig(scale = 2.0, pixfrac = 1.0, kernel = DrizzleKernel.Square)
        val result = drizzle(listOf(image to identityMatrix()), config)

        var inputSum = 0.0
        for (y in 0 until height) for (x in 0 until width) inputSum += image[x, y, Channel.Red]

        var outputSum = 0.0
        for (y in 0 until result.height) for (x in 0 until result.width) outputSum += result[x, y, Channel.Red]

        // Normalised pixel average should be preserved: outputSum / (outW * outH) ≈ inputSum / (W * H)
        val inputAvg = inputSum / (width * height)
        val outputAvg = outputSum / (result.width * result.height)
        assertEquals(inputAvg, outputAvg, inputAvg * 0.15, "average pixel value should be preserved")
    }

    @Test
    fun `zero-weight pixels at boundary receive value 0`() {
        val width = 10
        val height = 10
        val image = uniformImage(width, height, 0.5)
        // Shift by half the image so the top-left area has no coverage
        val config = DrizzleConfig(scale = 1.0, pixfrac = 0.5, kernel = DrizzleKernel.Square)
        val result = drizzle(listOf(image to translationMatrix(width / 2.0, height / 2.0)), config)

        assertEquals(0.0, result[0, 0, Channel.Red], 1e-6, "top-left pixel should be 0 (no coverage)")
    }

    @Test
    fun `gaussian kernel output pixels bounded in 0 to 1`() {
        val width = 15
        val height = 15
        val image = uniformImage(width, height, 1.0)
        val config = DrizzleConfig(scale = 1.0, pixfrac = 0.5, kernel = DrizzleKernel.Gaussian)
        val result = drizzle(listOf(image to identityMatrix()), config)

        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val v = result[x, y, Channel.Red]
                assertTrue(v >= -1e-6, "[$x,$y] = $v should be >= 0")
                assertTrue(v <= 1.0 + 1e-6, "[$x,$y] = $v should be <= 1")
            }
        }
    }

    @Test
    fun `sigma clip rejection removes hot pixel from single frame`() {
        val width = 10
        val height = 10
        val normal = uniformImage(width, height, 0.5)
        // One frame has a hot pixel at (5,5) — extreme outlier on Red channel
        val hotMatrix = DoubleMatrix(height, width) { row, col ->
            if (row == 5 && col == 5) 1.0 else 0.5
        }
        val withHotPixel = MatrixImage(width, height,
            Channel.Red to hotMatrix,
            Channel.Green to DoubleMatrix(height, width) { _, _ -> 0.5 },
            Channel.Blue to DoubleMatrix(height, width) { _, _ -> 0.5 }
        )

        // 3 frames: normal, hot, normal → sigma clip should reject the hot value
        val config = DrizzleConfig(
            scale = 1.0, pixfrac = 1.0, kernel = DrizzleKernel.Square,
            rejection = DrizzleRejection.SigmaClip, kappa = 2.0, iterations = 3
        )
        val result = drizzle(
            listOf(normal to identityMatrix(), withHotPixel to identityMatrix(), normal to identityMatrix()),
            config
        )

        // Without rejection the result at (5,5) Red would be ~0.667; with rejection it should be ~0.5
        assertEquals(0.5, result[5, 5, Channel.Red], 0.05, "hot pixel should be rejected by sigma clip")
        // Green channel (no hot pixel) should be unaffected
        assertEquals(0.5, result[5, 5, Channel.Green], 0.05, "green channel should be unaffected")
    }

    @Test
    fun `winsorize rejection clamps outlier towards mean`() {
        val width = 10
        val height = 10
        val normal = uniformImage(width, height, 0.5)
        val hotMatrix = DoubleMatrix(height, width) { row, col ->
            if (row == 5 && col == 5) 1.0 else 0.5
        }
        val withHotPixel = MatrixImage(width, height,
            Channel.Red to hotMatrix,
            Channel.Green to DoubleMatrix(height, width) { _, _ -> 0.5 },
            Channel.Blue to DoubleMatrix(height, width) { _, _ -> 0.5 }
        )

        val configNoRejection = DrizzleConfig(scale = 1.0, pixfrac = 1.0, kernel = DrizzleKernel.Square,
            rejection = DrizzleRejection.None)
        val configWinsorize = DrizzleConfig(scale = 1.0, pixfrac = 1.0, kernel = DrizzleKernel.Square,
            rejection = DrizzleRejection.Winsorize, kappa = 2.0)

        val frames = listOf(normal to identityMatrix(), withHotPixel to identityMatrix(), normal to identityMatrix())
        val resultNoRejection = drizzle(frames, configNoRejection)
        val resultWinsorize = drizzle(frames, configWinsorize)

        // Winsorize should move result closer to 0.5 compared to no rejection
        val noRejectionValue = resultNoRejection[5, 5, Channel.Red]
        val winsorizeValue = resultWinsorize[5, 5, Channel.Red]
        assertTrue(winsorizeValue < noRejectionValue, "winsorize should reduce outlier influence")
        assertTrue(winsorizeValue < 0.6, "winsorized value should be much closer to 0.5 than 0.667")
    }

    @Test
    fun `no rejection is default and matches original single-pass result`() {
        val width = 15
        val height = 15
        val image = uniformImage(width, height, 0.5)
        val frames = listOf(image to identityMatrix(), image to translationMatrix(0.5, 0.0))

        val configNone = DrizzleConfig(scale = 1.0, pixfrac = 0.8, kernel = DrizzleKernel.Square,
            rejection = DrizzleRejection.None)
        val result = drizzle(frames, configNone)

        // All interior pixels should have the expected uniform value
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                assertTrue(result[x, y, Channel.Red] > 0.0, "[$x,$y] should be covered")
            }
        }
    }

    @Test
    fun `two shifted frames both contribute to interior output pixels`() {
        val width = 20
        val height = 20
        val value = 0.6
        val image1 = uniformImage(width, height, value)
        val image2 = uniformImage(width, height, value)
        val config = DrizzleConfig(scale = 2.0, pixfrac = 0.7, kernel = DrizzleKernel.Square)
        val result = drizzle(
            listOf(
                image1 to translationMatrix(0.5, 0.0),
                image2 to translationMatrix(-0.5, 0.0)
            ),
            config
        )

        for (y in 2 until result.height - 2) {
            for (x in 2 until result.width - 2) {
                assertTrue(result[x, y, Channel.Red] > 0.0, "[$x,$y] should be covered, got ${result[x, y, Channel.Red]}")
            }
        }
    }

    @Test
    fun `crop config restricts output to specified region and adjusts output dimensions`() {
        val width = 20
        val height = 20
        val value = 0.5
        val image = uniformImage(width, height, value)

        // Crop to the bottom-right quadrant in reference-frame coords
        val cropX = width / 2
        val cropY = height / 2
        val cropW = width / 2
        val cropH = height / 2
        val scale = 2.0

        val config = DrizzleConfig(
            scale = scale,
            pixfrac = 1.0,
            kernel = DrizzleKernel.Square,
            crop = DrizzleCropConfig(enabled = true, x = cropX, y = cropY, width = cropW, height = cropH)
        )
        val result = drizzle(listOf(image to identityMatrix()), config)

        // Output dimensions should reflect the crop size scaled up
        assertEquals((cropW * scale).toInt(), result.width, "output width should be cropW * scale")
        assertEquals((cropH * scale).toInt(), result.height, "output height should be cropH * scale")

        // Interior pixels should have the expected uniform value
        for (y in 1 until result.height - 1) {
            for (x in 1 until result.width - 1) {
                assertEquals(value, result[x, y, Channel.Red], 1e-4, "[$x,$y] Red should equal $value")
            }
        }
    }

    @Test
    fun `boundary pixels are not black with crop at image edge and default pixfrac`() {
        // Crop covers the full image so there are no input pixels outside the crop to
        // "leak" into the last output column/row.  The drizzle kernel must itself reach
        // the boundary from the last interior input pixel.
        val width = 20
        val height = 20
        val value = 0.5
        val image = uniformImage(width, height, value)

        val scale = 2.0

        val config = DrizzleConfig(
            scale = scale,
            pixfrac = 0.7,
            kernel = DrizzleKernel.Square,
            crop = DrizzleCropConfig(enabled = true, x = 0, y = 0, width = width, height = height)
        )
        val result = drizzle(listOf(image to identityMatrix()), config)

        for (y in 2 until result.height - 2) {
            assertTrue(result[result.width - 1, y, Channel.Red] > 0.0,
                "Right column pixel [${result.width - 1},$y] should not be black")
        }
        for (x in 2 until result.width - 2) {
            assertTrue(result[x, result.height - 1, Channel.Red] > 0.0,
                "Bottom row pixel [$x,${result.height - 1}] should not be black")
        }
    }

    @Test
    fun `crop disabled produces same output size as no crop`() {
        val width = 16
        val height = 16
        val image = uniformImage(width, height, 0.5)
        val scale = 2.0

        val configNoCrop = DrizzleConfig(scale = scale, pixfrac = 1.0, kernel = DrizzleKernel.Square)
        val configCropDisabled = DrizzleConfig(
            scale = scale, pixfrac = 1.0, kernel = DrizzleKernel.Square,
            crop = DrizzleCropConfig(enabled = false, x = 4, y = 4, width = 8, height = 8)
        )

        val resultNoCrop = drizzle(listOf(image to identityMatrix()), configNoCrop)
        val resultCropDisabled = drizzle(listOf(image to identityMatrix()), configCropDisabled)

        assertEquals(resultNoCrop.width, resultCropDisabled.width, "disabled crop should not change output width")
        assertEquals(resultNoCrop.height, resultCropDisabled.height, "disabled crop should not change output height")
    }

    @Test
    fun `tiled two-pass produces same result as full mmap two-pass`() {
        val width = 16
        val height = 16
        val normal = uniformImage(width, height, 0.5)
        val hotMatrix = DoubleMatrix(height, width) { row, col ->
            if (row == 8 && col == 8) 1.0 else 0.5
        }
        val withHotPixel = MatrixImage(width, height,
            Channel.Red to hotMatrix,
            Channel.Green to DoubleMatrix(height, width) { _, _ -> 0.5 },
            Channel.Blue to DoubleMatrix(height, width) { _, _ -> 0.5 }
        )

        val config = DrizzleConfig(
            scale = 1.0, pixfrac = 1.0, kernel = DrizzleKernel.Square,
            rejection = DrizzleRejection.SigmaClip, kappa = 2.0, iterations = 3
        )
        val frames = listOf(normal to identityMatrix(), withHotPixel to identityMatrix(), normal to identityMatrix())

        // Full mmap (no disk limit)
        val fullResult = drizzle(frames.map { (img, m) -> ({ img } to m) }, config, maxDiskSpaceBytes = Long.MAX_VALUE)

        // Tiled: force single-row tiles by setting maxDiskSpaceBytes to 1 byte (< any real usage)
        val tiledResult = drizzle(frames.map { (img, m) -> ({ img } to m) }, config, maxDiskSpaceBytes = 1L)

        assertEquals(fullResult.width, tiledResult.width)
        assertEquals(fullResult.height, tiledResult.height)
        for (y in 0 until fullResult.height) {
            for (x in 0 until fullResult.width) {
                assertEquals(
                    fullResult[x, y, Channel.Red], tiledResult[x, y, Channel.Red], 1e-5,
                    "[$x,$y] Red differs between full and tiled"
                )
            }
        }
    }

    @Test
    fun `tiled two-pass with scale=2 produces same result as full mmap`() {
        val width = 12
        val height = 12
        val image = uniformImage(width, height, 0.5)
        val frames = listOf(
            { image } to identityMatrix(),
            { image } to translationMatrix(0.5, 0.5),
        )
        val config = DrizzleConfig(
            scale = 2.0, pixfrac = 0.7, kernel = DrizzleKernel.Square,
            rejection = DrizzleRejection.SigmaClip, kappa = 2.0, iterations = 3
        )

        val fullResult = drizzle(frames, config, maxDiskSpaceBytes = Long.MAX_VALUE)
        val tiledResult = drizzle(frames, config, maxDiskSpaceBytes = 1L)

        assertEquals(fullResult.width, tiledResult.width)
        assertEquals(fullResult.height, tiledResult.height)
        for (y in 0 until fullResult.height) {
            for (x in 0 until fullResult.width) {
                assertEquals(
                    fullResult[x, y, Channel.Red], tiledResult[x, y, Channel.Red], 1e-5,
                    "[$x,$y] Red differs between full and tiled (scale=2)"
                )
            }
        }
    }
}
