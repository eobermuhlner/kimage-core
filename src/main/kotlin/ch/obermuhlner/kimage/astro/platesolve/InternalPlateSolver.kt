package ch.obermuhlner.kimage.astro.platesolve

import ch.obermuhlner.kimage.astro.align.Star
import ch.obermuhlner.kimage.astro.align.calculateTransformationMatrix
import ch.obermuhlner.kimage.astro.align.decomposeTransformationMatrix
import ch.obermuhlner.kimage.astro.align.findStars
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.Matrix
import ch.obermuhlner.kimage.core.matrix.linearalgebra.invert
import java.io.File
import kotlin.math.*

class InternalPlateSolver(
    private val catalog: StarCatalog = VizieRStarCatalog()
) : PlateSolver {

    override fun solve(image: Image, inputFile: File, searchRa: Double?, searchDec: Double?, searchRadius: Double?): Map<String, String>? {
        if (searchRa == null || searchDec == null) {
            return null
        }

        val radius = searchRadius ?: 0.5 // Very tight radius

        // 1. Fetch catalog stars
        val catalogStars = catalog.getStars(searchRa, searchDec, radius)
            .sortedByDescending { it.brightness } // Brightest first
            .take(20)
        println("Catalog stars used: ${catalogStars.size}")


        // 2. Project catalog stars to a flat plane using Gnomonic projection
        // We use (searchRa, searchDec) as the projection center.
        // X and Y will be in radians.
        // We scale them by a large factor for numerical stability in the affine fit.
        val scaleFactor = 10000.0
        val projectedCatalogStars = catalogStars.map { catalogStar ->
            val (x, y) = GnomonicProjection.project(catalogStar.x, catalogStar.y, searchRa, searchDec)
            Star(x * scaleFactor, y * scaleFactor, catalogStar.brightness, 1.0, 1.0)
        }

        // 3. Find stars in the image
        val imageStars = findStars(image)
            .sortedByDescending { it.brightness } // Brightest first
            .take(20)
        if (imageStars.isEmpty()) {
            println("No stars found in image")
            return null
        }
        println("Image stars used: ${imageStars.size}")
        // 4. Calculate transformation matrix
        // We pass 0, 0 for dimensions to avoid centering pixels using radian-based logic.
        val transformScaled = calculateTransformationMatrix(
            projectedCatalogStars,
            imageStars,
            0,
            0,
            angleTolerance = 0.001,
            maxIterations = 20000,
            positionTolerance = 0.1 // ~2 pixels
        ) ?: return null
        
        println("Found scaled transformation matrix:")
        for (row in 0..2) {
            println("  [${transformScaled[row, 0]}, ${transformScaled[row, 1]}, ${transformScaled[row, 2]}]")
        }


        
        // De-scale the transform: image_pixel -> radians
        // AlignStars uses V_new = T * V_old, so T is:
        // [ a  b  c ]
        // [ d  e  f ]
        // [ 0  0  1 ]
        // Where x' = ax + by + c, y' = dx + ey + f
        val transform = DoubleMatrix(3, 3)
        for (row in 0..1) {
            for (col in 0..2) {
                transform[row, col] = transformScaled[row, col] / scaleFactor
            }
        }
        transform[2, 2] = 1.0

        // 5. Generate WCS Map
        val res = mutableMapOf<String, String>()
        
        val radToDeg = 180.0 / Math.PI
        
        // Let's use image center as CRPIX
        val crpix1 = image.width / 2.0
        val crpix2 = image.height / 2.0
        
        // Find RA/Dec at CRPIX using the transformation: x' = ax + by + c
        val xRadCenter = transform[0, 0] * crpix1 + transform[0, 1] * crpix2 + transform[0, 2]
        val yRadCenter = transform[1, 0] * crpix1 + transform[1, 1] * crpix2 + transform[1, 2]
        val (raCenter, decCenter) = GnomonicProjection.unproject(xRadCenter, yRadCenter, searchRa, searchDec)

        res["RA"] = raCenter.toString()
        res["DEC"] = decCenter.toString()
        res["CTYPE1"] = "RA---TAN"
        res["CTYPE2"] = "DEC--TAN"
        res["CRVAL1"] = raCenter.toString()
        res["CRVAL2"] = decCenter.toString()
        res["CRPIX1"] = crpix1.toString()
        res["CRPIX2"] = crpix2.toString()

        // CD matrix mapping:
        // x_deg = (x_img - CRPIX1) * CD1_1 + (y_img - CRPIX2) * CD1_2
        // x_rad * 57.3 = (x_img - CRPIX1) * a * 57.3 + (y_img - CRPIX2) * b * 57.3
        res["CD1_1"] = (transform[0, 0] * radToDeg).toString()
        res["CD1_2"] = (transform[0, 1] * radToDeg).toString()
        res["CD2_1"] = (transform[1, 0] * radToDeg).toString()
        res["CD2_2"] = (transform[1, 1] * radToDeg).toString()

        // Add secondary keywords for WCSConverter compatibility
        val cd1_1 = transform[0, 0] * radToDeg
        val cd1_2 = transform[0, 1] * radToDeg
        val cd2_1 = transform[1, 0] * radToDeg
        val cd2_2 = transform[1, 1] * radToDeg
        
        val scaleX = sqrt(cd1_1 * cd1_1 + cd2_1 * cd2_1)
        val scaleY = sqrt(cd1_2 * cd1_2 + cd2_2 * cd2_2)
        val rotation = Math.toDegrees(atan2(cd2_1, cd1_1))

        res["CDELT1"] = scaleX.toString()
        res["CDELT2"] = scaleY.toString()
        res["CROTA1"] = rotation.toString()
        res["CROTA2"] = rotation.toString()

        return res
    }

    private fun angularDistance(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Double {
        val r1 = Math.toRadians(ra1)
        val d1 = Math.toRadians(dec1)
        val r2 = Math.toRadians(ra2)
        val d2 = Math.toRadians(dec2)
        
        return Math.toDegrees(acos(sin(d1) * sin(d2) + cos(d1) * cos(d2) * cos(r1 - r2)))
    }
}
