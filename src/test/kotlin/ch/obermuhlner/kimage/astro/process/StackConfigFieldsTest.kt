package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
import ch.obermuhlner.kimage.core.image.stack.StackPrecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StackConfigFieldsTest {

    @Test
    fun `StackConfig has precision field with Float default`() {
        val config = StackConfig()
        assertEquals(StackPrecision.Float, config.precision)
    }

    @Test
    fun `StackConfig has kappa and iterations fields`() {
        val config = StackConfig(kappa = 3.0, iterations = 5)
        assertEquals(3.0, config.kappa)
        assertEquals(5, config.iterations)
    }

    @Test
    fun `StackConfig has tempDir field defaulting to null`() {
        val config = StackConfig()
        assertNull(config.tempDir)
    }

    @Test
    fun `StackConfig has maxDiskSpaceBytes field`() {
        val config = StackConfig(maxDiskSpaceBytes = 500_000_000L)
        assertEquals(500_000_000L, config.maxDiskSpaceBytes)
    }

    @Test
    fun `StackConfig with double precision can be constructed`() {
        val config = StackConfig(
            algorithm = StackAlgorithm.SigmaClipMedian,
            kappa = 2.5,
            iterations = 8,
            precision = StackPrecision.Double,
            tempDir = "/tmp/kimage",
            maxDiskSpaceBytes = 2_000_000_000L,
        )
        assertEquals(StackAlgorithm.SigmaClipMedian, config.algorithm)
        assertEquals(StackPrecision.Double, config.precision)
        assertEquals(2_000_000_000L, config.maxDiskSpaceBytes)
    }
}
