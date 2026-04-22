package ch.obermuhlner.kimage.astro.cosmetic

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.math.stddev
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.values.values
import kotlin.math.abs
import kotlin.math.max

enum class CosmeticCorrectionMode {
    Hot,
    Cold,
    Both
}

data class CosmeticCorrectionConfig(
    var enabled: Boolean = true,
    var mode: CosmeticCorrectionMode = CosmeticCorrectionMode.Both,
    var sigmaThreshold: Double = 5.0,
    var checkRadius: Int = 2,
    var fixRadius: Int = 1,
    var minNetNoise: Double = 0.01
)

fun Image.cosmeticCorrect(
    config: CosmeticCorrectionConfig = CosmeticCorrectionConfig()
): Image {
    if (!config.enabled) {
        return this
    }

    val channelList = this.channels.toList()
    return MatrixImage(this.width, this.height, channelList) { channel, _, _ ->
        val channelMatrix = this[channel]
        channelMatrix.cosmeticCorrectChannel(
            mode = config.mode,
            sigmaThreshold = config.sigmaThreshold,
            checkRadius = config.checkRadius,
            fixRadius = config.fixRadius,
            minNetNoise = config.minNetNoise
        )
    }
}

fun Matrix.cosmeticCorrectChannel(
    mode: CosmeticCorrectionMode = CosmeticCorrectionMode.Both,
    sigmaThreshold: Double = 5.0,
    checkRadius: Int = 2,
    fixRadius: Int = 1,
    minNetNoise: Double = 0.01
): Matrix {
    val globalStdDev = this.values().stddev()
    val effectiveSigma = max(globalStdDev, minNetNoise)

    val referenceMatrix = DoubleMatrix(this.rows, this.cols)
    for (row in 0 until this.rows) {
        for (col in 0 until this.cols) {
            var sum = 0.0
            var count = 0
            for (dy in -fixRadius..fixRadius) {
                for (dx in -fixRadius..fixRadius) {
                    if (dx == 0 && dy == 0) continue
                    val r = row + dy
                    val c = col + dx
                    if (r >= 0 && r < this.rows && c >= 0 && c < this.cols) {
                        sum += this[r, c]
                        count++
                    }
                }
            }
            referenceMatrix[row, col] = if (count > 0) sum / count else this[row, col]
        }
    }

    val result = this.copy()

    val rows = this.rows
    val cols = this.cols

    val isHot = mode == CosmeticCorrectionMode.Hot || mode == CosmeticCorrectionMode.Both
    val isCold = mode == CosmeticCorrectionMode.Cold || mode == CosmeticCorrectionMode.Both

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val originalValue = this[row, col]
            val referenceValue = referenceMatrix[row, col]
            val deviation: Double = originalValue - referenceValue
            val absDeviation = abs(deviation)

            if (absDeviation > effectiveSigma * sigmaThreshold) {
                val isHotPixel = isHot && deviation > 0.0
                val isColdPixel = isCold && deviation < 0.0

                if (isHotPixel || isColdPixel) {
                    result[row, col] = referenceValue
                }
            }
        }
    }

    return result
}