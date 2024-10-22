package ch.obermuhlner.kimage.core.image.io.dimg

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.values.values
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

object DoubleImageIO {

    fun writeImage(image: Image, file: File) {
        DataOutputStream(file.outputStream()).use { output ->
            output.writeInt(image.width)
            output.writeInt(image.height)
            output.writeInt(image.channels.size)
            for (channel in image.channels) {
                output.writeUTF(channel.name)
                image[channel].values().forEach { value ->
                    output.writeDouble(value)
                }
            }
        }
    }

    fun readImage(file: File): Image {
        return DataInputStream(file.inputStream()).use { input ->
            val width = input.readInt()
            val height = input.readInt()
            val channelCount = input.readInt()
            val channels = mutableListOf<Channel>()
            val channelMatrices = mutableListOf<Matrix>()

            for (i in 0 until channelCount) {
                channels.add(Channel.valueOf(input.readUTF()))
                channelMatrices.add(DoubleMatrix(height, width) { _, _ -> input.readDouble() })
            }

            MatrixImage(width, height, channels, channelMatrices)
        }
    }
}