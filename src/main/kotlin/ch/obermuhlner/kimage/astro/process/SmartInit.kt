package ch.obermuhlner.kimage.astro.process

import ch.obermuhlner.kimage.core.image.io.ImageReader
import ch.obermuhlner.kimage.core.image.values.values
import ch.obermuhlner.kimage.core.math.clamp
import ch.obermuhlner.kimage.core.math.median
import nom.tam.fits.Fits
import nom.tam.fits.ImageHDU
import java.io.File

data class SmartInitDetection(
    val inputExtension: String,
    val inputDirectory: String,
    val debayerEnabled: Boolean,
    val bayerPattern: String,
    val biasDirectory: String,
    val darkDirectory: String,
    val flatDirectory: String,
    val darkflatDirectory: String,
    val sigmoidMidpoint: Double,
    val targetName: String?,
    val lightFileCount: Int,
)

private val KNOWN_IMAGE_EXTENSIONS = setOf("fit", "fits", "tif", "tiff", "jpg", "jpeg", "png", "cr2", "nef", "arw", "orf")
private val PRE_DEBAYERED_EXTENSIONS = setOf("tif", "tiff", "jpg", "jpeg", "png")
private val FITS_EXTENSIONS = setOf("fit", "fits")
private val LIGHT_SUBDIRECTORY_LOWER_NAMES = setOf("light", "lights")

private val BIAS_ALIASES = listOf("bias", "biases")
private val DARK_ALIASES = listOf("dark", "darks")
private val FLAT_ALIASES = listOf("flat", "flats")
private val DARKFLAT_ALIASES = listOf("darkflat", "darkflats", "dark_flat", "dark_flats")

internal fun detectLightFiles(workingDir: File): Pair<List<File>, String> {
    val allEntries = workingDir.listFiles().orEmpty()

    val rootFiles = allEntries.filter { it.isFile && it.extension.lowercase() in KNOWN_IMAGE_EXTENSIONS }
    if (rootFiles.isNotEmpty()) return rootFiles to "."

    val lightSubdir = allEntries.firstOrNull { it.isDirectory && it.name.lowercase() in LIGHT_SUBDIRECTORY_LOWER_NAMES }
    if (lightSubdir != null) {
        val files = lightSubdir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in KNOWN_IMAGE_EXTENSIONS }
            .orEmpty()
        if (files.isNotEmpty()) return files to lightSubdir.name
    }

    return emptyList<File>() to "."
}

internal fun findCalibrationDir(workingDir: File, aliases: List<String>): String? {
    val lowerAliases = aliases.map { it.lowercase() }.toSet()
    return workingDir.listFiles()
        ?.firstOrNull { it.isDirectory && it.name.lowercase() in lowerAliases }
        ?.name
}

data class FitsHeaderInfo(
    val bayerPattern: String?,
    val targetName: String?,
    val isPreDebayered: Boolean,
)

internal fun readFitsHeaderInfo(file: File): FitsHeaderInfo {
    return try {
        Fits(file).use { fits ->
            val hdu = fits.getHDU(0)
            if (hdu is ImageHDU) {
                val header = hdu.header
                val bayerPat = header.getStringValue("BAYERPAT")?.trim()?.uppercase()
                val target = (header.getStringValue("OBJECT") ?: header.getStringValue("TARGET"))
                    ?.trim()?.ifBlank { null }
                FitsHeaderInfo(bayerPat, target, isPreDebayered = hdu.axes.size == 3)
            } else {
                FitsHeaderInfo(null, null, false)
            }
        }
    } catch (_: Exception) {
        FitsHeaderInfo(null, null, false)
    }
}

internal fun computeSigmoidMidpoint(lightFiles: List<File>): Double {
    if (lightFiles.isEmpty()) return 0.01
    val medians = lightFiles.mapNotNull { file ->
        try {
            val image = ImageReader.read(file)
            val sampled = image.values().filterIndexed { i, _ -> i % 64 == 0 }.toList().toDoubleArray()
            if (sampled.isEmpty()) null else sampled.median()
        } catch (_: Exception) {
            null
        }
    }
    if (medians.isEmpty()) return 0.01
    return clamp(medians.toDoubleArray().median(), 0.001, 0.3)
}

fun detectSmartInitConfig(workingDir: File = File(".")): SmartInitDetection {
    val (lightFiles, inputDirectory) = detectLightFiles(workingDir)

    val inputExtension = lightFiles
        .groupBy { it.extension.lowercase() }
        .maxByOrNull { it.value.size }?.key ?: "fit"

    val biasDirectory = findCalibrationDir(workingDir, BIAS_ALIASES) ?: "bias"
    val darkDirectory = findCalibrationDir(workingDir, DARK_ALIASES) ?: "dark"
    val flatDirectory = findCalibrationDir(workingDir, FLAT_ALIASES) ?: "flat"
    val darkflatDirectory = findCalibrationDir(workingDir, DARKFLAT_ALIASES) ?: "darkflat"

    var bayerPattern = "RGGB"
    var debayerEnabled = true
    var targetName: String? = null

    when (inputExtension.lowercase()) {
        in PRE_DEBAYERED_EXTENSIONS -> debayerEnabled = false
        in FITS_EXTENSIONS -> lightFiles.firstOrNull()?.let { sampleFile ->
            val info = readFitsHeaderInfo(sampleFile)
            if (info.bayerPattern != null) bayerPattern = info.bayerPattern
            if (info.isPreDebayered) debayerEnabled = false
            targetName = info.targetName
        }
    }

    return SmartInitDetection(
        inputExtension = inputExtension,
        inputDirectory = inputDirectory,
        debayerEnabled = debayerEnabled,
        bayerPattern = bayerPattern,
        biasDirectory = biasDirectory,
        darkDirectory = darkDirectory,
        flatDirectory = flatDirectory,
        darkflatDirectory = darkflatDirectory,
        sigmoidMidpoint = computeSigmoidMidpoint(lightFiles.take(3)),
        targetName = targetName,
        lightFileCount = lightFiles.size,
    )
}

private fun formatMidpoint(d: Double): String = "%.4f".format(d)

fun generateSmartConfigText(detection: SmartInitDetection): String {
    var text = defaultAstroProcessConfigText

    // R3: debayer enabled/disabled
    if (!detection.debayerEnabled) {
        text = text.replace(
            "    enabled: true               # Convert Bayer pattern to RGB (disable if already RGB)",
            "    enabled: false              # Convert Bayer pattern to RGB (disable if already RGB)"
        )
    }

    // R3: Bayer pattern
    text = text.replace("bayerPattern: RGGB", "bayerPattern: ${detection.bayerPattern}")

    // R1: input extension
    text = text.replace("inputImageExtension: fit", "inputImageExtension: ${detection.inputExtension}")

    // R1: input directory (inject before inputImageExtension when lights are in a subdirectory)
    if (detection.inputDirectory != ".") {
        text = text.replace(
            "  inputImageExtension:",
            "  inputDirectory: ${detection.inputDirectory}\n  inputImageExtension:"
        )
    }

    // R4: sigmoid midpoint — replace only the first occurrence (the initial stretch sigmoid)
    text = text.replaceFirst(
        Regex("""(\s{6}midpoint: )0\.01(\s+# Midpoint of S-curve)"""),
        "$1${formatMidpoint(detection.sigmoidMidpoint)}$2"
    )

    // R2: inject calibrate section before the enhance section when any directory name differs from default
    val hasNonDefaultCalib = detection.biasDirectory != "bias" ||
        detection.darkDirectory != "dark" ||
        detection.flatDirectory != "flat" ||
        detection.darkflatDirectory != "darkflat"

    if (hasNonDefaultCalib) {
        val calibSection = buildString {
            appendLine("# Calibration Configuration - Directories detected during init")
            appendLine("calibrate:")
            appendLine("  biasDirectory: ${detection.biasDirectory}")
            appendLine("  darkDirectory: ${detection.darkDirectory}")
            appendLine("  flatDirectory: ${detection.flatDirectory}")
            appendLine("  darkflatDirectory: ${detection.darkflatDirectory}")
            appendLine()
        }
        text = text.replace("# Enhancement Pipeline", "${calibSection}# Enhancement Pipeline")
    }

    // R6: target name in annotation title
    if (detection.targetName != null) {
        text = text.replace("    title: \"Object Name\"", "    title: \"${detection.targetName}\"")
    }

    return text
}

fun printDetectionSummary(detection: SmartInitDetection) {
    println("Smart init detected:")
    println("  Light frames : ${detection.lightFileCount} *.${detection.inputExtension} in '${detection.inputDirectory}'")
    println("  Debayer      : ${if (detection.debayerEnabled) "enabled (pattern: ${detection.bayerPattern})" else "disabled (already RGB/debayered)"}")
    println("  Calibration  : bias='${detection.biasDirectory}', dark='${detection.darkDirectory}', flat='${detection.flatDirectory}', darkflat='${detection.darkflatDirectory}'")
    println("  Sigmoid      : midpoint=${formatMidpoint(detection.sigmoidMidpoint)}")
    if (detection.targetName != null) println("  Target       : ${detection.targetName}")
    if (detection.lightFileCount == 0) println("  WARNING: No light frames found. Please set inputImageExtension in the generated config.")
}
