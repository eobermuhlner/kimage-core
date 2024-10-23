package ch.obermuhlner.kimage.core.huge

import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class HugeMultiDimensionalFloatArray(vararg dimensions: Int) : MultiDimensionalFloatArray(*dimensions) {

    private val maxBufferSize = Integer.MAX_VALUE / 4

    private val elementSize = 4 // float has 4 bytes

    private val memoryFiles: MutableList<File> = mutableListOf()
    private val floatBuffers: Array<FloatBuffer>
    private val bufferSize: Long

    init {
        val n = dimensions.map(Int::toLong).reduce(Long::times)

        val bufferCount = (n / maxBufferSize).toInt() + 1 // at least one buffer
        bufferSize = n / bufferCount + 1 // prevent rounding down and missing 1 element
        val bufferByteSize = bufferSize * elementSize
        floatBuffers = Array(bufferCount) {
            val file = File.createTempFile("HugeFloatArray_${it}_", ".mem")
            memoryFiles.add(file)
            file.deleteOnExit()

            val channel: FileChannel = Files.newByteChannel(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE) as FileChannel
            val buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, bufferByteSize)

            buffer.asFloatBuffer()
        }
    }

    override operator fun get(index: Long): Float {
        val bufferIndex = (index / bufferSize).toInt()
        val bufferModuloIndex = (index % bufferSize).toInt()
        return floatBuffers[bufferIndex][bufferModuloIndex]
    }

    override operator fun set(index: Long, value: Float) {
        val bufferIndex = (index / bufferSize).toInt()
        val bufferModuloIndex = (index % bufferSize).toInt()
        floatBuffers[bufferIndex].put(bufferModuloIndex, value)
    }

    override fun close() {
        memoryFiles.forEach {
            it.delete()
        }
    }
}
