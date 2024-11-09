package ch.obermuhlner.kimage.core.math

import kotlin.math.*

fun Float.finiteOrElse(default: Float = 0f) = if (this.isFinite()) this else default

fun Double.finiteOrElse(default: Double = 0.0) = if (this.isFinite()) this else default

fun Double.decimalExponent(): Int {
    return floor(log10(abs(this))).toInt()
}

fun Double.decimalMantissaExponent(): Pair<Double, Int> {
    val exponent = this.decimalExponent()
    val mantissa = this / 10.0.pow(exponent.toDouble())
    return Pair(mantissa, exponent)
}

fun decimalMantissaExponentToDouble(mantissa: Double, exponent: Int): Double {
    return mantissa * 10.0.pow(exponent.toDouble())
}

fun clamp(x: Double, min: Double, max: Double): Double {
    return when {
        x < min -> min
        x > max -> max
        else -> x
    }
}

fun clamp(x: Float, min: Float, max: Float): Float {
    return when {
        x < min -> min
        x > max -> max
        else -> x
    }
}

fun clamp(x: Int, min: Int, max: Int): Int {
    return when {
        x < min -> min
        x > max -> max
        else -> x
    }
}

fun smoothstep(x0: Double, x1: Double, a: Double): Double {
    val x = clamp((a - x0) / (x1 - x0), 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x);
}

fun smootherstep(x0: Double, x1: Double, a: Double): Double {
    val x = clamp((a - x0) / (x1 - x0), 0.0, 1.0)
    return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
}

fun mixLinear(x0: Double, x1: Double, a: Double): Double {
    return (1.0 - a) * x0 + a * x1
}

fun mixLinear(x0: Float, x1: Float, a: Float): Float {
    return (1.0f - a) * x0 + a * x1
}

fun mixBilinear(x00: Double, x01: Double, x10: Double, x11: Double, ax: Double, ay: Double): Double {
    return mixLinear(mixLinear(x00, x10, ax), mixLinear(x01, x11, ax), ay)
}

fun mixCubicHermite(x00: Double, x01: Double, x10: Double, x11: Double, t: Double): Double {
    val a = -x00 / 2.0 + 3.0 * x01 / 2.0 - 3.0 * x10 / 2.0 + x11 / 2.0
    val b = x00 - 5.0 * x01 / 2.0 + 2.0 * x10 - x11 / 2.0
    val c = -x00 / 2.0 + x10 / 2.0
    return a * t * t * t + b * t * t + c * t + x01
}

fun sigmoid(x: Double, midpoint: Double = 0.5, strength: Double = 2.0): Double {
    return (1 / (1 + exp(-strength * (strength - midpoint))))
}

fun sigmoidLike(x: Double, midpoint: Double, strength: Double): Double {
    val xCorrected = x.coerceIn(0.0, 1.0)
    val midpointCorrected = midpoint.coerceIn(0.0, 1.0)
    require(strength > 0) { "strength must be positive" }

    val leftTerm = (xCorrected / midpointCorrected).pow(strength)
    val rightTerm = ((1 - xCorrected) / (1 - midpointCorrected)).pow(strength)

    return leftTerm / (leftTerm + rightTerm)
}

private fun <T, U> Iterator<T>.reduceAndCount(initial: U, empty: U, accumulator: (U, T) -> U): Pair<U, Int> {
    var result = initial
    var count = 0

    for (value in this) {
        if (value != null) {
            result = accumulator.invoke(result, value)
            count++
        }
    }

    return if (count == 0) Pair(empty, 0) else Pair(result, count)
}

fun <T, U> Iterator<T>.reduce(initial: U, empty: U, accumulator: (U, T) -> U): U {
    return reduceAndCount(initial, empty, accumulator).first
}

fun Iterator<Float>.min(): Float {
    return reduce(Float.MAX_VALUE, Float.NaN) { a, b -> kotlin.math.min(a, b) }
}

fun Iterator<Double>.min(): Double {
    return reduce(Double.MAX_VALUE, Double.NaN) { a, b -> kotlin.math.min(a, b) }
}

fun FloatArray.min(offset: Int = 0, length: Int = size-offset): Float {
    return ArrayFloatIterator(this, offset, length).min()
}

fun DoubleArray.min(offset: Int = 0, length: Int = size-offset): Double {
    return ArrayDoubleIterator(this, offset, length).min()
}

fun Iterable<Float>.min(): Float {
    return iterator().min()
}

fun Iterable<Double>.min(): Double {
    return iterator().min()
}

fun Iterator<Float>.max(): Float {
    return reduce(Float.MIN_VALUE, Float.NaN) { a, b -> kotlin.math.max(a, b) }
}

fun Iterator<Double>.max(): Double {
    return reduce(Double.MIN_VALUE, Double.NaN) { a, b -> kotlin.math.max(a, b) }
}

fun FloatArray.max(offset: Int = 0, length: Int = size-offset): Float {
    return ArrayFloatIterator(this, offset, length).max()
}

fun DoubleArray.max(offset: Int = 0, length: Int = size-offset): Double {
    return ArrayDoubleIterator(this, offset, length).max()
}

fun Iterable<Float>.max(): Float {
    return iterator().max()
}

fun Iterable<Double>.max(): Double {
    return iterator().max()
}

private fun Iterator<Float>.sumAndCountFloat(): Pair<Float, Int> {
    return reduceAndCount(0f, Float.NaN) { a, b -> a + b }
}

fun Iterator<Double?>.sumAndCountDouble(): Pair<Double, Int> {
    return reduceAndCount(0.0, Double.NaN) { a, b -> a + b!! }
}

fun Iterator<Float>.sum(): Float {
    return sumAndCountFloat().first
}

fun Iterator<Double>.sum(): Double {
    return sumAndCountDouble().first
}

fun Iterable<Float>.sum(): Float {
    return iterator().sum()
}

fun Iterable<Double>.sum(): Double {
    return iterator().sum()
}

fun FloatArray.sum(offset: Int = 0, length: Int = size-offset): Float {
    return ArrayFloatIterator(this, offset, length).sum()
}

fun DoubleArray.sum(offset: Int = 0, length: Int = size-offset): Double {
    return ArrayDoubleIterator(this, offset, length).sum()
}

fun Iterator<Float>.average(): Float {
    val (sum, count) = sumAndCountFloat()
    return sum / count
}

fun Iterator<Double?>.average(): Double {
    val (sum, count) = sumAndCountDouble()
    return sum / count
}

fun Iterable<Float>.average(): Float {
    return iterator().average()
}

fun Iterable<Double?>.average(): Double {
    return iterator().average()
}

fun FloatArray.average(offset: Int = 0, length: Int = size-offset): Float {
    return ArrayFloatIterator(this, offset, length).sum() / length
}

enum class StandardDeviation { Population, Sample }

fun Iterable<Float>.stddev(type: StandardDeviation = StandardDeviation.Population): Float {
    val (sum, count) = iterator().sumAndCountFloat()

    when (count) {
        0 -> return Float.NaN
        1 -> return 0f
    }

    val avg = sum / count

    var sumDeltaSquare = 0f
    for (value in iterator()) {
        val delta = value - avg
        sumDeltaSquare += delta * delta
    }

    val denom = when (type) {
        StandardDeviation.Population -> count
        StandardDeviation.Sample -> count - 1
    }
    return sqrt(sumDeltaSquare / denom)
}

fun Iterable<Double>.stddev(type: StandardDeviation = StandardDeviation.Population): Double {
    val (sum, count) = iterator().sumAndCountDouble()

    when (count) {
        0 -> return Double.NaN
        1 -> return 0.0
    }

    val avg = sum / count

    var sumDeltaSquare = 0.0
    for (value in iterator()) {
        val delta = value - avg
        sumDeltaSquare += delta * delta
    }

    val denom = when (type) {
        StandardDeviation.Population -> count
        StandardDeviation.Sample -> count - 1
    }
    return sqrt(sumDeltaSquare / denom)
}

fun FloatArray.stddev(type: StandardDeviation = StandardDeviation.Population, offset: Int = 0, length: Int = size-offset): Float {
    return ArrayFloatIterable(this, offset, length).stddev(type)
}

fun DoubleArray.stddev(type: StandardDeviation = StandardDeviation.Population, offset: Int = 0, length: Int = size-offset): Double {
    return ArrayDoubleIterable(this, offset, length).stddev(type)
}

fun FloatArray.medianInplace(offset: Int = 0, length: Int = size-offset): Float {
    if (length == 0) {
        return Float.NaN
    }

    sort(offset, offset+length)

    val half = offset + length / 2
    return if (length % 2 == 0) {
        (this[half-1] + this[half]) / 2
    } else {
        this[half]
    }
}

fun DoubleArray.medianInplace(offset: Int = 0, length: Int = size-offset): Double {
    if (length == 0) {
        return Double.NaN
    }

    sort(offset, offset+length)

    val half = offset + length / 2
    return if (length % 2 == 0) {
        (this[half-1] + this[half]) / 2
    } else {
        this[half]
    }
}

fun MutableList<Float>.medianInplace(offset: Int = 0, length: Int = size-offset): Float {
    if (length == 0) {
        return Float.NaN
    }

    subList(offset, offset+length).sort()

    val half = offset + length / 2
    return if (length % 2 == 0) {
        (this[half-1] + this[half]) / 2
    } else {
        this[half]
    }
}

fun MutableList<Double>.medianInplace(offset: Int = 0, length: Int = size-offset): Double {
    if (length == 0) {
        return Double.NaN
    }

    subList(offset, offset+length).sort()

    val half = offset + length / 2
    return if (length % 2 == 0) {
        (this[half-1] + this[half]) / 2
    } else {
        this[half]
    }
}

fun FloatArray.median(offset: Int = 0, length: Int = size-offset): Float {
    if (length == 0) {
        return Float.NaN
    }

    val array = copyOfRange(offset, offset+length)
    return array.medianInplace()
}

fun DoubleArray.median(offset: Int = 0, length: Int = size-offset): Double {
    if (length == 0) {
        return Double.NaN
    }

    val array = copyOfRange(offset, offset+length)
    return array.medianInplace()
}

fun Iterable<Float>.median(): Float {
    return toMutableList().medianInplace()
}

fun Iterable<Double>.median(): Double {
    return toMutableList().medianInplace()
}

fun Iterable<Double>.medianAbsoluteDeviation(): Double {
    val m = median()
    val list = toMutableList()
    for (i in list.indices) {
        list[i] = abs(list[i] - m)
    }
    return list.onEach { abs(it - m) }.medianInplace()
}

fun Iterable<Double>.medianAndMedianAbsoluteDeviation(): Pair<Double, Double> {
    val m = median()
    val list = toMutableList()
    for (i in list.indices) {
        list[i] = abs(list[i] - m)
    }
    val mad = list.medianInplace()
    return Pair(m, mad)
}

fun FloatArray.weightedAverage(weightFunction: (i: Int, value: Float) -> Float, offset: Int = 0, length: Int = size-offset): Float {
    val weights = FloatArray(length)
    var totalWeight = 0.0f
    for (i in 0 until length) {
        val weight = weightFunction.invoke(i, this[i+offset])
        weights[i] = weight
        totalWeight += weight
    }

    var result = 0.0f
    for (i in 0 until length) {
        result += this[i+offset] * weights[i] / totalWeight
    }
    return result
}

fun DoubleArray.weightedAverage(weightFunction: (i: Int, value: Double) -> Double, offset: Int = 0, length: Int = size-offset): Double {
    val weights = DoubleArray(length)
    var totalWeight = 0.0
    for (i in 0 until length) {
        val weight = weightFunction.invoke(i, this[i+offset])
        weights[i] = weight
        totalWeight += weight
    }

    var result = 0.0
    for (i in 0 until length) {
        result += this[i+offset] * weights[i] / totalWeight
    }
    return result
}

private class ArrayFloatIterator(private val array: FloatArray, private val offset: Int, private val length: Int) : FloatIterator() {
    private var index = offset
    override fun hasNext() = index < offset + length
    override fun nextFloat() = try { array[index++] } catch (e: ArrayIndexOutOfBoundsException) { index -= 1; throw NoSuchElementException(e.message) }
}

private class ArrayDoubleIterator(private val array: DoubleArray, private val offset: Int, private val length: Int) : DoubleIterator() {
    private var index = offset
    override fun hasNext() = index < offset + length
    override fun nextDouble() = try { array[index++] } catch (e: ArrayIndexOutOfBoundsException) { index -= 1; throw NoSuchElementException(e.message) }
}

private class ArrayFloatIterable(private val array: FloatArray, private val offset: Int, private val length: Int) : Iterable<Float> {
    override fun iterator(): Iterator<Float> = ArrayFloatIterator(array, offset, length)
}

private class ArrayDoubleIterable(private val array: DoubleArray, private val offset: Int, private val length: Int) : Iterable<Double> {
    override fun iterator(): Iterator<Double> = ArrayDoubleIterator(array, offset, length)
}
