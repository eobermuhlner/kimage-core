package ch.obermuhlner.kimage.core.huge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class HugeMultiDimensionalDoubleArrayTest {

    @Test
    fun `basic read and write round-trips without precision loss`() {
        HugeMultiDimensionalDoubleArray(3, 4).use { arr ->
            val value = 1.23456789012345
            arr[0L] = value
            assertEquals(value, arr[0L], 0.0)
        }
    }

    @Test
    fun `multi-dimensional indexing works correctly`() {
        HugeMultiDimensionalDoubleArray(2, 3, 4).use { arr ->
            arr[1, 2, 3] = 99.5
            assertEquals(99.5, arr[1, 2, 3], 0.0)
            assertEquals(0.0, arr[0, 0, 0], 0.0)
        }
    }

    @Test
    fun `temp files are created in custom tempDir`() {
        val customTempDir = Files.createTempDirectory("kimage_double_test_tempdir").toFile()
        try {
            HugeMultiDimensionalDoubleArray(2, 3, tempDir = customTempDir).use { arr ->
                val files = customTempDir.listFiles()!!
                assertTrue(files.isNotEmpty(), "Expected temp files in custom tempDir")
                assertTrue(files.all { it.name.startsWith("HugeDoubleArray_") })

                arr[0L] = 3.14159265358979
                assertEquals(3.14159265358979, arr[0L], 0.0)
            }
            assertTrue(customTempDir.listFiles()!!.isEmpty(), "Expected temp files deleted after close()")
        } finally {
            customTempDir.deleteRecursively()
        }
    }

    @Test
    fun `single-buffer fast path works for small arrays`() {
        // Arrays under ~1GB use a single buffer - verify correctness
        HugeMultiDimensionalDoubleArray(10, 10, 10).use { arr ->
            for (i in 0 until 1000) {
                arr[i.toLong()] = i.toDouble() * 0.001
            }
            for (i in 0 until 1000) {
                assertEquals(i.toDouble() * 0.001, arr[i.toLong()], 0.0)
            }
        }
    }

    @Test
    fun `stores double precision that would be lost in float`() {
        // A value that can be represented exactly in double but not float
        val preciseValue = 1.0000000000001  // beyond float precision
        HugeMultiDimensionalDoubleArray(5).use { arr ->
            arr[0L] = preciseValue
            val retrieved = arr[0L]
            // Float would round this to 1.0; double should preserve it
            assertTrue(retrieved != 1.0, "Double precision should distinguish from 1.0")
            assertEquals(preciseValue, retrieved, 1e-15)
        }
    }
}
