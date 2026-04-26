package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.bayer
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import java.io.File
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertTrue

abstract class AbstractAstroProcessIntegrationTest : AbstractImageProcessingTest() {

    var testDir: File = File(".")
    var random: Random = Random(1)
    var width = 50
    var height = 50
    var starDensity = 0.02
    val starPositions = mutableListOf<MockStar>()

    var flatLevel = 0.8
    var flatSigma = 0.1

    var vignetteStrength = 0.5
    var vignetteRadius = 1.0

    var noiseBiasSigma = 0.001
    var noiseBiasLevel = 0.01
    var noiseReadSigma = 0.005
    var noiseReadLevel = 0.01

    var photonNoiseScale = 0.1

    var starWidth = 0.5
    var starBleed = 2.5
    var starBeta = 2.5


    fun initTestRun() {
        testDir = prepareTestRunDirectory()
        random = Random(42)
        starPositions.clear()

        val starFieldWidth = width * 3
        val starFieldHeight = height * 2
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
        jitter: Double = 3.0,
        bayerPattern: BayerPattern? = null,
        addBiasNoise: Boolean = true,
        addReadNoise: Boolean = true,
        addSignal: Boolean = true,
    ) {
        directory.mkdirs()
        for (index in 1..count) {
            val effectiveJitter = if (index == 1) 0.0 else jitter
            val jitterX = if (effectiveJitter == 0.0) 0.0 else random.nextDouble(-effectiveJitter, effectiveJitter)
            val jitterY = if (effectiveJitter == 0.0) 0.0 else random.nextDouble(-effectiveJitter, effectiveJitter)
            var image = createRandomAstroImage(width, height, starPositions, jitterX, jitterY, addBiasNoise, addReadNoise, addSignal)
            if (bayerPattern != null) {
                image = image.bayer(bayerPattern)
            }
            val file = File(directory, "${prefix}${index}.png")
            writeTestImage(file, image)
        }
    }

    fun createRandomBayerImages(
        directory: File,
        prefix: String,
        count: Int,
        pattern: BayerPattern = BayerPattern.RGGB,
        jitter: Int = 3,
        addBiasNoise: Boolean = true,
    ) {
        directory.mkdirs()
        for (index in 1..count) {
            val jitterX = random.nextInt(-jitter, jitter + 1)
            val jitterY = random.nextInt(-jitter, jitter + 1)
            val rgbImage = createRandomAstroImage(width, height, starPositions, jitterX, jitterY, addBiasNoise)
            val bayerImage = rgbImage.bayer(pattern)
            val file = File(directory, "${prefix}${index}.png")
            writeTestImage(file, bayerImage)
        }
    }

    fun createRandomFlatImages(
        directory: File,
        prefix: String,
        count: Int,
    ) {
        directory.mkdirs()
        for (index in 1..count) {
            val image = createRandomFlatImage(width, height)
            val file = File(directory, "${prefix}${index}.png")
            writeTestImage(file, image)
        }
    }

    fun createRandomAstroImage(
        width: Int,
        height: Int,
        starPositions: List<MockStar>,
        jitterX: Double = 0.0,
        jitterY: Double = 0.0,
        addBiasNoise: Boolean = true,
        addReadNoise: Boolean = true,
        addSignal: Boolean = true,
    ): MatrixImage {
        val centerX = width / 2.0
        val centerY = height / 2.0
        val matrix = DoubleMatrix(height, width) { row, col ->
            val biasNoise = random.nextGaussian(noiseBiasLevel, noiseBiasSigma)
            val readNoise = random.nextGaussian(noiseReadLevel, noiseReadSigma)
            val skyValue = 0.1

            val dxV = ((col - centerX) / centerX).coerceIn(-1.0, 1.0)
            val dyV = ((row - centerY) / centerY).coerceIn(-1.0, 1.0)
            val r = sqrt(dxV * dxV + dyV * dyV)
            val vignette = (1.0 - vignetteStrength * r.pow(2.0 / vignetteRadius)).coerceIn(0.0, 1.0)

            var totalStarSignal = 0.0
            for (star in starPositions) {
                val starX = star.x + jitterX
                val starY = star.y + jitterY
                val dxStar = col.toDouble() - starX
                val dyStar = row.toDouble() - starY
                val dist = sqrt(dxStar * dxStar + dyStar * dyStar)
                val effectiveWidth = starWidth * (1.0 + star.brightness * starBleed)
                val gamma = 1.0 / effectiveWidth
                val moffatPSF = (1.0 + (dist * gamma).pow(2.0)).pow(-starBeta)
                totalStarSignal += star.brightness * moffatPSF
            }

            val rawSignal = (totalStarSignal + skyValue) * vignette

            val photonNoise: Double = if (addSignal) random.nextGaussian(0.0, sqrt(rawSignal + 1e-10) * photonNoiseScale) else 0.0

            val biasValue: Double = if (addBiasNoise) biasNoise else 0.0
            val readValue: Double = if (addReadNoise) readNoise else 0.0
            val signalValue: Double = if (addSignal) rawSignal + photonNoise else 0.0
            val value = biasValue + readValue + signalValue
            value.coerceIn(0.0, 1.0)
        }
        return MatrixImage(
            width, height,
            Channel.Red to matrix,
            Channel.Green to matrix,
            Channel.Blue to matrix
        )
    }

    fun createRandomFlatImage(
        width: Int,
        height: Int,
    ): MatrixImage {
        val centerX = width / 2.0
        val centerY = height / 2.0
        val matrix = DoubleMatrix(height, width) { row, col ->
            val dx = ((col - centerX) / centerX).coerceIn(-1.0, 1.0)
            val dy = ((row - centerY) / centerY).coerceIn(-1.0, 1.0)
            val r = sqrt(dx * dx + dy * dy)
            val vignette = (1.0 - vignetteStrength * r.pow(2.0 / vignetteRadius)).coerceIn(0.0, 1.0)
            random.nextGaussian(flatLevel, flatSigma) * vignette
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