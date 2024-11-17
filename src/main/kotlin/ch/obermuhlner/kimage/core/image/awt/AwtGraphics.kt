package ch.obermuhlner.kimage.core.image.awt

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.values.asXY
import java.awt.RenderingHints
import java.awt.image.BufferedImage

fun graphics(image: Image, marginTop: Int, marginLeft: Int, marginBottom: Int, marginRight: Int, func: (java.awt.Graphics2D, Int, Int, Int, Int) -> Unit): Image {
    val width = image.width + marginLeft + marginRight
    val height = image.height + marginTop + marginBottom
    val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = result.createGraphics()

    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    graphics.background = java.awt.Color.BLACK
    graphics.clearRect(0, 0, width, height)

    val bufferedImage = toBufferedImage(image)
    graphics.drawImage(bufferedImage, marginLeft, marginTop, null)

    func(graphics, width, height, marginLeft, marginTop)

    graphics.dispose()
    return toImage(result)
}

fun toBufferedImage(image: Image): BufferedImage {
    val result = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)

    val pixel = IntArray(3)
    val red = image[Channel.Red].asXY()
    val green = image[Channel.Green].asXY()
    val blue = image[Channel.Blue].asXY()
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            pixel[0] = (red[x, y] * 255).toInt()
            pixel[1] = (green[x, y] * 255).toInt()
            pixel[2] = (blue[x, y] * 255).toInt()
            result.raster.setPixel(x, y, pixel)
        }
    }

    return result
}

fun toImage(bufferedImage: BufferedImage): Image {
    val result = MatrixImage(bufferedImage.width, bufferedImage.height)

    val pixel = DoubleArray(3)
    for (y in 0 until bufferedImage.height) {
        for (x in 0 until bufferedImage.width) {
            bufferedImage.raster.getPixel(x, y, pixel)
            pixel[0] = pixel[0] / 256
            pixel[1] = pixel[1] / 256
            pixel[2] = pixel[2] / 256
            result.setPixel(x, y, pixel)
        }
    }

    return result
}