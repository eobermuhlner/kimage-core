package ch.obermuhlner.kimage.core.image.filter

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.Matrix

open class MatrixImageFilter(
    private val matrixFilter: (Channel, Matrix) -> Matrix,
    private val channelFilter: (Channel) -> Boolean = { true }
) : Filter<Image> {

    override fun filter(source: Image): Image {
        return MatrixImage(source.width, source.height, source.channels.filter(channelFilter)) { channel, _, _ ->
            matrixFilter.invoke(channel, source[channel])
        }
    }
}