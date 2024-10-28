package ch.obermuhlner.kimage.util

fun <T> elapsed(name: String, func: () -> T): T {
    val startMillis = System.currentTimeMillis()
    val result = func()
    val endMillis = System.currentTimeMillis()
    println("Elapsed $name in ${endMillis - startMillis} ms")

    return result
}