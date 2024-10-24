package ch.obermuhlner.kimage.image

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.matrix.values.asXY
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import kotlin.math.max

abstract class AbstractImageProcessingTest {

    val testInputDirectory = File("test-input")

    private fun testResultsDirectory(): File {
        val className = this::class.qualifiedName
        val methodName = Thread.currentThread().stackTrace.firstOrNull {
            it.className == className
        } ?.methodName ?: "unknown"

        val directory = File("test-results/$className/$methodName")
        return directory
    }

    fun assertReferenceImage(name: String, image: Image, epsilon: Double = 1e-10) {
        assertReferenceImage(name, image, "png", max(epsilon, 1e-4))
        assertReferenceImage(name, image, "dimg", epsilon)
    }

    fun assertReferenceImage(name: String, image: Image, extension: String, epsilon: Double): Image {
        val directory = testResultsDirectory()
        directory.mkdirs()
        val referenceImageFile = File(directory, "$name.$extension")
        return if (!referenceImageFile.exists()) {
            println("Writing reference image: $referenceImageFile")
            ImageWriter.write(image, referenceImageFile)
            image
        } else {
            println("Reading reference image: $referenceImageFile")
            val referenceImage = ImageReader.read(referenceImageFile)
            assertImage(referenceImage, image, name, epsilon)
            referenceImage
        }
    }

    fun assertImage(expected: Image, actual: Image, name: String = "", epsilon: Double = 1e-10) {
        assertEquals(expected.width, actual.width, "$name.width")
        assertEquals(expected.height, actual.height, "$name.height")
        assertEquals(expected.channels, actual.channels, "$name.channels")

        for (channel in expected.channels) {
            val expectedMatrixXY = expected[channel].asXY()
            val actualMatrixXY = actual[channel].asXY()
            assertEquals(expectedMatrixXY.width, actualMatrixXY.width, "$name[$channel].width")
            assertEquals(expectedMatrixXY.height, actualMatrixXY.height, "$name[$channel].height")
            for (y in 0 until expectedMatrixXY.height) {
                for (x in 0 until expectedMatrixXY.width) {
                    assertEquals(expectedMatrixXY[x,y], actualMatrixXY[x,y], epsilon, "$name[$channel][$x,$y]")
                }
            }
        }
    }

    fun readTestImage(name: String = "flowers_small.png"): Image {
        return ImageReader.read(File(testInputDirectory, name))
    }
}