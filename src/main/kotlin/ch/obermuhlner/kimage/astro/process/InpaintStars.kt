package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.FloatMatrix
import java.util.PriorityQueue
import kotlin.math.*

enum class InpaintAlgorithm { None, Annulus, Erosion, Polynomial, RBF, Telea }

fun inpaintStars(image: Image, stars: List<Star>, factor: Double, algorithm: InpaintAlgorithm): Image {
    if (algorithm == InpaintAlgorithm.None || stars.isEmpty()) return image
    val mask = buildBinaryStarMask(image.width, image.height, stars, factor)
    return when (algorithm) {
        InpaintAlgorithm.None     -> image
        InpaintAlgorithm.Annulus  -> inpaintAnnulus(image, stars, factor)
        InpaintAlgorithm.Erosion  -> inpaintErosion(image, mask)
        InpaintAlgorithm.Polynomial -> inpaintPolynomial(image, stars, factor)
        InpaintAlgorithm.RBF      -> inpaintRBF(image, stars, factor)
        InpaintAlgorithm.Telea    -> inpaintTelea(image, mask)
    }
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

private fun buildBinaryStarMask(width: Int, height: Int, stars: List<Star>, factor: Double): BooleanArray {
    val mask = BooleanArray(width * height)
    for (star in stars) {
        val rx = (star.fwhmX * factor / 2 + 0.5).toInt().coerceAtLeast(1)
        val ry = (star.fwhmY * factor / 2 + 0.5).toInt().coerceAtLeast(1)
        val cx = star.intX; val cy = star.intY
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = (x - cx).toDouble() / rx
                val dy = (y - cy).toDouble() / ry
                if (dx * dx + dy * dy <= 1.0) mask[y * width + x] = true
            }
        }
    }
    return mask
}

private fun imageToPixels(image: Image): Array<FloatArray> {
    val width = image.width
    return Array(image.channels.size) { ch ->
        val channel = image.channels[ch]
        FloatArray(width * image.height) { idx -> image[channel][idx / width, idx % width].toFloat() }
    }
}

private fun pixelsToImage(pixels: Array<FloatArray>, width: Int, height: Int, channels: List<Channel>): Image =
    MatrixImage(width, height, channels) { channel, rows, cols ->
        val ch = channels.indexOf(channel)
        FloatMatrix(rows, cols) { row, col -> pixels[ch][row * width + col].coerceIn(0f, 1f) }
    }

// ---------------------------------------------------------------------------
// Annulus: fill each star hole with the median of a thin ring just outside it
// ---------------------------------------------------------------------------

private fun inpaintAnnulus(image: Image, stars: List<Star>, factor: Double): Image {
    val width = image.width; val height = image.height
    val outerFactor = factor * 1.4
    val nch = image.channels.size
    val pixels = imageToPixels(image)

    for (star in stars) {
        val rx  = (star.fwhmX * factor     / 2 + 0.5).toInt().coerceAtLeast(1)
        val ry  = (star.fwhmY * factor     / 2 + 0.5).toInt().coerceAtLeast(1)
        val orx = (star.fwhmX * outerFactor / 2 + 0.5).toInt().coerceAtLeast(rx + 2)
        val ory = (star.fwhmY * outerFactor / 2 + 0.5).toInt().coerceAtLeast(ry + 2)
        val cx = star.intX; val cy = star.intY

        val samples = Array(nch) { mutableListOf<Float>() }
        for (y in (cy - ory)..(cy + ory)) {
            for (x in (cx - orx)..(cx + orx)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dxO = (x - cx).toDouble() / orx; val dyO = (y - cy).toDouble() / ory
                val dxI = (x - cx).toDouble() / rx;  val dyI = (y - cy).toDouble() / ry
                if (dxO * dxO + dyO * dyO <= 1.0 && dxI * dxI + dyI * dyI > 1.0)
                    for (ch in 0 until nch) samples[ch].add(pixels[ch][y * width + x])
            }
        }
        if (samples[0].isEmpty()) continue

        val fill = FloatArray(nch) { ch -> samples[ch].sorted().let { it[it.size / 2] } }

        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dxI = (x - cx).toDouble() / rx; val dyI = (y - cy).toDouble() / ry
                if (dxI * dxI + dyI * dyI <= 1.0)
                    for (ch in 0 until nch) pixels[ch][y * width + x] = fill[ch]
            }
        }
    }
    return pixelsToImage(pixels, width, height, image.channels)
}

// ---------------------------------------------------------------------------
// Erosion: BFS inward from boundary, averaging unmasked neighbours each step
// ---------------------------------------------------------------------------

private fun inpaintErosion(image: Image, mask: BooleanArray): Image {
    val width = image.width; val height = image.height
    val nch = image.channels.size
    val pixels = imageToPixels(image)
    val isMasked = mask.copyOf()

    val dx8 = intArrayOf(-1, 1, 0, 0, -1, -1, 1, 1)
    val dy8 = intArrayOf( 0, 0,-1, 1, -1,  1,-1, 1)

    val visited = BooleanArray(width * height)
    val queue   = ArrayDeque<Int>()

    for (idx in 0 until width * height) {
        if (!isMasked[idx]) continue
        val row = idx / width; val col = idx % width
        for (d in 0..7) {
            val nr = row + dy8[d]; val nc = col + dx8[d]
            if (nr !in 0 until height || nc !in 0 until width) continue
            if (!isMasked[nr * width + nc]) { queue.add(idx); visited[idx] = true; break }
        }
    }

    while (queue.isNotEmpty()) {
        val idx = queue.removeFirst()
        if (!isMasked[idx]) continue
        val row = idx / width; val col = idx % width
        val sum = FloatArray(nch); var count = 0
        for (d in 0..7) {
            val nr = row + dy8[d]; val nc = col + dx8[d]
            if (nr !in 0 until height || nc !in 0 until width) continue
            val nidx = nr * width + nc
            if (!isMasked[nidx]) { count++; for (ch in 0 until nch) sum[ch] += pixels[ch][nidx] }
        }
        if (count > 0) {
            for (ch in 0 until nch) pixels[ch][idx] = sum[ch] / count
            isMasked[idx] = false
            for (d in 0..7) {
                val nr = row + dy8[d]; val nc = col + dx8[d]
                if (nr !in 0 until height || nc !in 0 until width) continue
                val nidx = nr * width + nc
                if (isMasked[nidx] && !visited[nidx]) { visited[nidx] = true; queue.add(nidx) }
            }
        }
    }
    return pixelsToImage(pixels, width, height, image.channels)
}

// ---------------------------------------------------------------------------
// Polynomial: fit a 2-D plane through the annulus boundary, evaluate inside
// ---------------------------------------------------------------------------

private fun inpaintPolynomial(image: Image, stars: List<Star>, factor: Double): Image {
    val width = image.width; val height = image.height
    val outerFactor = factor * 1.4
    val nch = image.channels.size
    val pixels = imageToPixels(image)

    for (star in stars) {
        val rx  = (star.fwhmX * factor      / 2 + 0.5).toInt().coerceAtLeast(1)
        val ry  = (star.fwhmY * factor      / 2 + 0.5).toInt().coerceAtLeast(1)
        val orx = (star.fwhmX * outerFactor / 2 + 0.5).toInt().coerceAtLeast(rx + 2)
        val ory = (star.fwhmY * outerFactor / 2 + 0.5).toInt().coerceAtLeast(ry + 2)
        val cx = star.intX; val cy = star.intY
        val cxd = cx.toDouble(); val cyd = cy.toDouble()

        val sxList = mutableListOf<Double>(); val syList = mutableListOf<Double>()
        val szList = Array(nch) { mutableListOf<Double>() }

        for (y in (cy - ory)..(cy + ory)) {
            for (x in (cx - orx)..(cx + orx)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dxO = (x - cx).toDouble() / orx; val dyO = (y - cy).toDouble() / ory
                val dxI = (x - cx).toDouble() / rx;  val dyI = (y - cy).toDouble() / ry
                if (dxO * dxO + dyO * dyO <= 1.0 && dxI * dxI + dyI * dyI > 1.0) {
                    sxList.add(x - cxd); syList.add(y - cyd)
                    for (ch in 0 until nch) szList[ch].add(pixels[ch][y * width + x].toDouble())
                }
            }
        }
        val n = sxList.size
        if (n < 3) continue

        // Normal equations for plane f(dx,dy)=a+b·dx+c·dy
        var s1 = 0.0; var sx = 0.0; var sy = 0.0
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0
        for (i in 0 until n) {
            val x = sxList[i]; val y = syList[i]
            s1 += 1.0; sx += x; sy += y; sxx += x*x; sxy += x*y; syy += y*y
        }
        val AtA = arrayOf(
            doubleArrayOf(s1,  sx,  sy),
            doubleArrayOf(sx,  sxx, sxy),
            doubleArrayOf(sy,  sxy, syy),
        )
        for (ch in 0 until nch) {
            var r0 = 0.0; var r1 = 0.0; var r2 = 0.0
            for (i in 0 until n) {
                val z = szList[ch][i]; r0 += z; r1 += sxList[i]*z; r2 += syList[i]*z
            }
            val coeff = gaussianElim3(AtA, doubleArrayOf(r0, r1, r2)) ?: continue
            for (y in (cy - ry)..(cy + ry)) {
                for (x in (cx - rx)..(cx + rx)) {
                    if (x !in 0 until width || y !in 0 until height) continue
                    val dxI = (x - cx).toDouble() / rx; val dyI = (y - cy).toDouble() / ry
                    if (dxI * dxI + dyI * dyI <= 1.0) {
                        val v = coeff[0] + coeff[1]*(x - cxd) + coeff[2]*(y - cyd)
                        pixels[ch][y * width + x] = v.toFloat().coerceIn(0f, 1f)
                    }
                }
            }
        }
    }
    return pixelsToImage(pixels, width, height, image.channels)
}

private fun gaussianElim3(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
    val aug = Array(3) { i -> DoubleArray(4) { j -> if (j < 3) A[i][j] else b[i] } }
    for (col in 0..2) {
        var maxRow = col
        for (row in col + 1..2) if (abs(aug[row][col]) > abs(aug[maxRow][col])) maxRow = row
        val tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp
        if (abs(aug[col][col]) < 1e-12) return null
        for (row in col + 1..2) {
            val f = aug[row][col] / aug[col][col]
            for (j in col..3) aug[row][j] -= f * aug[col][j]
        }
    }
    val x = DoubleArray(3)
    for (i in 2 downTo 0) {
        x[i] = aug[i][3]
        for (j in i + 1..2) x[i] -= aug[i][j] * x[j]
        x[i] /= aug[i][i]
    }
    return x
}

// ---------------------------------------------------------------------------
// RBF: thin-plate spline through boundary samples, evaluate inside
// ---------------------------------------------------------------------------

private fun inpaintRBF(image: Image, stars: List<Star>, factor: Double): Image {
    val width = image.width; val height = image.height
    val outerFactor = factor * 1.4
    val maxSamples = 80
    val nch = image.channels.size
    val pixels = imageToPixels(image)

    for (star in stars) {
        val rx  = (star.fwhmX * factor      / 2 + 0.5).toInt().coerceAtLeast(1)
        val ry  = (star.fwhmY * factor      / 2 + 0.5).toInt().coerceAtLeast(1)
        val orx = (star.fwhmX * outerFactor / 2 + 0.5).toInt().coerceAtLeast(rx + 2)
        val ory = (star.fwhmY * outerFactor / 2 + 0.5).toInt().coerceAtLeast(ry + 2)
        val cx = star.intX; val cy = star.intY

        val bxList = mutableListOf<Double>(); val byList = mutableListOf<Double>()
        val bzList = Array(nch) { mutableListOf<Double>() }

        for (y in (cy - ory)..(cy + ory)) {
            for (x in (cx - orx)..(cx + orx)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dxO = (x - cx).toDouble() / orx; val dyO = (y - cy).toDouble() / ory
                val dxI = (x - cx).toDouble() / rx;  val dyI = (y - cy).toDouble() / ry
                if (dxO * dxO + dyO * dyO <= 1.0 && dxI * dxI + dyI * dyI > 1.0) {
                    bxList.add(x.toDouble()); byList.add(y.toDouble())
                    for (ch in 0 until nch) bzList[ch].add(pixels[ch][y * width + x].toDouble())
                }
            }
        }

        val total = bxList.size
        val n = minOf(total, maxSamples)
        if (n < 4) continue

        val indices = if (total <= maxSamples) (0 until total).toList()
                      else (0 until n).map { (it.toLong() * total / n).toInt() }
        val sx = DoubleArray(n) { bxList[indices[it]] }
        val sy = DoubleArray(n) { byList[indices[it]] }
        val sz = Array(nch) { ch -> DoubleArray(n) { bzList[ch][indices[it]] } }

        // Thin-plate spline kernel: φ(r²) = r²·ln(r) = r²·ln(r²)/2
        fun tps(r2: Double) = if (r2 < 1e-10) 0.0 else r2 * ln(r2) / 2.0

        // Build (n+3)×(n+3) TPS system
        val m = n + 3
        val mat = Array(m) { i ->
            DoubleArray(m) { j ->
                when {
                    i < n && j < n  -> tps((sx[i]-sx[j]).pow(2) + (sy[i]-sy[j]).pow(2))
                    i < n && j == n -> 1.0
                    i < n && j == n+1 -> sx[i]
                    i < n && j == n+2 -> sy[i]
                    i == n   && j < n -> 1.0
                    i == n+1 && j < n -> sx[j]
                    i == n+2 && j < n -> sy[j]
                    else -> 0.0
                }
            }
        }

        for (ch in 0 until nch) {
            val rhs = DoubleArray(m) { if (it < n) sz[ch][it] else 0.0 }
            val sol = gaussianElimN(mat.map { it.copyOf() }.toTypedArray(), rhs) ?: continue
            val w = sol.copyOf(n); val a0 = sol[n]; val a1 = sol[n+1]; val a2 = sol[n+2]

            for (y in (cy - ry)..(cy + ry)) {
                for (x in (cx - rx)..(cx + rx)) {
                    if (x !in 0 until width || y !in 0 until height) continue
                    val dxI = (x - cx).toDouble() / rx; val dyI = (y - cy).toDouble() / ry
                    if (dxI * dxI + dyI * dyI <= 1.0) {
                        var v = a0 + a1 * x + a2 * y
                        for (i in 0 until n) v += w[i] * tps((x - sx[i]).pow(2) + (y - sy[i]).pow(2))
                        pixels[ch][y * width + x] = v.toFloat().coerceIn(0f, 1f)
                    }
                }
            }
        }
    }
    return pixelsToImage(pixels, width, height, image.channels)
}

private fun gaussianElimN(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
    val n = A.size
    val aug = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) A[i][j] else b[i] } }
    for (col in 0 until n) {
        var maxRow = col
        for (row in col + 1 until n) if (abs(aug[row][col]) > abs(aug[maxRow][col])) maxRow = row
        val tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp
        if (abs(aug[col][col]) < 1e-12) return null
        for (row in col + 1 until n) {
            val f = aug[row][col] / aug[col][col]
            for (j in col..n) aug[row][j] -= f * aug[col][j]
        }
    }
    val x = DoubleArray(n)
    for (i in n - 1 downTo 0) {
        x[i] = aug[i][n]
        for (j in i + 1 until n) x[i] -= aug[i][j] * x[j]
        x[i] /= aug[i][i]
    }
    return x
}

// ---------------------------------------------------------------------------
// Telea: fast-marching fill with distance-weighted gradient propagation
// ---------------------------------------------------------------------------

private fun inpaintTelea(image: Image, mask: BooleanArray): Image {
    val width = image.width; val height = image.height
    val nch = image.channels.size
    val pixels = imageToPixels(image)
    val isMasked = mask.copyOf()
    val searchRadius = 5

    val dist = FloatArray(width * height) { if (mask[it]) Float.MAX_VALUE else 0f }
    // PQ entries: (distance, index) — lazy-deletion for stale entries
    val pq = PriorityQueue<Pair<Float, Int>>(compareBy { it.first })

    val dx4 = intArrayOf(-1, 1,  0, 0)
    val dy4 = intArrayOf( 0, 0, -1, 1)

    // Seed: masked pixels directly adjacent to known pixels
    for (idx in 0 until width * height) {
        if (!mask[idx]) continue
        val row = idx / width; val col = idx % width
        for (d in 0..3) {
            val nr = row + dy4[d]; val nc = col + dx4[d]
            if (nr !in 0 until height || nc !in 0 until width) continue
            if (!mask[nr * width + nc]) { dist[idx] = 1f; pq.add(Pair(1f, idx)); break }
        }
    }

    val filled = BooleanArray(width * height) { !mask[it] }

    while (pq.isNotEmpty()) {
        val (d, idx) = pq.poll()
        if (filled[idx] || d > dist[idx] + 1e-6f) continue   // stale entry
        val row = idx / width; val col = idx % width

        val sum = FloatArray(nch); var wsum = 0f
        for (dr in -searchRadius..searchRadius) {
            for (dc in -searchRadius..searchRadius) {
                val nr = row + dr; val nc = col + dc
                if (nr !in 0 until height || nc !in 0 until width) continue
                val nidx = nr * width + nc
                if (!filled[nidx]) continue
                val d2 = (dr * dr + dc * dc).toFloat()
                if (d2 > searchRadius * searchRadius) continue
                val w = 1f / (d2 + 1f)
                val gx = gradX(pixels, nch, filled, nr, nc, width, height)
                val gy = gradY(pixels, nch, filled, nr, nc, width, height)
                for (ch in 0 until nch) {
                    val predicted = pixels[ch][nidx] + gx[ch] * dc + gy[ch] * dr
                    sum[ch] += w * predicted.coerceIn(0f, 1f)
                }
                wsum += w
            }
        }
        if (wsum > 0f) {
            for (ch in 0 until nch) pixels[ch][idx] = (sum[ch] / wsum).coerceIn(0f, 1f)
            filled[idx] = true
            for (d2 in 0..3) {
                val nr = row + dy4[d2]; val nc = col + dx4[d2]
                if (nr !in 0 until height || nc !in 0 until width) continue
                val nidx = nr * width + nc
                if (isMasked[nidx] && !filled[nidx]) {
                    val newDist = dist[idx] + 1f
                    if (newDist < dist[nidx]) { dist[nidx] = newDist; pq.add(Pair(newDist, nidx)) }
                }
            }
        }
    }
    return pixelsToImage(pixels, width, height, image.channels)
}

private fun gradX(pixels: Array<FloatArray>, nch: Int, filled: BooleanArray, row: Int, col: Int, width: Int, height: Int): FloatArray {
    val hasL = col > 0        && filled[row * width + col - 1]
    val hasR = col < width-1  && filled[row * width + col + 1]
    return FloatArray(nch) { ch ->
        when {
            hasL && hasR -> (pixels[ch][row * width + col + 1] - pixels[ch][row * width + col - 1]) / 2f
            hasR         ->  pixels[ch][row * width + col + 1] - pixels[ch][row * width + col]
            hasL         ->  pixels[ch][row * width + col]     - pixels[ch][row * width + col - 1]
            else         ->  0f
        }
    }
}

private fun gradY(pixels: Array<FloatArray>, nch: Int, filled: BooleanArray, row: Int, col: Int, width: Int, height: Int): FloatArray {
    val hasA = row > 0        && filled[(row - 1) * width + col]
    val hasB = row < height-1 && filled[(row + 1) * width + col]
    return FloatArray(nch) { ch ->
        when {
            hasA && hasB -> (pixels[ch][(row + 1) * width + col] - pixels[ch][(row - 1) * width + col]) / 2f
            hasB         ->  pixels[ch][(row + 1) * width + col] - pixels[ch][row * width + col]
            hasA         ->  pixels[ch][row * width + col]       - pixels[ch][(row - 1) * width + col]
            else         ->  0f
        }
    }
}
