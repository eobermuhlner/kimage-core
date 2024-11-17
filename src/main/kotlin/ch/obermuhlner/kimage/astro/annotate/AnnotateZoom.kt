package ch.obermuhlner.kimage.astro.annotate

import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme.Blue
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme.Cyan
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme.Green
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme.Red
import ch.obermuhlner.kimage.astro.annotate.AnnotateZoom.ColorTheme.White
import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.PointXY
import ch.obermuhlner.kimage.core.image.awt.graphics
import ch.obermuhlner.kimage.core.image.awt.toBufferedImage
import ch.obermuhlner.kimage.core.image.crop.cropCenter
import ch.obermuhlner.kimage.core.image.scaling.scaleTo
import ch.obermuhlner.kimage.core.matrix.scaling.Scaling
import java.awt.Font
import java.awt.Graphics2D
import java.io.File
import java.util.Locale
import java.util.Optional
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class AnnotateZoom {
    enum class MarkerStyle {
        Square,
        Rectangle,
        Circle,
        Oval,
        None
    }

    enum class MarkerLabelStyle {
        Index,
        Name,
        None
    }

    enum class ColorTheme {
        White,
        Cyan,
        Red,
        Green,
        Blue
    }

    var magnitude: Optional<Double> = Optional.empty()
    var minObjectSize: Int = 50
    var title: String = ""
    var subtitle: String = ""
    var text: String = ""
    var whiteList: Optional<List<String>> = Optional.empty()
    var blackList: Optional<List<String>> = Optional.empty()
    var ignoreClipped: Boolean = true
    var markerStyle: MarkerStyle = MarkerStyle.Square
    var markerLabelStyle: MarkerLabelStyle = MarkerLabelStyle.Index
    var thumbnailSize: Int = 400
    var thumbnailMargin: Int = 20
    var brightnessGraphSize: Int = 200
    var baseFontSize: Double = 50.0
    var baseStrokeSize: Double = 3.0
    var titleFontSizeFactor: Double = 2.0
    var textFontSizeFactor: Double = 1.0
    var markerIndexFontSizeFactor: Double = 0.8
    var thumbnailLabelFontSizeFactor: Double = 1.2
    var thumbnailInfoFontSizeFactor: Double = 1.0
    var thumbnailIndexFontSizeFactor: Double = 0.8
    var titleColor: String = ""
    var subtitleColor: String = ""
    var textColor: String = ""
    var markerRectColor: String = ""
    var markerIndexColor: String = ""
    var thumbnailRectColor: String = ""
    var thumbnailLabelColor: String = ""
    var thumbnailInfoColor: String = ""
    var thumbnailIndexColor: String = ""
    var brightnessGraphColor: String = ""
    var shadowColor: String = ""

    var titleFontSize = baseFontSize * titleFontSizeFactor
    var textFontSize = baseFontSize * textFontSizeFactor
    var markerIndexFontSize = baseFontSize * markerIndexFontSizeFactor
    var thumbnailLabelFontSize = baseFontSize * thumbnailLabelFontSizeFactor
    var thumbnailInfoFontSize = baseFontSize * thumbnailInfoFontSizeFactor
    var thumbnailIndexFontSize = baseFontSize * thumbnailIndexFontSizeFactor

    var gridColor = ""
    var gridStrokeSize = baseStrokeSize * 0.5

    var brightnessGraphStrokeSize = baseStrokeSize * 0.5
    var brightnessGraphGridColor = ""
    var brightnessGraphGridStrokeSize = brightnessGraphStrokeSize

    val shadowOffsets: MutableList<PointXY> = mutableListOf(
        PointXY(-1, 0),
        PointXY(1, 0),
        PointXY(0, -1),
        PointXY(0, 1),
    )
    val markers = mutableListOf<Marker>()

    init {
        setColorTheme(ColorTheme.White)
    }

    fun setColorTheme(colorTheme: ColorTheme) {
        when(colorTheme) {
            White -> {
                titleColor = "ffffff"
                subtitleColor = "cccccc"
                textColor = "aaaaaa"
                markerRectColor = "cccccc"
                markerIndexColor = "aaaaaa"
                thumbnailRectColor = "aaaaaa"
                thumbnailLabelColor = "cccccc"
                thumbnailInfoColor = "aaaaaa"
                thumbnailIndexColor = "aaaaaa"
                brightnessGraphColor = "aaaaaa"
                gridColor = "888888"
                brightnessGraphGridColor = "666666"
                shadowColor = "000000"
            }
            Cyan -> {
                titleColor = "ccffff"
                subtitleColor = "88aaaa"
                textColor = "88aaaa"
                markerRectColor = "88ffff"
                markerIndexColor = "aacccc"
                thumbnailRectColor = "88ffff"
                thumbnailLabelColor = "aaffff"
                thumbnailInfoColor = "88aaaa"
                thumbnailIndexColor = "aacccc"
                brightnessGraphColor = "00cccc"
                gridColor = "226666"
                brightnessGraphGridColor = "226666"
                //shadowColor = "006666"
                shadowColor = "00aaaa"
            }
            Red -> {
                titleColor = "ffcccc"
                subtitleColor = "aa8888"
                textColor = "cc8888"
                markerRectColor = "ff8888"
                markerIndexColor = "aa8888"
                thumbnailRectColor = "ff8888"
                thumbnailLabelColor = "ffaaaa"
                thumbnailInfoColor = "cc8888"
                thumbnailIndexColor = "aa8888"
                brightnessGraphColor = "cc0000"
                gridColor = "662222"
                brightnessGraphGridColor = "662222"
                shadowColor = "660000"
            }
            Green -> {
                titleColor = "ccffcc"
                subtitleColor = "88aa88"
                textColor = "88cc88"
                markerRectColor = "88ff88"
                markerIndexColor = "88aa88"
                thumbnailRectColor = "88ff88"
                thumbnailLabelColor = "aaffaa"
                thumbnailInfoColor = "88cc88"
                thumbnailIndexColor = "88aa88"
                brightnessGraphColor = "00cc00"
                gridColor = "226622"
                brightnessGraphGridColor = "226622"
                shadowColor = "006600"
            }
            Blue -> {
                titleColor = "ccccff"
                subtitleColor = "8888aa"
                textColor = "8888cc"
                markerRectColor = "8888ff"
                markerIndexColor = "8888aa"
                thumbnailRectColor = "8888ff"
                thumbnailLabelColor = "aaaaff"
                thumbnailInfoColor = "8888cc"
                thumbnailIndexColor = "8888aa"
                brightnessGraphColor = "0000cc"
                gridColor = "222266"
                brightnessGraphGridColor = "222266"
                shadowColor = "000066"
            }
        }
    }

    private fun arePointsOnSameSide(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Boolean {
        val x1 = cos(Math.toRadians(ra1)) * cos(Math.toRadians(dec1))
        val y1 = sin(Math.toRadians(ra1)) * cos(Math.toRadians(dec1))
        val z1 = sin(Math.toRadians(dec1))

        val x2 = cos(Math.toRadians(ra2)) * cos(Math.toRadians(dec2))
        val y2 = sin(Math.toRadians(ra2)) * cos(Math.toRadians(dec2))
        val z2 = sin(Math.toRadians(dec2))

        val dotProduct = x1 * x2 + y1 * y2 + z1 * z2

        return dotProduct >= 0
    }

    fun addPlatesolve(inputImage: Image, wcsFile: File) {
        val wcsData = WCSParser.parse(wcsFile)
        val wcsConverter = WCSConverter(wcsData)

        val (centerRa, centerDec) = wcsConverter.convertXYToRADec(inputImage.width/2.0, inputImage.height/2.0)

        val filteredNGCs = mutableListOf<DeepSkyObjects.NGC>()
        for (ngc in DeepSkyObjects.all()) {
            val (x, y) = wcsConverter.convertRADecToXY(ngc.ra, ngc.dec)
            if (x in 0.0..inputImage.width.toDouble() &&
                y in 0.0..inputImage.height.toDouble() &&
                arePointsOnSameSide(ngc.ra, ngc.dec, centerRa, centerDec) &&
                (!whiteList.isPresent || whiteList.get().contains(ngc.name) || whiteList.get().contains(ngc.messierOrName))) {
                filteredNGCs += ngc
            }
        }
        if (magnitude.isPresent) {
            filteredNGCs.removeIf { ngc ->
                val mag = ngc.mag
                mag == null || mag > magnitude.get()
            }
        }
        filteredNGCs.removeIf { ngc -> ngc.type == "*" || ngc.type == "Other" }
        if (blackList.isPresent) {
            filteredNGCs.removeIf { ngc -> blackList.get().contains(ngc.name) || blackList.get().contains(ngc.messierOrName)}
        }
        filteredNGCs.sortBy { it.mag ?: Double.MAX_VALUE }

        title = if(title.isBlank() && filteredNGCs.isNotEmpty()) {
            filteredNGCs.sortedBy { it.mag ?: 99.9 }.take(3).joinToString(" ") { it.messierOrName }
        } else {
            title
        }
        subtitle = subtitle.ifBlank { "${formatDegreesToHMS(centerRa)} ${formatDegreesToDMS(centerDec)}" }

        filteredNGCs.forEach { ngc ->
            val (x, y) = wcsConverter.convertRADecToXY(ngc.ra, ngc.dec)
            val name = ngc.messierOrName
            val info1 = "${ngc.typeEnglish}" + if (ngc.mag != null) " ${ngc.mag}mag" else ""
            val info2 = "${formatDegreesToHMS(ngc.ra)} ${formatDegreesToDMS(ngc.dec)}"
            val pixelX = x.toInt()
            val pixelY = inputImage.height - y.toInt()
            val majAx = ngc?.majAx ?: ngc.minAx
            val minAx = ngc?.minAx ?: ngc.majAx
            val posAngle = ngc?.posAngle ?: 0.0
            val pixelMajAx =
                if (majAx != null) wcsConverter.convertDegreeToLength(majAx).absoluteValue.toInt() else minObjectSize
            val pixelMinAx =
                if (minAx != null) wcsConverter.convertDegreeToLength(minAx).absoluteValue.toInt() else minObjectSize
            val pixelSize = if (majAx != null && minAx != null) {
                max(
                    wcsConverter.convertDegreeToLength(max(majAx, minAx)).absoluteValue.toInt(),
                    minObjectSize
                )
            } else minObjectSize

            addMarker(Marker(name, pixelX, pixelY, pixelSize, info1, info2, pixelMajAx, pixelMinAx, posAngle))
        }
    }

    fun addMarker(marker: Marker) {
        markers.add(marker)
    }

    fun annotate(inputImage: Image, ): Image {
        var titleFontHeight = 0
        var textFontHeight = 0
        var thumbnailLabelFontHeight = 0
        var thumbnailInfoFontHeight = 0
        graphics(inputImage, 0, 0, 0, 0) { graphics, width, height, offsetX, offsetY ->
            if (title.isNotBlank() || subtitle.isNotBlank()) {
                graphics.font = graphics.font.deriveFont(titleFontSize.toFloat())
                titleFontHeight = graphics.fontMetrics.height
            }

            if (text.isNotBlank()) {
                graphics.font = graphics.font.deriveFont(textFontSize.toFloat())
                textFontHeight = graphics.fontMetrics.height
            }

            graphics.font = graphics.font.deriveFont(thumbnailLabelFontSize.toFloat())
            thumbnailLabelFontHeight = graphics.fontMetrics.height

            graphics.font = graphics.font.deriveFont(thumbnailInfoFontSize.toFloat())
            thumbnailInfoFontHeight = graphics.fontMetrics.height
        }

        val textHeight = textFontHeight * text.lines().size

        val thumbnailColWidth = thumbnailSize + thumbnailMargin
        val thumbnailRowHeight = thumbnailSize + thumbnailMargin + thumbnailLabelFontHeight + thumbnailInfoFontHeight + thumbnailInfoFontHeight + brightnessGraphSize
        val thumbnailCols = inputImage.width / thumbnailColWidth
        val thumbnailRows = ceil(markers.size.toDouble() / thumbnailCols).toInt()

        val marginTop = thumbnailMargin + titleFontHeight + textHeight
        val marginLeft = thumbnailMargin
        val marginBottom = thumbnailRows * thumbnailRowHeight + thumbnailMargin
        val marginRight = thumbnailMargin

        return graphics(inputImage, marginTop, marginLeft, marginBottom, marginRight) { graphics, width, height, offsetX, offsetY ->
            val thumbnailStartX = offsetX
            val thumbnailStartY = offsetY + inputImage.height + thumbnailMargin + thumbnailLabelFontHeight + thumbnailInfoFontHeight + thumbnailInfoFontHeight

            var thumbnailX = thumbnailStartX
            var thumbnailY = thumbnailStartY
            var thumbnailIndex = 1

            var textY = 0

            graphics.stroke = java.awt.BasicStroke(baseStrokeSize.toFloat())
            val titleFont = graphics.font.deriveFont(titleFontSize.toFloat())
            val textFont = graphics.font.deriveFont(textFontSize.toFloat())
            val markerIndexFont = graphics.font.deriveFont(markerIndexFontSize.toFloat())
            val thumbnailLabelFont = graphics.font.deriveFont(thumbnailLabelFontSize.toFloat())
            val thumbnailInfoFont = graphics.font.deriveFont(thumbnailInfoFontSize.toFloat())
            val thumbnailIndexFont = graphics.font.deriveFont(thumbnailIndexFontSize.toFloat())

            val shadowColor = java.awt.Color(shadowColor.toInt(16))
            fun Graphics2D.drawShadow(drawFunc: (dx: Int, dy: Int) -> Unit) {
                val color = this.color
                this.color = shadowColor
                for (shadowOffset in shadowOffsets) {
                    drawFunc(shadowOffset.x, shadowOffset.y)
                }
                this.color = color
                drawFunc(0, 0)
            }

//            graphics.clipRect(offsetX, offsetY, inputImage.width, inputImage.height)
//            graphics.color = java.awt.Color(gridColor.toInt(16))
//            graphics.stroke = java.awt.BasicStroke(gridStrokeSize.toFloat())
//            var raHour = 0.0
//            var dec: Double
//            while (raHour <= 24.0) {
//                val ra = raHour / 24 * 360
//                dec = -90.0
//                var lastX = 0.0
//                var lastY = 0.0
//                while (dec <= 90.0) {
//                    val (x, y) = wcsConverter.convertRADecToXY(ra, dec)
//                    if (dec != -90.0) {
//                        graphics.drawLine((lastX+offsetX).toInt(), (lastY+offsetY).toInt(), (x+offsetX).toInt(), (y+offsetY).toInt())
//                    }
//                    lastX = x
//                    lastY = y
//                    dec += 0.1
//                }
//                raHour += 0.5
//            }
//            dec = -90.0
//            while (dec <= 90.0) {
//                raHour = 0.0
//                var lastX = 0.0
//                var lastY = 0.0
//                while (raHour <= 24.0) {
//                    val ra = raHour / 24 * 360
//                    val (x, y) = wcsConverter.convertRADecToXY(ra, dec)
//                    if (ra != 0.0) {
//                        graphics.drawLine((lastX+offsetX).toInt(), (lastY+offsetY).toInt(), (x+offsetX).toInt(), (y+offsetY).toInt())
//                    }
//                    lastX = x
//                    lastY = y
//                    raHour += 0.1
//                }
//                dec += 1.0
//            }
//            graphics.setClip(java.awt.Rectangle(0, 0, width, height))


            for (marker in markers) {
                if (ignoreClipped) {
                    if (marker.x - marker.size / 2 < 0 || marker.x + marker.size / 2 > inputImage.width || marker.y - marker.size / 2 < 0 || marker.y + marker.size / 2 > inputImage.height) {
                        continue
                    }
                }

                val inputCrop = inputImage.cropCenter(marker.size / 2, marker.x, marker.y, false).scaleTo(thumbnailSize, thumbnailSize, scaling = Scaling.Nearest)
                val crop = toBufferedImage(inputCrop)
                if (thumbnailX + thumbnailSize > width) {
                    thumbnailX = thumbnailStartX
                    thumbnailY += thumbnailRowHeight
                }
                graphics.color = java.awt.Color(markerIndexColor.toInt(16))
                graphics.font = markerIndexFont
                val markerLabel = when (markerLabelStyle) {
                    MarkerLabelStyle.Index -> thumbnailIndex.toString()
                    MarkerLabelStyle.Name -> marker.name
                    MarkerLabelStyle.None -> ""
                }
                when (markerStyle) {
                    MarkerStyle.Square -> {
                        graphics.drawShadow { dx, dy ->
                            graphics.drawString(markerLabel, offsetX + marker.x - marker.size / 2 + dx, offsetY + marker.y - marker.size / 2 - graphics.fontMetrics.descent + dy)
                        }
                    }

                    else -> {
                        val stringWidth = graphics.fontMetrics.stringWidth(markerLabel)
                        graphics.drawShadow { dx, dy ->
                            graphics.drawString(markerLabel, offsetX + marker.x - stringWidth / 2 + dx, offsetY + marker.y - marker.size / 2 - graphics.fontMetrics.descent + dy)
                        }
                    }
                }

                graphics.color = java.awt.Color(markerRectColor.toInt(16))
                when (markerStyle) {
                    MarkerStyle.Square -> {
                        graphics.drawShadow { dx, dy ->
                            graphics.drawRect(offsetX + marker.x - marker.size / 2 + dx, offsetY + marker.y - marker.size / 2 + dy, marker.size, marker.size)
                        }

                    }

                    MarkerStyle.Rectangle -> {
                        val backupTransform = graphics.transform
                        graphics.translate(offsetX + marker.x, offsetY + marker.y)
                        graphics.rotate(Math.toRadians(marker.posAngle))
                        graphics.drawShadow { dx, dy ->
                            graphics.drawRect(-marker.majAx / 2 + dx, -marker.minAx / 2, marker.majAx, marker.minAx + dy)
                        }
                        graphics.transform = backupTransform
                    }

                    MarkerStyle.Circle -> {
                        graphics.drawShadow { dx, dy ->
                            graphics.drawOval(offsetX + marker.x - marker.size / 2 + dx, offsetY + marker.y - marker.size / 2, marker.size, marker.size + dy)
                        }
                    }

                    MarkerStyle.Oval -> {
                        graphics.drawShadow { dx, dy ->
                            val backupTransform = graphics.transform
                            graphics.translate(offsetX + marker.x, offsetY + marker.y)
                            graphics.rotate(Math.toRadians(marker.posAngle))
                            graphics.drawOval(-marker.majAx / 2 + dx, -marker.minAx / 2, marker.majAx, marker.minAx + dy)
                            graphics.transform = backupTransform
                        }
                    }

                    MarkerStyle.None -> {}
                }

                graphics.drawImage(crop, thumbnailX, thumbnailY, null)

                graphics.color = java.awt.Color(thumbnailLabelColor.toInt(16))
                setAdaptiveFont(graphics, thumbnailLabelFont, marker.name, thumbnailSize)
                graphics.drawShadow { dx, dy ->
                    graphics.drawString(marker.name, thumbnailX + dx, thumbnailY - thumbnailInfoFontHeight - thumbnailInfoFontHeight - graphics.fontMetrics.descent + dy)
                }

                graphics.color = java.awt.Color(thumbnailInfoColor.toInt(16))
                setAdaptiveFont(graphics, thumbnailInfoFont, marker.info1, thumbnailSize)
                graphics.drawShadow { dx, dy ->
                    graphics.drawString(marker.info1, thumbnailX + dx, thumbnailY - graphics.fontMetrics.height - graphics.fontMetrics.descent + dy)
                }
                setAdaptiveFont(graphics, thumbnailInfoFont, marker.info2, thumbnailSize)
                graphics.drawShadow { dx, dy ->
                    graphics.drawString(marker.info2, thumbnailX + dx, thumbnailY - graphics.fontMetrics.descent + dy)
                }

                if (markerLabelStyle == MarkerLabelStyle.Index) {
                    graphics.color = java.awt.Color(thumbnailIndexColor.toInt(16))
                    setAdaptiveFont(graphics, thumbnailIndexFont, markerLabel, thumbnailSize / 4)
                    graphics.drawShadow { dx, dy ->
                        graphics.drawString(markerLabel, thumbnailX + baseStrokeSize.roundToInt() + dx, thumbnailY + baseStrokeSize.roundToInt() + graphics.fontMetrics.height + dy)
                    }
                }

                graphics.color = java.awt.Color(thumbnailRectColor.toInt(16))
                graphics.stroke = java.awt.BasicStroke(gridStrokeSize.toFloat())
                graphics.drawRect(thumbnailX, thumbnailY, crop.width, crop.height)

                if (brightnessGraphSize > 0) {
                    graphics.color = java.awt.Color(brightnessGraphGridColor.toInt(16))
                    graphics.stroke = java.awt.BasicStroke(brightnessGraphGridStrokeSize.toFloat())

                    //val inputCropChannel = inputCrop[Channel.Gray]
                    val graphX = thumbnailX
                    val graphY = thumbnailY + thumbnailSize

                    val gridStepsX = 10
                    val gridStepsY = 4
                    for (step in 1 until gridStepsX) {
                        graphics.drawLine(graphX + crop.width * step / gridStepsX, graphY, graphX + crop.width * step / gridStepsX, graphY + brightnessGraphSize)
                    }
                    for (step in 1 until gridStepsY) {
                        graphics.drawLine(graphX, graphY + brightnessGraphSize * step / gridStepsY, graphX + crop.width, graphY + brightnessGraphSize * step / gridStepsY)
                    }

                    for (channel in listOf(Channel.Red, Channel.Green, Channel.Blue)) {
                        val inputCropChannel = inputCrop[channel]

                        //graphics.color = java.awt.Color(brightnessGraphColor.toInt(16))
                        graphics.color = java.awt.Color(
                            when (channel) {
                                Channel.Red -> "ff4444".toInt(16)
                                Channel.Green -> "44ff44".toInt(16)
                                Channel.Blue -> "4444ff".toInt(16)
                                else -> "888888".toInt(16)
                            }
                        )
                        graphics.stroke = java.awt.BasicStroke(brightnessGraphStrokeSize.toFloat())

                        var lastY = 0
                        for (x in 0 until crop.width) {
                            val value = inputCropChannel[x, crop.height / 2]
                            val y = ((1.0 - value) * (brightnessGraphSize - baseStrokeSize) + baseStrokeSize).toInt()
                            if (x > 0) {
                                graphics.drawLine(graphX + x - 1, graphY + lastY, graphX + x, graphY + y)
                            }
                            lastY = y
                        }
                    }

                    graphics.stroke = java.awt.BasicStroke(baseStrokeSize.toFloat())
                    graphics.color = java.awt.Color(thumbnailRectColor.toInt(16))
                    graphics.drawRect(graphX, graphY, crop.width, brightnessGraphSize)
                }

                thumbnailX += thumbnailSize + thumbnailMargin
                thumbnailIndex++
            }

            if (title.isNotBlank() || subtitle.isNotBlank()) {
                setAdaptiveFont(graphics, titleFont, title, inputImage.width)
                textY += titleFontHeight
                var titleWidth = graphics.fontMetrics.stringWidth(title)
                var subtitleWidth = inputImage.width - titleWidth
                if (subtitleWidth < inputImage.width / 3) {
                    subtitleWidth = inputImage.width / 3
                    titleWidth = inputImage.width - subtitleWidth
                }
                graphics.color = java.awt.Color(titleColor.toInt(16))
                setAdaptiveFont(graphics, titleFont, title, titleWidth)
                graphics.drawShadow { dx, dy ->
                    graphics.drawString(title, offsetX + dx, textY + dy)
                }

                graphics.color = java.awt.Color(subtitleColor.toInt(16))
                setAdaptiveFont(graphics, titleFont, subtitle, subtitleWidth)
                subtitleWidth = graphics.fontMetrics.stringWidth(subtitle)
                graphics.drawShadow { dx, dy ->
                    graphics.drawString(subtitle, offsetX + inputImage.width - subtitleWidth + dx, textY + dy)
                }
            }

            if (text.isNotBlank()) {
                graphics.color = java.awt.Color(textColor.toInt(16))
                for (textLine in text.lines()) {
                    textY += textFontHeight
                    graphics.font = textFont
                    graphics.drawShadow { dx, dy ->
                        graphics.drawString(textLine, offsetX + dx, textY + dy)
                    }
                }
            }

            graphics.color = java.awt.Color(thumbnailRectColor.toInt(16))
            graphics.stroke = java.awt.BasicStroke(baseStrokeSize.toFloat())
            graphics.drawRect(offsetX, offsetY, inputImage.width, inputImage.height)
        }
    }

    fun setAdaptiveFont(graphics: Graphics2D, font: Font, text: String, maxWidth: Int) {
        graphics.font = font
        val width = graphics.fontMetrics.stringWidth(text)
        if (width > maxWidth) {
            val correctedFontSize = font.size.toDouble() * maxWidth / width
            graphics.font = font.deriveFont(correctedFontSize.toFloat())
        }
    }

    data class Marker(
        val name: String,
        val x: Int,
        val y: Int,
        val size: Int,
        val info1: String,
        val info2: String,
        val majAx: Int = size,
        val minAx: Int = size,
        val posAngle: Double = 0.0
    )

    fun formatDegreesToHMS(degrees: Double): String {
        val totalSeconds = (degrees / 360 * 24 * 3600)
        val h = (totalSeconds / 3600).toLong()
        val m = (totalSeconds % 3600).toLong() / 60
        val s = totalSeconds % 60

        return String.format(Locale.US, "%02dh%02dm%04.1fs", h, m, s)
    }

    fun formatDegreesToDMS(degrees: Double): String {
        val totalSeconds = degrees.absoluteValue * 3600
        val d = (totalSeconds / 3600).toLong()
        val m = (totalSeconds % 3600).toLong() / 60
        val s = totalSeconds % 60
        val sign = if (degrees > 0.0) '+' else '-'

        return String.format(Locale.US, "%s%02d°%02d'%04.1f\"", sign, d, m, s)
    }}
