package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import java.io.File
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertTrue

abstract class AbstractAstroProcessIntegrationTest : AbstractImageProcessingTest() {

    var testDir: File = File(".")
    var random: Random = Random(1)
    var width = 20
    var height = 20
    val starPositions = mutableListOf<MockStar>()

    var noiseBiasSigma = 0.01
    var noiseBiasLevel = 0.02
    var noiseReadSigma = 0.05
    var noiseReadLevel = 0.1


    fun initTestRun() {
        testDir = prepareTestRunDirectory()
        random = Random(42)
        starPositions.clear()

        val starFieldWidth = width * 3
        val starFieldHeight = height * 2
        val starDensity = 0.05
        val starCount = (starFieldWidth * starFieldHeight * starDensity).toInt()
        for (i in 0 until starCount) {
            starPositions.add(MockStar(
                random.nextInt(starFieldWidth) - width,
                random.nextInt(starFieldHeight) - height,
                random.nextDouble(0.0, 1.0)))
        }
    }

    fun assertAstroProcess(processConfig: ProcessConfig) {
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

    data class MockStar(
        val x: Int,
        val y: Int,
        val brightness: Double,
    )

    fun createRandomAstroImages(
        directory: File,
        prefix: String,
        count: Int,
        jitter: Int = 3,
        addBiasNoise: Boolean = true,
        addReadNoise: Boolean = true,
        addSignal: Boolean = true,
    ) {
        directory.mkdirs()
        for (index in 1..count) {
            val jitterX = random.nextInt(-jitter, jitter + 1)
            val jitterY = random.nextInt(-jitter, jitter + 1)
            val image = createRandomAstroImage(width, height, starPositions, jitterX, jitterY, addBiasNoise, addReadNoise, addSignal)
            val file = File(directory, "${prefix}${index}.png")
            writeTestImage(file, image)
        }
    }

    fun createRandomAstroImage(
        width: Int,
        height: Int,
        starPositions: List<MockStar>,
        jitterX: Int = 0,
        jitterY: Int = 0,
        addBiasNoise: Boolean = true,
        addReadNoise: Boolean = true,
        addSignal: Boolean = true,
    ): MatrixImage {
        val matrix = DoubleMatrix(height, width) { row, col ->
            val biasNoise = random.nextGaussian(noiseBiasLevel, noiseBiasSigma)
            val readNoise = random.nextGaussian(noiseReadLevel, noiseReadSigma)

            val star = starPositions.find { (sx, sy) ->
                val tx = sx + jitterX
                val ty = sy + jitterY
                tx == col && ty == row
            }
            val starValue = star?.brightness ?: 0.0
            val skyValue = 0.01

            val biasValue = if (addBiasNoise) biasNoise else 0.0
            val readValue = if (addReadNoise) readNoise else 0.0
            val signalValue = if (addSignal) starValue + skyValue else 0.0
            val value = (biasValue + readValue + signalValue)
            value.coerceIn(0.0, 1.0)
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

    private fun Random.nextGaussian(mean: Double = 0.0, stdDev: Double = 1.0): Double {
        var u: Double
        var v: Double
        var s: Double

        do {
            u = 2.0 * this.nextDouble() - 1.0
            v = 2.0 * this.nextDouble() - 1.0
            s = u*u + v*v
        } while (s >= 1 || s == 0.0)

        val mul = sqrt(-2.0 * ln(s) / s)
        return mean + stdDev * u * mul
    }

    private fun Random.nextPoisson(lambda: Double = 1.0): Double {
        return nextGaussian(lambda, sqrt(lambda))
    }
}