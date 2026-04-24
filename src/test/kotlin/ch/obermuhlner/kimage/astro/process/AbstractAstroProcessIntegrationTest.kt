package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import java.io.File
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

abstract class AbstractAstroProcessIntegrationTest : AbstractImageProcessingTest() {

    fun assertAstroProcess(processConfig: ProcessConfig) {
        val testDir = prepareTestRunDirectory()

        createRandomAstroImages(testDir, "light", 10, 20, 20)

        val process = AstroProcess(processConfig)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles() ?: emptyArray()
        assertTrue(outputFiles.isNotEmpty(), "Output directory should contain files")

        val outputImage = ImageReader.read(outputFiles.first())
        assertReferenceImage("test_output", outputImage)
    }

    fun prepareTestRunDirectory(): File {
        val testRunDirectory = testRunDirectory()
        testRunDirectory.mkdirs()
        testRunDirectory.deleteRecursively()
        testRunDirectory.mkdirs()
        return testRunDirectory
    }

    fun testRunDirectory(): File {
        return testResultsDirectory().resolve("temp-test-run")
    }

    data class MockStar(val x: Int, val y: Int, val brightness: Double)

    fun createRandomAstroImages(directory: File, prefix: String, count: Int, width: Int, height: Int, jitter: Int = 3, starDensity: Double = 0.05, random: Random = Random(42)) {
        val starPositions = mutableListOf<MockStar>()
        val starFieldWidth = width + jitter*2
        val starFieldHeight = height + jitter*2
        for (i in 0 until (starFieldWidth * starFieldHeight * starDensity).toInt()) {
            starPositions.add(MockStar(
                random.nextInt(-jitter, width+jitter + 1),
                random.nextInt(-jitter, height+jitter + 1),
                random.nextDouble(0.1, 1.0)))
        }
        for (index in 1..count) {
            val jitterX = random.nextInt(-jitter, jitter + 1)
            val jitterY = random.nextInt(-jitter, jitter + 1)
            val image = createRandomAstroImage(width, height, starPositions, random, jitterX, jitterY)
            val file = File(directory, "${prefix}${index}.png")
            writeTestImage(file, image)
        }
    }

    fun createRandomAstroImage(
        width: Int,
        height: Int,
        starPositions: List<MockStar>,
        random: Random,
        jitterX: Int = 0,
        jitterY: Int = 0,
        maxNoise: Double = 0.2
    ): MatrixImage {
        val matrix = DoubleMatrix(height, width) { row, col ->
            val noise = random.nextDouble() * maxNoise
            val star = starPositions.find { (sx, sy) ->
                val tx = sx + jitterX
                val ty = sy + jitterY
                tx == col && ty == row
            }
            ((star?.brightness ?: 0.0) + noise).coerceAtMost(1.0)
        }
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