package ch.obermuhlner.kimage.core.math

import ch.obermuhlner.kimage.core.matrix.Matrix;
import kotlin.math.min
import kotlin.ranges.IntRange;

class Histogram(private val binCount: Int = 256) {
    private val bins = IntArray(binCount)
    private var entryCount = 0

    val n get() = entryCount

    val indices: IntRange = bins.indices

    operator fun get(index: Int) = bins[index]

    fun clear() {
        for (i in indices) {
            bins[i] = 0
        }
        entryCount = 0
    }

    fun add(
        matrix:Matrix,
        rowOffset: Int = 0,
        colOffset: Int = 0,
        rows: Int = matrix.rows - rowOffset,
        cols: Int = matrix.cols - colOffset
    ) {
        for (row in rowOffset until rowOffset+rows) {
            for (col in colOffset until colOffset+cols) {
                add(matrix[row, col])
            }
        }
    }

    fun remove(
        matrix: Matrix,
        rowOffset: Int = 0,
        colOffset: Int = 0,
        rows: Int = matrix.rows - rowOffset,
        cols: Int = matrix.cols - colOffset
    ) {
        for (row in rowOffset until rowOffset+rows) {
            for (col in colOffset until colOffset+cols) {
                remove(matrix[row, col])
            }
        }
    }

    fun add(value: Double) {
        add((value * binCount).toInt())
    }

    fun remove(value: Double) {
        remove((value * binCount).toInt())
    }

    fun add(value: Int) {
        val index = clamp(value, 0, binCount-1)
        bins[index]++
        entryCount++
    }

    fun remove(value: Int) {
        val index = clamp(value, 0, binCount-1)
        bins[index]--
        entryCount--
    }

    fun max(ignoreMinMaxBins: Boolean = false): Int {
        val range = if (ignoreMinMaxBins) {
            1 until bins.size-1
        } else {
            indices
        }
        return range.maxOf { this[it] }
    }

    fun estimateMean(): Double {
        var sum = 0.0
        for (i in indices) {
            sum += bins[i] * (i.toDouble() + 0.5) / (binCount - 1).toDouble()
        }
        return sum / entryCount
    }

    fun estimateMedian(): Double = estimatePercentile(0.5)

    fun estimatePercentile(percentile: Double): Double {
        val nPercentile = (entryCount * percentile).toInt()
        var cumulativeN = 0
        for (i in indices) {
            if (cumulativeN + bins[i] >= nPercentile) {
                val lowerLimit = i.toDouble() / (binCount - 1).toDouble()
                val width = 1.0 / (binCount - 1).toDouble()
                return if (bins[i] == 0) {
                    0.0
                } else {
                    lowerLimit + (nPercentile - cumulativeN) / bins[i].toDouble() * width
                }
            }
            cumulativeN += bins[i]
        }
        return 0.0
    }

    fun print(chartWidth: Int = 60, ignoreMinMaxBins: Boolean = false) {
        val max = max(ignoreMinMaxBins)
        for (i in indices) {
            val length = min(chartWidth, (chartWidth.toDouble() * this[i] / max).toInt())
            val line = String.format("%3d : %10d %s", i, this[i], "#".repeat(length))
            println(line)
        }
    }
}