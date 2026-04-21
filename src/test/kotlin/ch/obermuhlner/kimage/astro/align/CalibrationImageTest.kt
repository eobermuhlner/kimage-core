package ch.obermuhlner.kimage.astro.align

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.math.median
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.values.values
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CalibrationImageTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `processCalibrationImages stacks multiple images`() {
        val calibrationDir = File(tempDir, "bias")
        calibrationDir.mkdirs()

        val bias1 = createTestImage(10, 10, 0.1)
        val bias2 = createTestImage(10, 10, 0.12)
        val bias3 = createTestImage(10, 10, 0.08)

        writeTestImage(File(calibrationDir, "bias1.png"), bias1)
        writeTestImage(File(calibrationDir, "bias2.png"), bias2)
        writeTestImage(File(calibrationDir, "bias3.png"), bias3)

        val (master, dirty) = processCalibrationImages(
            calibrationName = "bias",
            baseDirectory = tempDir,
            dirty = false,
            calibrationImageDirectoryPath = "bias",
            searchParentDirectories = false,
            debayer = false,
            inputImageExtension = "png",
            outputImageExtension = "png"
        )

        assertNotNull(master)
        assertEquals(true, dirty)
    }

    @Test
    fun `processCalibrationImages loads existing master`() {
        val calibrationDir = File(tempDir, "bias")
        calibrationDir.mkdirs()

        val existingMaster = createTestImage(10, 10, 0.1)
        writeTestImage(File(calibrationDir, "master_bias_bayer.png"), existingMaster)

        val (master, dirty) = processCalibrationImages(
            calibrationName = "bias",
            baseDirectory = tempDir,
            dirty = false,
            calibrationImageDirectoryPath = "bias",
            searchParentDirectories = false,
            debayer = false,
            inputImageExtension = "png",
            outputImageExtension = "png"
        )

        assertNotNull(master)
        assertEquals(false, dirty)
    }

    @Test
    fun `processCalibrationImages returns null when directory missing`() {
        val (master, dirty) = processCalibrationImages(
            calibrationName = "bias",
            baseDirectory = tempDir,
            dirty = false,
            calibrationImageDirectoryPath = "nonexistent",
            searchParentDirectories = false,
            debayer = false,
            outputImageExtension = "fit"
        )

        assertEquals(null, master)
        assertEquals(false, dirty)
    }

    @Test
    fun `processCalibrationImages calculates median stacking`() {
        val calibrationDir = File(tempDir, "bias")
        calibrationDir.mkdirs()

        val bias1 = createTestImage(10, 10, 0.1)
        val bias2 = createTestImage(10, 10, 0.2)
        val bias3 = createTestImage(10, 10, 0.3)

        writeTestImage(File(calibrationDir, "bias1.png"), bias1)
        writeTestImage(File(calibrationDir, "bias2.png"), bias2)
        writeTestImage(File(calibrationDir, "bias3.png"), bias3)

        val (master, _) = processCalibrationImages(
            calibrationName = "bias",
            baseDirectory = tempDir,
            dirty = false,
            calibrationImageDirectoryPath = "bias",
            searchParentDirectories = false,
            debayer = false,
            inputImageExtension = "png",
            outputImageExtension = "png"
        )

        assertNotNull(master)
        assertEquals(0.2, master!![Channel.Red].values().median(), 0.001)
    }

    private fun createTestImage(width: Int, height: Int, value: Double): MatrixImage {
        val matrix = DoubleMatrix(height, width) { _, _ -> value }
        return MatrixImage(
            width, height,
            Channel.Red to matrix,
            Channel.Green to matrix,
            Channel.Blue to matrix
        )
    }

    private fun writeTestImage(file: File, image: MatrixImage) {
        ImageWriter.write(image, file)
    }
}
