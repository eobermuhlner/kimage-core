package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SmartInitTest {

    @TempDir
    lateinit var tempDir: Path

    // ---- detectLightFiles ----

    @Test
    fun `detectLightFiles returns empty and dot when directory is empty`() {
        val (files, dir) = detectLightFiles(tempDir.toFile())
        assertTrue(files.isEmpty())
        assertEquals(".", dir)
    }

    @Test
    fun `detectLightFiles finds fit files in root directory`() {
        tempDir.resolve("frame1.fit").toFile().createNewFile()
        tempDir.resolve("frame2.fit").toFile().createNewFile()
        val (files, dir) = detectLightFiles(tempDir.toFile())
        assertEquals(2, files.size)
        assertEquals(".", dir)
    }

    @Test
    fun `detectLightFiles finds fits files in root directory`() {
        tempDir.resolve("frame1.fits").toFile().createNewFile()
        val (files, dir) = detectLightFiles(tempDir.toFile())
        assertEquals(1, files.size)
        assertEquals(".", dir)
    }

    @Test
    fun `detectLightFiles finds files in light subdirectory`() {
        val lightDir = tempDir.resolve("light").toFile()
        lightDir.mkdirs()
        lightDir.resolve("frame1.fit").createNewFile()
        lightDir.resolve("frame2.fit").createNewFile()
        val (files, dir) = detectLightFiles(tempDir.toFile())
        assertEquals(2, files.size)
        assertEquals("light", dir)
    }

    @Test
    fun `detectLightFiles finds files in Lights subdirectory`() {
        val lightsDir = tempDir.resolve("Lights").toFile()
        lightsDir.mkdirs()
        lightsDir.resolve("frame1.tif").createNewFile()
        val (files, dir) = detectLightFiles(tempDir.toFile())
        assertEquals(1, files.size)
        assertEquals("Lights", dir)
    }

    @Test
    fun `detectLightFiles prefers root directory over light subdirectory`() {
        tempDir.resolve("frame1.fit").toFile().createNewFile()
        val lightDir = tempDir.resolve("light").toFile()
        lightDir.mkdirs()
        lightDir.resolve("frame2.fit").createNewFile()
        val (files, dir) = detectLightFiles(tempDir.toFile())
        assertEquals(1, files.size)
        assertEquals(".", dir)
    }

    // ---- findCalibrationDir ----

    @Test
    fun `findCalibrationDir returns null when no matching directory exists`() {
        val result = findCalibrationDir(tempDir.toFile(), listOf("bias", "biases"))
        assertNull(result)
    }

    @Test
    fun `findCalibrationDir finds default-named directory`() {
        tempDir.resolve("dark").toFile().mkdirs()
        val result = findCalibrationDir(tempDir.toFile(), listOf("dark", "darks"))
        assertEquals("dark", result)
    }

    @Test
    fun `findCalibrationDir finds plural alias`() {
        tempDir.resolve("darks").toFile().mkdirs()
        val result = findCalibrationDir(tempDir.toFile(), listOf("dark", "darks"))
        assertEquals("darks", result)
    }

    @Test
    fun `findCalibrationDir finds dark underscore flat alias`() {
        tempDir.resolve("dark_flat").toFile().mkdirs()
        val result = findCalibrationDir(tempDir.toFile(), listOf("darkflat", "darkflats", "dark_flat", "dark_flats"))
        assertEquals("dark_flat", result)
    }

    @Test
    fun `findCalibrationDir finds case-variant directory`() {
        tempDir.resolve("BIAS").toFile().mkdirs()
        val result = findCalibrationDir(tempDir.toFile(), listOf("bias", "biases"))
        assertEquals("BIAS", result)
    }

    @Test
    fun `findCalibrationDir prefers exact match over case variant`() {
        tempDir.resolve("bias").toFile().mkdirs()
        tempDir.resolve("BIAS").toFile().mkdirs()
        val result = findCalibrationDir(tempDir.toFile(), listOf("bias", "biases"))
        assertEquals("bias", result)
    }

    // ---- computeSigmoidMidpoint ----

    @Test
    fun `computeSigmoidMidpoint returns 0_01 for empty file list`() {
        val midpoint = computeSigmoidMidpoint(emptyList())
        assertEquals(0.01, midpoint)
    }

    @Test
    fun `computeSigmoidMidpoint derives low midpoint from dark image`() {
        val file = tempDir.resolve("dark.tif").toFile()
        val image = createUniformImage(0.003)
        ImageWriter.write(image, file)
        val midpoint = computeSigmoidMidpoint(listOf(file))
        assertTrue(midpoint in 0.001..0.01, "Expected midpoint near 0.003, got $midpoint")
    }

    @Test
    fun `computeSigmoidMidpoint derives mid-level midpoint from suburban-sky image`() {
        val file = tempDir.resolve("suburban.tif").toFile()
        val image = createUniformImage(0.08)
        ImageWriter.write(image, file)
        val midpoint = computeSigmoidMidpoint(listOf(file))
        assertTrue(midpoint in 0.05..0.15, "Expected midpoint near 0.08, got $midpoint")
    }

    @Test
    fun `computeSigmoidMidpoint clamps bright image to 0_3`() {
        val file = tempDir.resolve("bright.tif").toFile()
        val image = createUniformImage(0.5)
        ImageWriter.write(image, file)
        val midpoint = computeSigmoidMidpoint(listOf(file))
        assertEquals(0.3, midpoint)
    }

    @Test
    fun `computeSigmoidMidpoint clamps very dark image to 0_001`() {
        val file = tempDir.resolve("veryDark.tif").toFile()
        val image = createUniformImage(0.0001)
        ImageWriter.write(image, file)
        val midpoint = computeSigmoidMidpoint(listOf(file))
        assertEquals(0.001, midpoint)
    }

    // ---- generateSmartConfigText ----

    private fun defaultDetection() = SmartInitDetection(
        inputExtension = "fit",
        inputDirectory = ".",
        debayerEnabled = true,
        bayerPattern = "RGGB",
        biasDirectory = "bias",
        darkDirectory = "dark",
        flatDirectory = "flat",
        darkflatDirectory = "darkflat",
        sigmoidMidpoint = 0.01,
        targetName = null,
        lightFileCount = 10,
    )

    @Test
    fun `generateSmartConfigText default detection preserves default extension`() {
        val text = generateSmartConfigText(defaultDetection())
        assertTrue(text.contains("inputImageExtension: fit"), "Should contain default extension")
    }

    @Test
    fun `generateSmartConfigText uses detected fits extension`() {
        val text = generateSmartConfigText(defaultDetection().copy(inputExtension = "fits"))
        assertTrue(text.contains("inputImageExtension: fits"))
        assertFalse(text.contains("inputImageExtension: fit\n") || text.contains("inputImageExtension: fit "), "Should not contain the old 'fit' value")
    }

    @Test
    fun `generateSmartConfigText uses detected tif extension`() {
        val text = generateSmartConfigText(defaultDetection().copy(inputExtension = "tif"))
        assertTrue(text.contains("inputImageExtension: tif"))
    }

    @Test
    fun `generateSmartConfigText injects inputDirectory for light subdirectory`() {
        val text = generateSmartConfigText(defaultDetection().copy(inputDirectory = "light"))
        assertTrue(text.contains("inputDirectory: light"))
    }

    @Test
    fun `generateSmartConfigText does not inject inputDirectory for root`() {
        val text = generateSmartConfigText(defaultDetection())
        assertFalse(text.contains("inputDirectory:"))
    }

    @Test
    fun `generateSmartConfigText disables debayer for tiff`() {
        val text = generateSmartConfigText(defaultDetection().copy(debayerEnabled = false))
        assertTrue(text.contains("enabled: false"))
        assertFalse(text.contains("    enabled: true               # Convert Bayer"))
    }

    @Test
    fun `generateSmartConfigText replaces bayer pattern`() {
        val text = generateSmartConfigText(defaultDetection().copy(bayerPattern = "GRBG"))
        assertTrue(text.contains("bayerPattern: GRBG"))
        assertFalse(text.contains("bayerPattern: RGGB"))
    }

    @Test
    fun `generateSmartConfigText replaces first sigmoid midpoint only`() {
        val text = generateSmartConfigText(defaultDetection().copy(sigmoidMidpoint = 0.0500))
        // First sigmoid (step 6) should use new midpoint
        assertTrue(text.contains("midpoint: 0.0500"), "Expected midpoint 0.0500, text had: ${text.lines().filter { "midpoint" in it }}")
        // Subsequent sigmoids (steps 8-12) should keep 0.4
        val midpointLines = text.lines().filter { it.contains("midpoint:") }
        val fourPointLines = midpointLines.filter { it.contains("0.4") }
        assertTrue(fourPointLines.size >= 4, "Expected at least 4 lines with midpoint 0.4, got $fourPointLines")
    }

    @Test
    fun `generateSmartConfigText does not inject calibrate section for default directory names`() {
        val text = generateSmartConfigText(defaultDetection())
        assertFalse(text.contains("calibrate:"))
    }

    @Test
    fun `generateSmartConfigText injects calibrate section when dark dir is non-default`() {
        val text = generateSmartConfigText(defaultDetection().copy(darkDirectory = "darks"))
        assertTrue(text.contains("calibrate:"))
        assertTrue(text.contains("darkDirectory: darks"))
    }

    @Test
    fun `generateSmartConfigText calibrate section appears before enhance section`() {
        val text = generateSmartConfigText(defaultDetection().copy(flatDirectory = "flats"))
        val calibratePos = text.indexOf("calibrate:")
        val enhancePos = text.indexOf("enhance:")
        assertTrue(calibratePos < enhancePos, "calibrate section should precede enhance section")
    }

    @Test
    fun `generateSmartConfigText sets target name in annotation title`() {
        val text = generateSmartConfigText(defaultDetection().copy(targetName = "M42"))
        assertTrue(text.contains("title: \"M42\""))
        assertFalse(text.contains("title: \"Object Name\""))
    }

    @Test
    fun `generateSmartConfigText leaves default annotation title when target name absent`() {
        val text = generateSmartConfigText(defaultDetection())
        assertTrue(text.contains("title: \"Object Name\""))
    }

    // ---- helpers ----

    private fun createUniformImage(level: Double): MatrixImage {
        val size = 64
        val matrix = DoubleMatrix.matrixOf(size, size) { _, _ -> level }
        return MatrixImage(size, size,
            Channel.Red to matrix,
            Channel.Green to matrix,
            Channel.Blue to matrix,
        )
    }
}
