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
}