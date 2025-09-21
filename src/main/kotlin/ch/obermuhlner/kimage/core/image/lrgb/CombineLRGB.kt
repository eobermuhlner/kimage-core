package ch.obermuhlner.kimage.core.image.lrgb

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Channel.Blue
import ch.obermuhlner.kimage.core.image.Channel.Green
import ch.obermuhlner.kimage.core.image.Channel.Red
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.Matrix

fun Image.replaceBrightness(brightnessImage: Image, strength: Double = 1.0, brightnessChannel: Channel = Channel.Brightness): Image {
    val hue = this[Channel.Hue]
    val saturation = this[Channel.Saturation]
    val brightness = if (strength == 1.0) {
        brightnessImage[brightnessChannel]
    } else {
        brightnessImage[brightnessChannel] * strength + this[Channel.Brightness] * (1.0 - strength)
    }

    val hsbImage = MatrixImage(width, height,
        Channel.Hue to hue,
        Channel.Saturation to saturation,
        Channel.Brightness to brightness,
    )

    return MatrixImage(width, height,
        Channel.Red to hsbImage[Channel.Red],
        Channel.Green to hsbImage[Channel.Green],
        Channel.Blue to hsbImage[Channel.Blue],
    )
}

fun Image.combineGray(luminanceImage: Image, strength: Double, luminanceChannel: Channel = Channel.Gray): Image {
    val luminanceMatrix = luminanceImage[luminanceChannel]
    return combineGray(luminanceMatrix, strength, this[Red], this[Green], this[Blue])
}

fun combineGray(
    luminance: Matrix,
    strength: Double,
    red: Matrix,
    green: Matrix,
    blue: Matrix,
    combineFunc: (row: Int, col: Int, value: Double) -> Double = { row, col, value -> value * (1.0 - strength) + luminance[row, col] * strength}
): Image {
    val combinedRed = red.copy()
    val combinedGreen = green.copy()
    val combinedBlue = blue.copy()

    combinedRed.applyEach(combineFunc)

    return MatrixImage(luminance.cols, luminance.rows,
        Channel.Red to combinedRed,
        Channel.Green to combinedGreen,
        Channel.Blue to combinedBlue
    )
}