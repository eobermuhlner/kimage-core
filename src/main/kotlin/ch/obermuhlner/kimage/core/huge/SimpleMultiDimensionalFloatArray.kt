package ch.obermuhlner.kimage.core.huge

class SimpleMultiDimensionalFloatArray(vararg dimensions: Int) : MultiDimensionalFloatArray(*dimensions) {

    private val array = FloatArray(size.toInt())

    override fun get(index: Long): Float {
        return array[index.toInt()]
    }

    override fun set(index: Long, value: Float) {
        array[index.toInt()] = value
    }

    override fun close() {
        // empty
    }

}