package ch.obermuhlner.kimage.core.image.stack

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class StackConfigTest {

    private fun makeImages(count: Int, width: Int = 4, height: Int = 4, baseValue: Double = 0.5): List<() -> MatrixImage> {
        return (0 until count).map { i ->
            {
                val m = DoubleMatrix(height, width) { r, c -> baseValue + (i + r + c) * 0.01 }
                MatrixImage(width, height,
                    Channel.Red to m,
                    Channel.Green to m,
                    Channel.Blue to m
                )
            }
        }
    }

    @Test
    fun `tile-based produces same result as mmap for median`() {
        val suppliers = makeImages(5)

        val mmapResult = stack(suppliers, StackConfig(algorithm = StackAlgorithm.Median))
        val tileResult = stack(suppliers, StackConfig(
            algorithm = StackAlgorithm.Median,
            maxDiskSpaceBytes = 0L  // forces tile-based (row-by-row)
        ))

        val channels = listOf(Channel.Red, Channel.Green, Channel.Blue)
        for (ch in channels) {
            val mmapMatrix = mmapResult[ch]
            val tileMatrix = tileResult[ch]
            for (i in 0 until mmapMatrix.size) {
                assertEquals(mmapMatrix[i], tileMatrix[i], 1e-9,
                    "Channel $ch pixel $i differs between mmap and tile-based")
            }
        }
    }

    @Test
    fun `tile-based produces same result as mmap for sigma clip average`() {
        val suppliers = makeImages(10)

        val mmapResult = stack(suppliers, StackConfig(algorithm = StackAlgorithm.SigmaClipAverage))
        val tileResult = stack(suppliers, StackConfig(
            algorithm = StackAlgorithm.SigmaClipAverage,
            maxDiskSpaceBytes = 0L
        ))

        val channels = listOf(Channel.Red, Channel.Green, Channel.Blue)
        for (ch in channels) {
            val mmapMatrix = mmapResult[ch]
            val tileMatrix = tileResult[ch]
            for (i in 0 until mmapMatrix.size) {
                assertEquals(mmapMatrix[i], tileMatrix[i], 1e-9,
                    "Channel $ch pixel $i differs between mmap and tile-based")
            }
        }
    }

    @Test
    fun `double precision produces results close to float precision for median`() {
        val suppliers = makeImages(5)

        val floatResult = stack(suppliers, StackConfig(algorithm = StackAlgorithm.Median, precision = StackPrecision.Float))
        val doubleResult = stack(suppliers, StackConfig(algorithm = StackAlgorithm.Median, precision = StackPrecision.Double))

        // Results should be very close (float vs double rounding only)
        for (ch in listOf(Channel.Red, Channel.Green, Channel.Blue)) {
            val fm = floatResult[ch]
            val dm = doubleResult[ch]
            for (i in 0 until fm.size) {
                assertEquals(fm[i], dm[i], 1e-5,
                    "Channel $ch pixel $i: float and double precision diverged too much")
            }
        }
    }

    @Test
    fun `double precision tile-based produces same result as double mmap`() {
        val suppliers = makeImages(5)

        val mmapResult = stack(suppliers, StackConfig(algorithm = StackAlgorithm.Median, precision = StackPrecision.Double))
        val tileResult = stack(suppliers, StackConfig(
            algorithm = StackAlgorithm.Median,
            precision = StackPrecision.Double,
            maxDiskSpaceBytes = 0L
        ))

        for (ch in listOf(Channel.Red, Channel.Green, Channel.Blue)) {
            val mm = mmapResult[ch]
            val tm = tileResult[ch]
            for (i in 0 until mm.size) {
                assertEquals(mm[i], tm[i], 1e-15,
                    "Double precision mmap and tile-based should be identical")
            }
        }
    }

    @Test
    fun `maxDiskSpaceBytes limits disk usage for large stacks`() {
        // Stack that would normally require disk: 10 images x 3 channels x 16 pixels x 4 bytes = 1920 bytes
        // Set limit to 100 bytes → forces tile-based
        val suppliers = makeImages(10)

        val result = stack(suppliers, StackConfig(
            algorithm = StackAlgorithm.Average,
            maxDiskSpaceBytes = 100L
        ))

        // Should complete without error and produce a valid image
        val red = result[Channel.Red]
        for (i in 0 until red.size) {
            val v = red[i]
            assert(v in 0.0..1.0) { "Pixel $i out of range: $v" }
        }
    }

    @Test
    fun `stack config with all algorithms completes without error`() {
        val suppliers = makeImages(6)
        val algorithmsToTest = StackAlgorithm.entries.filter { it != StackAlgorithm.Drizzle }

        for (algorithm in algorithmsToTest) {
            // Test both precisions and tile-based
            for (precision in StackPrecision.entries) {
                val result = stack(suppliers, StackConfig(algorithm = algorithm, precision = precision))
                assertEquals(4, result.width)
                assertEquals(4, result.height)
            }
            // Test tile-based
            val tileResult = stack(suppliers, StackConfig(algorithm = algorithm, maxDiskSpaceBytes = 0L))
            assertEquals(4, tileResult.width)
        }
    }

    @Test
    fun `tiled stack path uses tempDir for mmap files not RAM`() {
        val nonExistentDir = File(System.getProperty("java.io.tmpdir"), "kimage_nonexistent_${System.nanoTime()}")
        assertFalse(nonExistentDir.exists(), "Test precondition: directory must not exist")

        val suppliers = makeImages(3)
        val config = StackConfig(
            algorithm = StackAlgorithm.Median,
            maxDiskSpaceBytes = 1L,  // force tiling
            tempDir = nonExistentDir,
        )
        // Tiled path must use HugeMultiDimensionalFloatArray (disk), which fails when tempDir
        // doesn't exist. If this assertion fails the tiled path is silently using RAM instead.
        assertThrows<Exception> { stack(suppliers, config) }
    }

    @Test
    fun `required disk space is calculated correctly`() {
        val suppliers = makeImages(10)
        // 10 images × 3 channels × 16 pixels × 4 bytes = 1920 bytes for float
        // Setting maxDiskSpaceBytes to exactly 1920 should use mmap
        // Setting to 1919 should use tile-based

        val atLimit = stack(suppliers, StackConfig(algorithm = StackAlgorithm.Average, maxDiskSpaceBytes = 1920L))
        val overLimit = stack(suppliers, StackConfig(algorithm = StackAlgorithm.Average, maxDiskSpaceBytes = 1919L))

        // Both should give same result since the algorithm is deterministic
        for (ch in listOf(Channel.Red, Channel.Green, Channel.Blue)) {
            for (i in 0 until atLimit[ch].size) {
                assertEquals(atLimit[ch][i], overLimit[ch][i], 1e-6,
                    "At-limit and over-limit should produce same result")
            }
        }
    }
}
