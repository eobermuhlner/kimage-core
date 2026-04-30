package ch.obermuhlner.kimage.astro.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

class CalibrateEnabledTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `CalibrateConfig enabled defaults to true`() {
        val config = CalibrateConfig()
        assertEquals(true, config.enabled)
    }

    @Test
    fun `CalibrateConfig enabled can be set to false`() {
        val config = CalibrateConfig().apply {
            enabled = false
        }
        assertEquals(false, config.enabled)
    }

    @Test
    fun `CalibrateConfig yaml deserialization supports enabled field`() {
        val yaml = """
enabled: false
inputImageExtension: fit
biasDirectory: "bias"
flatDirectory: "flat"
darkflatDirectory: "darkflat"
darkDirectory: "dark"
searchParentDirectories: true
darkskip: false
darkScalingFactor: 1.0
calibratedOutputDirectory: "astro-process/calibrated"
        """.trimIndent()

        val config = Yaml().loadAs(yaml, CalibrateConfig::class.java)

        assertEquals(false, config.enabled)
        assertEquals("bias", config.biasDirectory)
    }

    @Test
    fun `CalibrateConfig yaml deserialization defaults enabled to true`() {
        val yaml = """
inputImageExtension: fit
biasDirectory: "bias"
        """.trimIndent()

        val config = Yaml().loadAs(yaml, CalibrateConfig::class.java)

        assertEquals(true, config.enabled)
    }
}