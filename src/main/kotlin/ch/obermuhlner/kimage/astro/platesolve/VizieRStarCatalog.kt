package ch.obermuhlner.kimage.astro.platesolve

import ch.obermuhlner.kimage.astro.align.Star
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Interface for star catalogs.
 */
interface StarCatalog {
    fun getStars(ra: Double, dec: Double, radius: Double): List<Star>
}

/**
 * A StarCatalog implementation that fetches data from VizieR and caches it locally.
 */
class VizieRStarCatalog(
    private val catalogId: String = "I/355/gaiadr3", // Gaia DR3
    private val cacheDir: File = File(System.getProperty("user.home"), ".kimage-astro-process/star-catalog")
) : StarCatalog {

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    override fun getStars(ra: Double, dec: Double, radius: Double): List<Star> {
        val query = "-c.ra=$ra&-c.dec=$dec&-c.rd=$radius&-out.max=1000&-out=_RA&-out=_DE"
        val cacheFile = File(cacheDir, "vizier_${catalogId.replace("/", "_")}_${ra}_${dec}_${radius}.csv")

        if (!cacheFile.exists()) {
            println("Downloading star catalog data for RA=$ra Dec=$dec Radius=$radius...")
            val urlString = "https://vizier.cds.unistra.fr/viz-bin/asu-tsv?-source=$catalogId&$query"
            try {
                val connection = URL(urlString).openConnection()
                connection.connect()
                val content = connection.getInputStream().bufferedReader().use { it.readText() }
                cacheFile.writeText(content)
            } catch (e: Exception) {
                println("Error downloading from VizieR: ${e.message}")
                return emptyList()
            }
        }

        return parseVizieRTSV(cacheFile)
    }

    private fun parseVizieRTSV(file: File): List<Star> {
        val stars = mutableListOf<Star>()
        var dataStarted = false
        file.forEachLine { line ->
            if (line.startsWith("---")) {
                dataStarted = true
                return@forEachLine
            }
            if (!dataStarted) {
                return@forEachLine
            }
            
            val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.trim() }
            if (parts.size >= 2) {
                val ra = parts[0].toDoubleOrNull()
                val dec = parts[1].toDoubleOrNull()
                val mag = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 20.0 else 20.0
                if (ra != null && dec != null) {
                    val brightness = (20.0 - mag).coerceIn(0.0, 20.0) / 20.0
                    stars.add(Star(ra, dec, brightness, 1.0, 1.0))
                } else {
                    // println("Failed to parse RA/Dec from parts: $parts")
                }
            } else {
                // println("Too few parts in line: $line -> $parts")
            }
        }
        return stars
    }
}
