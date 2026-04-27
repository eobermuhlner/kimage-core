package ch.obermuhlner.kimage.core.image.awt

import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AwtGraphicsCrossPlatformTest : AbstractImageProcessingTest() {

    @Test
    fun `crossPlatformFontMetrics returns positive height and descent`() {
        val font = AnnotateZoom.loadBundledFont(20f)
        val metrics = CrossPlatformFontMetrics(font)
        assertTrue(metrics.height > 0, "height should be positive")
        assertTrue(metrics.descent > 0, "descent should be positive")
        assertTrue(metrics.height > metrics.descent, "height should exceed descent")
        assertTrue(metrics.stringWidth("Hello") > 0, "stringWidth should be positive")
    }

    @Test
    fun `drawStringCP renders text platform-independently`() {
        val image = MatrixImage(300, 60, Channel.Red, Channel.Green, Channel.Blue)
        val result = graphics(image, 0, 0, 0, 0) { g, _, _, ox, oy ->
            g.color = java.awt.Color.WHITE
            g.font = AnnotateZoom.loadBundledFont(24f)
            g.drawStringCP("Hello World", ox + 5, oy + 45)
        }
        assertReferenceImage("drawStringCP_hello_world", result)
    }
}
