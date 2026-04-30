package ch.obermuhlner.kimage.core.matrix.filter

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun nextPow2(n: Int): Int {
    var p = 1
    while (p < n) p = p shl 1
    return p
}

// In-place radix-2 Cooley-Tukey FFT. Size must be a power of 2.
internal fun fft1D(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
    val n = re.size
    require(n and (n - 1) == 0) { "FFT size must be power of 2, got $n" }

    // Bit-reversal permutation
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
        j = j xor bit
        if (i < j) {
            var tmp = re[i]; re[i] = re[j]; re[j] = tmp
            tmp = im[i]; im[i] = im[j]; im[j] = tmp
        }
    }

    // Butterfly stages
    var len = 2
    while (len <= n) {
        val angle = (if (inverse) 2.0 else -2.0) * PI / len
        val wRe = cos(angle)
        val wIm = sin(angle)
        var i = 0
        while (i < n) {
            var curRe = 1.0
            var curIm = 0.0
            for (k in 0 until len / 2) {
                val uRe = re[i + k]
                val uIm = im[i + k]
                val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                re[i + k] = uRe + vRe
                im[i + k] = uIm + vIm
                re[i + k + len / 2] = uRe - vRe
                im[i + k + len / 2] = uIm - vIm
                val newCurRe = curRe * wRe - curIm * wIm
                curIm = curRe * wIm + curIm * wRe
                curRe = newCurRe
            }
            i += len
        }
        len = len shl 1
    }

    if (inverse) {
        for (i in re.indices) { re[i] /= n; im[i] /= n }
    }
}

// In-place 2D FFT via row-column decomposition.
internal fun fft2D(re: Array<DoubleArray>, im: Array<DoubleArray>, inverse: Boolean = false) {
    val rows = re.size
    val cols = re[0].size
    for (row in 0 until rows) fft1D(re[row], im[row], inverse)
    val colRe = DoubleArray(rows)
    val colIm = DoubleArray(rows)
    for (col in 0 until cols) {
        for (row in 0 until rows) { colRe[row] = re[row][col]; colIm[row] = im[row][col] }
        fft1D(colRe, colIm, inverse)
        for (row in 0 until rows) { re[row][col] = colRe[row]; im[row][col] = colIm[row] }
    }
}
