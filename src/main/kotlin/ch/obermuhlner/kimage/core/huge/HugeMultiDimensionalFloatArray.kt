package ch.obermuhlner.kimage.core.huge

import java.io.File
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import sun.misc.Unsafe

/**
 * Memory-mapped implementation of [MultiDimensionalFloatArray].
 *
 * Data is stored in one or more temporary files using Java NIO memory-mapped I/O so that
 * arrays larger than the JVM heap can be handled without OOM.  The OS page cache provides
 * transparent read-ahead; no heap beyond the buffer-descriptor array is required.
 *
 * **Float precision:** values are stored as 32-bit floats.  The interface accepts and returns
 * `Float`, matching [MultiDimensionalFloatArray].  Callers converting from `Double` (e.g. image
 * pixel values) will lose roughly 8 significant decimal digits relative to a `Double` store.
 * This is acceptable for 8-/16-bit image sources but may introduce visible error for 32-bit
 * float FITS inputs.
 *
 * @param dimensions logical dimensions of the N-D array (product must be representable as `Long`)
 * @param tempDir    directory in which temporary backing files are created; defaults to the
 *                   system temporary directory when `null`
 */
class HugeMultiDimensionalFloatArray(vararg dimensions: Int, tempDir: File? = null) : MultiDimensionalFloatArray(*dimensions) {

    private val maxBufferSize = Integer.MAX_VALUE / 4

    private val elementSize = 4 // float has 4 bytes

    private val memoryFiles: MutableList<File> = mutableListOf()
    private val fileChannels: MutableList<FileChannel> = mutableListOf()
    private val mappedBuffers: MutableList<MappedByteBuffer> = mutableListOf()
    private val floatBuffers: Array<FloatBuffer>
    private val bufferSize: Long

    init {
        val n = dimensions.map(Int::toLong).reduce(Long::times)

        val bufferCount = (n / maxBufferSize).toInt() + 1 // at least one buffer
        bufferSize = n / bufferCount + 1 // prevent rounding down and missing 1 element
        val bufferByteSize = bufferSize * elementSize
        floatBuffers = Array(bufferCount) {
            val file = if (tempDir != null) {
                Files.createTempFile(tempDir.toPath(), "HugeFloatArray_${it}_", ".mem").toFile()
            } else {
                File.createTempFile("HugeFloatArray_${it}_", ".mem")
            }
            memoryFiles.add(file)
            file.deleteOnExit()

            val channel = Files.newByteChannel(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE) as FileChannel
            fileChannels.add(channel)
            val mapped = channel.map(FileChannel.MapMode.READ_WRITE, 0, bufferByteSize)
            mappedBuffers.add(mapped)

            mapped.asFloatBuffer()
        }
    }

    override operator fun get(index: Long): Float {
        return if (floatBuffers.size == 1) {
            floatBuffers[0][index.toInt()]
        } else {
            val bufferIndex = (index / bufferSize).toInt()
            val bufferModuloIndex = (index % bufferSize).toInt()
            floatBuffers[bufferIndex][bufferModuloIndex]
        }
    }

    override operator fun set(index: Long, value: Float) {
        if (floatBuffers.size == 1) {
            floatBuffers[0].put(index.toInt(), value)
        } else {
            val bufferIndex = (index / bufferSize).toInt()
            val bufferModuloIndex = (index % bufferSize).toInt()
            floatBuffers[bufferIndex].put(bufferModuloIndex, value)
        }
    }

    override fun close() {
        try {
            // Force-unmap via Unsafe so that on Windows the file handle is released and
            // the temp file can be deleted immediately (mapped files cannot be deleted
            // on Windows while any mapping remains active).
            val unsafe = unsafeInstance
            mappedBuffers.forEach { buf ->
                runCatching { unsafe?.invokeCleaner(buf) }
            }
            fileChannels.forEach { runCatching { it.close() } }
        } finally {
            memoryFiles.forEach { it.delete() }
        }
    }

    companion object {
        private val unsafeInstance: Unsafe? = runCatching {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }.getOrNull()
    }
}
