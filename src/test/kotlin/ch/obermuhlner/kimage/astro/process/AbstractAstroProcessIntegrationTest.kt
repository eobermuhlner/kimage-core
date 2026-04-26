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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
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

    var satelliteTrailProbability = 0.3
    var satelliteTrailBrightnessMin = 0.1
    var satelliteTrailBrightnessMax = 1.0
    var satelliteTrailWidthMin = 0.1
    var satelliteTrailWidthMax = 1.1

    var sensorHotPixelCount = 0
    var sensorDeadPixelCount = 0
    var sensorStuckPixelCount = 0
    var sensorBadColumnCount = 0

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
                random.nextDouble(0.0, 1.0),
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
        val colorIndex: Double = 0.5,
    )

    data class MockSatelliteTrail(
        val x0: Double,
        val y0: Double,
        val x1: Double,
        val y1: Double,
        val brightness: Double,
        val width: Double,
    )

    data class SensorArtifacts(
        val hotPixels: List<Pair<Int, Int>> = emptyList(),
        val deadPixels: List<Pair<Int, Int>> = emptyList(),
        val stuckPixels: List<Triple<Int, Int, Channel>> = emptyList(),
        val badColumns: List<Int> = emptyList(),
    )

    private fun generateSensorArtifacts(): SensorArtifacts {
        val hotPixels = (0 until sensorHotPixelCount).map {
            random.nextInt(height) to random.nextInt(width)
        }
        val deadPixels = (0 until sensorDeadPixelCount).map {
            random.nextInt(height) to random.nextInt(width)
        }
        val stuckPixels = (0 until sensorStuckPixelCount).map {
            val x = random.nextInt(width)
            val y = random.nextInt(height)
            val channel = Channel.entries[random.nextInt(Channel.entries.size)]
            Triple(x, y, channel)
        }
        val badColumns = (0 until sensorBadColumnCount).map {
            random.nextInt(width)
        }
        return SensorArtifacts(hotPixels, deadPixels, stuckPixels, badColumns)
    }

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
        val sensorArtifacts = generateSensorArtifacts()
        for (index in 1..count) {
            val effectiveJitter = if (index == 1) 0.0 else jitter
            val jitterX = if (effectiveJitter == 0.0) 0.0 else random.nextDouble(-effectiveJitter, effectiveJitter)
            val jitterY = if (effectiveJitter == 0.0) 0.0 else random.nextDouble(-effectiveJitter, effectiveJitter)
            val trailCount = random.nextPoisson(satelliteTrailProbability)
            val trails = List(trailCount) { createRandomSatelliteTrail() }
            var image = createRandomAstroImage(width, height, starPositions, jitterX, jitterY, addBiasNoise, addReadNoise, addSignal, trails, sensorArtifacts)
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
        jitter: Double = 3.0,
        addBiasNoise: Boolean = true,
    ) {
        directory.mkdirs()
        for (index in 1..count) {
            val effectiveJitter = if (index == 1) 0.0 else jitter
            val jitterX = if (effectiveJitter == 0.0) 0.0 else random.nextDouble(-effectiveJitter, effectiveJitter)
            val jitterY = if (effectiveJitter == 0.0) 0.0 else random.nextDouble(-effectiveJitter, effectiveJitter)
            val rgbImage = createRandomAstroImage(width, height, starPositions, jitterX, jitterY, addBiasNoise)
            val bayerImage = rgbImage.bayer(pattern)
            val file = File(directory, "${prefix}${index}.png")
            writeTestImage(file, bayerImage)
        }
    }

    private fun createRandomSatelliteTrail(): MockSatelliteTrail {
        val edge = random.nextInt(4)
        val x0: Double
        val y0: Double
        val x1: Double
        val y1: Double
        when (edge) {
            0 -> { x0 = random.nextDouble(0.0, width.toDouble()); y0 = 0.0; x1 = random.nextDouble(0.0, width.toDouble()); y1 = height.toDouble() }
            1 -> { x0 = width.toDouble(); y0 = random.nextDouble(0.0, height.toDouble()); x1 = 0.0; y1 = random.nextDouble(0.0, height.toDouble()) }
            2 -> { x0 = random.nextDouble(0.0, width.toDouble()); y0 = height.toDouble(); x1 = random.nextDouble(0.0, width.toDouble()); y1 = 0.0 }
            else -> { x0 = 0.0; y0 = random.nextDouble(0.0, height.toDouble()); x1 = width.toDouble(); y1 = random.nextDouble(0.0, height.toDouble()) }
        }
        return MockSatelliteTrail(x0, y0, x1, y1,
            random.nextDouble(satelliteTrailBrightnessMin, satelliteTrailBrightnessMax),
            random.nextDouble(satelliteTrailWidthMin, satelliteTrailWidthMax))
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
        satelliteTrails: List<MockSatelliteTrail> = emptyList(),
        sensorArtifacts: SensorArtifacts = SensorArtifacts(),
    ): MatrixImage {
        val centerX = width / 2.0
        val centerY = height / 2.0

        fun channelMatrix(skyValue: Double, starBrightnessFactor: (MockStar) -> Double, channel: Channel): DoubleMatrix {
            return DoubleMatrix(height, width) { row, col ->
                val biasNoise = random.nextGaussian(noiseBiasLevel, noiseBiasSigma)
                val readNoise = random.nextGaussian(noiseReadLevel, noiseReadSigma)

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
                    totalStarSignal += starBrightnessFactor(star) * moffatPSF
                }

                val rawSignal = (totalStarSignal + skyValue) * vignette

                var totalTrailSignal = 0.0
                for (trail in satelliteTrails) {
                    val dx = trail.x1 - trail.x0
                    val dy = trail.y1 - trail.y0
                    val len = sqrt(dx * dx + dy * dy)
                    if (len > 1e-10) {
                        val dist = abs(dx * (trail.y0 - row) - (trail.x0 - col) * dy) / len
                        totalTrailSignal += trail.brightness * exp(-0.5 * (dist / trail.width).pow(2.0))
                    }
                }

                val photonNoise: Double = if (addSignal) random.nextGaussian(0.0, sqrt(rawSignal + 1e-10) * photonNoiseScale) else 0.0

                val biasValue: Double = if (addBiasNoise) biasNoise else 0.0
                val readValue: Double = if (addReadNoise) readNoise else 0.0
                var signalValue: Double = if (addSignal) rawSignal + photonNoise + totalTrailSignal else 0.0

                if (row to col in sensorArtifacts.hotPixels) {
                    signalValue = 1.0
                }
                if (row to col in sensorArtifacts.deadPixels) {
                    signalValue = 0.0
                }
                if (sensorArtifacts.badColumns.contains(col)) {
                    signalValue = 0.0
                }
                val stuckInThisChannel = sensorArtifacts.stuckPixels.any { it.first == col && it.second == row && it.third == channel }
                if (stuckInThisChannel) {
                    signalValue = 1.0
                }

                (biasValue + readValue + signalValue).coerceIn(0.0, 1.0)
            }
        }

        val redMatrix = channelMatrix(0.10, { star -> star.brightness * (0.3 + 0.7 * star.colorIndex) }, Channel.Red)
        val greenMatrix = channelMatrix(0.10, { star ->
            val r = 0.3 + 0.7 * star.colorIndex
            val b = 0.3 + 0.7 * (1.0 - star.colorIndex)
            star.brightness * (minOf(r, b) + 0.4 * abs(r - b))
        }, Channel.Green)
        val blueMatrix = channelMatrix(0.10, { star -> star.brightness * (0.3 + 0.7 * (1.0 - star.colorIndex)) }, Channel.Blue)

        return MatrixImage(
            width, height,
            Channel.Red to redMatrix,
            Channel.Green to greenMatrix,
            Channel.Blue to blueMatrix
        )
    }

    fun createRandomFlatImage(
        width: Int,
        height: Int,
    ): MatrixImage {
        val centerX = width / 2.0
        val centerY = height / 2.0

        fun channelMatrix(channelFlatLevel: Double): DoubleMatrix {
            return DoubleMatrix(height, width) { row, col ->
                val dx = ((col - centerX) / centerX).coerceIn(-1.0, 1.0)
                val dy = ((row - centerY) / centerY).coerceIn(-1.0, 1.0)
                val r = sqrt(dx * dx + dy * dy)
                val vignette = (1.0 - vignetteStrength * r.pow(2.0 / vignetteRadius)).coerceIn(0.0, 1.0)
                random.nextGaussian(channelFlatLevel, flatSigma) * vignette
            }
        }

        return MatrixImage(
            width, height,
            Channel.Red to channelMatrix(flatLevel * 0.90),
            Channel.Green to channelMatrix(flatLevel * 1.00),
            Channel.Blue to channelMatrix(flatLevel * 0.85)
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

    private fun Random.nextPoisson(lambda: Double): Int {
        return if (lambda < 30.0) {
            val l = exp(-lambda)
            var k = 0
            var p = 1.0
            do { k++; p *= nextDouble() } while (p > l)
            k - 1
        } else {
            nextGaussian(lambda, sqrt(lambda)).roundToInt().coerceAtLeast(0)
        }
    }
}