package ch.obermuhlner.kimage.core.image.filter

interface Filter<T> {
    fun filter(source: T): T
}