package ch.obermuhlner.kimage.core.image

abstract class AbstractImage(
    override val width: Int,
    override val height: Int,
    override val channels: List<Channel>
) : Image {
    private val channelToIndex = IntArray(Channel.entries.size) {
        channels.indexOf(Channel.entries[it])
    }

    override fun channelIndex(channel: Channel) = channelToIndex[channel.ordinal]

    override fun toString(): String {
        return "Image($width, $height)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractImage

        if (width != other.width) return false
        if (height != other.height) return false
        if (channels != other.channels) return false
        for (channel in channels) {
            if (this[channel] != other[channel]) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + channels.hashCode()
        for (channel in channels) {
            result = 31 * result + this[channel].hashCode()
        }
        return result
    }
}