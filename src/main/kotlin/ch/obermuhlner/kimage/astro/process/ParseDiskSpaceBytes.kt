package ch.obermuhlner.kimage.astro.process

private val SUFFIX_TABLE = listOf(
    "TiB" to (1L shl 40),
    "GiB" to (1L shl 30),
    "MiB" to (1L shl 20),
    "KiB" to (1L shl 10),
    "TB"  to 1_000_000_000_000L,
    "GB"  to 1_000_000_000L,
    "MB"  to 1_000_000L,
    "kB"  to 1_000L,
    "KB"  to 1_000L,
)

fun parseDiskSpaceBytes(s: String): Long {
    val trimmed = s.trim()
    if (trimmed.isEmpty() || trimmed.equals("max", ignoreCase = true)) return Long.MAX_VALUE

    for ((suffix, multiplier) in SUFFIX_TABLE) {
        val withoutSuffix = trimmed.removeSuffix(suffix).trim()
        if (withoutSuffix.length < trimmed.length) {
            return (withoutSuffix.toDouble() * multiplier).toLong()
        }
    }

    return trimmed.toLongOrNull()
        ?: throw IllegalArgumentException("Cannot parse disk space: '$s'")
}
