package ch.obermuhlner.kimage.astro.align

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.linearalgebra.invert
import ch.obermuhlner.kimage.core.matrix.values.MatrixXY
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

data class Star(val x: Int, val y: Int, val brightness: Double)
data class TriangleFeature(
    val indices: List<Int>,
    val angles: List<Double>,
    val orientation: Int
)

fun findStars(image: Image, threshold: Double = 0.2): List<Star> {
    val height = image.height
    val width = image.width

    val matrix = image[Channel.Gray]
    val visited = Array(height) { BooleanArray(width) { false } }
    val localMaxima = mutableListOf<PointXY>()

    // Step 1: Detect local maxima
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val centerValue = matrix[x, y]
            if (centerValue >= threshold) {
                var isLocalMax = true
                loop@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (matrix[x + dx, y + dy] > centerValue) {
                            isLocalMax = false
                            break@loop
                        }
                    }
                }
                if (isLocalMax) {
                    localMaxima.add(PointXY(x, y))
                }
            }
        }
    }

    // Step 2: Cluster local maxima
    val clusters = mutableListOf<List<PointXY>>()
    val labelMap = Array(height) { IntArray(width) { -1 } }
    var currentLabel = 0

    for ((x, y) in localMaxima) {
        if (!visited[y][x]) {
            val queue = ArrayDeque<PointXY>()
            val cluster = mutableListOf<PointXY>()
            queue.add(PointXY(x, y))
            visited[y][x] = true

            while (queue.isNotEmpty()) {
                val (cx, cy) = queue.removeFirst()
                cluster.add(PointXY(cx, cy))
                labelMap[cy][cx] = currentLabel

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx in 0 until width && ny in 0 until height &&
                            !visited[ny][nx] && localMaxima.contains(PointXY(nx, ny))) {
                            visited[ny][nx] = true
                            queue.add(PointXY(nx, ny))
                        }
                    }
                }
            }
            clusters.add(cluster)
            currentLabel++
        }
    }

    // Step 3: Compute centroids
    val stars = clusters.map { cluster ->
        var sumX = 0.0
        var sumY = 0.0
        var totalBrightness = 0.0

        for ((x, y) in cluster) {
            val brightness = matrix[x, y]
            sumX += x * brightness
            sumY += y * brightness
            totalBrightness += brightness
        }

        val centroidX = sumX / totalBrightness
        val centroidY = sumY / totalBrightness
        val averageBrightness = totalBrightness / cluster.size

        Star(centroidX.toInt(), centroidY.toInt(), averageBrightness)
    }

    return stars.sortedByDescending { it.brightness }
}

fun calculateTransformationMatrix(
    referenceStars: List<Star>,
    otherStars: List<Star>,
    imageWidth: Int,
    imageHeight: Int,
    angleTolerance: Double = 0.01,
    positionTolerance: Double = 5.0,
    maxIterations: Int = 1000,
): Matrix? {
    var bestTransformation: Matrix? = null
    var bestInliers = 0
    var bestError = Double.MAX_VALUE

    val referenceTriangles = computeTriangleFeatures(referenceStars)
    val otherTriangles = computeTriangleFeatures(otherStars)
    println("Triangles: reference ${referenceTriangles.size}, other ${otherTriangles.size}")

    for (iteration in 1..maxIterations) {
        if (otherTriangles.isEmpty()) break

        // Randomly select a triangle from otherTriangles
        val randomIndex = Random.nextInt(otherTriangles.size)
        val otherTriangle = otherTriangles[randomIndex]

        // Find matching triangles in referenceTriangles based on angle similarity and orientation
        val matchingTriangles = referenceTriangles.filter { refTriangle ->
            anglesAreSimilar(otherTriangle.angles, refTriangle.angles, angleTolerance) &&
                    otherTriangle.orientation == refTriangle.orientation
        }

        if (matchingTriangles.isEmpty()) continue

        // Loop through matching triangles and compute transformation
        for (refTriangle in matchingTriangles) {
            val tripletOther = otherTriangle.indices.map { otherStars[it] }
            val tripletReference = refTriangle.indices.map { referenceStars[it] }

            // Compute the transformation matrix
            val transformation = computeTransformationMatrix(tripletOther, tripletReference, imageWidth, imageHeight)
            if (transformation != null) {
                // Apply the transformation to the other stars
                val transformedStars = applyTransformationToStars(otherStars, transformation, imageWidth, imageHeight)

                val inliers = countInliers(transformedStars, referenceStars, positionTolerance)

                if (inliers > bestInliers) {
                    bestError = calculateSquareError(transformedStars, referenceStars)
                    println("Inliers $inliers > $bestInliers of ${referenceStars.size} - error: $bestError")
                    bestInliers = inliers
                    bestTransformation = transformation
                } else if (inliers == bestInliers) {
                    val error = calculateSquareError(transformedStars, referenceStars)
                    if (error < bestError) {
                        println("Error $error < $bestError - $inliers inliers of ${referenceStars.size}")
                        bestError = error
                        bestTransformation = transformation
                    }

                    if (error < 1e-6) {
                        return bestTransformation
                    }
                }
            }
        }
    }

    return bestTransformation
}

// Helper function to compute the sum of squared errors
fun calculateSquareError(transformedStars: List<Star>, referenceStars: List<Star>): Double {
    var totalError = 0.0

    // Sum the squared distances between transformed stars and the closest reference stars
    for (tStar in transformedStars) {
        var minDistanceSquared = Double.MAX_VALUE

        // Find the closest reference star for each transformed star
        for (rStar in referenceStars) {
            val dx = (tStar.x - rStar.x).toDouble()
            val dy = (tStar.y - rStar.y).toDouble()
            val distanceSquared = dx * dx + dy * dy

            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared
            }
        }

        // Add the minimum squared distance for this transformed star
        totalError += minDistanceSquared
    }

    return totalError
}

// Function to compute triangle features from a list of stars
fun computeTriangleFeatures(stars: List<Star>): List<TriangleFeature> {
    val n = stars.size
    val triangleFeatures = mutableListOf<TriangleFeature>()

    for (i in 0 until n - 2) {
        for (j in i + 1 until n - 1) {
            for (k in j + 1 until n) {
                val starA = stars[i]
                val starB = stars[j]
                val starC = stars[k]

                val angles = computeTriangleAngles(starA, starB, starC)
                val sortedAngles = angles.sorted()

                val signedArea = triangleSignedArea(starA, starB, starC)
                val orientation = if (signedArea >= 0) 1 else -1

                triangleFeatures.add(
                    TriangleFeature(
                        indices = listOf(i, j, k),
                        angles = sortedAngles,
                        orientation = orientation
                    )
                )
            }
        }
    }

    return triangleFeatures
}

// Function to compute the signed area of a triangle
fun triangleSignedArea(p1: Star, p2: Star, p3: Star): Double {
    return 0.5 * (
            p1.x * (p2.y - p3.y) +
                    p2.x * (p3.y - p1.y) +
                    p3.x * (p1.y - p2.y)
            )
}

// Function to compute the internal angles of a triangle formed by three stars
fun computeTriangleAngles(starA: Star, starB: Star, starC: Star): List<Double> {
    val a = distance(starB, starC)
    val b = distance(starA, starC)
    val c = distance(starA, starB)

    val angleA = lawOfCosinesAngle(b, c, a)
    val angleB = lawOfCosinesAngle(a, c, b)
    val angleC = lawOfCosinesAngle(a, b, c)

    return listOf(angleA, angleB, angleC)
}

// Function to calculate the distance between two stars
fun distance(star1: Star, star2: Star): Double {
    val dx = (star1.x - star2.x).toDouble()
    val dy = (star1.y - star2.y).toDouble()
    return sqrt(dx * dx + dy * dy)
}

// Function to calculate the angle using the Law of Cosines
fun lawOfCosinesAngle(side1: Double, side2: Double, oppositeSide: Double): Double {
    val numerator = side1 * side1 + side2 * side2 - oppositeSide * oppositeSide
    val denominator = 2 * side1 * side2
    val cosAngle = numerator / denominator
    val clampedCosAngle = cosAngle.coerceIn(-1.0, 1.0)
    return acos(clampedCosAngle)
}

// Function to check if two sets of angles are similar within a tolerance
fun anglesAreSimilar(angles1: List<Double>, angles2: List<Double>, tolerance: Double): Boolean {
    for (i in angles1.indices) {
        if (abs(angles1[i] - angles2[i]) > tolerance) {
            return false
        }
    }
    return true
}

// Function to compute the affine transformation matrix from three point correspondences
fun computeTransformationMatrix(tripletOther: List<Star>, tripletReference: List<Star>, imageWidth: Int, imageHeight: Int): Matrix? {
    if (tripletOther.size != 3 || tripletReference.size != 3) {
        return null
    }

    // Check for colinearity in tripletOther and tripletReference
    val areaOther = triangleArea(tripletOther[0], tripletOther[1], tripletOther[2])
    val areaReference = triangleArea(tripletReference[0], tripletReference[1], tripletReference[2])
    if (areaOther < 1e-6 || areaReference < 1e-6) {
        return null // Points are collinear
    }

    // Calculate the center of the image
    val centerX = imageWidth / 2.0
    val centerY = imageHeight / 2.0

    // Prepare matrices
    val A = DoubleMatrix.matrixOf(6, 6)
    val B = DoubleMatrix.matrixOf(6, 1)

    for (i in 0..2) {
        // Translate the coordinates to center the rotation at (centerX, centerY)
        val x = tripletOther[i].x.toDouble() - centerX
        val y = tripletOther[i].y.toDouble() - centerY
        val xPrime = tripletReference[i].x.toDouble() - centerX
        val yPrime = tripletReference[i].y.toDouble() - centerY

        // First equation: x' = a * x + b * y + c
        A[i * 2, 0] = x
        A[i * 2, 1] = y
        A[i * 2, 2] = 1.0
        A[i * 2, 3] = 0.0
        A[i * 2, 4] = 0.0
        A[i * 2, 5] = 0.0
        B[i * 2, 0] = xPrime

        // Second equation: y' = d * x + e * y + f
        A[i * 2 + 1, 0] = 0.0
        A[i * 2 + 1, 1] = 0.0
        A[i * 2 + 1, 2] = 0.0
        A[i * 2 + 1, 3] = x
        A[i * 2 + 1, 4] = y
        A[i * 2 + 1, 5] = 1.0
        B[i * 2 + 1, 0] = yPrime
    }

    // Solve the linear system A * params = B
    val inverseA = A.invert() ?: return DoubleMatrix.identity(3)
    val params = inverseA.times(B)

    // Construct the affine transformation matrix
    val transformation = DoubleMatrix.matrixOf(3, 3)
    transformation[0, 0] = params[0, 0] // a
    transformation[0, 1] = params[1, 0] // b
    transformation[0, 2] = params[2, 0] // c
    transformation[1, 0] = params[3, 0] // d
    transformation[1, 1] = params[4, 0] // e
    transformation[1, 2] = params[5, 0] // f
    transformation[2, 0] = 0.0
    transformation[2, 1] = 0.0
    transformation[2, 2] = 1.0

    return transformation
}

// Function to compute the area of a triangle
fun triangleArea(p1: Star, p2: Star, p3: Star): Double {
    return 0.5 * abs(
        p1.x * (p2.y - p3.y) +
                p2.x * (p3.y - p1.y) +
                p3.x * (p1.y - p2.y)
    )
}

// Function to apply the transformation matrix to a list of stars
fun applyTransformationToStars(stars: List<Star>, transformationMatrix: Matrix, imageWidth: Int, imageHeight: Int): List<Star> {
    val transformedStars = mutableListOf<Star>()

    val centerX = imageWidth / 2.0
    val centerY = imageHeight / 2.0

    for (star in stars) {
        val x = star.x.toDouble()
        val y = star.y.toDouble()
        val vector = DoubleMatrix.matrixOf(3, 1)
        vector[0, 0] = x - centerX
        vector[1, 0] = y - centerY
        vector[2, 0] = 1.0

        val result = transformationMatrix.times(vector)
        val xTransformed = result[0, 0] + centerX
        val yTransformed = result[1, 0] + centerY

        transformedStars.add(Star(xTransformed.toInt(), yTransformed.toInt(), star.brightness))
    }
    return transformedStars
}

fun createDebugImageFromTransformedStars(stars: List<Star>, transformationMatrix: Matrix, imageWidth: Int, imageHeight: Int): Image {
    val transformedStars = applyTransformationToStars(stars, transformationMatrix, imageWidth, imageHeight)

    val matrix = DoubleMatrix.matrixOf(imageHeight, imageHeight)
    val matrixXY = MatrixXY(matrix)
    for (star in transformedStars) {
        matrixXY[star.x, star.y] = star.brightness
    }

    return MatrixImage(imageWidth, imageHeight,
        Channel.Red to matrix,
        Channel.Green to matrix,
        Channel.Blue to matrix)
}

// Function to count the number of inliers between transformed stars and reference stars
fun countInliers(transformedStars: List<Star>, referenceStars: List<Star>, tolerance: Double): Int {
    var inliers = 0
    for (tStar in transformedStars) {
        for (rStar in referenceStars) {
            val dx = (tStar.x - rStar.x).toDouble()
            val dy = (tStar.y - rStar.y).toDouble()
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= tolerance) {
                inliers++
                break
            }
        }
    }
    return inliers
}

// Function to apply the transformation matrix to the input image and obtain transformed channels
fun applyTransformationToImage(inputImage: Image, imageStars: List<Star>, transformationMatrix: Matrix): Image {
    return applyTransformationToImageUsingSimpleTransformation(inputImage, imageStars, transformationMatrix)
    //return applyTransformationToImageUsingInvertedTransformation(inputImage, transformationMatrix)
}

fun applyTransformationToImageUsingSimpleTransformation(inputImage: Image, stars: List<Star>, transformationMatrix: Matrix): Image {
    val width = inputImage.width
    val height = inputImage.height

    val centerX = width / 2.0
    val centerY = height / 2.0

    val redChannel = inputImage[Channel.Red]
    val greenChannel = inputImage[Channel.Green]
    val blueChannel = inputImage[Channel.Blue]

    val transformedRed = DoubleMatrix.matrixOf(height, width)
    val transformedGreen = DoubleMatrix.matrixOf(height, width)
    val transformedBlue = DoubleMatrix.matrixOf(height, width)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val inputVector = DoubleMatrix.matrixOf(3, 1)
            inputVector[0, 0] = x.toDouble() - centerX
            inputVector[1, 0] = y.toDouble() - centerY
            inputVector[2, 0] = 1.0

            val outputVector = transformationMatrix.times(inputVector)
            val xTransformed = outputVector[0, 0] + centerX
            val yTransformed = outputVector[1, 0] + centerY

            val x0 = floor(xTransformed + 0.5).toInt()
            val y0 = floor(yTransformed + 0.5).toInt()
            if (x0 >= 0 && x0 < width && y0 >= 0 && y0 < height) {
                transformedRed[y0, x0] = redChannel[y, x]
                transformedGreen[y0, x0] = greenChannel[y, x]
                transformedBlue[y0, x0] = blueChannel[y, x]
            }
        }
    }

    for (star in stars) {
        val x = star.x.toDouble()
        val y = star.y.toDouble()
        val vector = DoubleMatrix.matrixOf(3, 1)
        vector[0, 0] = x - centerX
        vector[1, 0] = y - centerY
        vector[2, 0] = 1.0

        val result = transformationMatrix.times(vector)
        val xTransformed = result[0, 0] + centerX
        val yTransformed = result[1, 0] + centerY

        val x0 = floor(xTransformed).toInt()
        val y0 = floor(yTransformed).toInt()
        if (x0 >= 0 && x0 < width && y0 >= 0 && y0 < height) {
            transformedRed[y0, x0] = 1.0
            transformedGreen[y0, x0] = 0.0
            transformedBlue[y0, x0] = 0.0
        }
    }

    return MatrixImage(width, height,
        Channel.Red to transformedRed,
        Channel.Green to transformedGreen,
        Channel.Blue to transformedBlue)
}

fun applyTransformationToImageUsingInvertedTransformation(inputImage: Image, transformationMatrix: Matrix): Image {
    val width = inputImage.width
    val height = inputImage.height

    val centerX = width / 2.0
    val centerY = height / 2.0

    // Extract the red, green, and blue channels as matrices
    val redChannel = inputImage[Channel.Red]
    val greenChannel = inputImage[Channel.Green]
    val blueChannel = inputImage[Channel.Blue]

    // Create matrices for the transformed channels (assuming the same size as the reference image)
    val transformedRed = DoubleMatrix.matrixOf(height, width)
    val transformedGreen = DoubleMatrix.matrixOf(height, width)
    val transformedBlue = DoubleMatrix.matrixOf(height, width)

    // Compute the inverse of the transformation matrix
    val inverseTransformationMatrix = transformationMatrix.invert() ?: return inputImage

    // Iterate over each pixel in the output image (reference image coordinates)
    for (yPrime in 0 until height) {
        for (xPrime in 0 until width) {
            // Create the coordinate vector for the output pixel
            val outputVector = DoubleMatrix.matrixOf(3, 1)
            outputVector[0, 0] = xPrime.toDouble() - centerX
            outputVector[1, 0] = yPrime.toDouble() - centerY
            outputVector[2, 0] = 1.0

            // Compute the corresponding input coordinates using the inverse transformation
            val inputVector = inverseTransformationMatrix.times(outputVector)
            val x = inputVector[0, 0] + centerX
            val y = inputVector[1, 0] + centerY

            val adjustedX = x
            val adjustedY = y

            // Check if the input coordinates are within the bounds of the input image
            if (adjustedX >= 0 && adjustedX < width - 1 && adjustedY >= 0 && adjustedY < height - 1) {
                // Perform bilinear interpolation to get the pixel values
                val x0 = floor(adjustedX).toInt()
                val x1 = x0 + 1
                val y0 = floor(adjustedY).toInt()
                val y1 = y0 + 1

                val dx = adjustedX - x0
                val dy = adjustedY - y0

                // Interpolate red channel
                val redValue = interpolate(
                    redChannel[y0, x0], redChannel[y0, x1],
                    redChannel[y1, x0], redChannel[y1, x1], dx, dy
                )
                transformedRed[yPrime, xPrime] = redValue

                // Interpolate green channel
                val greenValue = interpolate(
                    greenChannel[y0, x0], greenChannel[y0, x1],
                    greenChannel[y1, x0], greenChannel[y1, x1], dx, dy
                )
                transformedGreen[yPrime, xPrime] = greenValue

                // Interpolate blue channel
                val blueValue = interpolate(
                    blueChannel[y0, x0], blueChannel[y0, x1],
                    blueChannel[y1, x0], blueChannel[y1, x1], dx, dy
                )
                transformedBlue[yPrime, xPrime] = blueValue
            } else {
                // If out of bounds, you can set the pixel to a default value (e.g., black)
                transformedRed[yPrime, xPrime] = 0.0
                transformedGreen[yPrime, xPrime] = 0.0
                transformedBlue[yPrime, xPrime] = 0.0
            }
        }
    }

    return MatrixImage(width, height,
        listOf(Channel.Red, Channel.Green, Channel.Blue),
        listOf(transformedRed, transformedGreen, transformedBlue))
}

// Helper function for bilinear interpolation
fun interpolate(q11: Double, q21: Double, q12: Double, q22: Double, dx: Double, dy: Double): Double {
    val r1 = q11 * (1 - dx) + q21 * dx
    val r2 = q12 * (1 - dx) + q22 * dx
    return r1 * (1 - dy) + r2 * dy
}

data class TransformationComponents(
    val translationX: Double,
    val translationY: Double,
    val scaleX: Double,
    val scaleY: Double,
    val rotation: Double,  // In radians
    val shear: Double
)

fun decomposeTransformationMatrix(matrix: Matrix): TransformationComponents {
    require(matrix.rows == 3 && matrix.cols == 3) { "Matrix must be 3x3." }

    // Extract matrix components
    val a = matrix[0, 0]
    val b = matrix[0, 1]
    val tx = matrix[0, 2]
    val c = matrix[1, 0]
    val d = matrix[1, 1]
    val ty = matrix[1, 2]

    // Calculate translation
    val translationX = tx
    val translationY = ty

    // Calculate scale
    val scaleX = sqrt(a * a + c * c)
    val scaleY = sqrt(b * b + d * d)

    // Calculate rotation
    val rotation = atan2(c, a)

    // Calculate shear (skew)
    val shear = (a * b + c * d) / scaleY

    return TransformationComponents(
        translationX = translationX,
        translationY = translationY,
        scaleX = scaleX,
        scaleY = scaleY,
        rotation = rotation,
        shear = shear
    )
}

fun formatTransformation(components: TransformationComponents): String {
    val rotationDegrees = Math.toDegrees(components.rotation)
    return """
        Translation: (${components.translationX}, ${components.translationY})
        Scale: (X: ${components.scaleX}, Y: ${components.scaleY})
        Rotation: $rotationDegrees degrees
        Shear: ${components.shear}
    """.trimIndent()
}

fun alignStarImage(referenceImage: Image, otherImage: Image): Image? {
    val referenceStars = findStars(referenceImage)
    val otherStars = findStars(otherImage)
    val transform = calculateTransformationMatrix(referenceStars, otherStars, referenceImage.width, referenceImage.height) ?: return null
    return applyTransformationToImage(referenceImage, otherStars, transform)
}
