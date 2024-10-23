package ch.obermuhlner.kimage.image.stack

import ch.obermuhlner.kimage.core.huge.SimpleMultiDimensionalFloatArray
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.onEach
import ch.obermuhlner.kimage.core.image.stack.StackAlgorithm
import ch.obermuhlner.kimage.core.image.stack.stack
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.image.AbstractImageProcessingTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ImageStackTest: AbstractImageProcessingTest() {

    @Test
    fun `should stack images`() {
        val baseImage = readTestImage()
//        val baseImage = MatrixImage(1, 2, listOf(Channel.Red, Channel.Green, Channel.Blue)) { _, rows, cols ->
//            DoubleMatrix.matrixOf(rows, cols) { _-> 0.5 }
//        }
        assertReferenceImage("base", baseImage)

        val error = 0.5
        val random = Random(1234)
        val images = mutableListOf<Image>()
        for (i in 0 until 100) {
            val randomImage = baseImage.onEach { v -> clamp(v + random.nextDouble(-error, error), 0.0, 1.0) }
            if (i < 3) {
                assertReferenceImage("image$i", randomImage)
            }
            images.add(randomImage)
        }
        val imageSuppliers = images.map { img -> { img }}

        for (stackAlgorithm in StackAlgorithm.entries) {
            val stacked = stack(imageSuppliers, stackAlgorithm)
            assertReferenceImage("stacked_$stackAlgorithm", stacked)
        }
    }
}