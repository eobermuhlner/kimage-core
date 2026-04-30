package ch.obermuhlner.kimage.astro.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class NormalizeBackgroundConfigTest {

    @Test
    fun `NormalizeBackgroundConfig defaults`() {
        val config = NormalizeBackgroundConfig()
        assertEquals(true, config.enabled)
        assertEquals("astro-process/normalized", config.outputDirectory)
    }

    @Test
    fun `BackgroundNormalizeConfig defaults`() {
        val config = BackgroundNormalizeConfig()
        assertEquals(true, config.enabled)
        assertEquals(0.01, config.offset)
    }

    @Test
    fun `BackgroundNeutralizeConfig defaults`() {
        val config = BackgroundNeutralizeConfig()
        assertEquals(false, config.enabled)
        assertEquals(0.01, config.offset)
    }

    @Test
    fun `ProcessConfig has top-level normalizeBackground field`() {
        val config = ProcessConfig()
        assertEquals(true, config.normalizeBackground.enabled)
        assertEquals(true, config.normalizeBackground.normalize.enabled)
        assertEquals(false, config.normalizeBackground.neutralize.enabled)
    }

    @Test
    fun `NormalizeBackgroundConfig yaml deserialization with both sub-blocks`() {
        val yaml = """
normalizeBackground:
  enabled: true
  normalize:
    enabled: true
    offset: 0.005
  neutralize:
    enabled: true
    offset: 0.02
  outputDirectory: "astro-process/my-normalized"
        """.trimIndent()

        val config = Yaml().loadAs(yaml, ProcessConfig::class.java)

        assertEquals(true, config.normalizeBackground.enabled)
        assertEquals(true, config.normalizeBackground.normalize.enabled)
        assertEquals(0.005, config.normalizeBackground.normalize.offset)
        assertEquals(true, config.normalizeBackground.neutralize.enabled)
        assertEquals(0.02, config.normalizeBackground.neutralize.offset)
        assertEquals("astro-process/my-normalized", config.normalizeBackground.outputDirectory)
    }

    @Test
    fun `NormalizeBackgroundConfig yaml deserialization with defaults`() {
        val yaml = """
normalizeBackground:
  enabled: false
        """.trimIndent()

        val config = Yaml().loadAs(yaml, ProcessConfig::class.java)

        assertEquals(false, config.normalizeBackground.enabled)
        assertEquals(true, config.normalizeBackground.normalize.enabled)
        assertEquals(0.01, config.normalizeBackground.normalize.offset)
        assertEquals(false, config.normalizeBackground.neutralize.enabled)
    }
}
