package ch.obermuhlner.kimage.astro.platesolve

import ch.obermuhlner.kimage.core.image.Image
import java.io.File

interface PlateSolver {
    fun solve(image: Image, inputFile: File, searchRa: Double? = null, searchDec: Double? = null, searchRadius: Double? = null): Map<String, String>?
}
