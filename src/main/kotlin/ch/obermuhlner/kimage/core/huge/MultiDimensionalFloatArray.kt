package ch.obermuhlner.kimage.core.huge

import java.io.Closeable

abstract class MultiDimensionalFloatArray(private vararg val dimensions: Int) : Closeable {

    abstract operator fun get(index: Long): Float
    abstract operator fun set(index: Long, value: Float)

    val size: Long
        get() = dimensions.map(Int::toLong).reduce(Long::times)


    operator fun get(index0: Int, index1: Int): Float {
        require(dimensions.size >= 2)
        val index = index0.toLong() + index1.toLong() * dimensions[0]
        return get(index)
    }
    operator fun set(index0: Int, index1: Int, value: Float) {
        require(dimensions.size >= 2)
        val index = index0.toLong() + index1.toLong() * dimensions[0]
        set(index, value)
    }

    operator fun get(index0: Int, index1: Int, index2: Int): Float {
        require(dimensions.size >= 3)
        val index = index0.toLong() + index1.toLong() * dimensions[0] + index2.toLong() * (dimensions[0]*dimensions[1])
        return get(index)
    }
    operator fun set(index0: Int, index1: Int, index2: Int, value: Float) {
        require(dimensions.size >= 2)
        val index = index0.toLong() + index1.toLong() * dimensions[0] + index2.toLong() * (dimensions[0]*dimensions[1])
        set(index, value)
    }

    operator fun get(index0: Int, index1: Int, index2: Int, index3: Int): Float {
        require(dimensions.size >= 3)
        val index = index0.toLong() + index1.toLong() * dimensions[0] + index2.toLong() * (dimensions[0]*dimensions[1]) + index3.toLong() * (dimensions[0]*dimensions[1]*dimensions[2])
        return get(index)
    }
    operator fun set(index0: Int, index1: Int, index2: Int, index3: Int, value: Float) {
        require(dimensions.size >= 4)
        val index = index0.toLong() + index1.toLong() * dimensions[0] + index2.toLong() * (dimensions[0]*dimensions[1]) + index3.toLong() * (dimensions[0]*dimensions[1]*dimensions[2])
        set(index, value)
    }
}
