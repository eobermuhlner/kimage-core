package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
import org.junit.jupiter.api.Test

class AstroProcessIntegrationTest : AbstractAstroProcessIntegrationTest() {

    @Test
    fun `processAstro runs with minimal config and light frames`() {
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
}