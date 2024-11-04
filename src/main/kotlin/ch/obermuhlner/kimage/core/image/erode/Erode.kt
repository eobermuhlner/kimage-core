package ch.obermuhlner.kimage.core.image.erode

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.erode.erode

fun Image.erode(kernelRadius: Int = 1): Image {
    val hue = this[Channel.Hue]
    val saturation = this[Channel.Saturation]
    val brightness = this[Channel.Brightness].erode(kernelRadius)

    val hsbImage = MatrixImage(this.width, this.height,
        Channel.Hue to hue,
        Channel.Saturation to saturation,
        Channel.Brightness to brightness)

    return MatrixImage(this.width, this.height,
        Channel.Red to hsbImage[Channel.Red],
        Channel.Green to hsbImage[Channel.Green],
        Channel.Blue to hsbImage[Channel.Blue])
}

fun Image.erode(kernel: Matrix, strength: Double = 1.0, repeat: Int = 1): Image {
    val hue = this[Channel.Hue]
    val saturation = this[Channel.Saturation]
    val brightness = this[Channel.Brightness].erode(kernel, strength, repeat)

    val hsbImage = MatrixImage(this.width, this.height,
        Channel.Hue to hue,
        Channel.Saturation to saturation,
        Channel.Brightness to brightness)

    return MatrixImage(this.width, this.height,
        Channel.Red to hsbImage[Channel.Red],
        Channel.Green to hsbImage[Channel.Green],
        Channel.Blue to hsbImage[Channel.Blue])
}
