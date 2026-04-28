package ch.obermuhlner.kimage.core.huge

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

class HugeMultiDimensionalFloatArrayTest {

    @Test
    fun `temp files are created in custom tempDir`() {
        val customTempDir = Files.createTempDirectory("kimage_test_tempdir").toFile()
        try {
            HugeMultiDimensionalFloatArray(2, 3, tempDir = customTempDir).use { arr ->
                val filesBefore = customTempDir.listFiles()!!
                assertTrue(filesBefore.isNotEmpty(), "Expected temp files in custom tempDir")
                assertTrue(filesBefore.all { it.name.startsWith("HugeFloatArray_") })

                // basic read/write sanity
                arr[0L] = 1.23f
                assertEquals(1.23f, arr[0L], 0.0001f)
            }
            // files deleted on close
            assertTrue(customTempDir.listFiles()!!.isEmpty(), "Expected temp files deleted after close()")
        } finally {
            customTempDir.deleteRecursively()
        }
    }

    @Test
    fun `drizzle with custom tempDir uses that directory for two-pass`() {
        val customTempDir = Files.createTempDirectory("kimage_test_drizzle").toFile()
        try {
            val config = ch.obermuhlner.kimage.core.image.stack.DrizzleConfig(
                rejection = ch.obermuhlner.kimage.core.image.stack.DrizzleRejection.SigmaClip,
            )

            val m = ch.obermuhlner.kimage.core.matrix.DoubleMatrix(4, 4) { _, _ -> 0.5 }
            val image = ch.obermuhlner.kimage.core.image.MatrixImage(4, 4,
                ch.obermuhlner.kimage.core.image.Channel.Red to m,
                ch.obermuhlner.kimage.core.image.Channel.Green to m,
                ch.obermuhlner.kimage.core.image.Channel.Blue to m
            )
            val identity = ch.obermuhlner.kimage.core.matrix.DoubleMatrix(3, 3) { r, c -> if (r == c) 1.0 else 0.0 }

            val frames = listOf({ image } to identity, { image } to identity)
            ch.obermuhlner.kimage.core.image.stack.drizzle(frames, config, tempDir = customTempDir)

            // all temp files cleaned up after drizzle completes
            assertTrue(customTempDir.listFiles()!!.isEmpty(), "Expected temp files cleaned up after drizzle")
        } finally {
            customTempDir.deleteRecursively()
        }
    }
}
