package ch.obermuhlner.kimage.core.math

fun Iterable<Float>.fastMedian(binCount: Int = 100): Float {
    val min = min()
    val max = max()
    return fastMedian(min, max, binCount)
}

fun Iterable<Float>.fastMedian(min: Float, max: Float, binCount: Int = 100): Float {
    if (!this.iterator().hasNext()) {
        return Float.NaN;
    }

    val histogram = Histogram(binCount)
    for(value in this) {
        histogram.add(((value - min) / (max - min)).toDouble())
    }
    return (histogram.estimateMedian() * (max - min) + min).toFloat()
}

fun Iterable<Double>.fastMedian(binCount: Int = 100): Double {
    val min = min()
    val max = max()
    return fastMedian(min, max, binCount)
}

fun Iterable<Double>.fastMedian(min: Double, max: Double, binCount: Int = 100): Double {
    if (!this.iterator().hasNext()) {
        return Double.NaN;
    }

    val histogram = Histogram(binCount)
    for(value in this) {
        histogram.add((value - min) / (max - min))
    }
    return histogram.estimateMedian() * (max - min) + min
}
