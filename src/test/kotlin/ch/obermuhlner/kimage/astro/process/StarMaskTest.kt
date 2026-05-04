package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp

class StarMaskTest {

    private fun createTestImage(width: Int = 40, height: Int = 40): MatrixImage {
        val matrix = DoubleMatrix.matrixOf(height, width) { _, _ -> 0.05 }
        // Gaussian-like star PSF at (20, 20)
        for (dy in -5..5) {
            for (dx in -5..5) {
                val v = exp(-(dx * dx + dy * dy) / 4.0) * 0.9
                val row = 20 + dy
                val col = 20 + dx
                if (row in 0 until height && col in 0 until width) {
                    matrix[row, col] = (matrix[row, col] + v).coerceAtMost(1.0)
                }
            }
        }
        // Smaller star at (5, 5)
        matrix[5, 5] = 0.7
        matrix[5, 6] = 0.35
        matrix[6, 5] = 0.35
        return MatrixImage(width, height,
            Channel.Red to matrix,
            Channel.Green to matrix,
            Channel.Blue to matrix,
        )
    }

    private fun baseCfg(algorithm: StarMaskAlgorithm) = ExtractStarsConfig(
        starMaskAlgorithm = algorithm,
        starThreshold = 0.3,
        softMaskBlurRadius = 0,
    )

    /** Mask value at image position (x, y): matrix is row-major [row=y, col=x]. */
    private fun maskAt(mask: ch.obermuhlner.kimage.core.matrix.Matrix, x: Int, y: Int) = mask[y, x]

    @Test
    fun `FwhmEllipse masks star center and not background`() {
        val image = createTestImage()
        val cfg = baseCfg(StarMaskAlgorithm.FwhmEllipse)
        val stars = findStars(image, cfg.starThreshold, cfg.channel)
        val mask = buildStarMask(image, { stars }, cfg)

        assertTrue(maskAt(mask, 20, 20) > 0.5, "FwhmEllipse: star center should be masked (got ${maskAt(mask, 20, 20)})")
        assertTrue(maskAt(mask, 0, 0) < 0.1, "FwhmEllipse: background should not be masked (got ${maskAt(mask, 0, 0)})")
    }

    @Test
    fun `WhiteTopHat masks star center and not background`() {
        val image = createTestImage()
        val cfg = baseCfg(StarMaskAlgorithm.WhiteTopHat).copy(diskRadius = 4, maskThreshold = 0.1)
        val stars = findStars(image, cfg.starThreshold, cfg.channel)
        val mask = buildStarMask(image, { stars }, cfg)

        assertTrue(maskAt(mask, 20, 20) > maskAt(mask, 0, 0),
            "WhiteTopHat: star center (${maskAt(mask, 20, 20)}) should exceed background corner (${maskAt(mask, 0, 0)})")
    }

    @Test
    fun `GaussianBlurDiff masks star center and not background`() {
        val image = createTestImage()
        val cfg = baseCfg(StarMaskAlgorithm.GaussianBlurDiff).copy(blurRadius = 8, maskThreshold = 0.1)
        val stars = findStars(image, cfg.starThreshold, cfg.channel)
        val mask = buildStarMask(image, { stars }, cfg)

        assertTrue(maskAt(mask, 20, 20) > maskAt(mask, 0, 0),
            "GaussianBlurDiff: star center (${maskAt(mask, 20, 20)}) should exceed background corner (${maskAt(mask, 0, 0)})")
    }

    @Test
    fun `DifferenceOfGaussians masks star center and not background`() {
        val image = createTestImage()
        val cfg = baseCfg(StarMaskAlgorithm.DifferenceOfGaussians).copy(dogRadius1 = 1, dogRadius2 = 6, maskThreshold = 0.05)
        val stars = findStars(image, cfg.starThreshold, cfg.channel)
        val mask = buildStarMask(image, { stars }, cfg)

        assertTrue(maskAt(mask, 20, 20) > maskAt(mask, 0, 0),
            "DifferenceOfGaussians: star center (${maskAt(mask, 20, 20)}) should exceed background corner (${maskAt(mask, 0, 0)})")
    }

    @Test
    fun `RegionGrowing masks star center and not background`() {
        val image = createTestImage()
        val cfg = baseCfg(StarMaskAlgorithm.RegionGrowing).copy(growthFactor = 0.3)
        val stars = findStars(image, cfg.starThreshold, cfg.channel)
        val mask = buildStarMask(image, { stars }, cfg)

        assertTrue(maskAt(mask, 20, 20) > maskAt(mask, 0, 0),
            "RegionGrowing: star center (${maskAt(mask, 20, 20)}) should exceed background corner (${maskAt(mask, 0, 0)})")
    }

    @Test
    fun `GaussianPsfFit masks star center and not background`() {
        val image = createTestImage()
        val cfg = baseCfg(StarMaskAlgorithm.GaussianPsfFit).copy(factor = 2.0)
        val stars = findStars(image, cfg.starThreshold, cfg.channel)
        val mask = buildStarMask(image, { stars }, cfg)

        assertTrue(maskAt(mask, 20, 20) > maskAt(mask, 0, 0),
            "GaussianPsfFit: star center (${maskAt(mask, 20, 20)}) should exceed background corner (${maskAt(mask, 0, 0)})")
    }

    @Test
    fun `AdaptiveLocalThreshold masks star center and not background`() {
        val image = createTestImage()
        val cfg = baseCfg(StarMaskAlgorithm.AdaptiveLocalThreshold).copy(windowRadius = 7, kappa = 1.3)
        val stars = findStars(image, cfg.starThreshold, cfg.channel)
        val mask = buildStarMask(image, { stars }, cfg)

        assertTrue(maskAt(mask, 20, 20) > maskAt(mask, 0, 0),
            "AdaptiveLocalThreshold: star center (${maskAt(mask, 20, 20)}) should exceed background corner (${maskAt(mask, 0, 0)})")
    }

    @Test
    fun `LuminancePercentile masks star center and not background`() {
        val image = createTestImage()
        val cfg = baseCfg(StarMaskAlgorithm.LuminancePercentile).copy(percentile = 0.90)
        val stars = findStars(image, cfg.starThreshold, cfg.channel)
        val mask = buildStarMask(image, { stars }, cfg)

        assertTrue(maskAt(mask, 20, 20) > maskAt(mask, 0, 0),
            "LuminancePercentile: star center (${maskAt(mask, 20, 20)}) should exceed background corner (${maskAt(mask, 0, 0)})")
    }
}
