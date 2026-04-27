package ch.obermuhlner.kimage.astro.align

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.linearalgebra.invert
import ch.obermuhlner.kimage.core.matrix.values.MatrixXY
import ch.obermuhlner.kimage.core.matrix.values.asXY
import java.util.ArrayDeque
import kotlin.math.*
import kotlin.random.Random

data class Star(val x: Double, val y: Double, val brightness: Double, val fwhmX: Double, val fwhmY: Double) {
    val intX: Int get() = (x + 0.5).toInt()
    val intY: Int get() = (y + 0.5).toInt()
}

data class TriangleFeature(
    val indices: List<Int>,
    val angles: List<Double>,
    val orientation: Int
)

data class QuadFeature(
    val indices: List<Int>,
    val xC: Double,
    val yC: Double,
    val xD: Double,
    val yD: Double
)

fun selectUniformStars(stars: List<Star>, width: Double, height: Double, gridX: Int = 4, gridY: Int = 4, starsPerCell: Int = 5): List<Star> {
    if (stars.isEmpty()) return emptyList()

    val minX = stars.minOf { it.x }
    val maxX = stars.maxOf { it.x }
    val minY = stars.minOf { it.y }
    val maxY = stars.maxOf { it.y }

    val actualWidth = max(width, maxX - minX)
    val actualHeight = max(height, maxY - minY)
    val offsetX = if (width > 0) 0.0 else minX
    val offsetY = if (height > 0) 0.0 else minY

    val cellWidth = actualWidth / gridX
    val cellHeight = actualHeight / gridY
    val grid = Array(gridY) { Array(gridX) { mutableListOf<Star>() } }

    for (star in stars) {
        val gx = ((star.x - offsetX) / cellWidth).toInt().coerceIn(0, gridX - 1)
        val gy = ((star.y - offsetY) / cellHeight).toInt().coerceIn(0, gridY - 1)
        grid[gy][gx].add(star)
    }

    val selectedStars = mutableListOf<Star>()
    for (y in 0 until gridY) {
        for (x in 0 until gridX) {
            selectedStars.addAll(grid[y][x].sortedByDescending { it.brightness }.take(starsPerCell))
        }
    }

    return selectedStars.sortedByDescending { it.brightness }
}

fun findStars(image: Image, threshold: Double = 0.2, channel: Channel = Channel.Gray): List<Star> {
    val height = image.height
    val width = image.width

    val matrixXY = image[channel].asXY()
    val visited = Array(height) { BooleanArray(width) { false } }
    val localMaxima = mutableSetOf<PointXY>()

    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val centerValue = matrixXY[x, y]
            if (centerValue >= threshold) {
                var isLocalMax = true
                loop@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (matrixXY[x + dx, y + dy] > centerValue) {
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

    val stars = clusters.map { cluster ->
        var sumX = 0.0
        var sumY = 0.0
        var totalBrightness = 0.0
        var maxBrightness = 0.0

        for ((x, y) in cluster) {
            val brightness = matrixXY[x, y]
            sumX += x * brightness
            sumY += y * brightness
            totalBrightness += brightness
            if (brightness > maxBrightness) {
                maxBrightness = brightness
            }
        }

        val centroidX = sumX / totalBrightness
        val centroidY = sumY / totalBrightness
        val averageBrightness = totalBrightness / cluster.size
        val halfMax = maxBrightness / 2.0

        var fwhmX = 0.0
        var currentX = centroidX.toInt()
        while (currentX < width && matrixXY[currentX, centroidY.toInt()] > halfMax) {
            currentX++
            fwhmX++
        }
        currentX = centroidX.toInt()
        while (currentX >= 0 && matrixXY[currentX, centroidY.toInt()] > halfMax) {
            currentX--
            fwhmX++
        }

        var fwhmY = 0.0
        var currentY = centroidY.toInt()
        while (currentY < height && matrixXY[centroidX.toInt(), currentY] > halfMax) {
            currentY++
            fwhmY++
        }
        currentY = centroidY.toInt()
        while (currentY >= 0 && matrixXY[centroidX.toInt(), currentY] > halfMax) {
            currentY--
            fwhmY++
        }

        Star(centroidX, centroidY, averageBrightness, fwhmX, fwhmY)
    }

    return stars.sortedByDescending { it.brightness }
}

fun copyOnlyStars(image: Image, stars: List<Star>, factor: Double = 3.0): Image {
    val width = image.width
    val height = image.height

    val starImage = MatrixImage(image.width, image.height, image.channels)

    for (channel in image.channels) {
        val matrixXY = starImage[channel].asXY()

        for (star in stars) {
            val radiusX = (star.fwhmX * factor / 2 + 0.5).toInt()
            val radiusY = (star.fwhmY * factor / 2 + 0.5).toInt()
            val centerX = star.intX
            val centerY = star.intY

            for (y in (centerY - radiusY)..(centerY + radiusY)) {
                for (x in (centerX - radiusX)..(centerX + radiusX)) {
                    if (x in 0 until width && y in 0 until height) {
                        val distanceX = (x - centerX).toDouble() / radiusX
                        val distanceY = (y - centerY).toDouble() / radiusY
                        if (distanceX * distanceX + distanceY * distanceY <= 1.0) {
                            matrixXY[x, y] = image[channel][y, x]
                        }
                    }
                }
            }
        }
    }
    return starImage
}

fun calculateTransformationMatrix(
    referenceStars: List<Star>,
    otherStars: List<Star>,
    imageWidth: Int,
    imageHeight: Int,
    brightnessToleranceFactor: Double = 0.1,
    angleTolerance: Double = 0.01,
    positionTolerance: Double = 5.0,
    maxIterations: Int = 1000,
    minInliersThresholdFactor: Double = 0.1,
    minScale: Double = 0.0,
    maxScale: Double = Double.MAX_VALUE
): Matrix? {
    var bestTransformation: Matrix? = null
    var bestInliers = 0
    var bestError = Double.MAX_VALUE
    val minInliersThreshold = (min(referenceStars.count(), otherStars.count()) * minInliersThresholdFactor).toInt()

    val referenceQuads = computeQuadFeatures(referenceStars)
    val otherQuads = computeQuadFeatures(otherStars)
    if (otherQuads.isEmpty()) return null

    val sortedReferenceQuads = referenceQuads.sortedBy { it.xC }

    data class Result(val transformation: Matrix, val inliers: Int, val score: Double, val error: Double)

    // Seed must stay hardcoded to keep alignment deterministic.
    val random = Random(123)

    val nThreads = Runtime.getRuntime().availableProcessors()
    val iterationsPerThread = maxIterations / nThreads

    val results = (0 until nThreads).map { threadIdx ->
        val start = threadIdx * iterationsPerThread
        val end = if (threadIdx == nThreads - 1) maxIterations else (threadIdx + 1) * iterationsPerThread

        var bestLocalTransformation: Matrix? = null
        var bestLocalInliers = 0
        var bestLocalScore = -1.0
        var bestLocalError = Double.MAX_VALUE
        var matchedQuads = 0
        var rankConsistentQuads = 0

        for (iteration in start until end) {
            val randomIndex = random.nextInt(otherQuads.size)
            val otherQuad = otherQuads[randomIndex]

            val matchingQuads = findPotentialMatches(otherQuad, sortedReferenceQuads, angleTolerance)
            if (matchingQuads.isEmpty()) continue
            matchedQuads += matchingQuads.size

            for (refQuad in matchingQuads) {
                val quadOther = otherQuad.indices.map { otherStars[it] }
                val quadReference = refQuad.indices.map { referenceStars[it] }

                if (!isRankConsistent(quadOther, quadReference)) {
                    continue
                }
                rankConsistentQuads++

                val transformation = computeTransformationMatrix(quadOther.take(3), quadReference.take(3), imageWidth, imageHeight)
                if (transformation != null) {
                    val components = decomposeTransformationMatrix(transformation)
                    if (components.scaleX < minScale || components.scaleX > maxScale ||
                        components.scaleY < minScale || components.scaleY > maxScale) {
                        continue
                    }

                    val transformedStars = applyTransformationToStars(otherStars, transformation, imageWidth, imageHeight)
                    val inliers = countInliers(transformedStars, referenceStars, positionTolerance)
                    val score = calculateScore(transformedStars, referenceStars, positionTolerance)
                    val error = calculateSquareError(transformedStars, referenceStars)

                    if (score > bestLocalScore) {
                        bestLocalScore = score
                        bestLocalInliers = inliers
                        bestLocalError = error
                        bestLocalTransformation = transformation
                    }
                }
            }
        }
        println("Thread $threadIdx: iterations ${end - start}, matched quads $matchedQuads, rank consistent $rankConsistentQuads, best inliers $bestLocalInliers, best score $bestLocalScore")
        if (bestLocalTransformation != null) {
            Result(bestLocalTransformation, bestLocalInliers, bestLocalScore, bestLocalError)
        } else {
            null
        }
    }.filterNotNull()

    val bestResult = results.maxByOrNull { it.score } ?: return null

    if (bestResult.inliers < minInliersThreshold) {
        return null
    }

    return bestResult.transformation
}

private fun findPotentialMatches(otherQuad: QuadFeature, sortedReferenceQuads: List<QuadFeature>, tolerance: Double): List<QuadFeature> {
    val lowerBound = otherQuad.xC - tolerance
    val upperBound = otherQuad.xC + tolerance

    val startIndex = sortedReferenceQuads.binarySearch {
        it.xC.compareTo(lowerBound)
    }.let { if (it < 0) -it - 1 else it }

    val endIndex = sortedReferenceQuads.binarySearch {
        it.xC.compareTo(upperBound)
    }.let { if (it < 0) -it - 1 else it }

    return sortedReferenceQuads.subList(startIndex, endIndex).filter {
        abs(it.yC - otherQuad.yC) < tolerance &&
        abs(it.xD - otherQuad.xD) < tolerance &&
        abs(it.yD - otherQuad.yD) < tolerance
    }
}

private fun calculateScore(transformedStars: List<Star>, referenceStars: List<Star>, positionTolerance: Double): Double {
    val kdTree = StarKDTree(referenceStars)
    var score = 0.0
    for (tStar in transformedStars) {
        val nearest = kdTree.findNearest(tStar.x, tStar.y, positionTolerance)
        if (nearest != null) {
            val dx = tStar.x - nearest.x
            val dy = tStar.y - nearest.y
            val d2 = dx * dx + dy * dy
            val distanceScore = 1.0 - sqrt(d2) / positionTolerance
            val brightnessScore = 1.0 - abs(tStar.brightness - nearest.brightness)
            score += distanceScore * brightnessScore
        }
    }
    return score
}

private fun isRankConsistent(stars1: List<Star>, stars2: List<Star>): Boolean {
    fun getRankMatrix(stars: List<Star>): Array<IntArray> {
        val n = stars.size
        val matrix = Array(n) { IntArray(n) }
        val tolerance = 0.1 // 10% brightness tolerance
        for (i in 0 until n) {
            for (j in 0 until n) {
                matrix[i][j] = when {
                    stars[i].brightness > stars[j].brightness * (1.0 + tolerance) -> 1
                    stars[i].brightness < stars[j].brightness * (1.0 - tolerance) -> -1
                    else -> 0
                }
            }
        }
        return matrix
    }

    val m1 = getRankMatrix(stars1)
    val m2 = getRankMatrix(stars2)
    
    for (i in stars1.indices) {
        for (j in stars1.indices) {
            if (m1[i][j] != 0 && m2[i][j] != 0 && m1[i][j] != m2[i][j]) {
                return false
            }
        }
    }
    return true
}

private fun computeQuadFeatures(stars: List<Star>): List<QuadFeature> {
    val maxStarsForQuads = 50
    val topStars = stars.take(maxStarsForQuads)
    val n = topStars.size
    val quadFeatures = mutableListOf<QuadFeature>()

    for (i in 0 until n - 3) {
        for (j in i + 1 until n - 2) {
            for (k in j + 1 until n - 1) {
                for (l in k + 1 until n) {
                    val indices = listOf(i, j, k, l)
                    quadFeatures.add(computeQuadFeature(topStars, indices))
                }
            }
        }
    }
    println("Computed ${quadFeatures.size} quads from $n stars")
    return quadFeatures
}

private fun computeQuadFeature(stars: List<Star>, indices: List<Int>): QuadFeature {
    val p = indices.map { stars[it] }
    var maxDistSq = -1.0
    var idxA = -1
    var idxB = -1
    for (i in 0..3) {
        for (j in i + 1..3) {
            val d2 = distanceSq(p[i], p[j])
            if (d2 > maxDistSq) {
                maxDistSq = d2
                idxA = i
                idxB = j
            }
        }
    }
    
    val pA = p[idxA]
    val pB = p[idxB]
    val otherIndices = (0..3).filter { it != idxA && it != idxB }
    val pC = p[otherIndices[0]]
    val pD = p[otherIndices[1]]
    
    fun getNormalizedCoords(a: Star, b: Star, c: Star, d: Star): QuadFeature {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val L2 = dx * dx + dy * dy
        val L = sqrt(L2)
        val ux = dx / L
        val uy = dy / L
        val vx = -uy
        val vy = ux
        
        fun project(p: Star): Pair<Double, Double> {
            val px = p.x - a.x
            val py = p.y - a.y
            return (px * ux + py * uy) / L to (px * vx + py * vy) / L
        }
        
        val (xc, yc) = project(c)
        val (xd, yd) = project(d)
        
        return if (xc < xd || (xc == xd && yc < yd)) {
            QuadFeature(indices, xc, yc, xd, yd)
        } else {
            QuadFeature(indices, xd, yd, xc, yc)
        }
    }
    
    val quad1 = getNormalizedCoords(pA, pB, pC, pD)
    val quad2 = getNormalizedCoords(pB, pA, pC, pD)
    
    return if (quad1.xC + quad1.xD < 1.0 || (abs(quad1.xC + quad1.xD - 1.0) < 1e-9 && quad1.yC + quad1.yD > 0)) {
        quad1
    } else {
        quad2
    }
}

private fun distanceSq(s1: Star, s2: Star): Double {
    val dx = s1.x - s2.x
    val dy = s1.y - s2.y
    return dx * dx + dy * dy
}

private fun calculateSquareError(transformedStars: List<Star>, referenceStars: List<Star>): Double {
    var totalError = 0.0
    val kdTree = StarKDTree(referenceStars)
    for (tStar in transformedStars) {
        val nearest = kdTree.findNearest(tStar.x, tStar.y, Double.MAX_VALUE)
        if (nearest != null) {
            val dx = tStar.x - nearest.x
            val dy = tStar.y - nearest.y
            totalError += dx * dx + dy * dy
        }
    }
    return totalError
}

fun distance(star1: Star, star2: Star): Double {
    val dx = (star1.x - star2.x).toDouble()
    val dy = (star1.y - star2.y).toDouble()
    return sqrt(dx * dx + dy * dy)
}

fun computeTransformationMatrix(tripletOther: List<Star>, tripletReference: List<Star>, imageWidth: Int, imageHeight: Int): Matrix? {
    if (tripletOther.size != 3 || tripletReference.size != 3) return null
    val centerX = imageWidth / 2.0
    val centerY = imageHeight / 2.0
    val A = DoubleMatrix.matrixOf(6, 6)
    val B = DoubleMatrix.matrixOf(6, 1)

    for (i in 0..2) {
        val x = tripletOther[i].x.toDouble() - centerX
        val y = tripletOther[i].y.toDouble() - centerY
        val xPrime = tripletReference[i].x.toDouble() - centerX
        val yPrime = tripletReference[i].y.toDouble() - centerY

        A[i * 2, 0] = x
        A[i * 2, 1] = y
        A[i * 2, 2] = 1.0
        B[i * 2, 0] = xPrime
        A[i * 2 + 1, 3] = x
        A[i * 2 + 1, 4] = y
        A[i * 2 + 1, 5] = 1.0
        B[i * 2 + 1, 0] = yPrime
    }

    val inverseA = A.invert() ?: return null
    val params = inverseA.times(B)
    val transformation = DoubleMatrix.matrixOf(3, 3)
    transformation[0, 0] = params[0, 0]
    transformation[0, 1] = params[1, 0]
    transformation[0, 2] = params[2, 0]
    transformation[1, 0] = params[3, 0]
    transformation[1, 1] = params[4, 0]
    transformation[1, 2] = params[5, 0]
    transformation[2, 2] = 1.0
    return transformation
}

fun applyTransformationToStars(stars: List<Star>, transformationMatrix: Matrix, imageWidth: Int, imageHeight: Int): List<Star> {
    val transformedStars = mutableListOf<Star>()
    val centerX = imageWidth / 2.0
    val centerY = imageHeight / 2.0
    for (star in stars) {
        val vector = DoubleMatrix.matrixOf(3, 1)
        vector[0, 0] = star.x - centerX
        vector[1, 0] = star.y - centerY
        vector[2, 0] = 1.0
        val result = transformationMatrix.times(vector)
        transformedStars.add(Star(result[0, 0] + centerX, result[1, 0] + centerY, star.brightness, star.fwhmX, star.fwhmY))
    }
    return transformedStars
}

fun countInliers(transformedStars: List<Star>, referenceStars: List<Star>, tolerance: Double): Int {
    val kdTree = StarKDTree(referenceStars)
    var inliers = 0
    for (tStar in transformedStars) {
        if (kdTree.findNearest(tStar.x, tStar.y, tolerance) != null) inliers++
    }
    return inliers
}

class StarKDTree(stars: List<Star>) {
    private class Node(val star: Star, var left: Node? = null, var right: Node? = null)
    private val root: Node? = buildTree(stars, 0)

    private fun buildTree(stars: List<Star>, depth: Int): Node? {
        if (stars.isEmpty()) return null
        val sortedStars = if (depth % 2 == 0) stars.sortedBy { it.x } else stars.sortedBy { it.y }
        val median = sortedStars.size / 2
        val node = Node(sortedStars[median])
        node.left = buildTree(sortedStars.subList(0, median), depth + 1)
        node.right = buildTree(sortedStars.subList(median + 1, sortedStars.size), depth + 1)
        return node
    }

    fun findNearest(x: Double, y: Double, maxDist: Double): Star? {
        var bestStar: Star? = null
        var bestDistSq = maxDist * maxDist
        fun search(node: Node?, depth: Int) {
            if (node == null) return
            val d2 = (node.star.x - x).let { it * it } + (node.star.y - y).let { it * it }
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestStar = node.star
            }
            val diff = if (depth % 2 == 0) x - node.star.x else y - node.star.y
            val nearNode = if (diff < 0) node.left else node.right
            val farNode = if (diff < 0) node.right else node.left
            search(nearNode, depth + 1)
            if (diff * diff < bestDistSq) search(farNode, depth + 1)
        }
        search(root, 0)
        return bestStar
    }
}

fun applyTransformationToImage(inputImage: Image, transformationMatrix: Matrix): Image {
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
    val inverse = transformationMatrix.invert() ?: return inputImage

    for (yPrime in 0 until height) {
        for (xPrime in 0 until width) {
            val outV = DoubleMatrix.matrixOf(3, 1)
            outV[0, 0] = xPrime - centerX
            outV[1, 0] = yPrime - centerY
            outV[2, 0] = 1.0
            val inV = inverse.times(outV)
            val x = inV[0, 0] + centerX
            val y = inV[1, 0] + centerY
            if (x >= 0 && x < width && y >= 0 && y < height) {
                val x0 = floor(x).toInt()
                val y0 = floor(y).toInt()
                if (x0 < width - 1 && y0 < height - 1) {
                    val dx = x - x0
                    val dy = y - y0
                    fun interp(c: Matrix) = c[y0, x0]*(1-dx)*(1-dy) + c[y0, x0+1]*dx*(1-dy) + c[y0+1, x0]*(1-dx)*dy + c[y0+1, x0+1]*dx*dy
                    transformedRed[yPrime, xPrime] = interp(redChannel)
                    transformedGreen[yPrime, xPrime] = interp(greenChannel)
                    transformedBlue[yPrime, xPrime] = interp(blueChannel)
                } else {
                    // Boundary pixel: nearest-neighbour instead of black
                    val xi = x0.coerceIn(0, width - 1)
                    val yi = y0.coerceIn(0, height - 1)
                    transformedRed[yPrime, xPrime] = redChannel[yi, xi]
                    transformedGreen[yPrime, xPrime] = greenChannel[yi, xi]
                    transformedBlue[yPrime, xPrime] = blueChannel[yi, xi]
                }
            }
        }
    }
    return MatrixImage(width, height, listOf(Channel.Red, Channel.Green, Channel.Blue), listOf(transformedRed, transformedGreen, transformedBlue))
}

data class TransformationComponents(val translationX: Double, val translationY: Double, val scaleX: Double, val scaleY: Double, val rotation: Double, val shear: Double)

fun decomposeTransformationMatrix(matrix: Matrix): TransformationComponents {
    val a = matrix[0, 0]; val b = matrix[0, 1]; val tx = matrix[0, 2]
    val c = matrix[1, 0]; val d = matrix[1, 1]; val ty = matrix[1, 2]
    val scaleX = sqrt(a*a + c*c)
    val scaleY = sqrt(b*b + d*d)
    return TransformationComponents(tx, ty, scaleX, scaleY, atan2(c, a), (a*b + c*d)/scaleY)
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
    return applyTransformationToImage(referenceImage, transform)
}

fun triangleArea(a: Star, b: Star, c: Star): Double {
    return 0.5 * abs(a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
}

fun lawOfCosinesAngle(a: Double, b: Double, c: Double): Double {
    val cosC = (a * a + b * b - c * c) / (2 * a * b)
    return acos(cosC.coerceIn(-1.0, 1.0))
}

fun computeTriangleAngles(a: Star, b: Star, c: Star): List<Double> {
    val distAB = distance(a, b)
    val distBC = distance(b, c)
    val distCA = distance(c, a)

    return listOf(
        lawOfCosinesAngle(distAB, distCA, distBC),
        lawOfCosinesAngle(distAB, distBC, distCA),
        lawOfCosinesAngle(distBC, distCA, distAB)
    ).sorted()
}

fun anglesAreSimilar(angles1: List<Double>, angles2: List<Double>, tolerance: Double): Boolean {
    if (angles1.size != angles2.size) return false
    for (i in angles1.indices) {
        if (abs(angles1[i] - angles2[i]) > tolerance) return false
    }
    return true
}

fun computeTriangleFeatures(stars: List<Star>): List<TriangleFeature> {
    val n = stars.size
    val features = mutableListOf<TriangleFeature>()

    for (i in 0 until n - 2) {
        for (j in i + 1 until n - 1) {
            for (k in j + 1 until n) {
                val angles = computeTriangleAngles(stars[i], stars[j], stars[k])
                val orientation = 0 // Not implemented yet
                features.add(TriangleFeature(listOf(i, j, k), angles, orientation))
            }
        }
    }

    return features
}

fun interpolate(q11: Double, q21: Double, q12: Double, q22: Double, dx: Double, dy: Double): Double {
    val r1 = q11 * (1 - dx) + q21 * dx
    val r2 = q12 * (1 - dx) + q22 * dx
    return r1 * (1 - dy) + r2 * dy
}
