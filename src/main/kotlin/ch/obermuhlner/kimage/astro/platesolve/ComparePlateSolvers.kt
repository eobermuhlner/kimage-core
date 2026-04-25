package ch.obermuhlner.kimage.astro.platesolve

import ch.obermuhlner.kimage.astro.annotate.DeepSkyObjects
import ch.obermuhlner.kimage.astro.annotate.WCSConverter
import ch.obermuhlner.kimage.core.image.io.ImageReader
import java.io.File

fun main() {
    val imagePath = "D:/Photos/Astro/ASIAIR/2026-04-19 NGC2244/NGC 2244 Satellite C/astro-process/aligned/Light_NGC 2244 Satellite C_180.0s_Bin1_533MC_gain100_20260420-213426_150deg_-10.0C_0001.tif"
    val imageFile = File(imagePath)
    
    if (!imageFile.exists()) {
        println("Image file not found: $imagePath")
        return
    }

    println("Reading image: ${imageFile.name}...")
    val image = ImageReader.read(imageFile)

    // Hint for internal solver: ASTAP result
    val searchRa = 97.98001232935
    val searchDec = 4.949946856351
    println("Target hint (from ASTAP): RA=$searchRa, Dec=$searchDec")

    println("\n--- Solving with ASTAP ---")
    val astapSolver = AstapPlateSolver("C:/Program Files/astap/astap_cli.exe")
    val astapResult = astapSolver.solve(image, imageFile, searchRa, searchDec)
    
    println("\n--- Solving with Internal Solver (Gaia DR3) ---")
    val internalSolver = InternalPlateSolver(VizieRStarCatalog("I/355/gaiadr3"))
    val internalResult = internalSolver.solve(image, imageFile, searchRa, searchDec, 2.0)

    println("\n=== Comparison ===")
    val keys = ( (astapResult?.keys ?: emptySet()) + (internalResult?.keys ?: emptySet()) ).sorted()
    
    println(String.format("%-10s | %-30s | %-30s", "Key", "ASTAP", "Internal"))
    println("-".repeat(76))
    for (key in keys) {
        val v1 = astapResult?.get(key) ?: "N/A"
        val v2 = internalResult?.get(key) ?: "N/A"
        println(String.format("%-10s | %-30s | %-30s", key, v1, v2))
    }

    println("\n=== Verification (Center Pixel) ===")
    val w = image.width.toDouble()
    val h = image.height.toDouble()
    val testPoints = listOf(
        Pair(0.0, 0.0),
        Pair(w/2, h/2),
        Pair(w, h)
    )

    for ((px, py) in testPoints) {
        println("Pixel ($px, $py):")
        if (astapResult != null) {
            val conv = WCSConverter(astapResult)
            val (ra, dec) = conv.convertXYToRADec(px, py)
            println(String.format("  ASTAP    : RA=%10.6f, Dec=%10.6f", ra, dec))
        }
        if (internalResult != null) {
            val conv = WCSConverter(internalResult)
            val (ra, dec) = conv.convertXYToRADec(px, py)
            println(String.format("  Internal : RA=%10.6f, Dec=%10.6f", ra, dec))
        }
    }
}
