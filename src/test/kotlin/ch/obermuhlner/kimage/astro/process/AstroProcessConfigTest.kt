package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.cosmetic.CosmeticCorrectionConfig
import ch.obermuhlner.kimage.astro.cosmetic.CosmeticCorrectionMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AstroProcessConfigTest {

    @Test
    fun `BranchConfig has correct defaults`() {
        val config = BranchConfig()
        assertEquals("", config.name)
        assertEquals(mutableListOf<EnhanceStepConfig>(), config.steps)
    }

    @Test
    fun `EnhanceConfig has branches and input fields`() {
        val config = EnhanceConfig()
        assertNull(config.branches)
        assertNull(config.input)
    }

    @Test
    fun `StackConfig has perFrame field defaulting to false`() {
        val config = StackConfig()
        assertFalse(config.perFrame)
    }

    @Test
    fun `FormatConfig has exposure filter fields`() {
        val config = FormatConfig()
        assertNull(config.minExposureSeconds)
        assertNull(config.maxExposureSeconds)
    }

    @Test
    fun `ProcessConfig has sources field`() {
        val config = ProcessConfig()
        assertNull(config.sources)
    }

    @Test
    fun `InfoTokens includes branchName and frameIndex`() {
        assertNotNull(InfoTokens.branchName)
        assertNotNull(InfoTokens.frameIndex)
    }

    @Test
    fun `EnhanceStepConfig mapping to EnhanceStepType`() {
        assertEquals(EnhanceStepType.Debayer, EnhanceStepConfig(debayer = DebayerConfig()).type)
        assertEquals(EnhanceStepType.Crop, EnhanceStepConfig(crop = RectangleConfig()).type)
        assertEquals(EnhanceStepType.Rotate, EnhanceStepConfig(rotate = RotateConfig()).type)
        assertEquals(EnhanceStepType.ReduceNoise, EnhanceStepConfig(reduceNoise = ReduceNoiseConfig()).type)
        assertEquals(EnhanceStepType.TGVDenoise, EnhanceStepConfig(tgvDenoise = TGVDenoiseConfig()).type)
        assertEquals(EnhanceStepType.Whitebalance, EnhanceStepConfig(whitebalance = WhitebalanceConfig()).type)
        assertEquals(EnhanceStepType.RemoveBackground, EnhanceStepConfig(removeBackground = RemoveBackgroundConfig()).type)
        assertEquals(EnhanceStepType.AutoStretch, EnhanceStepConfig(autoStretch = AutoStretchConfig()).type)
        assertEquals(EnhanceStepType.Sigmoid, EnhanceStepConfig(sigmoid = SigmoidConfig()).type)
        assertEquals(EnhanceStepType.ArcSinh, EnhanceStepConfig(arcsinh = AsinhConfig()).type)
        assertEquals(EnhanceStepType.GHS, EnhanceStepConfig(generalizedHyperbolicStretch = GHSConfig()).type)
        assertEquals(EnhanceStepType.LinearPercentile, EnhanceStepConfig(linearPercentile = LinearPercentileConfig()).type)
        assertEquals(EnhanceStepType.Blur, EnhanceStepConfig(blur = BlurConfig()).type)
        assertEquals(EnhanceStepType.Sharpen, EnhanceStepConfig(sharpen = SharpenConfig()).type)
        assertEquals(EnhanceStepType.UnsharpMask, EnhanceStepConfig(unsharpMask = UnsharpMaskConfig()).type)
        assertEquals(EnhanceStepType.HighDynamicRange, EnhanceStepConfig(highDynamicRange = HighDynamicRangeConfig()).type)
        assertEquals(EnhanceStepType.CosmeticCorrection, EnhanceStepConfig(cosmeticCorrection = CosmeticCorrectionConfig()).type)
        assertEquals(EnhanceStepType.Deconvolve, EnhanceStepConfig(deconvolve = DeconvolutionConfig()).type)
        assertEquals(EnhanceStepType.ExtractStars, EnhanceStepConfig(extractStars = ExtractStarsConfig()).type)
        assertEquals(EnhanceStepType.RemoveStars, EnhanceStepConfig(removeStars = RemoveStarsConfig()).type)
        assertEquals(EnhanceStepType.Decompose, EnhanceStepConfig(decompose = DecomposeConfig()).type)
        assertEquals(EnhanceStepType.CompositeChannels, EnhanceStepConfig(compositeChannels = CompositeChannelsConfig()).type)
        assertEquals(EnhanceStepType.MergeWith, EnhanceStepConfig(mergeWith = MergeWithConfig()).type)
        assertEquals(EnhanceStepType.StackSources, EnhanceStepConfig(stackSources = StackSourcesConfig()).type)
        assertEquals(EnhanceStepType.MaskedProcess, EnhanceStepConfig(maskedProcess = MaskedProcessConfig()).type)
        assertEquals(EnhanceStepType.Quantize, EnhanceStepConfig(quantize = QuantizeConfig()).type)
        assertEquals(EnhanceStepType.Edge, EnhanceStepConfig(edge = EdgeConfig()).type)
    }

    @Test
    fun `StarImageAlgorithm has expected values`() {
        assertEquals(3, StarImageAlgorithm.values().size)
        assertEquals(StarImageAlgorithm.Copy, StarImageAlgorithm.valueOf("Copy"))
        assertEquals(StarImageAlgorithm.Subtract, StarImageAlgorithm.valueOf("Subtract"))
        assertEquals(StarImageAlgorithm.SoftMaskedMultiply, StarImageAlgorithm.valueOf("SoftMaskedMultiply"))
    }

    @Test
    fun `MergeAlgorithm has expected values`() {
        assertEquals(2, MergeAlgorithm.values().size)
        assertEquals(MergeAlgorithm.LinearSoftBlend, MergeAlgorithm.valueOf("LinearSoftBlend"))
        assertEquals(MergeAlgorithm.AdditiveMerge, MergeAlgorithm.valueOf("AdditiveMerge"))
    }

    @Test
    fun `ExtractStarsConfig has correct defaults`() {
        val config = ExtractStarsConfig()
        assertEquals(2.0, config.factor)
        assertEquals(5, config.softMaskBlurRadius)
        assertEquals(InpaintAlgorithm.Erosion, config.inpaint)
        assertEquals(StarImageAlgorithm.Subtract, config.starImageAlgorithm)
        assertEquals(MergeAlgorithm.LinearSoftBlend, config.mergeAlgorithm)
    }

    @Test
    fun `TGVDenoiseConfig has correct defaults`() {
        val cfg = TGVDenoiseConfig()
        assertEquals(100.0, cfg.lambda)
        assertEquals(1.0, cfg.alpha0)
        assertEquals(2.0, cfg.alpha1)
        assertEquals(100, cfg.iterations)
    }

    @Test
    fun `HighDynamicRangeConfig has correct defaults`() {
        val cfg = HighDynamicRangeConfig()
        assertEquals(3, cfg.saturationBlurRadius)
        assertEquals(0.2, cfg.contrastWeight)
        assertEquals(0.1, cfg.saturationWeight)
        assertEquals(1.0, cfg.exposureWeight)
    }

    @Test
    fun `CosmeticCorrectionConfig has correct defaults`() {
        val cfg = CosmeticCorrectionConfig()
        assertTrue(cfg.enabled)
        assertEquals(CosmeticCorrectionMode.Both, cfg.mode)
        assertEquals(5.0, cfg.sigmaThreshold)
        assertEquals(2, cfg.checkRadius)
        assertEquals(1, cfg.fixRadius)
        assertEquals(0.01, cfg.minNetNoise)
    }

    @Test
    fun `HighlightProtectionConfig has correct defaults`() {
        val cfg = HighlightProtectionConfig()
        assertEquals(0.5, cfg.threshold)
    }

    @Test
    fun `EnhanceStepConfig highlightProtection defaults to null`() {
        assertNull(EnhanceStepConfig(sigmoid = SigmoidConfig()).highlightProtection)
    }

    @Test
    fun `QuantizeConfig defaults and validation`() {
        assertEquals(16, QuantizeConfig().levels)
        assertThrows<IllegalArgumentException> { QuantizeConfig(levels = 1) }
    }

    @Test
    fun `EdgeConfig defaults`() {
        assertEquals(EdgeAlgorithm.Sobel, EdgeConfig().algorithm)
        assertEquals(1.0, EdgeConfig().strength)
    }

    @Test
    fun `DeconvolutionConfig has correct defaults`() {
        val cfg = DeconvolutionConfig()
        assertEquals(DeconvolutionAlgorithm.RichardsonLucy, cfg.algorithm)
        assertEquals(1.5, cfg.psfSigma)
        assertEquals(20, cfg.iterations)
        assertEquals(0.01, cfg.noiseLevel)
    }

    @Test
    fun `RemoveBackgroundConfig has correct defaults`() {
        val cfg = RemoveBackgroundConfig()
        assertEquals(50, cfg.medianRadius)
        assertEquals(1.5, cfg.power)
        assertEquals(0.01, cfg.offset)
        assertEquals(FixPointType.FourCorners, cfg.fixPoints.type)
    }
}
