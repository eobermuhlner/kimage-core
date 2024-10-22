package ch.obermuhlner.kimage.core.image.io.json

import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.matrix.values.values

fun Image.toJson(): String {
    val sb = StringBuilder()

    sb.append("{")
    sb.append("\"width\": ${width},")
    sb.append("\"height\": ${height},")
    sb.append("\"data\": {")
    for (channelIndex in channels.indices) {
        val channel = channels[channelIndex]
        sb.append("\"${channel}\": ")
        sb.append(this[channel].values().joinToString(",", "[", "]"))
        if (channelIndex < channels.size - 1) {
            sb.append(",")
        }
    }
    sb.append("}")
    sb.append("}")

    return sb.toString()
}
