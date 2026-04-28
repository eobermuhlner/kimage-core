package ch.obermuhlner.kimage.core.huge

import java.io.File
import java.nio.DoubleBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import sun.misc.Unsafe

/**
 * Memory-mapped implementation of [MultiDimensionalDoubleArray].
 *
 * Stores values as 64-bit doubles (8 bytes per element) in memory-mapped temp files.
 * Use this when full double precision is required during stacking; expect ~2× the disk
 * usage compared to [HugeMultiDimensionalFloatArray].
 *
 * @param dimensions logical dimensions of the N-D array
 * @param tempDir    directory for temporary backing files; defaults to the system temp dir
 */
class HugeMultiDimensionalDoubleArray(vararg dimensions: Int, tempDir: File? = null) : MultiDimensionalDoubleArray(*dimensions) {

    private val maxBufferSize = Integer.MAX_VALUE / 8  // 8 bytes per double

    private val elementSize = 8

    private val memoryFiles: MutableList<File> = mutableListOf()
    private val fileChannels: MutableList<FileChannel> = mutableListOf()
    private val mappedBuffers: MutableList<MappedByteBuffer> = mutableListOf()
    private val doubleBuffers: Array<DoubleBuffer>
    private val bufferSize: Long

    init {
        val n = dimensions.map(Int::toLong).reduce(Long::times)

        val bufferCount = (n / maxBufferSize).toInt() + 1
        bufferSize = n / bufferCount + 1
        val bufferByteSize = bufferSize * elementSize
        doubleBuffers = Array(bufferCount) {
            val file = if (tempDir != null) {
                Files.createTempFile(tempDir.toPath(), "HugeDoubleArray_${it}_", ".mem").toFile()
            } else {
                File.createTempFile("HugeDoubleArray_${it}_", ".mem")
            }
            memoryFiles.add(file)
            file.deleteOnExit()

            val channel = Files.newByteChannel(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE) as FileChannel
            fileChannels.add(channel)
            val mapped = channel.map(FileChannel.MapMode.READ_WRITE, 0, bufferByteSize)
            mappedBuffers.add(mapped)

            mapped.asDoubleBuffer()
        }
    }

    override fun get(index: Long): Double {
        return if (doubleBuffers.size == 1) {
            doubleBuffers[0][index.toInt()]
        } else {
            val bufferIndex = (index / bufferSize).toInt()
            val bufferModuloIndex = (index % bufferSize).toInt()
            doubleBuffers[bufferIndex][bufferModuloIndex]
        }
    }

    override fun set(index: Long, value: Double) {
        if (doubleBuffers.size == 1) {
            doubleBuffers[0].put(index.toInt(), value)
        } else {
            val bufferIndex = (index / bufferSize).toInt()
            val bufferModuloIndex = (index % bufferSize).toInt()
            doubleBuffers[bufferIndex].put(bufferModuloIndex, value)
        }
    }

    override fun close() {
        try {
            val unsafe = unsafeInstance
            mappedBuffers.forEach { buf -> runCatching { unsafe?.invokeCleaner(buf) } }
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
