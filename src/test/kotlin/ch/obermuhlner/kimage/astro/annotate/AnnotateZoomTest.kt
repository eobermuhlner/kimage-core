package ch.obermuhlner.kimage.astro.annotate

import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.Marker
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.MarkerLabelStyle
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.MarkerStyle
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test

class AnnotateZoomTest : AbstractImageProcessingTest() {
    @Test
    fun `should annotate empty using defaults`() {
        val image = readTestImage("flowers.bmp")
        val annotateZoom = AnnotateZoom()
        assertReferenceImage("annotate", annotateZoom.annotate(image))
    }

    @Test
    fun `should annotate marker using defaults`() {
        val image = readTestImage("flowers.bmp")
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
        val image = readTestImage("flowers.bmp")
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
            100,
            100,
            10,
            "info1",
            ""
        ))
        annotateZoom.addMarker(Marker(
            "marker3",
            200,
            200,
            50,
            "info1",
            ""
        ))
        assertReferenceImage("annotate", annotateZoom.annotate(image))
    }

    @Test
    fun `should annotate marker using all color themes`() {
        val image = readTestImage("flowers.bmp")
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
        val image = readTestImage("flowers.bmp")

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
}
