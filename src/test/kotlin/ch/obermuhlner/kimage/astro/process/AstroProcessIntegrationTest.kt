package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.stack.DrizzleConfig
import ch.obermuhlner.kimage.core.image.stack.DrizzleCropConfig
import ch.obermuhlner.kimage.core.image.stack.DrizzleKernel
import ch.obermuhlner.kimage.core.image.stack.DrizzleRejection
import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
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
                drizzle = DrizzleConfig(scale = 2.0, pixfrac = 0.7, kernel = DrizzleKernel.Square)
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
    fun `processAstro runs with drizzle stacking and crop`() {
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
                    crop = DrizzleCropConfig(enabled = true, x = 10, y = 10, width = 30, height = 30)
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
}