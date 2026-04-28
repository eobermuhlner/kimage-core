package ch.obermuhlner.kimage.core.huge

class SimpleMultiDimensionalDoubleArray(vararg dimensions: Int) : MultiDimensionalDoubleArray(*dimensions) {

    private val array = DoubleArray(size.toInt())

    override fun get(index: Long): Double = array[index.toInt()]

    override fun set(index: Long, value: Double) {
        array[index.toInt()] = value
    }

    override fun close() {
        // empty
    }
}
