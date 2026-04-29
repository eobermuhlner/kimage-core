package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.bayer.BayerPattern
import ch.obermuhlner.kimage.core.image.bayer.DebayerInterpolation
import ch.obermuhlner.kimage.core.image.stack.DrizzleConfig
import ch.obermuhlner.kimage.core.image.stack.DrizzleCropConfig
import ch.obermuhlner.kimage.core.image.stack.DrizzleKernel
import ch.obermuhlner.kimage.core.image.stack.DrizzleRejection
import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AstroProcessIntegrationTest : AbstractAstroProcessIntegrationTest() {

    @Test
    fun `processAstro runs with minimal config and light frames`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 10, addBiasNoise = false)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Median
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro debayers light frames using VNG interpolation`() {
        initTestRun()
        createRandomBayerImages(testDir, "light", 5, BayerPattern.RGGB)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(
                    enabled = true,
                    cleanupBadPixels = false,
                    bayerPattern = BayerPattern.RGGB,
                    interpolation = DebayerInterpolation.VNG,
                )
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(steps = mutableListOf()),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro debayers light frames using PPG interpolation`() {
        initTestRun()
        createRandomBayerImages(testDir, "light", 5, BayerPattern.RGGB)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(
                    enabled = true,
                    cleanupBadPixels = false,
                    bayerPattern = BayerPattern.RGGB,
                    interpolation = DebayerInterpolation.PPG,
                )
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(steps = mutableListOf()),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `createRandomAstroImage star color reflects colorIndex`() {
        initTestRun()
        val savedPhotonNoiseScale = photonNoiseScale
        photonNoiseScale = 0.0

        // neutral star (colorIndex=0.5): green must not dominate
        val neutralImage = createRandomAstroImage(width, height,
            listOf(MockStar(width / 2, height / 2, 1.0, 0.5)),
            addBiasNoise = false, addReadNoise = false)
        val nR = neutralImage.getPixel(width / 2, height / 2, Channel.Red)
        val nG = neutralImage.getPixel(width / 2, height / 2, Channel.Green)
        val nB = neutralImage.getPixel(width / 2, height / 2, Channel.Blue)
        assertTrue(nG <= maxOf(nR, nB) + 0.01,
            "Neutral star (colorIndex=0.5): green should not dominate, got R=$nR G=$nG B=$nB")

        // blue star (colorIndex=0.0): blue must be the brightest channel
        val blueImage = createRandomAstroImage(width, height,
            listOf(MockStar(width / 2, height / 2, 1.0, 0.0)),
            addBiasNoise = false, addReadNoise = false)
        val bR = blueImage.getPixel(width / 2, height / 2, Channel.Red)
        val bG = blueImage.getPixel(width / 2, height / 2, Channel.Green)
        val bB = blueImage.getPixel(width / 2, height / 2, Channel.Blue)
        assertTrue(bB > bG && bB > bR,
            "Blue star (colorIndex=0.0): blue should dominate, got R=$bR G=$bG B=$bB")

        // red star (colorIndex=1.0): red must be the brightest channel
        val redImage = createRandomAstroImage(width, height,
            listOf(MockStar(width / 2, height / 2, 1.0, 1.0)),
            addBiasNoise = false, addReadNoise = false)
        val rR = redImage.getPixel(width / 2, height / 2, Channel.Red)
        val rG = redImage.getPixel(width / 2, height / 2, Channel.Green)
        val rB = redImage.getPixel(width / 2, height / 2, Channel.Blue)
        assertTrue(rR > rG && rR > rB,
            "Red star (colorIndex=1.0): red should dominate, got R=$rR G=$rG B=$rB")

        photonNoiseScale = savedPhotonNoiseScale
    }

    @Test
    fun `createRandomAstroImage generates distinct RGB channels`() {
        initTestRun()
        val image = createRandomAstroImage(width, height, starPositions)

        var redGreenDiff = 0.0
        var redBlueDiff = 0.0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                redGreenDiff += abs(image.getPixel(x, y, Channel.Red) - image.getPixel(x, y, Channel.Green))
                redBlueDiff += abs(image.getPixel(x, y, Channel.Red) - image.getPixel(x, y, Channel.Blue))
            }
        }
        assertTrue(redGreenDiff > 0.0, "Red and Green channels should be different (not grayscale)")
        assertTrue(redBlueDiff > 0.0, "Red and Blue channels should be different (not grayscale)")
    }

    @Test
    fun `processAstro runs with light frames and calibration frames`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 10)
        createRandomAstroImages(testDir.resolve("dark"), "dark", 10, addSignal = false)
        createRandomAstroImages(testDir.resolve("bias"), "bias", 10, addReadNoise = false, addSignal = false)
        createRandomFlatImages(testDir.resolve("flat"), "flat", 10)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = true,
                searchParentDirectories = false,
                debayer = DebayerConfig(enabled = false),
                normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Median
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with drizzle stacking`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 5, addBiasNoise = false)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Drizzle,
                drizzle = DrizzleConfig(
                    scale = 2.0,
                    pixfrac = 0.7,
                    kernel = DrizzleKernel.Square,
                )
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with bayered light frames`() {
        initTestRun()
        //starWidth = 0.8
        sensorHotPixelCount = 2
        sensorDeadPixelCount = 1
        sensorStuckPixelCount = 2
        sensorBadColumnCount = 1
        createRandomAstroImages(testDir, "light", 10, jitter = 3.0, bayerPattern = BayerPattern.RGGB)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = true, bayerPattern = BayerPattern.RGGB, cleanupBadPixels = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Median
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with bayered light frames debayering with Bilinear`() {
        initTestRun()
        //starWidth = 0.8
        sensorHotPixelCount = 2
        sensorDeadPixelCount = 1
        sensorStuckPixelCount = 2
        sensorBadColumnCount = 1
        createRandomAstroImages(testDir, "light", 10, jitter = 3.0, bayerPattern = BayerPattern.RGGB)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(
                    enabled = true,
                    bayerPattern = BayerPattern.RGGB,
                    cleanupBadPixels = false,
                    interpolation = DebayerInterpolation.Bilinear
                )
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Median
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with bayered light frames and calibration frames`() {
        initTestRun()
        //starWidth = 0.8
        sensorHotPixelCount = 2
        sensorDeadPixelCount = 1
        sensorStuckPixelCount = 2
        sensorBadColumnCount = 1
        createRandomAstroImages(testDir, "light", 10, jitter = 3.0, bayerPattern = BayerPattern.RGGB)
        createRandomAstroImages(testDir.resolve("dark"), "dark", 10, addSignal = false)
        createRandomAstroImages(testDir.resolve("bias"), "bias", 10, addReadNoise = false, addSignal = false)
        createRandomFlatImages(testDir.resolve("flat"), "flat", 10)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = true, bayerPattern = BayerPattern.RGGB, cleanupBadPixels = false)
            ),
            calibrate = CalibrateConfig(
                enabled = true,
                searchParentDirectories = false,
                debayer = DebayerConfig(enabled = false),
                normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Median
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with drizzle stacking and sigma clip rejection`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 5, addBiasNoise = false)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Drizzle,
                drizzle = DrizzleConfig(
                    scale = 2.0,
                    pixfrac = 0.7,
                    kernel = DrizzleKernel.Square,
                    rejection = DrizzleRejection.SigmaClip,
                    kappa = 2.0,
                    iterations = 3,
                )
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with drizzle stacking and sigma clip rejection no disk`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 5, addBiasNoise = false)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Drizzle,
                maxDiskSpaceBytes = "0",  // force row-by-row tiling
                drizzle = DrizzleConfig(
                    scale = 2.0,
                    pixfrac = 0.7,
                    kernel = DrizzleKernel.Square,
                    rejection = DrizzleRejection.SigmaClip,
                    kappa = 2.0,
                    iterations = 3,
                )
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with drizzle stacking and crop and sigma clip rejection`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 5, addBiasNoise = false)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Drizzle,
                drizzle = DrizzleConfig(
                    scale = 2.0,
                    pixfrac = 0.7,
                    kernel = DrizzleKernel.Square,
                    rejection = DrizzleRejection.SigmaClip,
                    kappa = 2.0,
                    iterations = 3,
                    crop = DrizzleCropConfig(
                        enabled = true,
                        x = 10,
                        y = 10,
                        width = 30,
                        height = 30,
                    )
                )
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf()
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with enhance`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 10, addBiasNoise = false)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Median
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        deconvolve = DeconvolutionConfig(
                            iterations = 50
                        )
                    )
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

    @Test
    fun `processAstro runs with enhance using wiener deconvolution`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 10, addBiasNoise = false)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
                normalizeBackground = NormalizeBackgroundConfig(enabled = false)
            ),
            align = AlignConfig(),
            stack = StackConfig(
                algorithm = StackAlgorithm.Median
            ),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        deconvolve = DeconvolutionConfig(
                            algorithm = DeconvolutionAlgorithm.Wiener,
                            iterations = 10,
                            noiseLevel = 0.005
                        )
                    )
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_output",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        assertAstroProcess(config)
    }

}