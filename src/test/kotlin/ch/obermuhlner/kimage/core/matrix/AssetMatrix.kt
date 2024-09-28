package ch.obermuhlner.kimage.core.matrix

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertEquals

fun assertMatrixEquals(expected: Matrix, actual: Matrix) {
    assertTrue(expected.contentEquals(actual)) { "expected ${expected.contentToString()} but was ${actual.contentToString()}" }
}