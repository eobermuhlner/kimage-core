package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariantsIntegrationTest : AbstractAstroProcessIntegrationTest() {

    // --- Integration tests ---

    @Test
    fun `processAstro with common steps and branches applies common steps before each branch`() {
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
            ),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(sigmoid = SigmoidConfig(midpoint = 0.3, strength = 0.8))
                ),
                branches = mutableListOf(
                    BranchConfig(
                        name = "soft",
                        steps = mutableListOf(
                            EnhanceStepConfig(sigmoid = SigmoidConfig(midpoint = 0.5, strength = 0.5))
                        )
                    ),
                    BranchConfig(
                        name = "aggressive",
                        steps = mutableListOf(
                            EnhanceStepConfig(sigmoid = SigmoidConfig(midpoint = 0.2, strength = 2.0))
                        )
                    )
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_{${InfoTokens.branchName.name}}",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", 5)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertEquals(2, outputFiles.size, "Should produce one output file per branch")
        assertTrue(outputFiles.any { it.name.contains("soft") }, "Should have 'soft' branch output")
        assertTrue(outputFiles.any { it.name.contains("aggressive") }, "Should have 'aggressive' branch output")

        val commonCacheDir = testDir.resolve("astro-process/enhanced/common")
        assertTrue(commonCacheDir.exists(), "Common step cache directory should exist")
        assertTrue(commonCacheDir.listFiles()?.isNotEmpty() == true, "Common step cache should contain step files")
    }

    @Test
    fun `processAstro with branches produces output for each branch`() {
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
            ),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                branches = mutableListOf(
                    BranchConfig(
                        name = "soft",
                        steps = mutableListOf(
                            EnhanceStepConfig(sigmoid = SigmoidConfig(midpoint = 0.3, strength = 0.8))
                        )
                    ),
                    BranchConfig(
                        name = "aggressive",
                        steps = mutableListOf(
                            EnhanceStepConfig(sigmoid = SigmoidConfig(midpoint = 0.2, strength = 1.5))
                        )
                    )
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_{${InfoTokens.branchName.name}}",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", 5)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertEquals(2, outputFiles.size, "Should produce one output file per branch")
        assertTrue(outputFiles.any { it.name.contains("soft") }, "Should have 'soft' branch output")
        assertTrue(outputFiles.any { it.name.contains("aggressive") }, "Should have 'aggressive' branch output")
    }

    @Test
    fun `processAstro with perFrame produces outputs for individual frames`() {
        val imageCount = 5
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
            ),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(perFrame = true),
            enhance = EnhanceConfig(steps = mutableListOf()),
            output = OutputFormatConfig(
                outputName = "frame_{${InfoTokens.frameIndex.name}}",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", imageCount)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Should produce output files for aligned frames")
    }

    @Test
    fun `processAstro with decompose LRGB step completes successfully`() {
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
            ),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        decompose = DecomposeConfig(
                            mode = DecomposeMode.LRGB,
                            luminance = BranchConfig(
                                name = "luminance",
                                steps = mutableListOf(
                                    EnhanceStepConfig(sigmoid = SigmoidConfig(midpoint = 0.3, strength = 1.0))
                                )
                            )
                        )
                    )
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_decompose",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", 5)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Should produce output files")
    }

    @Test
    fun `processAstro with extractStars step completes successfully`() {
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
            ),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        extractStars = ExtractStarsConfig(
                            factor = 2.0,
                            softMaskBlurRadius = 3,
                            starsBranch = BranchConfig(name = "stars", steps = mutableListOf()),
                            backgroundBranch = BranchConfig(name = "background", steps = mutableListOf())
                        )
                    )
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_extractstars",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", 5)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Should produce output files")
    }

    @Test
    fun `processAstro with extractStars using SoftMaskedMultiply star algorithm`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 5)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(enabled = false),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        extractStars = ExtractStarsConfig(
                            factor = 2.0,
                            softMaskBlurRadius = 3,
                            inpaint = InpaintAlgorithm.None,
                            starImageAlgorithm = StarImageAlgorithm.SoftMaskedMultiply,
                            starsBranch = BranchConfig(name = "stars", steps = mutableListOf()),
                            backgroundBranch = BranchConfig(name = "background", steps = mutableListOf())
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
    fun `processAstro with extractStars using AdditiveMerge`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 5)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(enabled = false),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        extractStars = ExtractStarsConfig(
                            factor = 2.0,
                            softMaskBlurRadius = 3,
                            inpaint = InpaintAlgorithm.None,
                            mergeAlgorithm = MergeAlgorithm.AdditiveMerge,
                            starsBranch = BranchConfig(name = "stars", steps = mutableListOf()),
                            backgroundBranch = BranchConfig(name = "background", steps = mutableListOf())
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
    fun `processAstro with extractStars using Copy star algorithm`() {
        initTestRun()
        createRandomAstroImages(testDir, "light", 5)

        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(enabled = false),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        extractStars = ExtractStarsConfig(
                            factor = 2.0,
                            softMaskBlurRadius = 3,
                            inpaint = InpaintAlgorithm.None,
                            starImageAlgorithm = StarImageAlgorithm.Copy,
                            starsBranch = BranchConfig(name = "stars", steps = mutableListOf()),
                            backgroundBranch = BranchConfig(name = "background", steps = mutableListOf())
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
    fun `processAstro with maskedProcess step completes successfully`() {
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
            ),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        maskedProcess = MaskedProcessConfig(
                            mask = MaskConfig(source = MaskSource.Luminance, threshold = 0.3, blur = 3),
                            insideMask = BranchConfig(name = "inside", steps = mutableListOf()),
                            outsideMask = BranchConfig(name = "outside", steps = mutableListOf())
                        )
                    )
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_maskedprocess",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", 5)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Should produce output files")
    }

    @Test
    fun `processAstro with highlightProtection on sigmoid completes successfully`() {
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(enabled = false),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(
                        sigmoid = SigmoidConfig(midpoint = 0.3),
                        highlightProtection = HighlightProtectionConfig(threshold = 0.5)
                    )
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_highlight_protection",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", 5)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Should produce output files")
    }

    @Test
    fun `processAstro with quantize step completes successfully`() {
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
            ),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(quantize = QuantizeConfig(levels = 64))
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_quantize",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", 5)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Should produce output files")
    }

    @Test
    fun `processAstro with edge step completes successfully`() {
        val config = ProcessConfig(
            format = FormatConfig(
                inputImageExtension = "png",
                outputImageExtension = "png",
                debayer = DebayerConfig(enabled = false)
            ),
            calibrate = CalibrateConfig(
                enabled = false,
            ),
            normalizeBackground = NormalizeBackgroundConfig(enabled = false),
            align = AlignConfig(),
            stack = StackConfig(algorithm = StackAlgorithm.Median),
            enhance = EnhanceConfig(
                steps = mutableListOf(
                    EnhanceStepConfig(edge = EdgeConfig(algorithm = EdgeAlgorithm.Sobel))
                )
            ),
            output = OutputFormatConfig(
                outputName = "test_edge",
                outputImageExtensions = mutableListOf("png"),
            )
        )

        val testDir = prepareTestRunDirectory()
        createRandomAstroImages(testDir, "light", 5)

        val process = AstroProcess(config)
        process.workingDirectory = testDir
        process.processAstro()

        val outputDir = testDir.resolve("astro-process/output")
        assertTrue(outputDir.exists(), "Output directory should exist")
        val outputFiles = outputDir.listFiles()?.sorted() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Should produce output files")
    }
}
