package ch.obermuhlner.kimage.core.image

operator fun Image.plus(other: Image): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        this[channel] + other[channel]
    }
}

operator fun Image.minus(other: Image): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        this[channel] - other[channel]
    }
}

fun Image.elementPlus(value: Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        this[channel].elementPlus(value)
    }
}

fun Image.elementMinus(value: Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        this[channel].elementMinus(value)
    }
}

// pixel-wise multiplication
operator fun Image.times(other: Image): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        val m = this[channel].copy()
        m.applyEach { x, y, value ->
            value * other[channel][x, y]
        }
        m
    }
}

// pixel-wise division
operator fun Image.div(other: Image): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        val m = this[channel].copy()
        m.applyEach { x, y, value ->
            value / other[channel][x, y]
        }
        m
    }
}

operator fun Image.times(value: Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        this[channel] * value
    }
}

operator fun Image.div(value: Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        this[channel] / value
    }
}

operator fun Image.plus(value: Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        this[channel] elementPlus value
    }
}

operator fun Image.minus(value: Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        this[channel] elementMinus value
    }
}

fun Image.onEach(func: (value: Double) -> Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels
    ) { channel, _, _ ->
        val m = this[channel].copy()
        m.applyEach(func)
        m
    }
}
