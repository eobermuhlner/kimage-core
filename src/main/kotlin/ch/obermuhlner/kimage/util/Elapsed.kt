package ch.obermuhlner.kimage.util

fun <T> elapsed(name: String, func: () -> T): T {
    println("Started  $name ...")
    val startMillis = System.currentTimeMillis()
    val result = func()
    val endMillis = System.currentTimeMillis()
    println("Finished $name in ${endMillis - startMillis} ms")

    return result
}