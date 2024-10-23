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
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DoubleImageIO {

    fun writeImage(image: Image, file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("image"))
            val output = DataOutputStream(zip)
            output.writeInt(image.width)
            output.writeInt(image.height)
            output.writeInt(image.channels.size)

            for (channel in image.channels) {
                output.writeUTF(channel.name)
                image[channel].values().forEach { value ->
                    output.writeDouble(value)
                }
            }
            output.flush()
            zip.closeEntry()
        }
    }

    fun readImage(file: File): Image {
        return ZipInputStream(file.inputStream()).use { zip ->
            zip.nextEntry
            val input = DataInputStream(zip)
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
