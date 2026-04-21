package ch.obermuhlner.kimage.core.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistogramTest {

    @Test
    fun `add increments bin count`() {
        val histogram = Histogram(10)
        histogram.add(5)
        assertEquals(1, histogram[5])
        assertEquals(1, histogram.n)
    }

    @Test
    fun `add clamps out of range values`() {
        val histogram = Histogram(10)
        histogram.add(-1)
        histogram.add(15)
        assertEquals(1, histogram[0])
        assertEquals(1, histogram[9])
    }

    @Test
    fun `remove decrements bin count`() {
        val histogram = Histogram(10)
        histogram.add(5)
        histogram.add(5)
        histogram.remove(5)
        assertEquals(1, histogram[5])
        assertEquals(1, histogram.n)
    }

    @Test
    fun `remove does not underflow when bin is zero`() {
        val histogram = Histogram(10)
        histogram.remove(5)
        assertEquals(0, histogram[5])
        assertEquals(0, histogram.n)
    }
}