package ch.obermuhlner.kimage.core.image.values

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.values.values
import java.util.NoSuchElementException

fun Image.applyEach(func: (value: Double) -> Double) {
    for (channel in channels) {
        val m = this[channel]
        m.applyEach(func)
    }
}

fun Image.onEach(func: (value: Double) -> Double): Image {
    return MatrixImage(
        this.width,
        this.height,
        this.channels) { channel, _, _ ->
            val m = this[channel].copy()
            m.applyEach(func)
            m
        }
}

fun Image.values(channels: List<Channel> = this.channels): Iterable<Double> =
    ImageValueIterable(this, channels)

private class CompositeIterator<T>(iterators: List<Iterator<T>>): Iterator<T> {
    val iterators = iterators.filter { it.hasNext() } .toMutableList()

    override fun hasNext(): Boolean = iterators.isNotEmpty()

    override fun next(): T {
        if (!hasNext()) {
            throw NoSuchElementException()
        }

        val element = iterators.first().next()

        if (!iterators.first().hasNext()) {
            iterators.removeFirst()
        }

        return element
    }
}

private class ImageValueIterable(private val image: Image, private val channels: List<Channel>) : Iterable<Double> {
    override fun iterator(): Iterator<Double> = CompositeIterator(channels.map { image[it].values().iterator() })
}
