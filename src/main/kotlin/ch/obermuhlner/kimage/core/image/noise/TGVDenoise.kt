package ch.obermuhlner.kimage.core.image.noise

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.FloatMatrix
import kotlin.math.sqrt

/**
 * TGV2 (Total Generalized Variation, order 2) denoising via the Chambolle-Pock
 * primal-dual algorithm (Bredies et al. 2010).
 *
 * Minimizes: (lambda/2)||u - f||^2 + alpha1*||∇u - p||_{1,2} + alpha0*||ε(p)||_{1,F}
 *
 * @param lambda   Data fidelity weight. Higher = less smoothing. Default 100.0.
 * @param alpha0   TGV weight for the second-order (curvature) term. Default 1.0.
 * @param alpha1   TGV weight for the first-order (gradient) term. Default 2.0.
 * @param iterations Number of Chambolle-Pock iterations. Default 100.
 */
fun Image.tgvDenoise(
    lambda: Double = 100.0,
    alpha0: Double = 1.0,
    alpha1: Double = 2.0,
    iterations: Int = 100,
): Image {
    return MatrixImage(width, height, channels) { channel, _, _ ->
        val f = this[channel]
        val h = f.rows
        val w = f.cols
        val n = h * w

        // Chambolle-Pock step sizes for the TGV2 operator (spectral norm <= sqrt(12))
        val stepSize = 1.0 / sqrt(12.0)

        // Primal variables
        val u = DoubleArray(n) { f[it] }
        val uBar = DoubleArray(n) { f[it] }
        val px = DoubleArray(n)
        val py = DoubleArray(n)
        val pxBar = DoubleArray(n)
        val pyBar = DoubleArray(n)

        // Dual variables: y1 for (∇u - p), y2 for ε(p) (symmetric gradient of p)
        val y1x = DoubleArray(n)
        val y1y = DoubleArray(n)
        val y2xx = DoubleArray(n)
        val y2xy = DoubleArray(n)
        val y2yy = DoubleArray(n)

        val uNew = DoubleArray(n)
        val pxNew = DoubleArray(n)
        val pyNew = DoubleArray(n)

        for (iter in 0 until iterations) {
            // Dual y1 update: y1 = proj_{alpha1}(y1 + sigma*(∇u_bar - p_bar))
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val i = row * w + col
                    val guX = if (col < w - 1) uBar[i + 1] - uBar[i] else 0.0
                    val guY = if (row < h - 1) uBar[i + w] - uBar[i] else 0.0
                    val v1x = y1x[i] + stepSize * (guX - pxBar[i])
                    val v1y = y1y[i] + stepSize * (guY - pyBar[i])
                    val norm = sqrt(v1x * v1x + v1y * v1y)
                    val scale = if (norm > alpha1) alpha1 / norm else 1.0
                    y1x[i] = v1x * scale
                    y1y[i] = v1y * scale
                }
            }

            // Dual y2 update: y2 = proj_{alpha0}(y2 + sigma*ε(p_bar))
            // ε(p) = (∂x px, (∂y px + ∂x py)/2, ∂y py)
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val i = row * w + col
                    val exx = if (col < w - 1) pxBar[i + 1] - pxBar[i] else 0.0
                    val eyy = if (row < h - 1) pyBar[i + w] - pyBar[i] else 0.0
                    val dpxY = if (row < h - 1) pxBar[i + w] - pxBar[i] else 0.0
                    val dpyX = if (col < w - 1) pyBar[i + 1] - pyBar[i] else 0.0
                    val exy = (dpxY + dpyX) * 0.5
                    val v2xx = y2xx[i] + stepSize * exx
                    val v2xy = y2xy[i] + stepSize * exy
                    val v2yy = y2yy[i] + stepSize * eyy
                    // Frobenius norm for symmetric 2x2: sqrt(a^2 + 2*b^2 + c^2)
                    val normF = sqrt(v2xx * v2xx + 2.0 * v2xy * v2xy + v2yy * v2yy)
                    val scale = if (normF > alpha0) alpha0 / normF else 1.0
                    y2xx[i] = v2xx * scale
                    y2xy[i] = v2xy * scale
                    y2yy[i] = v2yy * scale
                }
            }

            // Primal u update: u_new = (u + tau*div(y1) + tau*lambda*f) / (1 + tau*lambda)
            // div(y1) uses backward differences (adjoint of forward-diff gradient)
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val i = row * w + col
                    val dy1x = y1x[i] - (if (col > 0) y1x[i - 1] else 0.0)
                    val dy1y = y1y[i] - (if (row > 0) y1y[i - w] else 0.0)
                    uNew[i] = (u[i] + stepSize * (dy1x + dy1y) + stepSize * lambda * f[i]) /
                              (1.0 + stepSize * lambda)
                }
            }

            // Primal p update: p_new = p + tau*(y1 + div_sym(y2))
            // div_sym(y2).x = backward_x(y2xx) + backward_y(y2xy)
            // div_sym(y2).y = backward_x(y2xy) + backward_y(y2yy)
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val i = row * w + col
                    val bxY2xx = y2xx[i] - (if (col > 0) y2xx[i - 1] else 0.0)
                    val byY2xy = y2xy[i] - (if (row > 0) y2xy[i - w] else 0.0)
                    val bxY2xy = y2xy[i] - (if (col > 0) y2xy[i - 1] else 0.0)
                    val byY2yy = y2yy[i] - (if (row > 0) y2yy[i - w] else 0.0)
                    pxNew[i] = px[i] + stepSize * (y1x[i] + bxY2xx + byY2xy)
                    pyNew[i] = py[i] + stepSize * (y1y[i] + bxY2xy + byY2yy)
                }
            }

            // Over-relaxation: u_bar = 2*u_new - u_old
            for (i in 0 until n) {
                uBar[i] = 2.0 * uNew[i] - u[i]
                pxBar[i] = 2.0 * pxNew[i] - px[i]
                pyBar[i] = 2.0 * pyNew[i] - py[i]
                u[i] = uNew[i]
                px[i] = pxNew[i]
                py[i] = pyNew[i]
            }
        }

        FloatMatrix(h, w) { row, col -> u[row * w + col].coerceIn(0.0, 1.0).toFloat() }
    }
}
