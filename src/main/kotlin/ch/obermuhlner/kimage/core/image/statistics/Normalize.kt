package ch.obermuhlner.kimage.core.image.statistics

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.math.average
import ch.obermuhlner.kimage.core.matrix.values.values

fun Image?.normalizeImage(): Image? {
    if (this == null) return null

    for (channel in this.channels) {
        val m = this[channel]
        val average = m.values().average()
        m.applyEach { v -> v / average }
    }

    return this
}
