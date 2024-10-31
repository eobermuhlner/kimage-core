package ch.obermuhlner.kimage.astro.align

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.crop.crop
import ch.obermuhlner.kimage.core.image.io.ImageWriter
import ch.obermuhlner.kimage.core.image.minus
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.assertMatrixEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Ignore


@Ignore
class AlignStarsTest {

    @Test
    fun testFindStars() {
        val matrix = DoubleMatrix.matrixOf(5, 5)
        matrix[2, 2] = 0.8 // Add a star
        val image = MatrixImage(5, 5, listOf(Channel.Gray), listOf(matrix))

        val stars = findStars(image, threshold = 0.5)

        assertEquals(1, stars.size)
        assertEquals(2, stars[0].x)
        assertEquals(2, stars[0].y)
    }

    @Test
    fun testCalculateTransformationMatrixScaling() {
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

        val matrix = calculateTransformationMatrix(referenceStars, otherStars, 2, 2)!!

        val expectedTransformation = DoubleMatrix.matrixOf(
            3, 3,
            0.5, 0.0, 0.0,
            0.0, 0.5, 0.0,
            0.0, 0.0, 1.0
        )

        assertTrue(matrix.contentEquals(expectedTransformation))
    }

    @Test
    fun testCalculateTransformationMatrixTranslation() {
        val referenceStars = listOf(
            Star(0, 0, 1.0),
            Star(3, 0, 1.0),
            Star(0, 4, 1.0)
        )

        val otherStars = listOf(
            Star(10, 10, 1.0),
            Star(13, 10, 1.0),
            Star(10, 14, 1.0)
        )

        val transformation = calculateTransformationMatrix(referenceStars, otherStars, 100, 100)!!

        val expectedTransformation = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, -10.0,
            0.0, 1.0, -10.0,
            0.0, 0.0, 1.0
        )

        assertMatrixEquals(expectedTransformation, transformation)
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
    fun testComputeTransformationMatrixTranslationX() {
        val tripletOther = listOf(
            Star(0, 0, 1.0),
            Star(1, 0, 1.0),
            Star(0, 2, 1.0)
        )
        val tripletReference = listOf(
            Star(10, 0, 1.0),
            Star(11, 0, 1.0),
            Star(10, 2, 1.0)
        )

        val transformation = computeTransformationMatrix(tripletOther, tripletReference, 100, 100)
        assertNotNull(transformation)

        val expectedTransformation = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, 10.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )

        assertMatrixEquals(expectedTransformation, transformation!!)
    }

    @Test
    fun testComputeTransformationMatrixTranslationY() {
        val tripletOther = listOf(
            Star(0, 0, 1.0),
            Star(1, 0, 1.0),
            Star(0, 2, 1.0)
        )
        val tripletReference = listOf(
            Star(0, 10, 1.0),
            Star(1, 10, 1.0),
            Star(0, 12, 1.0)
        )

        val transformation = computeTransformationMatrix(tripletOther, tripletReference, 0, 0)
        assertNotNull(transformation)

        val expectedTransformation = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, 0.0,
            0.0, 1.0, 10.0,
            0.0, 0.0, 1.0
        )

        assertMatrixEquals(expectedTransformation, transformation!!)
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
    fun testApplyTransformationToStarsTranslation() {
        val stars = listOf(
            Star(0, 0, 1.0),
            Star(1, 0, 1.0),
            Star(0, 1, 1.0)
        )

        // Translation matrix (moves all stars 1 unit right and 20 units down)
        val transform = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, 1.0,
            0.0, 1.0, 20.0,
            0.0, 0.0, 1.0
        )

        val transformedStars = applyTransformationToStars(stars, transform, 2, 2)
        val expectedTransformedStars = listOf(
            Star(1, 20, 1.0),
            Star(2, 20, 1.0),
            Star(1, 21, 1.0)
        )
        assertEquals(expectedTransformedStars, transformedStars)
    }

    @Test
    fun testApplyTransformationToStarsRotation() {
        val stars = listOf(
            Star(0, 0, 1.0),
            Star(1, 1, 1.0),
            Star(0, 1, 1.0)
        )

        // Rotation matrix for 90 degrees counterclockwise
        val transform = DoubleMatrix.matrixOf(
            3, 3,
            0.0, -1.0, 0.0,
            1.0,  0.0, 0.0,
            0.0,  0.0, 1.0
        )

        val transformedStars = applyTransformationToStars(stars, transform, 2, 2)
        val expectedTransformedStars = listOf(
            Star(0, 1, 1.0),
            Star(1, 1, 1.0),
            Star(-1, 0, 1.0)
        )
        assertEquals(expectedTransformedStars, transformedStars)
    }

    @Test
    fun testApplyTransformationScaling() {
        val stars = listOf(
            Star(0, 0, 1.0),
            Star(1, 0, 1.0),
            Star(0, 1, 1.0)
        )

        val transform = DoubleMatrix.matrixOf(
            3, 3,
            2.0, 0.0, 0.0,
            0.0, 2.0, 0.0,
            0.0, 0.0, 1.0
        )

        val transformedStars = applyTransformationToStars(stars, transform, 2, 2)
        val expectedTransformedStars = listOf(
            Star(0, 0, 1.0),
            Star(2, 0, 1.0),
            Star(0, 2, 1.0)
        )
        assertEquals(expectedTransformedStars, transformedStars)
    }

    @Test
    fun testCountInliers() {
        val transformedStars = listOf(Star(0, 0, 1.0), Star(3, 4, 1.0))
        val referenceStars = listOf(Star(0, 0, 1.0), Star(3, 4, 1.0))

        val inliers = countInliers(transformedStars, referenceStars, tolerance = 1.0)
        assertEquals(2, inliers)
    }

    @Test
    fun testApplyTransformationToImageTranslationX() {
        val red = DoubleMatrix.matrixOf(
            3, 3,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 0.0
        )
        val green = DoubleMatrix.matrixOf(3, 3)
        val blue = DoubleMatrix.matrixOf(3, 3)
        val image = MatrixImage(3, 3, listOf(Channel.Red, Channel.Green, Channel.Blue), listOf(red, green, blue))

        // Translation matrix (moves pixel to the right by 1 unit)
        val transformationMatrix = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, 1.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )

        decomposeTransformationMatrix(transformationMatrix).let {
            assertEquals(1.0, it.translationX)
            assertEquals(0.0, it.translationY)
            assertEquals(1.0, it.scaleX)
            assertEquals(1.0, it.scaleY)
            assertEquals(0.0, it.rotation)
            assertEquals(0.0, it.shear)
        }

        val transformedImage = applyTransformationToImage(image, emptyList(), transformationMatrix)
        assertNotNull(transformedImage)

        val expected = DoubleMatrix.matrixOf(
            3, 3,
            0.0, 0.0, 0.0,
            0.0, 0.0, 1.0,
            0.0, 0.0, 0.0
        )

        assertMatrixEquals(expected, transformedImage[Channel.Red])
    }

    @Test
    fun testApplyTransformationToImageTranslationY() {
        val red = DoubleMatrix.matrixOf(
            3, 3,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 0.0
        )
        val green = DoubleMatrix.matrixOf(3, 3)
        val blue = DoubleMatrix.matrixOf(3, 3)
        val image = MatrixImage(3, 3, listOf(Channel.Red, Channel.Green, Channel.Blue), listOf(red, green, blue))

        // Translation matrix (moves pixel down by 1 unit)
        val transformationMatrix = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, 0.0,
            0.0, 1.0, 1.0,
            0.0, 0.0, 1.0
        )

        decomposeTransformationMatrix(transformationMatrix).let {
            assertEquals(0.0, it.translationX)
            assertEquals(1.0, it.translationY)
            assertEquals(1.0, it.scaleX)
            assertEquals(1.0, it.scaleY)
            assertEquals(0.0, it.rotation)
            assertEquals(0.0, it.shear)
        }

        val transformedImage = applyTransformationToImage(image, emptyList(), transformationMatrix)
        assertNotNull(transformedImage)

        val expected = DoubleMatrix.matrixOf(
            3, 3,
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0
        )

        assertMatrixEquals(expected, transformedImage[Channel.Red])
    }

    @Test
    fun testApplyTransformationToImageScaling() {
        val red = DoubleMatrix.matrixOf(
            3, 3,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 0.0
        )
        val green = DoubleMatrix.matrixOf(3, 3)
        val blue = DoubleMatrix.matrixOf(3, 3)
        val image = MatrixImage(3, 3, listOf(Channel.Red, Channel.Green, Channel.Blue), listOf(red, green, blue))

        // Scaling matrix (scales by a factor of 2)
        val scalingMatrix = DoubleMatrix.matrixOf(
            3, 3,
            2.0, 0.0, 0.0,
            0.0, 2.0, 0.0,
            0.0, 0.0, 1.0
        )

        val transformedImage = applyTransformationToImage(image, emptyList(), scalingMatrix)
        assertNotNull(transformedImage)

        val expected = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, 0.0,
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0
        )

        assertMatrixEquals(expected, transformedImage[Channel.Red])
    }

    @Test
    fun testApplyTransformationToImageRotation() {
        val red = DoubleMatrix.matrixOf(
            3, 3,
            1.0, 0.0, 0.0,
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0
        )
        val green = DoubleMatrix.matrixOf(3, 3) { _, _ -> 0.5 }
        val blue = DoubleMatrix.matrixOf(3, 3) { _, _ -> 0.25 }
        val image = MatrixImage(3, 3, listOf(Channel.Red, Channel.Green, Channel.Blue), listOf(red, green, blue))

        // 90-degree rotation matrix
        val transformationMatrix = DoubleMatrix.matrixOf(
            3, 3,
            0.0, -1.0, 0.0,
            1.0, 0.0, 0.0,
            0.0, 0.0, 1.0
        )

        decomposeTransformationMatrix(transformationMatrix).let {
            assertEquals(0.0, it.translationX)
            assertEquals(0.0, it.translationY)
            assertEquals(1.0, it.scaleX)
            assertEquals(1.0, it.scaleY)
            assertEquals(PI/2.0, it.rotation, 1E-6)
            assertEquals(0.0, it.shear)
        }

        val transformedImage = applyTransformationToImage(image, emptyList(), transformationMatrix)
        assertNotNull(transformedImage)

        val expected = DoubleMatrix.matrixOf(
            3, 3,
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0,
            1.0, 0.0, 0.0
        )

        assertMatrixEquals(expected, transformedImage[Channel.Red])
    }

    @Test
    fun testInterpolate() {
        val result = interpolate(1.0, 2.0, 3.0, 4.0, 0.5, 0.5)
        assertEquals(2.5, result, 1e-6)
    }

    @Test
    fun testAlignStarImage() {
        val img1 = createStarImage(1000, 1000, 100)
        ImageWriter.write(img1, File("img1"))
        val img2 = img1.crop(10, 0, img1.width, img1.height)
        ImageWriter.write(img2, File("img2"))

        val referenceStars = findStars(img1)
        val otherStars = findStars(img2)
        val transform = calculateTransformationMatrix(referenceStars, otherStars, img1.width, img2.height)!!
        println(formatTransformation(decomposeTransformationMatrix(transform)))
        val actualImg = applyTransformationToImage(img1, emptyList(), transform)
        ImageWriter.write(actualImg, File("img3"))

        val expectedImg = img1.crop(30, 0, img2.width, img2.height)
        val diffImg = expectedImg - actualImg!!
        val error = diffImg.values().map{ it -> it*it }.sum() / (diffImg.width * diffImg.height)
        println(error)
    }

    private fun createStarImage(width: Int, height: Int, starCount: Int = width * height / 100, seed: Long = System.currentTimeMillis()): Image {
        val m = DoubleMatrix.matrixOf(width, height)
        val random = Random(seed)
        for (i in 0 until starCount) {
            val x = random.nextInt(width)
            val y = random.nextInt(width)
            val brightness = random.nextDouble()
            m[y, x] = brightness
        }
        return MatrixImage(width, height,
            Channel.Red to m,
            Channel.Green to m,
            Channel.Blue to m
        )
    }
}
