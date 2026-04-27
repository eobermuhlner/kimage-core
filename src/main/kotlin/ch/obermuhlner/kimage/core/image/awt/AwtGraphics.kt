package ch.obermuhlner.kimage.core.image.awt

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.values.asXY
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import kotlin.math.ceil

private val CROSS_PLATFORM_FRC = FontRenderContext(null, false, false)

class CrossPlatformFontMetrics(private val font: Font) {
    val height: Int = ceil(font.getLineMetrics("Xg", CROSS_PLATFORM_FRC).height).toInt()
    val descent: Int = ceil(font.getLineMetrics("Xg", CROSS_PLATFORM_FRC).descent).toInt()
    fun stringWidth(text: String): Int =
        if (text.isEmpty()) 0 else ceil(font.getStringBounds(text, CROSS_PLATFORM_FRC).width).toInt()
}

val Graphics2D.fontMetricsCP: CrossPlatformFontMetrics
    get() = CrossPlatformFontMetrics(font)

fun Graphics2D.drawStringCP(text: String, x: Int, y: Int) {
    if (text.isEmpty()) return
    val glyphVector = font.createGlyphVector(CROSS_PLATFORM_FRC, text)
    fill(glyphVector.getOutline(x.toFloat(), y.toFloat()))
}

fun graphics(image: Image, marginTop: Int, marginLeft: Int, marginBottom: Int, marginRight: Int, func: (java.awt.Graphics2D, Int, Int, Int, Int) -> Unit): Image {
    val width = image.width + marginLeft + marginRight
    val height = image.height + marginTop + marginBottom
    val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = result.createGraphics()

    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF)

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
            pixel[0] = pixel[0] / 255.0
            pixel[1] = pixel[1] / 255.0
            pixel[2] = pixel[2] / 255.0
            result.setPixel(x, y, pixel)
        }
    }

    return result
}