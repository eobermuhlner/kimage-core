package ch.obermuhlner.kimage.astro.annotate

import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.Marker
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.MarkerLabelStyle
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.MarkerStyle
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.awt.graphics
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import javax.swing.Spring.height
import org.junit.jupiter.api.Test

class AnnotateZoomTest : AbstractImageProcessingTest() {
    @Test
    fun `should annotate empty using defaults`() {
        val image = createAnnotationTestImage()
        val annotateZoom = AnnotateZoom()
        assertReferenceImage("annotate", annotateZoom.annotate(image))
    }

    @Test
    fun `should annotate marker using defaults`() {
        val image = createAnnotationTestImage()
        val annotateZoom = AnnotateZoom()
        annotateZoom.title = "Title"
        annotateZoom.subtitle = "Subtitle"
        annotateZoom.addMarker(Marker(
            "marker1",
            50,
            50,
            30,
            "info1",
            "info2"
        ))
        assertReferenceImage("annotate", annotateZoom.annotate(image))
    }

    @Test
    fun `should annotate multiple markers`() {
        val image = createAnnotationTestImage()
        val annotateZoom = AnnotateZoom()
        annotateZoom.title = "Title"
        annotateZoom.subtitle = "Subtitle"
        annotateZoom.addMarker(Marker(
            "marker1",
            50,
            50,
            30,
            "info1",
            "info2"
        ))
        annotateZoom.addMarker(Marker(
            "marker2",
            50,
            100,
            10,
            "info1",
            ""
        ))
        annotateZoom.addMarker(Marker(
            "marker3",
            200,
            100,
            50,
            "info1",
            ""
        ))
        assertReferenceImage("annotate", annotateZoom.annotate(image))
    }

    @Test
    fun `should annotate marker using all color themes`() {
        val image = createAnnotationTestImage()
        for (colorTheme in ColorTheme.entries) {
            val annotateZoom = AnnotateZoom()
            annotateZoom.title = "Title"
            annotateZoom.subtitle = "Subtitle"
            annotateZoom.text = """
                Text line 1
                Text line 2
            """.trimIndent()
            annotateZoom.setColorTheme(colorTheme)
            annotateZoom.addMarker(Marker(
                "marker1",
                50,
                50,
                30,
                "info1",
                "info2"
            ))
            assertReferenceImage("annotate_${colorTheme}", annotateZoom.annotate(image))
        }
    }

    @Test
    fun `should annotate marker in all styles`() {
        val image = createAnnotationTestImage()

        for (markerStyle in MarkerStyle.entries) {
            for (markerLabelStyle in MarkerLabelStyle.entries) {
                val annotateZoom = AnnotateZoom()
                annotateZoom.title = "Title"
                annotateZoom.subtitle = "Subtitle"
                annotateZoom.markerStyle = markerStyle
                annotateZoom.markerLabelStyle = markerLabelStyle
                annotateZoom.addMarker(Marker(
                    "marker1",
                    50,
                    50,
                    40,
                    "info1",
                    "info2",
                    majAx = 30,
                    minAx = 10,
                    posAngle = 45.0
                ))
                assertReferenceImage("annotate_${markerStyle}_${markerLabelStyle}", annotateZoom.annotate(image))
            }
        }
    }

    private fun createAnnotationTestImage(): Image {
        val width = 600
        val height = 300
        val image = MatrixImage(width, height, Channel.Red, Channel.Green, Channel.Blue)

        return graphics(image, 0, 0, 0, 0) { graphics, width, height, offsetX, offsetY ->
            graphics.color = java.awt.Color.MAGENTA
            graphics.drawString("a", offsetY + 50, offsetY + 50 + graphics.fontMetrics.descent)
            graphics.color = java.awt.Color.YELLOW
            graphics.drawString("b", offsetY + 50, offsetY + 100 + graphics.fontMetrics.descent)
            graphics.color = java.awt.Color.ORANGE
            graphics.drawString("c", offsetY + 200, offsetY + 100 + graphics.fontMetrics.descent)
        }
    }
}
