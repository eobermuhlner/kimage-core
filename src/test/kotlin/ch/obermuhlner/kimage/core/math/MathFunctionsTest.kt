package ch.obermuhlner.kimage.core.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

class MathFunctionsTest {

    @Test
    fun `sigmoid output varies by x`() {
        val low = sigmoid(0.0)
        val mid = sigmoid(0.5)
        val high = sigmoid(1.0)

        assertNotEquals(low, high, "sigmoid(0.0) and sigmoid(1.0) must differ")
        assert(low < mid) { "sigmoid should increase: sigmoid(0.0)=$low must be < sigmoid(0.5)=$mid" }
        assert(mid < high) { "sigmoid should increase: sigmoid(0.5)=$mid must be < sigmoid(1.0)=$high" }
    }

    @Test
    fun `sigmoid midpoint is 0_5 by default`() {
        assertEquals(0.5, sigmoid(0.5), 1e-9)
    }

    @Test
    fun `sigmoid is monotonically increasing`() {
        val values = listOf(0.0, 0.1, 0.3, 0.5, 0.7, 0.9, 1.0)
        val results = values.map { sigmoid(it) }
        for (i in 0 until results.size - 1) {
            assert(results[i] < results[i + 1]) {
                "sigmoid should be monotonically increasing but sigmoid(${values[i]})=${results[i]} >= sigmoid(${values[i+1]})=${results[i+1]}"
            }
        }
    }

    @Test
    fun `medianAbsoluteDeviation of symmetric values`() {
        // Values: 1, 2, 3, 4, 5  →  median=3, deviations=2,1,0,1,2  →  MAD=1
        val mad = listOf(1.0, 2.0, 3.0, 4.0, 5.0).medianAbsoluteDeviation()
        assertEquals(1.0, mad, 1e-9)
    }

    @Test
    fun `medianAbsoluteDeviation of constant values is zero`() {
        val mad = listOf(5.0, 5.0, 5.0, 5.0).medianAbsoluteDeviation()
        assertEquals(0.0, mad, 1e-9)
    }

    @Test
    fun `medianAbsoluteDeviation of single outlier`() {
        // Values: 1, 1, 1, 1, 100  →  median=1, deviations=0,0,0,0,99  →  MAD=0
        val mad = listOf(1.0, 1.0, 1.0, 1.0, 100.0).medianAbsoluteDeviation()
        assertEquals(0.0, mad, 1e-9)
    }

    @Test
    fun `medianAbsoluteDeviation result is non-negative`() {
        val values = listOf(3.0, 1.0, 4.0, 1.0, 5.0, 9.0, 2.0, 6.0)
        assert(values.medianAbsoluteDeviation() >= 0.0)
    }

    @Test
    fun `smoothstep handles x0 equals x1`() {
        val result = smoothstep(1.0, 1.0, 0.5)
        assertEquals(0.0, result, 1e-9)
    }

    @Test
    fun `smoothstep returns 1 when a greater than x1 equals x0`() {
        val result = smoothstep(1.0, 1.0, 2.0)
        assertEquals(1.0, result, 1e-9)
    }

    @Test
    fun `smootherstep handles x0 equals x1`() {
        val result = smootherstep(1.0, 1.0, 0.5)
        assertEquals(0.0, result, 1e-9)
    }

    @Test
    fun `smootherstep returns 1 when a greater than x1 equals x0`() {
        val result = smootherstep(1.0, 1.0, 2.0)
        assertEquals(1.0, result, 1e-9)
    }
}
