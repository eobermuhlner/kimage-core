package ch.obermuhlner.kimage.astro.align

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class AlignmentTest {

    @Test
    fun testFindStars() {
        val matrix = DoubleMatrix.matrixOf(5, 5) { _, _ -> 0.0 }
        matrix[2, 2] = 0.8 // Add a star
        val image = MatrixImage(5, 5, listOf(Channel.Gray), listOf(matrix))

        val stars = findStars(image, threshold = 0.5)

        assertEquals(1, stars.size)
        assertEquals(2, stars[0].x)
        assertEquals(2, stars[0].y)
    }

    @Test
    fun testCalculateTransformationMatrix() {
        val referenceStars = listOf(
            Star(0, 0, 1.0),
            Star(3, 0, 1.0),
            Star(0, 4, 1.0)
        )

        val otherStars = listOf(
            Star(0, 0, 1.0),
            Star(6, 0, 1.0),
            Star(0, 8, 1.0)
        )

        val matrix = calculateTransformationMatrix(referenceStars, otherStars)

        println(matrix.contentToString())
        val expectedTransformation = DoubleMatrix.matrixOf(
            3, 3,
            0.5, 0.0, 0.0,
            0.0, 0.5, 0.0,
            0.0, 0.0, 1.0
        )

        assertTrue(matrix.contentEquals(expectedTransformation))
    }

    @Test
    fun testComputeTriangleFeatures() {
        val stars = listOf(
            Star(0, 0, 1.0),
            Star(1, 0, 1.0),
            Star(0, 1, 1.0)
        )

        val features = computeTriangleFeatures(stars)
        assertEquals(1, features.size)

        val expectedAngles = listOf(
            lawOfCosinesAngle(1.0, sqrt(2.0), 1.0),
            lawOfCosinesAngle(1.0, 1.0, sqrt(2.0)),
            lawOfCosinesAngle(sqrt(2.0), 1.0, 1.0)
        ).sorted()

        assertEquals(expectedAngles, features[0].angles)
    }

    @Test
    fun testComputeTriangleAngles() {
        val starA = Star(0, 0, 1.0)
        val starB = Star(1, 0, 1.0)
        val starC = Star(0, 1, 1.0)

        val angles = computeTriangleAngles(starA, starB, starC)
        assertEquals(3, angles.size)

        val expectedAngles = listOf(
            lawOfCosinesAngle(1.0, sqrt(2.0), 1.0),
            lawOfCosinesAngle(1.0, 1.0, sqrt(2.0)),
            lawOfCosinesAngle(sqrt(2.0), 1.0, 1.0)
        )

        assertEquals(expectedAngles.sorted(), angles.sorted())
    }

    @Test
    fun testDistance() {
        val star1 = Star(0, 0, 1.0)
        val star2 = Star(3, 4, 1.0)

        assertEquals(5.0, distance(star1, star2), 1e-6)
    }

    @Test
    fun testLawOfCosinesAngle() {
        val angle = lawOfCosinesAngle(3.0, 4.0, 5.0) // Right triangle
        assertEquals(Math.PI / 2, angle, 1e-6)
    }

    @Test
    fun testAnglesAreSimilar() {
        val angles1 = listOf(1.0, 2.0, 3.0)
        val angles2 = listOf(1.0, 2.001, 3.0)

        assertTrue(anglesAreSimilar(angles1, angles2, tolerance = 0.01))
        assertFalse(anglesAreSimilar(angles1, angles2, tolerance = 0.0001))
    }

    @Test
    fun testComputeTransformationMatrix() {
        val tripletOther = listOf(Star(0, 0, 1.0), Star(1, 0, 1.0), Star(0, 1, 1.0))
        val tripletReference = listOf(Star(0, 0, 1.0), Star(2, 0, 1.0), Star(0, 2, 1.0))

        val transformation = computeTransformationMatrix(tripletOther, tripletReference)
        assertNotNull(transformation)

        val expectedTransformation = DoubleMatrix.matrixOf(
            3, 3,
            2.0, 0.0, 0.0,
            0.0, 2.0, 0.0,
            0.0, 0.0, 1.0
        )

        assertTrue(transformation!!.contentEquals(expectedTransformation))
    }

    @Test
    fun testTriangleArea() {
        val starA = Star(0, 0, 1.0)
        val starB = Star(1, 0, 1.0)
        val starC = Star(0, 1, 1.0)

        val area = triangleArea(starA, starB, starC)
        assertEquals(0.5, area, 1e-6)
    }

    @Test
    fun testApplyTransformation() {
        val stars = listOf(
            Star(0, 0, 1.0),
            Star(1, 0, 1.0),
            Star(0, 1, 1.0)
        )

        val matrix = DoubleMatrix.matrixOf(
            3, 3,
            2.0, 0.0, 0.0,
            0.0, 2.0, 0.0,
            0.0, 0.0, 1.0
        )

        val transformedStars = applyTransformation(stars, matrix)
        assertEquals(3, transformedStars.size)

        assertEquals(0, transformedStars[0].x)
        assertEquals(0, transformedStars[0].y)
        assertEquals(2, transformedStars[1].x)
        assertEquals(0, transformedStars[1].y)
        assertEquals(0, transformedStars[2].x)
        assertEquals(2, transformedStars[2].y)
    }

    @Test
    fun testCountInliers() {
        val transformedStars = listOf(Star(0, 0, 1.0), Star(3, 4, 1.0))
        val referenceStars = listOf(Star(0, 0, 1.0), Star(3, 4, 1.0))

        val inliers = countInliers(transformedStars, referenceStars, tolerance = 1.0)
        assertEquals(2, inliers)
    }

    @Test
    fun testApplyTransformationToImage() {
        val red = DoubleMatrix.matrixOf(3, 3) { _, _ -> 1.0 }
        val green = DoubleMatrix.matrixOf(3, 3) { _, _ -> 0.5 }
        val blue = DoubleMatrix.matrixOf(3, 3) { _, _ -> 0.25 }
        val image = MatrixImage(3, 3, listOf(Channel.Red, Channel.Green, Channel.Blue), listOf(red, green, blue))

        val transformationMatrix = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )

        val transformedImage = applyTransformationToImage(image, transformationMatrix)
        assertNotNull(transformedImage)
        assertEquals(image.width, transformedImage.width)
        assertEquals(image.height, transformedImage.height)
    }

    @Test
    fun testInterpolate() {
        val result = interpolate(1.0, 2.0, 3.0, 4.0, 0.5, 0.5)
        assertEquals(2.5, result, 1e-6)
    }
}
